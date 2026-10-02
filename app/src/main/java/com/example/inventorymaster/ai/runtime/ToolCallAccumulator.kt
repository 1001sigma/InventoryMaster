package com.example.inventorymaster.ai.runtime

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.StreamChunk
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 聚合流式 tool_calls 分片为完整 [AiToolCall]（docs/AI_V3_REDESIGN.md §4.8）。
 *
 * OpenAI 流式协议下 id/name 只在首片出现，arguments 逐片追加，多个并行调用靠 index 区分。
 * 生命周期：每轮 LLM 调用新建一个实例，流结束后 [toToolCalls] 导出。
 */
class ToolCallAccumulator {

    private class Pending {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private val pending = LinkedHashMap<Int, Pending>()

    fun add(delta: StreamChunk.ToolCallDelta) {
        val item = pending.getOrPut(delta.index) { Pending() }
        delta.id?.let { item.id = it }
        delta.name?.let { item.name = it }
        if (delta.argumentsDelta.isNotEmpty()) item.arguments.append(delta.argumentsDelta)
    }

    fun isEmpty(): Boolean = pending.isEmpty()

    /**
     * 导出完整工具调用（按 index 升序）。
     * arguments 解析失败（截断/畸形）时置空对象——由 ToolExecutor 报参数错误回喂模型自纠，
     * 不让一个坏分片中断整轮对话。
     */
    fun toToolCalls(): List<AiToolCall> =
        pending.entries.sortedBy { it.key }.map { (index, item) ->
            val arguments = try {
                JsonParser.parseString(item.arguments.toString()).asJsonObject
            } catch (_: Exception) {
                JsonObject()
            }
            AiToolCall(
                id = item.id ?: "stream_$index",
                name = item.name ?: "unknown",
                arguments = arguments
            )
        }
}
