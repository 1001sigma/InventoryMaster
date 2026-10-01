package com.example.inventorymaster.ai

import android.util.Log
import com.example.inventorymaster.ai.agent.ActionResultExtractor
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.agent.AiAgent
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.agent.strategy.FunctionCallingStrategy
import com.example.inventorymaster.ai.agent.strategy.JsonToolCallStrategy
import com.example.inventorymaster.ai.agent.strategy.ToolCallOutcome
import com.example.inventorymaster.ai.agent.strategy.ToolCallStrategy
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.prompt.PromptBuilder
import com.example.inventorymaster.ai.provider.AiErrorKind
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiServiceException
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.ToolExecutor
import com.example.inventorymaster.ai.tools.ToolPermission
import com.example.inventorymaster.ai.tools.ToolPermissionManager
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.data.entity.InventorySession
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 库存 AI 助手 Agent（v3，docs/AI_V3_REDESIGN.md §4.3）。
 *
 * 相比 v2 的关键变化：
 * - 能力路由：按 [AiService.capabilities] 选择 Function Calling / JSON 协议，
 *   删除"扫描正文猜工具"的兜底劫持（误触发源头）
 * - 每轮 LLM 调用有超时、每个工具有超时、确认操作有超时；全程可取消
 * - 工具结果统一在轮末写入 memory（finally 兜底补齐），永不产生不完整的 tool_calls 配对
 * - 取消/异常时关闭当前轮次并补 assistant 说明，memory 导出序列永远合法
 */
