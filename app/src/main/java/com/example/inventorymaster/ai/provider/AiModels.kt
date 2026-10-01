package com.example.inventorymaster.ai.provider

import com.google.gson.JsonObject

data class AiMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<AiToolCall>? = null,
    val toolCallId: String? = null
)

data class AiResponse(
    val content: String,
    val toolCalls: List<AiToolCall>? = null
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * 流式事件（v3 二期密封化，docs/AI_V3_REDESIGN.md §4.8）。
 * Provider 把各厂商的流式格式翻译成这里的事件；上游（Agent）用 [ToolCallAccumulator]
 * 把 [ToolCallDelta] 分片聚合成完整 [AiToolCall]。
 */
sealed class StreamChunk {

    /** 正文增量 */
    data class Text(val text: String) : StreamChunk()

    /** 工具调用分片增量：按 index 累积 id/name/arguments（OpenAI delta.tool_calls 格式） */
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argumentsDelta: String = ""
    ) : StreamChunk()

    /** 流正常结束 */
    data object Completed : StreamChunk()

    /** 流异常结束（连接中断等；Agent 收到后按 AiServiceException 处理） */
    data class Error(val message: String) : StreamChunk()
}

/** 服务商能力声明（Agent 据此选择工具调用协议，见 docs/AI_V3_REDESIGN.md §4.1） */
data class AiCapabilities(
    val functionCalling: Boolean,
    val streaming: Boolean
)

enum class AiErrorKind {
    NOT_CONFIGURED,
    UNREACHABLE,
    AUTH_FAILED,
    RATE_LIMITED,
    SERVER,
    TIMEOUT,
    PARSE_ERROR,
    UNKNOWN
}

/** 类型化 AI 服务异常；UI 按 kind 呈现差异化错误卡片，不再吞成普通字符串 */
class AiServiceException(
    val kind: AiErrorKind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
