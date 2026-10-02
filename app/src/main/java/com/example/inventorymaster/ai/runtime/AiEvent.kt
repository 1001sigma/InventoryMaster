package com.example.inventorymaster.ai.runtime

import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiServiceException
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.ToolResult

/**
 * 运行时统一事件（AI_Runtime Phase 3）：AiRuntime 对外的唯一契约，
 * 与 UI 解耦——业务 Agent（如 InventoryAiAgent）负责把它映射成各自的展示事件（AgentEvent）。
 */
sealed class AiEvent {

    /** 工具轮中模型的思考/说明文本（FC 模型常为空） */
    data class ThinkingText(val text: String) : AiEvent()

    /** 流式正文增量（仅 FC 能力模型；JSON 协议正文可能是工具信封，运行时不外泄） */
    data class AnswerDelta(val text: String) : AiEvent()

    data class ToolCallStarted(val call: AiToolCall) : AiEvent()

    data class ToolCallFinished(val call: AiToolCall, val result: ToolResult) : AiEvent()

    /** CONFIRM 权限工具等待用户答复；用 respondConfirm 回传，超时按拒绝 */
    data class AwaitingConfirmation(val requestId: String, val call: AiToolCall) : AiEvent()

    /** 工具结果携带的界面动作（uiAction） */
    data class ActionAvailable(val action: UiAction) : AiEvent()

    /** 对话轮完成：最终回答（已写入 memory） */
    data class Completed(val answer: String) : AiEvent()

    /** 失败（memory 已写入兜底文案） */
    data class Failed(val error: AiServiceException) : AiEvent()
}
