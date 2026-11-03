package com.example.inventorymaster.ai.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.inventorymaster.ai.provider.AiMessage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地大模型运行时（LiteRT-LM）：app 级单例，Engine 按 modelPath 缓存常驻——
 * ChatViewModel 因 provider 配置变更重建 LocalAiService 时不会重载模型。
 *
 * 职责边界：这里只做纯文本进出。工具协议由 AiRuntime 按 FC=false 路由到
 * JsonToolCallStrategy（systemPrompt 已内嵌 JSON 工具协议），本地模型一律
 * functionCalling=false/streaming=false（ModelCapabilityCatalog.resolve 的 isLocal 短路）。
 *
 * 消息映射：Gemma/Qwen 的对话模板只有 user/model 两类轮次，故
 * tool 结果映射为 user 轮（「工具结果：」前缀）、连续同轮合并、
 * assistant 带 toolCalls 时以其 JSON 形态还原为 model 轮（保持模型自见的格式一致）。
 *
 * initialize() 首次加载 10s+ 且占内存 ≈ 模型体积，全路径走 Mutex 串行；
 * 切换模型（不同 modelPath）时先 close 旧 Engine。
 *
 * KV 复用（按线程 Conversation 复用）：Engine 之上再缓存一个「活跃 Conversation」，
 * 以内容指纹 diff 决定增量喂还是全量重建——AiRuntime 每轮全量重发历史，本类把
 * toHistoryMessages 映射后的 (role,text) 序列与上次已喂指纹做前缀比对，命中则只
 * sendMessage(delta)（正常恒为最后 1 条输入），未命中则按旧行为全量重建（fallback
 * 永远等于历史行为）。指纹能精确对齐的前提：AiRuntime 存进 memory 的 assistant
 * content 就是模型原始输出，映射时非空 content 原样保留（见 toHistoryMessages）。
 * 只保 1 个活跃会话（Qwen3-4B KV ≈147KB/token，8192 封顶 ≈1.2GB），切线程/删线程/
 * 系统提示词变化/用户停止/换模型一律丢弃重建；闲置超时与窗口将满也主动丢弃。
 */
object LocalLlmRuntime {

    private const val TAG = "LocalLlmRuntime"

    /**
     * 内存护栏（实测教训：小米 MIUI 对第三方应用有 6GB PSS 硬上限，前台也杀——
     * Qwen3-4B 默认配置下 PSS 冲到 8.09GB 被 MiuiSentinelMemoryManager 击杀）：
     * - maxNumTokens 封顶 KV cache（Qwen3-4B 约 147KB/token，8192 ≈ 1.2GB）；
     * - maxOutputToken 封顶单轮生成长度（同时避免 CPU 上无限生成导致"等很久"）；
     * - cacheDir 把 XNNPack weight cache 迁出 files/models（该 cache 体积≈权重，
     *   且映射进进程 PSS），放 cacheDir 便于系统回收。
     */
    private const val MAX_NUM_TOKENS = 8192
    private const val MAX_OUTPUT_TOKENS = 1024

    private const val BACKEND_GPU = "GPU"
    private const val BACKEND_CPU = "CPU"

    /** 活跃会话闲置超过该时长即丢弃（KV ≈1.2GB 不长期驻留，下次调用重新全量 prefill） */
    private const val CONVERSATION_IDLE_DROP_MS = 5 * 60_000L

    /** 窗口预丢弃余量：已用 token 超过 maxNumTokens − maxOutputToken − 该值 即主动丢弃会话 */
    private const val WINDOW_HEADROOM_TOKENS = 512

