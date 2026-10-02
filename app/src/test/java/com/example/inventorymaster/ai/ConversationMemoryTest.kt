package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮次记忆核心不变量：导出的消息序列中 tool_calls 与 tool 结果永远完整配对。
 * v2 的头部移除裁剪会拆散配对导致 OpenAI 兼容接口 400（docs/AI_V3_REDESIGN.md BUG-1）。
 */
class ConversationMemoryTest {

    private fun toolCall(id: String): AiToolCall =
        AiToolCall(id = id, name = "query_records", arguments = JsonObject())

    /** 校验：每个 tool 消息都有对应的待配对 tool_calls；序列末尾无未闭合的 tool_calls */
    private fun assertPairingValid(messages: List<AiMessage>) {
        val openIds = mutableSetOf<String>()
        for (m in messages) {
            m.toolCalls?.forEach { openIds.add(it.id) }
            if (m.role == "tool") {
                val id = m.toolCallId
                assertTrue("tool 消息($id) 无对应 tool_calls，序列非法", id != null && openIds.contains(id))
                openIds.remove(id)
            }
        }
        assertTrue("序列末尾仍有未配对的 tool_calls: $openIds", openIds.isEmpty())
    }

    private fun fillOneTurn(memory: ConversationMemory, userText: String, toolRounds: Int) {
        memory.openUserTurn(userText)
        repeat(toolRounds) { r ->
            memory.appendAssistantToolCalls(null, listOf(toolCall("call_${userText}_$r")))
            memory.appendToolResult("call_${userText}_$r", "query_records", "[]")
        }
        memory.closeTurn("回复 $userText")
    }

    @Test
    fun `轮次超过上限时裁剪不拆散工具配对`() {
        val memory = ConversationMemory()
        // 15 轮 × 每轮 3 条工具消息，必然触发 MAX_TURNS=10 裁剪
        repeat(15) { i -> fillOneTurn(memory, "问题$i", 3) }

        val exported = memory.messagesForRequest()
        assertPairingValid(exported)
        val userCount = exported.count { it.role == "user" && it.content.orEmpty().startsWith("问题") }
        assertTrue("裁剪后应只剩最近几轮", userCount in 1..10)
    }

    @Test
    fun `软字符预算触发裁剪且配对完整`() {
        val memory = ConversationMemory()
        val bigText = "很长的内容".repeat(1000) // ~6000 字符/轮
        repeat(8) { i -> fillOneTurn(memory, "$bigText#$i", 2) }

        val exported = memory.messagesForRequest()
        assertPairingValid(exported)
        val totalChars = exported.sumOf { it.content?.length ?: 0 }
        // 至少裁掉了一些旧轮次（否则远超预算）
        assertTrue("软预算未生效", totalChars < 8 * 6000)
    }

    @Test
    fun `超长工具结果被截断并注明`() {
        val memory = ConversationMemory()
        memory.openUserTurn("问题")
        memory.appendAssistantToolCalls(null, listOf(toolCall("c1")))
        val big = "x".repeat(9000)
        memory.appendToolResult("c1", "query_records", big)
        memory.closeTurn("完成")

        val toolMsg = memory.messagesForRequest().first { it.role == "tool" }
        assertTrue(toolMsg.content!!.contains("已截断"))
        assertTrue(toolMsg.content!!.length < big.length)
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `异常兜底 closeTurn 补齐未完成轮次`() {
        val memory = ConversationMemory()
        // 模拟取消场景：用户消息已写入、工具结果已写入但没有最终回复
        memory.openUserTurn("问题")
        memory.appendAssistantToolCalls(null, listOf(toolCall("c1")))
        memory.appendToolResult("c1", "query_records", "[]")
        memory.closeTurn("（本次回答已停止）")

        val exported = memory.messagesForRequest()
        assertEquals("assistant", exported.last().role)
        assertPairingValid(exported)
    }

    @Test
    fun `无开启轮次时 closeTurn 独立成条`() {
        val memory = ConversationMemory()
        memory.closeTurn("服务暂时不可用")
        val exported = memory.messagesForRequest()
        assertEquals(listOf("assistant"), exported.map { it.role })
        assertPairingValid(exported)
    }

    @Test
    fun `JSON 纠正流程的 assistant+user 追加保持序列合法`() {
        val memory = ConversationMemory()
        memory.openUserTurn("查库存")
        memory.appendAssistantMessage("{\"tool\": \"query_records\"") // 残缺 JSON
        memory.appendUserMessage("你上一条回复无法解析……")
        memory.appendAssistantMessage("好的，我来直接回答") // 模拟最终回答
        memory.closeTurn("最终回答")

        val exported = memory.messagesForRequest()
        assertPairingValid(exported)
        assertEquals("assistant", exported.last().role)
    }

    @Test
    fun `clear 后可重新开始`() {
        val memory = ConversationMemory()
        fillOneTurn(memory, "问题", 2)
        memory.clear()
        assertEquals(0, memory.messagesForRequest().size)
        fillOneTurn(memory, "新问题", 1)
        assertPairingValid(memory.messagesForRequest())
    }
}
