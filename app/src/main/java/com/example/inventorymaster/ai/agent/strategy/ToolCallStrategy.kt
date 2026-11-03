package com.example.inventorymaster.ai.agent.strategy

import com.example.inventorymaster.ai.provider.AiResponse
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.utils.JsonExtractor

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 工具调用协议策略：按服务商能力在原生 Function Calling 与 JSON 文本协议间路由
 * （docs/AI_V3_REDESIGN.md §4.1-4.2）。
 */
sealed class ToolCallOutcome {
    /** 模型直接给出最终回答（无工具调用） */
    data class FinalAnswer(val content: String) : ToolCallOutcome()

    /** 模型请求调用工具 */
    data class ToolCalls(val calls: List<AiToolCall>) : ToolCallOutcome()

    /** 仅 JSON 协议：模型试图调用工具但输出无法解析（可纠正重试一次） */
    data class MalformedJson(val raw: String) : ToolCallOutcome()
}

interface ToolCallStrategy {
    /** 组装系统提示词（JSON 协议在此附加工具说明） */
    fun buildSystemPrompt(basePrompt: String, tools: List<AiToolDefinition>): String

    /** 从模型响应提取工具调用意图 */
    fun extractOutcome(response: AiResponse, tools: List<AiToolDefinition>): ToolCallOutcome
}

/** 原生 Function Calling（Qwen/DeepSeek 及多数 OpenAI 兼容网关） */
class FunctionCallingStrategy : ToolCallStrategy {

    override fun buildSystemPrompt(basePrompt: String, tools: List<AiToolDefinition>): String = basePrompt

    override fun extractOutcome(response: AiResponse, tools: List<AiToolDefinition>): ToolCallOutcome {
        val calls = response.toolCalls.orEmpty()
        return if (calls.isEmpty()) ToolCallOutcome.FinalAnswer(response.content)
        else ToolCallOutcome.ToolCalls(calls)
    }
}

/**
 * JSON 文本协议：面向不支持原生 Function Calling 的模型（本地小模型、部分免费网关）。
 *
 * 工具定义写入系统提示词，要求模型需要数据时只输出 {"tool":..,"arguments":..} 单一 JSON。
 * 解析失败或未知工具名一律按最终回答处理（或判定为 MalformedJson 供上层纠正重试一次），
 * 绝不扫描正文"猜"工具意图伪造调用——v2 文本兜底拦截器的误触发教训。
 */
class JsonToolCallStrategy : ToolCallStrategy {

    override fun buildSystemPrompt(basePrompt: String, tools: List<AiToolDefinition>): String {
        if (tools.isEmpty()) return basePrompt
        return buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("# 工具调用协议（重要）")
            appendLine("你可以使用以下工具获取实时数据：")
            tools.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description}")
            }
            appendLine()
            appendLine("当需要调用工具时，你的整个回复必须只包含一个 JSON 对象，格式：")
            appendLine("{\"tool\": \"工具名\", \"arguments\": {参数}}")
            appendLine("【规则】")
            appendLine("1. 发起工具调用时只输出 JSON，严禁输出任何多余文字。")
            appendLine("2. 当收到【工具结果】后，表示系统已查询完毕，你必须直接用自然语言向用户总结回答，绝对不要再调用工具或输出 JSON。")
            appendLine("3. 如果无需工具即可回答，直接用自然语言回答，不要输出 JSON。")
        }
    }

    override fun extractOutcome(response: AiResponse, tools: List<AiToolDefinition>): ToolCallOutcome {
        val content = response.content
        if (content.isBlank()) return ToolCallOutcome.FinalAnswer(content)

        val json = JsonExtractor.extractJsonObject(content)
        if (json != null) {
            val nameElement = json.get("tool")
            if (nameElement != null && nameElement.isJsonPrimitive) {
                val toolName = nameElement.asString
                val knownTool = tools.firstOrNull { it.name.equals(toolName, ignoreCase = true) }
                    ?: return ToolCallOutcome.FinalAnswer(content)

                val argsElement = json.get("arguments")
                val args = when {
                    argsElement == null || argsElement.isJsonNull -> JsonObject()
                    argsElement.isJsonObject -> argsElement.asJsonObject
                    else -> return ToolCallOutcome.MalformedJson(content)
                }

                return ToolCallOutcome.ToolCalls(
                    listOf(AiToolCall(id = "json_${System.currentTimeMillis()}", name = knownTool.name, arguments = args))
                )
            }
        }

        // 原生小模型格式兜底（如 Gemma/Qwen 原生模板: <|tool_call>call:tool_name{args}）
        extractNativeToolCall(content, tools)?.let { return it }

        return if (looksLikeToolAttempt(content)) ToolCallOutcome.MalformedJson(content)
        else ToolCallOutcome.FinalAnswer(content)
    }

    /** 匹配原生工具调用模式：[<|tool_call>]call:toolName{...} */
    private fun extractNativeToolCall(content: String, tools: List<AiToolDefinition>): ToolCallOutcome? {
        val match = NATIVE_TOOL_CALL_REGEX.find(content) ?: return null
        val toolName = match.groupValues[1]
        val argsRaw = match.groupValues[2]
        val knownTool = tools.firstOrNull { it.name.equals(toolName, ignoreCase = true) } ?: return null
        val args = try {
            JsonParser.parseString(argsRaw).asJsonObject
        } catch (_: Exception) {
            return ToolCallOutcome.MalformedJson(content)
        }
        return ToolCallOutcome.ToolCalls(
            listOf(AiToolCall(id = "native_${System.currentTimeMillis()}", name = knownTool.name, arguments = args))
        )
    }

    /** 粗判模型是否"想调工具但没输出合法 JSON"：出现 tool 字样 + 花括号结构，或包含原生 tool_call 标记 */
    private fun looksLikeToolAttempt(content: String): Boolean {
        val hasBrace = content.contains('{') && content.contains('}')
        val mentionsTool = content.contains("\"tool\"") ||
            content.contains("工具调用") ||
            content.contains("调用工具") ||
            content.contains("<|tool_call>") ||
            content.contains("call:")
        return (hasBrace && mentionsTool) || content.contains("<|tool_call>")
    }

    companion object {
        private val NATIVE_TOOL_CALL_REGEX = Regex(
            """(?:<\|tool_call>)?call:([a-zA-Z0-9_]+)\s*(\{[\s\S]*?\})""",
            RegexOption.IGNORE_CASE
        )
    }
}
