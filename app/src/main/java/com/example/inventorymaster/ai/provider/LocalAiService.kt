package com.example.inventorymaster.ai.provider

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class LocalAiService(
    private val config: AiProviderConfig
) : AiService {

    /** 本地模型一律不支持原生 Function Calling（走 JSON 协议），暂不支持流式 */
    override val capabilities = AiCapabilities(functionCalling = false, streaming = false)

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): AiResponse = withContext(Dispatchers.Default) {
        try {
            val prompt = buildPrompt(messages, systemPrompt, tools)
            val result = simulateLocalInference(prompt)
            parseResponse(result, tools)
        } catch (e: Exception) {
            Log.e("LocalAiService", "Chat failed", e)
            AiResponse(content = "本地模型推理失败: ${e.message}")
        }
    }

    override fun streamChat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): Flow<StreamChunk> = callbackFlow {
        try {
            val prompt = buildPrompt(messages, systemPrompt, tools)
            val result = simulateLocalInference(prompt)
            val chars = result.toCharArray()
            for (char in chars) {
                trySend(StreamChunk(content = char.toString()))
            }
            trySend(StreamChunk(isComplete = true))
            close()
        } catch (e: Exception) {
            Log.e("LocalAiService", "Stream failed", e)
            trySend(StreamChunk(content = "本地模型推理失败: ${e.message}", isComplete = true))
            close(e)
        }
    }

    override suspend fun isAvailable(): Boolean {
        return config.localModelPath != null
    }

    override fun isLocal() = true

    /**
     * 模拟本地推理 - 实际实现将由 LlmEngine 提供
     * TODO: 接入 llama.cpp binding 后替换此方法
     */
    private suspend fun simulateLocalInference(prompt: String): String {
        return "本地模型 (${config.model}) 尚未接入推理引擎，请使用云端服务或等待后续更新。"
    }

    private fun buildPrompt(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): String {
        val sb = StringBuilder()

        systemPrompt?.let {
            sb.appendLine("[INST] <<SYS>>")
            sb.appendLine(it)
            sb.appendLine("<</SYS>>")
            sb.appendLine()
        }

        tools?.let { toolDefs ->
            if (toolDefs.isNotEmpty()) {
                sb.appendLine("可用工具：")
                toolDefs.forEach { tool ->
                    sb.appendLine("- ${tool.name}: ${tool.description}")
                    sb.appendLine("  参数: ${tool.parameters}")
                }
                sb.appendLine()
                sb.appendLine("如需调用工具，请输出 JSON 格式：{\"name\": \"工具名\", \"arguments\": {参数}}")
                sb.appendLine()
            }
        }

        messages.forEach { msg ->
            when (msg.role) {
                "system" -> {
                    sb.appendLine("[INST] <<SYS>>")
                    sb.appendLine(msg.content)
                    sb.appendLine("<</SYS>>")
                    sb.appendLine()
                }
                "user" -> {
                    sb.appendLine("[INST] ${msg.content} [/INST]")
                }
                "assistant" -> {
                    msg.toolCalls?.let { tcList ->
                        tcList.forEach { tc ->
                            sb.appendLine("<tool_call>{\"name\":\"${tc.name}\",\"arguments\":${tc.arguments}}</tool_call>")
                        }
                    } ?: run {
                        sb.appendLine("${msg.content}</s>")
                    }
                }
                "tool" -> {
                    sb.appendLine("Observation: ${msg.content}</s>")
                }
                else -> {
                    sb.appendLine(msg.content)
                }
            }
        }

        return sb.toString()
    }

    private fun parseResponse(output: String, tools: List<AiToolDefinition>?): AiResponse {
        if (tools.isNullOrEmpty()) return AiResponse(content = output)

        val toolCallPattern = """(?:<tool_call>|```json)\s*(\{.*?\})\s*(?:</tool_call>|```)""".toRegex()
        val match = toolCallPattern.find(output)

        return if (match != null) {
            try {
                val json = JsonParser.parseString(match.groupValues[1]).asJsonObject
                val toolCall = AiToolCall(
                    id = "local_${System.currentTimeMillis()}",
                    name = json.get("name")?.asString ?: return AiResponse(content = output),
                    arguments = json.getAsJsonObject("arguments") ?: JsonObject()
                )
                AiResponse(content = "", toolCalls = listOf(toolCall))
            } catch (_: Exception) {
                AiResponse(content = output)
            }
        } else {
            AiResponse(content = output)
        }
    }
}
