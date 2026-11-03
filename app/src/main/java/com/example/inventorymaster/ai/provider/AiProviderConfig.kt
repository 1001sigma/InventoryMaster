package com.example.inventorymaster.ai.provider

import java.util.UUID

data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val model: String = "",
    val apiKey: String = "",
    val supportsTools: Boolean = true,
    val modelsEndpoint: String = "",
    val isLocal: Boolean = false,
    val localModelPath: String? = null,
    // --- 高级模型参数配置（参考 Operit StandardModelParameters） ---
    val temperatureEnabled: Boolean = false,
    val temperature: Float = 0.7f,
    val topPEnabled: Boolean = false,
    val topP: Float = 0.95f,
    val maxTokensEnabled: Boolean = false,
    val maxTokens: Int = 4096,
    // --- 已获取的模型列表缓存 ---
    val cachedModels: List<String> = emptyList()
) {
    /** 智能推导模型列表端点：若未显式指定，按 baseUrl 规范自动推导 */
    fun resolveModelsEndpoint(): String {
        if (modelsEndpoint.isNotBlank()) return modelsEndpoint.trim()
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return ""
        return if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
    }
}
