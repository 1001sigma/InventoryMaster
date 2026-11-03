package com.example.inventorymaster.ai.runtime

import android.util.Log
import com.example.inventorymaster.ai.agent.ProductCardListCodec
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.agent.UserQuestionAnswer
import com.example.inventorymaster.ai.agent.strategy.FunctionCallingStrategy
import com.example.inventorymaster.ai.agent.strategy.JsonToolCallStrategy
import com.example.inventorymaster.ai.agent.strategy.ToolCallOutcome
import com.example.inventorymaster.ai.agent.strategy.ToolCallStrategy
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiErrorKind
import com.example.inventorymaster.ai.provider.AiResponse
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiServiceException
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.ai.provider.StreamChunk
import com.example.inventorymaster.ai.tools.AskQuestionTool
import com.example.inventorymaster.ai.tools.ToolErrorKind
import com.example.inventorymaster.ai.tools.ToolExecutor
import com.example.inventorymaster.ai.tools.ToolPermission
import com.example.inventorymaster.ai.tools.ToolPermissionManager
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.ai.tools.ToolResult
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeout
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 通用 Agent 运行时（AI_Runtime Phase 2）：与业务无关的工具循环。
 *
 * 职责：协议能力路由（FC/JSON）、流式调用与 tool_calls 分片聚合、循环防护
 * （同签名/同工具连调）、轮次与超时预算、CONFIRM 确认流、轮次制记忆生命周期。
 * 业务上下文（系统提示词、卡片持有、事件到 UI 的映射）由调用方注入/适配——
 * 参考库存侧的 InventoryAiAgent。
 *
 * 输出 [AiEvent] 流；对话轮的生命周期保证：任何退出路径（完成/失败/取消）
 * memory 轮次都会被补齐关闭，导出序列永远合法（tool 配对不拆散）。
 */
