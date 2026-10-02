package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.memory.ChatThreadCodec
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聊天线程持久化编解码测试：消息序列 ↔ Room 行往返无损 */
class ChatThreadCodecTest {

    private fun argsJson(): JsonObject =
        JsonParser.parseString("""{"limit": 5}""").asJsonObject

    private fun sampleMessages(): List<AiMessage> = listOf(
        AiMessage("user", "查一下库存"),
        AiMessage(role = "assistant", content = "", toolCalls = listOf(AiToolCall("c1", "query_records", argsJson()))),
        AiMessage(role = "tool", content = """{"状态":"ok"}""", toolCallId = "c1"),
        AiMessage("assistant", "库存共 12 件")
    )

    @Test
    fun `四类消息往返字段无损`() {
        val messages = sampleMessages()
        val rows = ChatThreadCodec.toRows("42", messages, now = 1000L)

        assertEquals(4, rows.size)
        assertTrue(rows.all { it.threadId == "42" })
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.sortIndex })
        assertTrue(rows.all { it.createdAt == 1000L })

        val restored = ChatThreadCodec.toMessages(rows)
        assertEquals(4, restored.size)
        assertEquals("user", restored[0].role)
        assertEquals("查一下库存", restored[0].content)
        val calls = restored[1].toolCalls.orEmpty()
        assertEquals(1, calls.size)
        assertEquals("c1", calls[0].id)
        assertEquals("query_records", calls[0].name)
        assertEquals(5, calls[0].arguments.get("limit").asInt)
        assertEquals("tool", restored[2].role)
        assertEquals("c1", restored[2].toolCallId)
        assertEquals("assistant", restored[3].role)
        assertEquals("库存共 12 件", restored[3].content)
    }

    @Test
    fun `空序列往返为空`() {
        assertTrue(ChatThreadCodec.toRows("global", emptyList()).isEmpty())
        assertTrue(ChatThreadCodec.toMessages(emptyList()).isEmpty())
    }

    @Test
    fun `往返后可直接恢复进memory且配对完整`() {
        val original = ConversationMemory()
        original.openUserTurn("查一下库存")
        original.appendAssistantToolCalls("", listOf(AiToolCall("c1", "query_records", argsJson())))
        original.appendToolResult("c1", "query_records", """{"状态":"ok","合计":12}""")
        original.closeTurn("库存共 12 件")
        val exported = original.messagesForRequest()

        val restored = ConversationMemory()
        restored.restoreFrom(ChatThreadCodec.toMessages(ChatThreadCodec.toRows("42", exported)))

        assertEquals(exported.map { it.role }, restored.messagesForRequest().map { it.role })
        assertEquals(exported, restored.messagesForRequest())
    }
}
