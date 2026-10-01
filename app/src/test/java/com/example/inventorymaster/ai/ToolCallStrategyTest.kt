package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.agent.strategy.FunctionCallingStrategy
import com.example.inventorymaster.ai.agent.strategy.JsonToolCallStrategy
import com.example.inventorymaster.ai.agent.strategy.ToolCallOutcome
import com.example.inventorymaster.ai.provider.AiResponse
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具调用协议策略测试（docs/AI_V3_REDESIGN.md §5 JsonToolCallStrategyTest）。
 * 覆盖 FC 透传与 JSON 文本协议的解析边界，防止 v2"正文猜工具"劫持回归。
 */
class ToolCallStrategyTest {

    private val tools = listOf(
        AiToolDefinition("query_records", "查询库存记录", JsonObject()),
        AiToolDefinition("list_sessions", "列出盘存任务", JsonObject())
    )

    // ── FunctionCallingStrategy ─────────────────────────────

    @Test
    fun `FC_原生tool_calls直接透传`() {
        val call = AiToolCall("c1", "query_records", JsonObject())
        val outcome = FunctionCallingStrategy().extractOutcome(
            AiResponse(content = "", toolCalls = listOf(call)), tools
        )
        val calls = (outcome as ToolCallOutcome.ToolCalls).calls
        assertEquals(1, calls.size)
        assertEquals("query_records", calls[0].name)
    }

    @Test
    fun `FC_无tool_calls为最终回答`() {
        val outcome = FunctionCallingStrategy().extractOutcome(AiResponse("库存正常"), tools)
        assertEquals("库存正常", (outcome as ToolCallOutcome.FinalAnswer).content)
    }

    @Test
    fun `FC_系统提示词不附加协议说明`() {
        assertEquals("基础提示词", FunctionCallingStrategy().buildSystemPrompt("基础提示词", tools))
    }

    // ── JsonToolCallStrategy ────────────────────────────────

    private fun extract(content: String): ToolCallOutcome =
        JsonToolCallStrategy().extractOutcome(AiResponse(content), tools)

    @Test
    fun `JSON_纯对象解析为工具调用`() {
        val outcome = extract("{\"tool\":\"query_records\",\"arguments\":{\"limit\":5}}")
        val calls = (outcome as ToolCallOutcome.ToolCalls).calls
        assertEquals("query_records", calls[0].name)
        assertEquals(5, calls[0].arguments.get("limit").asInt)
        assertTrue(calls[0].id.startsWith("json_"))
    }

    @Test
    fun `JSON_工具名大小写不敏感且归一为注册名`() {
        val outcome = extract("{\"tool\":\"QUERY_RECORDS\",\"arguments\":{}}")
        assertEquals("query_records", (outcome as ToolCallOutcome.ToolCalls).calls[0].name)
    }

    @Test
    fun `JSON_代码围栏包裹可解析`() {
        val outcome = extract("```json\n{\"tool\":\"list_sessions\",\"arguments\":{}}\n```")
        assertEquals("list_sessions", (outcome as ToolCallOutcome.ToolCalls).calls[0].name)
    }

    @Test
    fun `JSON_前后带说明文字仍能提取`() {
        val outcome = extract("好的，我来查询 {\"tool\":\"query_records\",\"arguments\":{}} 请稍候")
        assertEquals("query_records", (outcome as ToolCallOutcome.ToolCalls).calls[0].name)
    }

    @Test
    fun `JSON_缺arguments默认空参数`() {
        val outcome = extract("{\"tool\":\"list_sessions\"}")
        val calls = (outcome as ToolCallOutcome.ToolCalls).calls
        assertEquals(0, calls[0].arguments.entrySet().size)
    }

    @Test
    fun `JSON_纯文本回答不劫持`() {
        val outcome = extract("今天库存一切正常，共 120 条记录。")
        assertTrue(outcome is ToolCallOutcome.FinalAnswer)
    }

    @Test
    fun `JSON_未知工具名按普通回答处理而非伪造调用`() {
        val raw = "{\"tool\":\"delete_all_data\",\"arguments\":{}}"
        val outcome = extract(raw)
        assertEquals(raw, (outcome as ToolCallOutcome.FinalAnswer).content)
    }

    @Test
    fun `JSON_arguments非对象判为畸形`() {
        val outcome = extract("{\"tool\":\"query_records\",\"arguments\":\"limit=5\"}")
        assertTrue(outcome is ToolCallOutcome.MalformedJson)
    }

    @Test
    fun `JSON_疑似工具意图但非法JSON判为畸形`() {
        val outcome = extract("{\"tool\": \"query_records\", \"broken\": }")
        assertTrue(outcome is ToolCallOutcome.MalformedJson)
    }

    @Test
    fun `JSON_参数以JSON字符串原样保留`() {
        val outcome = extract("{\"tool\":\"query_records\",\"arguments\":{\"keyword\":\"输液器\"}}")
        val args = (outcome as ToolCallOutcome.ToolCalls).calls[0].arguments
        assertEquals("输液器", args.get("keyword").asString)
    }

    @Test
    fun `JSON_系统提示词附加协议与工具清单`() {
        val prompt = JsonToolCallStrategy().buildSystemPrompt("基础提示词", tools)
        assertTrue(prompt.startsWith("基础提示词"))
        assertTrue(prompt.contains("工具调用协议"))
        assertTrue(prompt.contains("query_records"))
        assertTrue(prompt.contains("list_sessions"))
        assertTrue(prompt.contains("\"tool\""))
    }

    @Test
    fun `JSON_无工具时系统提示词原样返回`() {
        assertEquals("基础提示词", JsonToolCallStrategy().buildSystemPrompt("基础提示词", emptyList()))
    }

    @Test
    fun `JSON_空内容按最终回答处理`() {
        val outcome = extract("")
        assertTrue(outcome is ToolCallOutcome.FinalAnswer)
    }

    // 工具参数构造辅助（防止以后误删导入）
    @Suppress("unused")
    private fun parseArgs(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}
