package com.example.inventorymaster.ai

import com.example.inventorymaster.utils.JsonExtractor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JsonExtractor 测试（实现为无正则的手工扫描——围栏正则在部分机型 ICU 引擎上抛
 * PatternSyntaxException，见实机案例；本测试同时固化提取优先级行为）。
 */
class JsonExtractorTest {

    @Test
    fun `纯JSON对象直接解析`() {
        val result = JsonExtractor.extractJsonObject("""{"tool":"query_records","arguments":{"limit":5}}""")
        assertEquals("query_records", result?.get("tool")?.asString)
    }

    @Test
    fun `json代码围栏提取`() {
        val result = JsonExtractor.extractJsonObject("```json\n{\"tool\":\"list_sessions\"}\n```")
        assertEquals("list_sessions", result?.get("tool")?.asString)
    }

    @Test
    fun `无语言标注围栏提取`() {
        val result = JsonExtractor.extractJsonObject("```\n{\"tool\":\"list_sessions\"}\n```")
        assertEquals("list_sessions", result?.get("tool")?.asString)
    }

    @Test
    fun `前后带说明文字仍能提取`() {
        val result = JsonExtractor.extractJsonObject("好的 {\"tool\":\"a\"} 请查收")
        assertEquals("a", result?.get("tool")?.asString)
    }

    @Test
    fun `非JSON文本返回null`() {
        assertNull(JsonExtractor.extractJsonObject("今天库存一切正常。"))
        assertNull(JsonExtractor.extractJsonObject(""))
        assertNull(JsonExtractor.extractJsonObject("{不完整的"))
    }

    @Test
    fun `嵌套JSON完整提取到最外层`() {
        val result = JsonExtractor.extractJsonObject("""{"tool":"t","arguments":{"limit":3}}""")
        assertEquals(3, result?.getAsJsonObject("arguments")?.get("limit")?.asInt)
    }

    @Test
    fun `提取结果是JsonObject类型`() {
        val result = JsonExtractor.extractJsonObject("""{"k":"v"}""")
        assertTrue(result is JsonObject)
        assertEquals(JsonParser.parseString("""{"k":"v"}"""), result)
    }
}
