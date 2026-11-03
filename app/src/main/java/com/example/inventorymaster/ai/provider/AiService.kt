package com.example.inventorymaster.ai.provider

import kotlinx.coroutines.flow.Flow

interface AiService {
    /** 服务商能力；Agent 据此在 Function Calling 与 JSON 协议间路由 */
    val capabilities: AiCapabilities

    /** 当前服务的模型 ID（运行时随最终回复戳记并持久化，操作条展示） */
    val modelId: String

    /** 单次模型调用超时预算；本地推理（2.5GB 级 int4 模型）远慢于云端，实现方覆写 */
    val chatTimeoutMs: Long get() = 90_000

    suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>? = null
    ): AiResponse

    fun streamChat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>? = null
    ): Flow<StreamChunk>

    suspend fun isAvailable(): Boolean

    fun isLocal(): Boolean = false
}
