package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.runtime.ToolCallAccumulator
import com.example.inventorymaster.ai.provider.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式 tool_calls 分片聚合测试（docs/AI_V3_REDESIGN.md §4.8）。
 * 覆盖：跨分片累积、多并行调用按 index 区分、缺 id 兜底、畸形参数容错。
 */
class ToolCallAccumulatorTest {

    private fun delta(
        index: Int,
        id: String? = null,
        name: String? = null,
        args: String = ""
    ) = StreamChunk.ToolCallDelta(index = index, id = id, name = name, argumentsDelta = args)

    @Test
    fun `跨分片累积id_name与参数`() {
        val acc = ToolCallAccumulator()
        acc.add(delta(0, id = "call_1", name = "query_records"))
        acc.add(delta(0, args = "{\"lim"))
        acc.add(delta(0, args = "it\":3}"))

        val calls = acc.toToolCalls()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("query_records", calls[0].name)
        assertEquals(3, calls[0].arguments.get("limit").asInt)
    }

    @Test
    fun `多并行调用按index区分且导出有序`() {
        val acc = ToolCallAccumulator()
        acc.add(delta(1, id = "c1", name = "list_sessions", args = "{}"))
        acc.add(delta(0, id = "c0", name = "query_records", args = "{}"))

        val calls = acc.toToolCalls()
        assertEquals(listOf("c0", "c1"), calls.map { it.id })
        assertEquals(listOf("query_records", "list_sessions"), calls.map { it.name })
    }

    @Test
    fun `缺id时用stream_index兜底`() {
        val acc = ToolCallAccumulator()
        acc.add(delta(2, name = "query_records", args = "{}"))
        assertEquals("stream_2", acc.toToolCalls()[0].id)
    }

    @Test
    fun `畸形参数JSON容错为空对象不中断`() {
        val acc = ToolCallAccumulator()
        acc.add(delta(0, id = "c1", name = "query_records", args = "{\"limit\": "))  // 截断
        val calls = acc.toToolCalls()
        assertEquals(1, calls.size)
        assertTrue(calls[0].arguments.entrySet().isEmpty())
    }

    @Test
    fun `空聚合器导出空列表`() {
        val acc = ToolCallAccumulator()
        assertTrue(acc.isEmpty())
        assertTrue(acc.toToolCalls().isEmpty())
    }
}
