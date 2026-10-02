package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.ai.provider.OpenAiRequestBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI 兼容请求体构造测试。
 * 固化兼容怪癖（均有实机依据）：null content→""、arguments 必须是字符串、system 置首。
 */
class OpenAiRequestBuilderTest {

    private val gson = com.google.gson.Gson()

    private fun build(
        messages: List<AiMessage>,
        systemPrompt: String? = null,
        tools: List<AiToolDefinition>? = null,
        stream: Boolean = false
    ): JsonObject =
        JsonParser.parseString(
            OpenAiRequestBuilder.build(gson, "test-model", messages, systemPrompt, tools, stream)
        ).asJsonObject

    @Test
    fun `system提示词置于消息列首`() {
        val json = build(listOf(AiMessage("user", "你好")), systemPrompt = "你是库存助手")
        val arr = json.getAsJsonArray("messages")
        assertEquals(2, arr.size())
        assertEquals("system", arr[0].asJsonObject.get("role").asString)
        assertEquals("你是库存助手", arr[0].asJsonObject.get("content").asString)
        assertEquals("user", arr[1].asJsonObject.get("role").asString)
    }

    @Test
    fun `assistant_content为null时序列化为空字符串_NVIDIA兼容`() {
        val json = build(listOf(AiMessage(role = "assistant", content = null)))
        val msg = json.getAsJsonArray("messages")[0].asJsonObject
        assertEquals("assistant", msg.get("role").asString)
        assertTrue(msg.get("content").isJsonPrimitive)
        assertEquals("", msg.get("content").asString)
    }

    @Test
    fun `tool_calls的arguments必须是JSON字符串而非对象`() {
        val args = JsonParser.parseString("""{"limit":5}""").asJsonObject
        val json = build(
            listOf(AiMessage(role = "assistant", content = "", toolCalls = listOf(AiToolCall("c1", "query_records", args))))
        )
        val tc = json.getAsJsonArray("messages")[0].asJsonObject
            .getAsJsonArray("tool_calls")[0].asJsonObject
        assertEquals("function", tc.get("type").asString)
        // arguments 是字符串，且其内容本身是合法 JSON 对象
        val argsStr = tc.getAsJsonObject("function").get("arguments").asString
        assertEquals(5, JsonParser.parseString(argsStr).asJsonObject.get("limit").asInt)
    }

    @Test
    fun `tool角色消息携带tool_call_id`() {
        val json = build(listOf(AiMessage(role = "tool", content = "结果", toolCallId = "c9")))
        val msg = json.getAsJsonArray("messages")[0].asJsonObject
        assertEquals("c9", msg.get("tool_call_id").asString)
        assertEquals("结果", msg.get("content").asString)
    }

    @Test
    fun `tools非空时携带function定义_空时不携带`() {
        val schema = JsonParser.parseString("""{"type":"object","properties":{}}""").asJsonObject
        val withTools = build(emptyList(), tools = listOf(AiToolDefinition("t1", "描述", schema)))
        val t = withTools.getAsJsonArray("tools")[0].asJsonObject
        assertEquals("function", t.get("type").asString)
        assertEquals("t1", t.getAsJsonObject("function").get("name").asString)

        val withoutTools = build(emptyList(), tools = emptyList())
        assertTrue(withoutTools.get("tools") == null)
    }

    @Test
    fun `stream标志写入请求体`() {
        assertTrue(build(emptyList(), stream = true).get("stream").asBoolean)
        assertFalse(build(emptyList(), stream = false).get("stream").asBoolean)
    }
}