    /** 本地小模型采样：温度调至 0.7（Qwen 推荐值），避免过低温度陷入高概率吸引子死循环复读 */
    private val samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)

    private var appContext: Context? = null
    private var engine: Engine? = null
    private var engineBackend: String? = null
    private var loadedPath: String? = null
    private val mutex = Mutex()

    // ---- 活跃会话（KV 复用）状态：全部仅在 mutex 内读写，dropActive 时一并清空 ----
    // activeConversation 加 @Volatile：interruptActiveGeneration 在 mutex 外跨线程读它
    @Volatile
    private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null
    private var fedFps: List<TurnFp> = emptyList()
    private var fedSystemHash: Int? = null
    private var lastConversationUseMs: Long = 0L

    /** 用户已请求停止（interruptActiveGeneration 置位，由持有 mutex 的生成协程消费后清除） */
    @Volatile
    private var interruptRequested = false

    /**
     * 用户点「停止」时的主动中断：立刻打断原生侧生成，不等协程取消传播到下一个挂起点
     * （协程取消只能在挂起点生效，recreate 的 prefill 长达数秒且中途无 token 产出，
     * 被动等待会让停止按钮"点了没反应"）。cancelProcess 是 SDK 的跨线程停止 API，
     * 可在任意线程调用；对不存在/已完成的会话无害。
     */
    fun interruptActiveGeneration() {
        interruptRequested = true
        runCatching { activeConversation?.takeIf { it.isAlive }?.cancelProcess() }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isReady(modelPath: String): Boolean =
        loadedPath == modelPath && engine?.isInitialized() == true

    /**
     * GPU 优先加载：权重驻 GPU 显存（dmabuf），不经应用 PSS 记账——MIUI 6GB PSS 硬闸的
     * 结构性解法（实测 CPU 后端 Gemma E2B PSS 峰值 5.99GB / Qwen3-4B 7.7GB，双双贴线或超线）。
     * GPU 初始化失败自动回退 CPU；GPU 推理中途失败在 [chat] 里重试一次 CPU。
     */
    suspend fun ensureLoaded(modelPath: String, preferGpu: Boolean = true): Result<Unit> =
        mutex.withLock {
            if (isReady(modelPath)) return@withLock Result.success(Unit)
            withContext(Dispatchers.IO) {
                runCatching { swapEngine(modelPath, preferGpu) }
                    .recoverCatching { gpuError ->
                        if (!preferGpu) throw gpuError
                        Log.w(TAG, "GPU backend init failed, falling back to CPU", gpuError)
                        try {
                            swapEngine(modelPath, preferGpu = false)
                        } catch (cpuError: Exception) {
                            Log.e(TAG, "CPU fallback failed too", cpuError)
                            throw cpuError
                        }
                    }
            }
        }

    private fun swapEngine(modelPath: String, preferGpu: Boolean) {
        dropActive("engine-swap")
        engine?.let { old ->
            Log.i(TAG, "Releasing previous local engine: $loadedPath")
            old.close()
        }
        engine = null
        engineBackend = null
        loadedPath = null

        // cacheDir 迁移前的旧版把 XNNPack weight cache（≈模型体积）落在模型同目录，清掉
        File(modelPath).parentFile?.listFiles()
            ?.filter { it.name.endsWith(".xnnpack_cache") }
            ?.forEach { it.delete() }

        val backend = if (preferGpu) Backend.GPU() else Backend.CPU()
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = MAX_NUM_TOKENS,
                cacheDir = appContext?.cacheDir?.absolutePath
            )
        )
        try {
            newEngine.initialize()
        } catch (e: Exception) {
            runCatching { newEngine.close() }
            throw e
        }
        engine = newEngine
        engineBackend = if (preferGpu) BACKEND_GPU else BACKEND_CPU
        loadedPath = modelPath
        Log.i(TAG, "Local model loaded: $modelPath backend=$engineBackend maxNumTokens=$MAX_NUM_TOKENS")
    }

    /**
     * 单轮对话：messages 为 AiRuntime 全量重发的轮次列表（最后一条是本轮输入——
     * user 提问或 tool 结果），history 进 initialMessages，最后一条经 sendMessage 触发生成。
     * 推理全程在 Dispatchers.Default——调用方是 viewModelScope（Main），native 推理
     * 留在主线程会把 UI 卡死整个生成期（实测"发送时卡卡的"根因）。
     */
    suspend fun chat(
        modelPath: String,
        messages: List<AiMessage>,
        systemPrompt: String?,
        allowGpu: Boolean = true
    ): String {
        ensureLoaded(modelPath, preferGpu = allowGpu).getOrElse { e ->
            throw IllegalStateException("本地模型加载失败: ${e.message}", e)
        }
        return withContext(Dispatchers.Default) {
            try {
                chatOnce(modelPath, messages, systemPrompt)
            } catch (e: Exception) {
                if (allowGpu && engineBackend == BACKEND_GPU) {
                    Log.w(TAG, "GPU inference failed, retrying once on CPU", e)
                    release()
                    chat(modelPath, messages, systemPrompt, allowGpu = false)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun chatOnce(
        modelPath: String,
        messages: List<AiMessage>,
        systemPrompt: String?
    ): String = mutex.withLock {
        val activeEngine = engine
        check(activeEngine != null && isReady(modelPath)) { "本地引擎未初始化" }

        val lease = acquireConversation(activeEngine, messages, systemPrompt)
        try {
            val reply = lease.conversation.sendMessage(lease.deltaText).extractText()
            val collapseIdx = findRepetitionCollapseIndex(reply)
            val finalReply = if (collapseIdx >= 0) {
                Log.w(TAG, "Repetition collapse detected in chatOnce at index $collapseIdx, dropping KV")
                dropActive("repetition-collapsed")
                reply.substring(0, collapseIdx).trimEnd()
            } else {
                reply
            }
            if (interruptRequested) {
                dropActive("interrupted")
                throw CancellationException("user stop")
            }
            if (collapseIdx < 0) {
                commitSuccess(lease, finalReply)
            }
            finalReply
        } catch (e: Exception) {
            // 会话状态自此不可信（可能只 prefill 了半截），丢弃让下次全量重建
            dropActive("send-failed: ${e.message?.take(80)}")
            throw e
        }
    }

    /**
     * 流式版单轮对话：sendMessageAsync 的 Flow 逐 token 发增量，调用方（AiRuntime）
     * 实时下发 AnswerDelta 实现打字机上屏。GPU 回退与 [ensureLoaded] 一致；
     * 生成中途失败不自动重试（部分文本已外显，重试会重复——上层按错误处理，用户手动重发）。
     * mutex 跨整个生成期持有：模型切换/释放会排队到流结束后执行。
     * 成功结束后会话保留供 KV 复用；取消/失败/退化坍塌则丢弃（半截或异常生成的 KV 状态不可信）。
     */
    fun chatStream(
        modelPath: String,
        messages: List<AiMessage>,
        systemPrompt: String?,
        allowGpu: Boolean = true
    ): Flow<String> = channelFlow {
        ensureLoaded(modelPath, preferGpu = allowGpu).getOrElse { e ->
            throw IllegalStateException("本地模型加载失败: ${e.message}", e)
        }
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val activeEngine = engine
                check(activeEngine != null && isReady(modelPath)) { "本地引擎未初始化" }

                val lease = acquireConversation(activeEngine, messages, systemPrompt)
                var collapsed = false
                try {
                    val startMs = SystemClock.elapsedRealtime()
                    var firstChunkLogged = false
                    val reply = StringBuilder()
                    lease.conversation.sendMessageAsync(lease.deltaText).collect { msg ->
                        if (collapsed) return@collect
                        val text = msg.extractText()
                        if (text.isNotEmpty()) {
                            if (!firstChunkLogged) {
                                Log.i(
                                    TAG,
                                    "first chunk in ${SystemClock.elapsedRealtime() - startMs}ms " +
                                        "reused=${lease.reused}"
                                )
                                firstChunkLogged = true
                            }
                            reply.append(text)
                            val collapseIdx = findRepetitionCollapseIndex(reply.toString())
                            if (collapseIdx >= 0) {
                                Log.w(TAG, "Repetition collapse detected in chatStream at index $collapseIdx, aborting")
                                collapsed = true
                                runCatching { lease.conversation.cancelProcess() }
                            } else {
                                send(text)
                            }
                        }
                    }
                    if (interruptRequested) {
                        dropActive("interrupted")
                        throw CancellationException("user stop")
                    }
                    if (collapsed) {
                        dropActive("repetition-collapsed")
                    } else {
                        commitSuccess(lease, reply.toString())
                    }
                } catch (ce: CancellationException) {
                    if (collapsed) {
                        dropActive("repetition-collapsed")
                        return@withLock
                    }
                    // 用户停止/超时：KV 只含半截生成，状态失真，丢弃
                    dropActive("cancelled")
                    throw ce
                } catch (e: Exception) {
                    dropActive("stream-failed: ${e.message?.take(80)}")
                    throw e
                }
            }
        }
    }

    /**
     * 检测小模型退化坍塌（死循环复读/标点狂吐）。
     * 返回坍塌发生的起始字符下标；若未发生则返回 -1。
     */
    internal fun findRepetitionCollapseIndex(text: String): Int {
        if (text.length < 8) return -1

        // 1. 单字符连续重复 >= 8 次（排除标尺 - = * #、空白换行、下划线及纯数字如条码 00000000）
        val ignoredChars = setOf('-', '=', '*', '#', ' ', '\t', '\n', '\r', '_')
        val lastChar = text.last()
        if (lastChar !in ignoredChars && !lastChar.isDigit()) {
            var startPos = text.length - 1
            while (startPos > 0 && text[startPos - 1] == lastChar) {
                startPos--
            }
            if (text.length - startPos >= 8) {
                return startPos
            }
        }

        // 2. 短词片段 (长度 2..8) 连续重复 >= 4 次
        for (len in 2..8) {
            if (text.length < len * 4) continue
            val pattern = text.takeLast(len)
            if (pattern.all { it in ignoredChars || it.isDigit() }) continue
            var matchCount = 1
            var pos = text.length - len
            while (pos >= len) {
                val prev = text.substring(pos - len, pos)
                if (prev == pattern) {
                    matchCount++
                    pos -= len
                } else {
                    break
                }
            }
            if (matchCount >= 4) {
                return pos
            }
        }

        return -1
    }

    suspend fun release() = mutex.withLock {
        dropActive("release")
        engine?.close()
        engine = null
        engineBackend = null
        loadedPath = null
        Log.i(TAG, "Local engine released")
    }

    /** 历史 → litertlm 轮次：system 归 systemInstruction；tool→user；assistant 带 toolCalls 时以其 JSON/原文形态还原为 model 轮 */
    internal fun toHistoryMessages(messages: List<AiMessage>): List<Message> =
        mergeTurns(messages).map { (role, text) ->
            if (role == "model") Message.model(text) else Message.user(text)
        }

    /**
     * 消息流 → 合并后的 (role, text) 轮次序列（"user"/"model"）。
     * 既是 [toHistoryMessages] 的映射主体，也是 KV 复用指纹的来源——两者必须同源，
     * 指纹相等才能保证「会话里已有的」与「这次请求声称的」逐字一致。
     */
    internal fun mergeTurns(messages: List<AiMessage>): List<TurnFp> {
        val merged = mutableListOf<TurnFp>()
        for (msg in messages) {
            val text = when (msg.role) {
                "system" -> continue
                "user" -> msg.content.orEmpty()
                "tool" -> "工具结果：\n${msg.content.orEmpty()}"
                "assistant" -> msg.content.takeUnless { it.isNullOrBlank() }
                    ?: msg.toolCalls?.joinToString("\n") { tc ->
                        """{"tool":"${tc.name}","arguments":${tc.arguments}}"""
                    }
                    ?: ""
                else -> msg.content.orEmpty()
            }
            if (text.isBlank()) continue
            val role = if (msg.role == "assistant") "model" else "user"
            val last = merged.lastOrNull()
            if (last != null && last.role == role) {
                merged[merged.size - 1] = last.copy(text = last.text + "\n\n" + text)
            } else {
                merged += TurnFp(role, text)
            }
        }
        return merged
    }

    // ---- KV 复用核心 ----

    /** 轮次指纹：映射后的角色（"user"/"model"）+ 渲染文本 */
    internal data class TurnFp(val role: String, val text: String)

    internal sealed class ReusePlan {
        /** 增量喂：deltaText 是本轮要 sendMessage 的输入（最后 1 条指纹的文本，恒为 user 轮） */
        data class Reuse(val deltaText: String) : ReusePlan()

        /** 全量重建（未建过/任何不一致/异常边角——重建行为与无复用时完全一致） */
        object Recreate : ReusePlan()
    }

    /**
     * 复用决策（纯函数，可单测）：上次已喂指纹 [fedFps] 与本次全量指纹 [fullFps] 做前缀比对。
     * 严格相等、宁错杀重建——喂错上下文的代价远大于一次重复 prefill。
     */
    internal fun planReuse(
        fedSystemHash: Int?,
        fedFps: List<TurnFp>,
        systemHash: Int?,
        fullFps: List<TurnFp>
    ): ReusePlan {
        if (fedSystemHash != systemHash) return ReusePlan.Recreate
        if (fedFps.isEmpty()) return ReusePlan.Recreate
        if (fullFps.size <= fedFps.size) return ReusePlan.Recreate
        for (i in fedFps.indices) {
            if (fedFps[i] != fullFps[i]) return ReusePlan.Recreate
        }
        val delta = fullFps.drop(fedFps.size)
        if (delta.size != 1 || delta[0].role != "user" || delta[0].text.isBlank()) {
            return ReusePlan.Recreate
        }
        return ReusePlan.Reuse(delta[0].text)
    }

    private class Lease(
        val conversation: com.google.ai.edge.litertlm.Conversation,
        val deltaText: String,
        val reused: Boolean,
        /** 本次调用完成后的完整指纹（含本轮输入；模型回复在 commitSuccess 时追加） */
        val fullFps: List<TurnFp>
    )

    /** 取活跃会话：闲置超时先丢，再按指纹 diff 决定复用或重建。仅可在 mutex 内调用。 */
    private fun acquireConversation(
        activeEngine: Engine,
        messages: List<AiMessage>,
        systemPrompt: String?
    ): Lease {
        val now = SystemClock.elapsedRealtime()
        interruptRequested = false
        if (activeConversation != null && now - lastConversationUseMs > CONVERSATION_IDLE_DROP_MS) {
            dropActive("idle>${CONVERSATION_IDLE_DROP_MS / 1000}s")
        }

        val system = systemPrompt ?: messages.firstOrNull { it.role == "system" }?.content
        val systemHash = system?.hashCode()

        // 唯一真理：全量消息经 mergeTurns 归一化后的标准轮次序列
        val canonicalFps = mergeTurns(messages)
        check(canonicalFps.isNotEmpty()) { "消息列表为空或无法映射有效轮次" }

        when (val plan = planReuse(fedSystemHash, fedFps, systemHash, canonicalFps)) {
            is ReusePlan.Reuse -> {
                val conv = activeConversation
                    ?: return recreate(activeEngine, system, systemHash, canonicalFps)
                Log.i(TAG, "KV reuse hit: fed=${fedFps.size} full=${canonicalFps.size}")
                lastConversationUseMs = now
                return Lease(conv, plan.deltaText, reused = true, fullFps = canonicalFps)
            }
            is ReusePlan.Recreate ->
                Log.i(TAG, "KV recreate: reason=fingerprint-mismatch fed=${fedFps.size} full=${canonicalFps.size}")
        }
        return recreate(activeEngine, system, systemHash, canonicalFps)
    }

    private fun recreate(
        activeEngine: Engine,
        system: String?,
        systemHash: Int?,
        canonicalFps: List<TurnFp>
    ): Lease {
        dropActive("recreate")
        val historyFps = canonicalFps.dropLast(1)
        val inputText = canonicalFps.last().text
        val conversation = activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = system?.let { Contents.of(it) },
                initialMessages = historyFps.map { (role, text) ->
                    if (role == "model") Message.model(text) else Message.user(text)
                },
                samplerConfig = samplerConfig,
                maxOutputToken = MAX_OUTPUT_TOKENS
            )
        )
        activeConversation = conversation
        fedSystemHash = systemHash
        fedFps = historyFps
        return Lease(conversation, inputText, reused = false, fullFps = canonicalFps)
    }

    /** 生成成功后记账：指纹补上本轮输入与模型回复；顺带做窗口余量预丢弃。仅可在 mutex 内调用。 */
    private fun commitSuccess(lease: Lease, replyText: String) {
        fedFps = lease.fullFps +
            (if (replyText.isNotBlank()) listOf(TurnFp("model", replyText)) else emptyList())
        lastConversationUseMs = SystemClock.elapsedRealtime()
        val tokens = runCatching { lease.conversation.getTokenCount() }.getOrNull()
        if (tokens != null && tokens > MAX_NUM_TOKENS - MAX_OUTPUT_TOKENS - WINDOW_HEADROOM_TOKENS) {
            Log.i(TAG, "KV near window cap ($tokens tokens), dropping conversation")
            dropActive("window-cap")
        }
    }

    /** 丢弃活跃会话（KV 随之释放）。仅可在 mutex 内调用；对未建会话幂等。 */
    private fun dropActive(reason: String) {
        val conv = activeConversation ?: return
        activeConversation = null
        fedFps = emptyList()
        fedSystemHash = null
        // 必须先取消原生侧生成再 close——生成途中直接 close 是 use-after-free（实机闪退教训，
        // 旧流式路径一直这么干：cancelProcess 对已完成会话无害）
        runCatching { if (conv.isAlive) conv.cancelProcess() }
        runCatching { conv.close() }
        Log.i(TAG, "KV conversation dropped: $reason")
    }

    private fun Message.extractText(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
}
