package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonObject

/**
 * 工具执行结果（v3 二期结构化升级，docs/AI_Runtime §9.2/§5.3）。
 *
 * 取代旧的 `execute(): String`：
 * - [content] 面向模型的文本（写进 memory 的 tool 消息），成功与失败都必须自洽可读；
 * - [data] 结构化数据（供程序/UI 使用，不进 memory）；
 * - [error] 失败时的分类错误（Agent 据此判断是否可自纠）；
 * - [uiAction] 需要界面执行的动作——替代 ActionResultExtractor 的 "ACTION:" 字符串协议。
 */
data class ToolResult(
    val callId: String,
    val toolName: String,
    val success: Boolean,
    val content: String,
    val data: JsonObject? = null,
    val error: ToolError? = null,
    val uiAction: UiAction? = null
) {
    companion object {
        fun success(
            call: AiToolCall,
            content: String,
            data: JsonObject? = null,
            uiAction: UiAction? = null
        ): ToolResult = ToolResult(
            callId = call.id,
            toolName = call.name,
            success = true,
            content = content,
            data = data,
            uiAction = uiAction
        )

        /** [content] 是给模型的完整错误说明，须包含可据以自纠的信息 */
        fun failure(
            call: AiToolCall,
            kind: ToolErrorKind,
            content: String,
            data: JsonObject? = null
        ): ToolResult = ToolResult(
            callId = call.id,
            toolName = call.name,
            success = false,
            content = content,
            data = data,
            error = ToolError(kind, content)
        )
    }
}