class InventoryAiAgent(
    private val aiService: AiService,
    private val registry: ToolRegistry,
    private val memory: ConversationMemory = ConversationMemory()
) : AiAgent {

    private val executor = ToolExecutor(registry)
    private var currentSession: InventorySession? = null
    private val pendingConfirms = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun setCurrentSession(session: InventorySession?) {
        currentSession = session
    }

    /** UI 对 ConfirmRequired 的答复 */
    fun respondConfirm(requestId: String, approved: Boolean) {
        pendingConfirms.remove(requestId)?.complete(approved)
    }

    override suspend fun chat(message: String): Flow<AgentEvent> = channelFlow {
        memory.openUserTurn(message)
        try {
            val strategy: ToolCallStrategy =
                if (aiService.capabilities.functionCalling) FunctionCallingStrategy()
                else JsonToolCallStrategy()

            val basePrompt = PromptBuilder.build(currentSession)
            val toolDefs = registry.getToolDefinitions()
            val systemPrompt = strategy.buildSystemPrompt(basePrompt, toolDefs)
            val useTools = toolDefs.isNotEmpty()

            var lastSignature = ""
            var lastSingleToolName = ""
            var sameToolStreak = 0
            var jsonRetryUsed = false
            var pendingCardAction: UiAction.ShowProductCardList? = null

            for (round in 0 until MAX_ROUNDS) {
                val response = try {
                    withTimeout(LLM_TIMEOUT_MS) {
                        aiService.chat(
                            messages = memory.messagesForRequest(),
                            systemPrompt = systemPrompt,
                            tools = if (useTools) toolDefs else null
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    throw AiServiceException(AiErrorKind.TIMEOUT, "AI 响应超时（${LLM_TIMEOUT_MS / 1000} 秒）", e)
                }

                val outcome = if (useTools) strategy.extractOutcome(response, toolDefs)
                else ToolCallOutcome.FinalAnswer(response.content)

                when (outcome) {
                    is ToolCallOutcome.FinalAnswer -> {
                        memory.closeTurn(outcome.content)
                        send(AgentEvent.Response(outcome.content, pendingCardAction))
                        return@channelFlow
                    }

                    is ToolCallOutcome.MalformedJson -> {
                        // JSON 协议下模型想调工具但输出不合法：纠正重试一次，再失败按普通回答展示
                        if (jsonRetryUsed) {
                            memory.closeTurn(outcome.raw)
                            send(AgentEvent.Response(outcome.raw))
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
                        val calls = outcome.calls.take(MAX_CALLS_PER_ROUND)

                        // 死循环检测必须在写入 memory 之前——截断路径不会执行工具，
                        // 若已写入 assistant(tool_calls) 而无 tool 结果，下一轮请求即 400
                        val signature = calls.joinToString(";") { it.name + ":" + normalizeArgs(it.arguments) }
                        if (signature == lastSignature) {
                            Log.w(TAG, "检测到完全相同的工具调用重复，截断")
                            val msg = "查询过程陷入循环，已中止。请换一种问法试试。"
                            memory.closeTurn(msg)
                            send(AgentEvent.Response(msg))
                            return@channelFlow
                        }
                        lastSignature = signature

                        // 同一工具连续调用 3 轮仍未得出结论
                        if (calls.size == 1) {
                            if (calls[0].name == lastSingleToolName) sameToolStreak++ else {
                                sameToolStreak = 1
                                lastSingleToolName = calls[0].name
                            }
                        } else {
                            sameToolStreak = 0
                        }
                        if (sameToolStreak >= 3) {
                            Log.w(TAG, "工具 ${lastSingleToolName} 连续调用 $sameToolStreak 轮，截断")
                            val msg = "同一查询连续执行多次未得出结论，已中止。请换一种问法或缩小查询范围。"
                            memory.closeTurn(msg)
                            send(AgentEvent.Response(msg))
                            return@channelFlow
                        }

                        memory.appendAssistantToolCalls(response.content.ifBlank { null }, calls)

                        // 权限检查 + 执行；finally 保证 tool 结果全部写入 memory（配对完整）
                        val results = mutableMapOf<String, String>()
                        try {
                            for (call in calls) {
                                when (ToolPermissionManager.check(call.name)) {
                                    ToolPermission.BLOCKED -> {
                                        results[call.id] = "工具 ${call.name} 已被禁用"
                                    }
                                    ToolPermission.CONFIRM -> {
                                        send(AgentEvent.ToolCallStart(call.name))
                                        val approved = requestConfirm({ send(it) }, call)
                                        results[call.id] = if (approved) executor.execute(call)
                                        else "用户拒绝执行该操作"
                                        send(AgentEvent.ToolCallResult(call.name, results[call.id] ?: ""))
                                    }
                                    ToolPermission.AUTO -> {
                                        send(AgentEvent.ToolCallStart(call.name))
                                        results[call.id] = executor.execute(call)
                                        send(AgentEvent.ToolCallResult(call.name, results[call.id] ?: ""))
                                    }
                                }
                            }
                        } finally {
                            for (call in calls) {
                                memory.appendToolResult(call.id, call.name, results[call.id] ?: "[执行中断]")
                            }
                        }

                        pendingCardAction = null
                        for (call in calls) {
                            val result = results[call.id] ?: continue
                            ActionResultExtractor.extract(call.name, result)?.let { action ->
                                if (action is UiAction.ShowProductCardList) {
                                    pendingCardAction = action
                                } else {
                                    send(AgentEvent.ActionExecuted(action))
                                }
                            }
                        }
                    }
                }
            }

            // 轮次耗尽：不带工具做一次总结性回答
            val finalResponse = aiService.chat(
                messages = memory.messagesForRequest(),
                systemPrompt = systemPrompt,
                tools = null
            )
            memory.closeTurn(finalResponse.content)
            send(AgentEvent.Response(finalResponse.content))
        } catch (e: CancellationException) {
            // 用户停止 / 页面退出：补齐轮次，保持 memory 序列合法
            memory.closeTurn("（本次回答已停止）")
            throw e
        } catch (e: AiServiceException) {
            Log.e(TAG, "Chat failed (${e.kind})", e)
            memory.closeTurn("服务暂时不可用：${e.message}")
            send(AgentEvent.Error(e.message ?: "AI 服务出错", e.kind))
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            memory.closeTurn("抱歉，处理请求时出错：${e.message}")
            send(AgentEvent.Error("AI 服务出错: ${e.message}", null))
        }
    }

    /** CONFIRM 权限：发事件等 UI 答复，30 秒无响应按拒绝处理 */
    private suspend fun requestConfirm(
        send: suspend (AgentEvent) -> Unit,
        call: AiToolCall
    ): Boolean {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirms[requestId] = deferred
        try {
            send(AgentEvent.ConfirmRequired(requestId, call.name, summarizeArguments(call.arguments)))
            return withTimeout(CONFIRM_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            return false
        } finally {
            pendingConfirms.remove(requestId)
        }
    }

    private fun normalizeArgs(args: JsonObject): String {
        val sorted = JsonObject()
        args.entrySet().sortedBy { it.key }.forEach { (key, value) -> sorted.add(key, value) }
        return sorted.toString()
    }

    private fun summarizeArguments(args: JsonObject): String =
        args.entrySet().joinToString(", ") { (key, value) ->
            "$key=${if (value.isJsonNull) "null" else value.toString().take(50)}"
        }.take(200).ifBlank { "（无参数）" }

    companion object {
        private const val TAG = "InventoryAiAgent"
        private const val MAX_ROUNDS = 5
        private const val MAX_CALLS_PER_ROUND = 4
        private const val LLM_TIMEOUT_MS = 90_000L
        private const val CONFIRM_TIMEOUT_MS = 30_000L
    }
}
