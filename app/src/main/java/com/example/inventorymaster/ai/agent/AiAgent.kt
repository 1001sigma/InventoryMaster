package com.example.inventorymaster.ai.agent

import com.example.inventorymaster.ai.provider.AiErrorKind
import kotlinx.coroutines.flow.Flow

interface AiAgent {
    suspend fun chat(message: String): Flow<AgentEvent>
}

sealed class AgentEvent {
    data class Thinking(val message: String) : AgentEvent()
    data class ToolCallStart(
        val toolName: String,
        /** 参数摘要（思维链卡片展示用） */
        val argumentsSummary: String = ""
    ) : AgentEvent()
    data class ToolCallResult(val toolName: String, val result: String) : AgentEvent()

    /** CONFIRM 权限工具：等待用户在 UI 上确认/拒绝（requestId 经 respondConfirm 回传） */
    data class ConfirmRequired(
        val requestId: String,
        val toolName: String,
        val argumentsSummary: String
    ) : AgentEvent()

    /** CONFIRM 等待超时（按拒绝处理）：UI 关闭对应确认弹窗，避免"弹窗挂着但已失效" */
    data class ConfirmTimedOut(val requestId: String) : AgentEvent()

    data class Response(
        val content: String,
        val cardAction: UiAction.ShowProductCardList? = null
    ) : AgentEvent()
    data class ResponseChunk(val content: String) : AgentEvent()
    data class Error(val message: String, val kind: AiErrorKind? = null) : AgentEvent()
    data class ActionExecuted(val action: UiAction) : AgentEvent()
}
