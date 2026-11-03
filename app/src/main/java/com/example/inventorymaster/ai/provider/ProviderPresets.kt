package com.example.inventorymaster.ai.provider

object ProviderPresets {

    data class Preset(
        val name: String,
        val config: AiProviderConfig
    )

    val presets: List<Preset> = listOf(
        Preset(
            name = "OpenAI",
            config = AiProviderConfig(
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o",
                modelsEndpoint = "https://api.openai.com/v1/models"
            )
        ),
        Preset(
            name = "通义千问",
            config = AiProviderConfig(
                name = "通义千问",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "qwen-plus",
                modelsEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/models"
            )
        ),
        Preset(
            name = "DeepSeek",
            config = AiProviderConfig(
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
                modelsEndpoint = "https://api.deepseek.com/v1/models"
            )
        ),
        Preset(
            name = "Claude (Anthropic)",
            config = AiProviderConfig(
                name = "Claude",
                baseUrl = "https://api.anthropic.com/v1",
                model = "claude-sonnet-4-20250514"
            )
        ),
        Preset(
            name = "Gemini",
            config = AiProviderConfig(
                name = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                model = "gemini-2.0-flash"
            )
        ),
        Preset(
            name = "Ollama (本地)",
            config = AiProviderConfig(
                name = "Ollama",
                baseUrl = "http://localhost:11434/v1",
                model = "llama3",
                modelsEndpoint = "http://localhost:11434/api/tags"
            )
        ),
        Preset(
            name = "Gemma 4 E2B (本地)",
            config = AiProviderConfig(
                name = "Gemma 4 E2B",
                baseUrl = "",
                model = "gemma-4-e2b",
                isLocal = true,
                localModelPath = null
            )
        ),
        Preset(
            name = "Qwen3 4B (本地)",
            config = AiProviderConfig(
                name = "Qwen3 4B",
                baseUrl = "",
                model = "qwen3-4b-instruct-2507",
                isLocal = true,
                localModelPath = null
            )
        )
    )
}
