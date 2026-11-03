package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.QuestionItem
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 交互式多选问答工具（ask_question）。
 *
 * 当大模型需要澄清用户模糊需求、确认决策选项或收集必要参数时调用。
 * 运行时拦截此工具并挂起协程，向 UI 发送交互卡片，等待用户操作后恢复执行。
 */
class AskQuestionTool : AiTool {

    override val name: String = "ask_question"

    override val description: String =
        "向用户展示交互式选择问答卡片，提出一个或多个单选/多选问题以澄清意图、确认决策或收集关键参数。用户既可点击预设选项，也可在文本框输入自定义补充要求。当需求存在多种可能性或需要用户确认时必须调用。"

    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        val properties = JsonObject()

        val questionsSchema = JsonObject().apply {
            addProperty("type", "array")
            addProperty("description", "需要用户选择或回答的问题列表（通常1~3题）")

            val itemSchema = JsonObject().apply {
                addProperty("type", "object")
                val itemProps = JsonObject()

                itemProps.add("question", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "问题题干，例如：您想要查看哪种类型的近效期物料？")
                })

                itemProps.add("options", JsonObject().apply {
                    addProperty("type", "array")
                    addProperty("description", "供用户点击选择的互斥选项列表（建议2~4项）")
                    val stringItem = JsonObject().apply { addProperty("type", "string") }
                    add("items", stringItem)
                })

                itemProps.add("is_multi_select", JsonObject().apply {
                    addProperty("type", "boolean")
                    addProperty("description", "是否允许多选。默认为 false（单选）")
                })

                add("properties", itemProps)
                val requiredItemFields = JsonArray().apply {
                    add("question")
                    add("options")
                }
                add("required", requiredItemFields)
            }

            add("items", itemSchema)
        }

        properties.add("questions", questionsSchema)
        add("properties", properties)

        val required = JsonArray().apply {
            add("questions")
        }
        add("required", required)
    }

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val questions = parseQuestions(toolCall.arguments)
        if (questions.isEmpty()) {
            return ToolResult.failure(
                toolCall,
                ToolErrorKind.INVALID_ARGUMENTS,
                "调用 ask_question 失败：questions 列表不能为空。请提供至少一道包含 question 与 options 的问题。"
            )
        }
        // 正常情况下由 AiRuntime 拦截挂起处理；若脱离 runtime 直调，返回模拟就绪
        return ToolResult.success(
            toolCall,
            "问答交互已就绪，等待用户作答（共 ${questions.size} 道题）。"
        )
    }

    companion object {
        fun parseQuestions(args: JsonObject): List<QuestionItem> {
            val questionsArray = args.getAsJsonArray("questions") ?: return emptyList()
            val list = mutableListOf<QuestionItem>()
            for (element in questionsArray) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val question = obj.get("question")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (question.isBlank()) continue

                val options = mutableListOf<String>()
                obj.getAsJsonArray("options")?.forEach { opt ->
                    if (opt.isJsonPrimitive) {
                        val optText = opt.asString.trim()
                        if (optText.isNotBlank()) options.add(optText)
                    }
                }
                if (options.isEmpty()) continue

                val isMulti = obj.get("is_multi_select")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                list.add(
                    QuestionItem(
                        question = question,
                        options = options,
                        isMultiSelect = isMulti,
                        allowCustom = true
                    )
                )
            }
            return list
        }
    }
}
