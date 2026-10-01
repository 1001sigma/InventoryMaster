package com.example.inventorymaster.ai.provider

import kotlinx.coroutines.flow.Flow

interface AiService {
    /** 服务商能力；Agent 据此在 Function Calling 与 JSON 协议间路由 */
    val capabilities: AiCapabilities

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
