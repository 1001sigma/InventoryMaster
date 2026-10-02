package com.example.inventorymaster.ai

import android.util.Log
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.agent.AiAgent
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.prompt.PromptBuilder
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.runtime.AiEvent
import com.example.inventorymaster.ai.runtime.AiRuntime
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.data.entity.InventorySession
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * 库存 AI 助手 Agent（v3.5，AI_Runtime Phase 2/3 后的业务适配层）。
 *
 * 通用工具循环已抽至 [AiRuntime]；本类只负责库存业务：
 * - 系统提示词注入（当前盘存任务上下文，PromptBuilder）
 * - AiEvent → AgentEvent（UI 事件契约）映射
 * - 产品卡片持有（ShowProductCardList 随最终回复下发，其余 uiAction 即时执行）
 */
class InventoryAiAgent(
    aiService: AiService,
    registry: ToolRegistry,
    memory: ConversationMemory = ConversationMemory()
) : AiAgent {

    private val runtime = AiRuntime(aiService, registry, memory)

    private var currentSession: InventorySession? = null

    fun setCurrentSession(session: InventorySession?) {
        currentSession = session
    }

    /** UI 对 ConfirmRequired 的答复（透传运行时） */
    fun respondConfirm(requestId: String, approved: Boolean) {
        runtime.respondConfirm(requestId, approved)
    }

    override suspend fun chat(message: String): Flow<AgentEvent> = channelFlow {
        var pendingCardAction: UiAction.ShowProductCardList? = null
        runtime.turn(message, PromptBuilder.build(currentSession)).collect { event ->
            when (event) {
                is AiEvent.ThinkingText -> send(AgentEvent.Thinking(event.text))
                is AiEvent.AnswerDelta -> send(AgentEvent.ResponseChunk(event.text))
                is AiEvent.ToolCallStarted -> send(
                    AgentEvent.ToolCallStart(event.call.name, summarizeArguments(event.call.arguments))
                )
                is AiEvent.ToolCallFinished -> {
                    send(AgentEvent.ToolCallResult(event.call.name, event.result.content))
                    event.result.uiAction?.let { action ->
                        if (action is UiAction.ShowProductCardList) pendingCardAction = action
                        else send(AgentEvent.ActionExecuted(action))
                    }
                }
                is AiEvent.AwaitingConfirmation -> send(
                    AgentEvent.ConfirmRequired(
                        event.requestId, event.call.name, summarizeArguments(event.call.arguments)
                    )
                )
                is AiEvent.ConfirmTimedOut -> send(AgentEvent.ConfirmTimedOut(event.requestId))
                is AiEvent.ActionAvailable -> Unit // uiAction 已在 ToolCallFinished 分支处理
                is AiEvent.Completed -> send(AgentEvent.Response(event.answer, pendingCardAction))
                is AiEvent.Failed -> send(AgentEvent.Error(event.error.message ?: "AI 服务出错", event.error.kind))
            }
        }
    }

    private fun summarizeArguments(args: JsonObject): String =
        args.entrySet().joinToString(", ") { (key, value) ->
            "$key=${if (value.isJsonNull) "null" else value.toString().take(50)}"
        }.take(200).ifBlank { "（无参数）" }
}
