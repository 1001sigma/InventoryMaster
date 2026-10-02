package com.example.inventorymaster.ai.provider

/**
 * 模型级能力目录（AI_Runtime Phase 6）：能力跟模型走，不只跟服务商走——
 * 同一服务商下不同模型的工具调用支持度可能不同（实机教训：NVIDIA 免费模型参差）。
 *
 * 解析顺序：用户显式开关（AiProviderConfig.supportsTools，设置页可改）∩ 目录默认值——
 * 用户关闭永远优先。目录条目为保守默认，未匹配模型按 FC 关、流式开处理
 * （JSON 文本协议兜底，模型再弱也能完成工具循环，见 JsonToolCallStrategy）。
 */
object ModelCapabilityCatalog {

    data class ModelCapabilities(
        val functionCalling: Boolean,
        val streaming: Boolean
    )

    private data class Rule(val pattern: Regex, val caps: ModelCapabilities, val note: String)

    private val rules = listOf(
        Rule(Regex("qwen", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "通义千问全系"),
        Rule(Regex("deepseek-chat|deepseek-v3", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "DeepSeek V3 系"),
        // 推理系模型流式下工具协议不稳，保守关 FC（v3 文档 R1）
        Rule(Regex("deepseek-reasoner|deepseek-r1", RegexOption.IGNORE_CASE), ModelCapabilities(false, true), "DeepSeek R1 推理系"),
        Rule(Regex("gpt-", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "OpenAI GPT 系"),
        Rule(Regex("gemini", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "Gemini OpenAI 兼容端点"),
        Rule(Regex("glm", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "智谱 GLM 系"),
        Rule(Regex("kimi|moonshot", RegexOption.IGNORE_CASE), ModelCapabilities(true, true), "Kimi/Moonshot"),
        // 本地/Ollama 模型默认走 JSON 协议（v3 决策：本地模型一律 FC=false，小模型 FC 脆弱）
        Rule(Regex("llama|mistral|phi", RegexOption.IGNORE_CASE), ModelCapabilities(false, true), "Ollama 本体系"),
        Rule(Regex("gemma", RegexOption.IGNORE_CASE), ModelCapabilities(false, false), "本地 Gemma（占位引擎无推理）")
    )

    /**
     * 按模型名解析能力默认值。未匹配 → 沿用目录改造前的旧行为（FC 开），保持既有云端
     * 配置兼容；已知弱模型家族才降级，用户可在设置页手动关闭任意模型。
     * （不要改回"未知即 FC 关"：实机教训——未知模型集体跌入 JSON 协议，而该路径曾踩
     * ICU 正则坑。）
     */
    fun forModel(model: String): ModelCapabilities {
        rules.firstOrNull { it.pattern.containsMatchIn(model) }?.let { return it.caps }
        return ModelCapabilities(functionCalling = true, streaming = true)
    }

    /** 最终能力 = 用户显式开关 ∩ 目录默认值；本地占位引擎两者皆关 */
    fun resolve(config: AiProviderConfig): AiCapabilities {
        if (config.isLocal) return AiCapabilities(functionCalling = false, streaming = false)
        val caps = forModel(config.model)
        return AiCapabilities(
            functionCalling = config.supportsTools && caps.functionCalling,
            streaming = caps.streaming
        )
    }
}
