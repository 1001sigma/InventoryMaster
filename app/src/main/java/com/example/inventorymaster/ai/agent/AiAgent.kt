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

    /** ask_question 工具：等待用户在卡片上作答或跳过（requestId 经 respondQuestion 回传） */
    data class QuestionRequired(
        val requestId: String,
        val questions: List<QuestionItem>
    ) : AgentEvent()

    /** ask_question 等待超时：UI 自动收起问答卡片 */
    data class QuestionTimedOut(val requestId: String) : AgentEvent()

    data class Response(
        val content: String,
        val cardAction: UiAction.ShowProductCardList? = null,
        /** 整轮耗时（毫秒，操作条展示）；null=未统计 */
        val durationMs: Long? = null,
        /** 生成该回复的模型 ID（随消息持久化，操作条展示）；null=未统计/旧数据 */
        val model: String? = null
    ) : AgentEvent()
    data class ResponseChunk(val content: String) : AgentEvent()
    data class Error(val message: String, val kind: AiErrorKind? = null) : AgentEvent()
    data class ActionExecuted(val action: UiAction) : AgentEvent()
}
