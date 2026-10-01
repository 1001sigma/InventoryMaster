package com.example.inventorymaster.ai.provider

import java.util.UUID

data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
    val supportsTools: Boolean = true,
    val modelsEndpoint: String = "",
    val isLocal: Boolean = false,
    val localModelPath: String? = null
)
