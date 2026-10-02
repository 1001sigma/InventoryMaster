package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class OcrCorrectTool(
    private val aiService: AiService
) : AiTool {
    override val name = "ocr_correct"
    override val description = "纠正 OCR 文字识别中的错误，特别是医疗器械名称、厂家名称、规格型号等专业术语"
    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("text", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "OCR 识别的原始文本")
            })
            add("context", JsonObject().apply {
                addProperty("type", "string")
                add("enum", JsonArray().apply {
                    add("product_name")
                    add("manufacturer")
                    add("specification")
                    add("general")
                })
                addProperty("description", "文本上下文类型")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("text") })
    }

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val text = toolCall.arguments.get("text")?.asString ?: ""
        if (text.isBlank()) {
            return ToolResult.failure(toolCall, ToolErrorKind.INVALID_ARGUMENTS, "OCR 文本不能为空")
        }
        val context = toolCall.arguments.get("context")?.asString ?: "general"

        val contextDesc = when (context) {
            "product_name" -> "产品名称"
            "manufacturer" -> "厂家名称"
            "specification" -> "规格型号"
            else -> "通用文本"
        }

        val prompt = buildString {
            appendLine("你是医疗器械行业的文字纠错专家。请纠正以下 OCR 识别文本中的错误。")
            appendLine()
            appendLine("上下文类型：$contextDesc")
            appendLine("原始文本：$text")
            appendLine()
            appendLine("常见 OCR 错误模式：")
            appendLine("- 数字 0/O 混淆、1/l/I 混淆")
            appendLine("- 中文形近字：已/己/巳、日/曰、末/未")
            appendLine("- 医疗器械专业术语：注射器、采血管、导管、敷料等")
            appendLine("- 厂家名称：迈瑞、新华、鱼跃、稳健等")
            appendLine()
            appendLine("请以 JSON 格式返回:")
            appendLine("{\"corrected_text\": \"纠正后的文本\", \"changes\": [\"原文→纠正\"], \"confidence\": \"高/中/低\"}")
        }

        return try {
            val response = aiService.chat(
                messages = listOf(AiMessage("user", prompt)),
                systemPrompt = "你是医疗器械行业文字纠错专家。",
                tools = null
            )

            val content = response.content
            val jsonStart = content.indexOf("{")
            val jsonEnd = content.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = content.substring(jsonStart, jsonEnd)
                val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject

                val corrected = json.get("corrected_text")?.asString ?: text
                val confidence = json.get("confidence")?.asString ?: "中"
                val changes = json.getAsJsonArray("changes")

                val resultText = buildString {
                    appendLine("【AI 纠错】原文: $text")
                    appendLine("纠正后: $corrected")
                    if (changes != null && changes.size() > 0) {
                        appendLine("修改内容:")
                        changes.forEach { appendLine("  - ${it.asString}") }
                    }
                    appendLine("置信度: $confidence")
                }
                ToolResult.success(toolCall, resultText, data = json)
            } else {
                ToolResult.failure(toolCall, ToolErrorKind.BUSINESS_ERROR, "AI 返回格式异常，请重试。原始回复: $content")
            }
        } catch (e: Exception) {
            ToolResult.failure(toolCall, ToolErrorKind.BUSINESS_ERROR, "AI 纠错失败: ${e.message}")
        }
    }
}
