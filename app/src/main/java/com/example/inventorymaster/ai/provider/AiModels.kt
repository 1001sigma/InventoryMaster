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

data class StreamChunk(
    val content: String? = null,
    val toolCall: AiToolCall? = null,
    val isComplete: Boolean = false
)

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