class AiRuntime(
    private val aiService: AiService,
    private val registry: ToolRegistry,
    private val memory: ConversationMemory,
    private val executor: ToolExecutor = ToolExecutor(registry),
    private val policies: Policies = Policies()
) {

    data class Policies(
        val maxRounds: Int = 5,
        val maxCallsPerRound: Int = 4,
        val llmTimeoutMs: Long = 90_000,
        val confirmTimeoutMs: Long = 30_000,
        val questionTimeoutMs: Long = 180_000,
        /** 同工具单调用连续 N 轮截断（4：给合理的参数修正重试留空间） */
        val sameToolStreakLimit: Int = 4
    )

    private val pendingConfirms = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pendingQuestions = ConcurrentHashMap<String, CompletableDeferred<UserQuestionAnswer>>()

    /** UI 对 AwaitingConfirmation 的答复 */
    fun respondConfirm(requestId: String, approved: Boolean) {
        pendingConfirms.remove(requestId)?.complete(approved)
    }

    /** UI 对 AwaitingQuestion 的答复（用户选择或跳过） */
    fun respondQuestion(requestId: String, answer: UserQuestionAnswer) {
        pendingQuestions.remove(requestId)?.complete(answer)
    }

    /**
     * 执行一轮对话。流式服务商逐块发 [AiEvent.AnswerDelta]（仅 FC 能力模型），
     * 工具调用分片聚合后按轮执行，全部结果经 ToolCallFinished 事件流出。
     */
    fun turn(userMessage: String, systemPrompt: String): Flow<AiEvent> = channelFlow {
        memory.openUserTurn(userMessage)
        val turnStartMs = SystemClock.elapsedRealtime()
        /** 整轮耗时（毫秒）：从用户消息进轮到最终回答（含工具执行/确认等待），随 Completed 与 memory 落库 */
        fun elapsedMs(): Long = SystemClock.elapsedRealtime() - turnStartMs
        // 本轮工具产出的产品卡片 JSON（随最终回复持久化；取消/异常路径不带卡片，与 UI 现场行为一致）
        var pendingCardJson: String? = null
        suspend fun finishTurn(answer: String) {
            val duration = elapsedMs()
            memory.closeTurn(answer, duration, aiService.modelId, pendingCardJson)
            send(AiEvent.Completed(answer, duration, aiService.modelId))
        }
        try {
            val strategy: ToolCallStrategy =
                if (aiService.capabilities.functionCalling) FunctionCallingStrategy()
                else JsonToolCallStrategy()

            val toolDefs = registry.getToolDefinitions()
            val sysPrompt = strategy.buildSystemPrompt(systemPrompt, toolDefs)
            val useTools = toolDefs.isNotEmpty()

            var lastSignature = ""
            var lastSingleToolName = ""
            var sameToolStreak = 0
            var jsonRetryUsed = false

            for (round in 0 until policies.maxRounds) {
                val response = try {
                    callModelRound({ send(it) }, sysPrompt, toolDefs, useTools)
                } catch (e: TimeoutCancellationException) {
                    throw AiServiceException(
                        AiErrorKind.TIMEOUT, "AI 响应超时（${aiService.chatTimeoutMs / 1000} 秒）", e
                    )
                }

                val outcome = if (useTools) strategy.extractOutcome(response, toolDefs)
                else ToolCallOutcome.FinalAnswer(response.content)

                when (outcome) {
                    is ToolCallOutcome.FinalAnswer -> {
                        finishTurn(outcome.content)
                        return@channelFlow
                    }

                    is ToolCallOutcome.MalformedJson -> {
                        // JSON 协议下模型想调工具但输出不合法：纠正重试一次，再失败按普通回答展示
                        if (jsonRetryUsed) {
                            finishTurn(outcome.raw)
                            return@channelFlow
                        }
                        jsonRetryUsed = true
                        Log.w(TAG, "JSON 模式输出无法解析，纠正重试: ${outcome.raw.take(100)}")
                        memory.appendAssistantMessage(outcome.raw)
                        memory.appendUserMessage(
                            "你上一条回复无法解析为工具调用 JSON。如需调用工具，请只输出一个 JSON 对象：" +
                                "{\"tool\": \"工具名\", \"arguments\": {参数}}，不要输出其他文字；" +
                                "如果不需要工具，请直接用自然语言回答。"
                        )
                    }

                    is ToolCallOutcome.ToolCalls -> {
                        val calls = outcome.calls.take(policies.maxCallsPerRound)

                        // 死循环检测必须在写入 memory 之前——截断路径不会执行工具，
                        // 若已写入 assistant(tool_calls) 而无 tool 结果，下一轮请求即 400
                        val signature = calls.joinToString(";") { it.name + ":" + normalizeArgs(it.arguments) }
                        if (signature == lastSignature) {
                            Log.w(TAG, "检测到完全相同的工具调用重复，截断")
                            finishTurn("查询过程陷入循环，已中止。请换一种问法试试。")
                            return@channelFlow
                        }
                        lastSignature = signature

                        // 同一工具连续调用 N 轮仍未得出结论。N=4：给合理的一次参数修正重试留空间
                        // （实机证据：模型因结果截断而用 limit 重试一次属正常行为，3 轮误杀）
                        if (calls.size == 1) {
                            if (calls[0].name == lastSingleToolName) sameToolStreak++ else {
                                sameToolStreak = 1
                                lastSingleToolName = calls[0].name
                            }
                        } else {
                            sameToolStreak = 0
                        }
                        if (sameToolStreak >= policies.sameToolStreakLimit) {
                            Log.w(TAG, "工具 $lastSingleToolName 连续调用 $sameToolStreak 轮，截断")
                            finishTurn("同一查询连续执行多次未得出结论，已中止。请换一种问法或缩小查询范围。")
                            return@channelFlow
                        }

                        memory.appendAssistantToolCalls(response.content.ifBlank { null }, calls)
                        // 思维链：仅 FC 模型有独立思考文本（JSON 模式下 content 为信封原文，严禁外显，铁律④）
                        if (aiService.capabilities.functionCalling && response.content.isNotBlank()) {
                            send(AiEvent.ThinkingText(response.content.trim()))
                        }

                        // 权限检查 + 执行；finally 保证 tool 结果全部写入 memory（配对完整）
                        val results = mutableMapOf<String, ToolResult>()
                        try {
                            for (call in calls) {
                                if (call.name == "ask_question") {
                                    send(AiEvent.ToolCallStarted(call))
                                    val questions = AskQuestionTool.parseQuestions(call.arguments)
                                    if (questions.isEmpty()) {
                                        results[call.id] = ToolResult.failure(
                                            call,
                                            ToolErrorKind.INVALID_ARGUMENTS,
                                            "调用 ask_question 失败：缺少有效的问题列表。"
                                        )
                                    } else {
                                        val answer = requestQuestion({ send(it) }, call)
                                        val result = if (answer.isSkipped) {
                                            ToolResult.success(call, "用户跳过了此问题，未做选择。请按照常规安全策略继续，或询问是否需要其他帮助。")
                                        } else {
                                            val formattedAnswers = answer.selectedOptions.entries.joinToString("\n") { (idx, selected) ->
                                                val qTitle = questions.getOrNull(idx)?.question ?: "第${idx + 1}题"
                                                "问题【$qTitle】用户的最终选择与补充要求：${selected.joinToString("，")}"
                                            }
                                            ToolResult.success(call, "用户已在交互卡片上完成作答：\n$formattedAnswers\n请务必严格根据用户的选择与补充要求，继续执行下一步查询或操作。")
                                        }
                                        results[call.id] = result
                                    }
                                    send(AiEvent.ToolCallFinished(call, results[call.id]!!))
                                    continue
                                }

                                when (ToolPermissionManager.check(call.name)) {
                                    ToolPermission.BLOCKED -> {
                                        results[call.id] = ToolResult.failure(
                                            call, ToolErrorKind.PERMISSION_DENIED, "工具 ${call.name} 已被禁用"
                                        )
                                    }
                                    ToolPermission.CONFIRM -> {
                                        send(AiEvent.ToolCallStarted(call))
                                        val approved = requestConfirm({ send(it) }, call)
                                        results[call.id] = if (approved) executor.execute(call)
                                        else ToolResult.failure(
                                            call, ToolErrorKind.PERMISSION_DENIED, "用户拒绝执行该操作"
                                        )
                                        send(AiEvent.ToolCallFinished(call, results[call.id]!!))
                                    }
                                    ToolPermission.AUTO -> {
                                        send(AiEvent.ToolCallStarted(call))
                                        val result = executor.execute(call)
                                        results[call.id] = result
                                        send(AiEvent.ToolCallFinished(call, result))
                                    }
                                }
                            }
                        } finally {
                            for (call in calls) {
                                memory.appendToolResult(call.id, call.name, results[call.id]?.content ?: "[执行中断]")
                            }
                        }

                        for (call in calls) {
                            results[call.id]?.uiAction?.let {
                                send(AiEvent.ActionAvailable(it))
                                // 产品卡片随最终回复持久化（多次产出时最后一次为准，与 UI 持卡语义一致）
                                if (it is UiAction.ShowProductCardList) {
                                    pendingCardJson = ProductCardListCodec.toJson(it)
                                }
                            }
                        }
                    }
                }
            }

            // 轮次耗尽：不带工具做一次总结性回答（同样走流式，总结文本实时外显）
            val finalResponse = callModelRound({ send(it) }, sysPrompt, toolDefs, useTools = false)
            finishTurn(finalResponse.content)
        } catch (e: CancellationException) {
            // 用户停止 / 页面退出：补齐轮次，保持 memory 序列合法
            memory.closeTurn("（本次回答已停止）", elapsedMs(), aiService.modelId)
            throw e
        } catch (e: AiServiceException) {
            Log.e(TAG, "Chat failed (${e.kind})", e)
            memory.closeTurn("服务暂时不可用：${e.message}", elapsedMs(), aiService.modelId)
            send(AiEvent.Failed(e))
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            memory.closeTurn("抱歉，处理请求时出错：${e.message}", elapsedMs(), aiService.modelId)
            send(AiEvent.Failed(AiServiceException(AiErrorKind.UNKNOWN, "AI 服务出错: ${e.message}", e)))
        }
    }

    /**
     * 单轮模型调用。支持流式的服务商走 streamChat：文本增量实时下发 AnswerDelta——
     * FC 模型直接外显；JSON 协议（FC=false）正文可能是工具信封，严禁流式外泄，
     * 待抽取或完成后整体交付（铁律④）。
     * 工具调用分片由 [ToolCallAccumulator] 聚合（docs/AI_V3_REDESIGN.md §4.8）。
     * 流式空结果（既无文本也无分片）降级为一次非流式调用。
     */
    private suspend fun callModelRound(
        send: suspend (AiEvent) -> Unit,
        systemPrompt: String,
        toolDefs: List<AiToolDefinition>,
        useTools: Boolean
    ): AiResponse {
        val tools = if (useTools) toolDefs else null
        val messages = memory.messagesForRequest()
        if (!aiService.capabilities.streaming) {
            return withTimeout(aiService.chatTimeoutMs) { aiService.chat(messages, systemPrompt, tools) }
        }
        val content = StringBuilder()
        val accumulator = ToolCallAccumulator()
        var suppressDelta: Boolean? = if (aiService.capabilities.functionCalling) false else null
        val bufferedDeltas = StringBuilder()

        withTimeout(aiService.chatTimeoutMs) {
            aiService.streamChat(messages, systemPrompt, tools).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Text -> {
                        content.append(chunk.text)
                        if (suppressDelta == null) {
                            bufferedDeltas.append(chunk.text)
                            val trimmed = bufferedDeltas.trimStart()
                            if (trimmed.isNotEmpty()) {
                                if (trimmed.startsWith("{") || trimmed.startsWith("```") || trimmed.startsWith("<|")) {
                                    suppressDelta = true
                                    bufferedDeltas.clear()
                                } else {
                                    suppressDelta = false
                                    send(AiEvent.AnswerDelta(bufferedDeltas.toString()))
                                    bufferedDeltas.clear()
                                }
                            }
                        } else if (!suppressDelta) {
                            send(AiEvent.AnswerDelta(chunk.text))
                        }
                    }
                    is StreamChunk.ToolCallDelta -> accumulator.add(chunk)
                    is StreamChunk.Completed -> Unit
                    is StreamChunk.Error -> throw AiServiceException(AiErrorKind.UNREACHABLE, chunk.message)
                }
            }
        }
        if (content.isBlank() && accumulator.isEmpty()) {
            return withTimeout(aiService.chatTimeoutMs) { aiService.chat(messages, systemPrompt, tools) }
        }
        val toolCalls = accumulator.toToolCalls()
        return AiResponse(content = content.toString(), toolCalls = toolCalls.takeIf { it.isNotEmpty() })
    }

    /** CONFIRM 权限：发事件等 UI 答复，超时无响应按拒绝处理并通知 UI 关闭弹窗 */
    private suspend fun requestConfirm(
        send: suspend (AiEvent) -> Unit,
        call: AiToolCall
    ): Boolean {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirms[requestId] = deferred
        try {
            send(AiEvent.AwaitingConfirmation(requestId, call))
            return withTimeout(policies.confirmTimeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            send(AiEvent.ConfirmTimedOut(requestId))
            return false
        } finally {
            pendingConfirms.remove(requestId)
        }
    }

    /** ask_question 工具：发事件等 UI 用户答复或跳过，超时自动按跳过处理 */
    private suspend fun requestQuestion(
        send: suspend (AiEvent) -> Unit,
        call: AiToolCall
    ): UserQuestionAnswer {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<UserQuestionAnswer>()
        pendingQuestions[requestId] = deferred
        val questions = AskQuestionTool.parseQuestions(call.arguments)
        try {
            send(AiEvent.AwaitingQuestion(requestId, questions))
            return withTimeout(policies.questionTimeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            send(AiEvent.QuestionTimedOut(requestId))
            return UserQuestionAnswer(isSkipped = true)
        } finally {
            pendingQuestions.remove(requestId)
        }
    }

    private fun normalizeArgs(args: JsonObject): String {
        val sorted = JsonObject()
        args.entrySet().sortedBy { it.key }.forEach { (key, value) -> sorted.add(key, value) }
        return sorted.toString()
    }

    companion object {
        private const val TAG = "AiRuntime"
    }
}
