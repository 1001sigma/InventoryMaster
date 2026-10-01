package com.example.inventorymaster.ai.provider

import com.google.gson.JsonParser

/**
 * OpenAI 兼容 SSE data 载荷解析（纯函数，可单测）。
 *
 * 输入为去掉 "data:" 前缀后的字符串，输出 0..N 个流式事件：
 * - `[DONE]` → [StreamChunk.Completed]
 * - `delta.content` → [StreamChunk.Text]
 * - `delta.tool_calls[]` → [StreamChunk.ToolCallDelta]（一个事件可含多个分片）
 * - role / finish_reason 等无产出事件 → 空列表
 * 单行损坏返回空列表——SSE 个别行损坏不应中断整条流。
 */
object SseParser {

    fun parse(data: String): List<StreamChunk> {
        if (data == "[DONE]") return listOf(StreamChunk.Completed)
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            val choices = json.getAsJsonArray("choices") ?: return emptyList()
            if (choices.size() == 0) return emptyList()
            val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return emptyList()
            val chunks = mutableListOf<StreamChunk>()

            delta.get("content")?.takeIf { it.isJsonPrimitive && it.asString.isNotEmpty() }?.let {
                chunks += StreamChunk.Text(it.asString)
            }

            delta.getAsJsonArray("tool_calls")?.forEach { element ->
                val tc = element.asJsonObject
                val function = tc.getAsJsonObject("function")
                chunks += StreamChunk.ToolCallDelta(
                    index = tc.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                    id = tc.get("id")?.takeIf { it.isJsonPrimitive }?.asString,
                    name = function?.get("name")?.takeIf { it.isJsonPrimitive }?.asString,
                    argumentsDelta = function?.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                )
            }
            chunks
        } catch (_: Exception) {
            emptyList()
        }
    }
}
