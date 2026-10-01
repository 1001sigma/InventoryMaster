package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.provider.SseParser
import com.example.inventorymaster.ai.provider.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI 兼容 SSE 载荷解析测试（docs/AI_V3_REDESIGN.md §4.8）。
 * 覆盖正文增量、tool_calls 分片、[DONE]、无产出事件与损坏行容错。
 */
class SseParserTest {

    @Test
    fun `正文增量解析为Text`() {
        val chunks = SseParser.parse("""{"choices":[{"delta":{"content":"你好"}}]}""")
        assertEquals(listOf(StreamChunk.Text("你好")), chunks)
    }

    @Test
    fun `工具调用首片携带id与name`() {
        val chunks = SseParser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"query_records","arguments":""}}]}}]}"""
        )
        assertEquals(1, chunks.size)
        val delta = chunks[0] as StreamChunk.ToolCallDelta
        assertEquals(0, delta.index)
        assertEquals("call_1", delta.id)
        assertEquals("query_records", delta.name)
        assertEquals("", delta.argumentsDelta)
    }

    @Test
    fun `工具调用参数分片只带增量`() {
        val chunks = SseParser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"lim"}}]}}]}"""
        )
        val delta = chunks[0] as StreamChunk.ToolCallDelta
        assertNull(delta.id)
        assertNull(delta.name)
        assertEquals("{\"lim", delta.argumentsDelta)
    }

    @Test
    fun `单事件多个tool_calls分片全部产出`() {
        val chunks = SseParser.parse(
            """{"choices":[{"delta":{"tool_calls":[
               {"index":0,"id":"c0","function":{"name":"a","arguments":"{}"}},
               {"index":1,"id":"c1","function":{"name":"b","arguments":""}}
               ]}}]}"""
        )
        assertEquals(2, chunks.size)
        assertEquals(0, (chunks[0] as StreamChunk.ToolCallDelta).index)
        assertEquals(1, (chunks[1] as StreamChunk.ToolCallDelta).index)
    }

    @Test
    fun `DONE标记解析为Completed`() {
        assertEquals(listOf<StreamChunk>(StreamChunk.Completed), SseParser.parse("[DONE]"))
    }

    @Test
    fun `role与finish_reason等无产出事件返回空`() {
        assertTrue(SseParser.parse("""{"choices":[{"delta":{"role":"assistant"}}]}""").isEmpty())
        assertTrue(SseParser.parse("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""").isEmpty())
    }

    @Test
    fun `损坏行返回空不抛异常`() {
        assertTrue(SseParser.parse("{not json").isEmpty())
        assertTrue(SseParser.parse("").isEmpty())
        assertTrue(SseParser.parse("""{"choices":"oops"}""").isEmpty())
    }

    private fun assertNull(actual: Any?) = org.junit.Assert.assertNull(actual)
}
