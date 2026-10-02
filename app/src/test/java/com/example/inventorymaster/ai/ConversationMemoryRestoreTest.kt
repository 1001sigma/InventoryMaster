package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConversationMemory.restoreFrom 测试（聊天持久化恢复）：
 * 恢复后导出序列与原序列一致、tool 配对完整、且可继续正常追加新轮次。
 */
class ConversationMemoryRestoreTest {

    private fun toolCall(id: String): AiToolCall =
        AiToolCall(id = id, name = "query_records", arguments = JsonObject())

    private fun assertPairingValid(messages: List<AiMessage>) {
        val openIds = mutableSetOf<String>()
        for (m in messages) {
            m.toolCalls?.forEach { openIds.add(it.id) }
            if (m.role == "tool") {
                assertTrue("tool 消息(${m.toolCallId}) 无对应 tool_calls", m.toolCallId != null && openIds.contains(m.toolCallId))
                openIds.remove(m.toolCallId)
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
    fun `恢复多轮快照后导出序列与原序列一致`() {
        val original = ConversationMemory()
        fillOneTurn(original, "问题A", 2)
        fillOneTurn(original, "问题B", 1)
        val exported = original.messagesForRequest()

        val restored = ConversationMemory()
        restored.restoreFrom(exported)

        assertEquals(exported, restored.messagesForRequest())
        assertPairingValid(restored.messagesForRequest())
    }

    @Test
    fun `恢复后可继续追加新轮次`() {
        val restored = ConversationMemory()
        restored.restoreFrom(listOf(AiMessage("user", "旧问题"), AiMessage("assistant", "旧回答")))

        fillOneTurn(restored, "新问题", 1)

        val exported = restored.messagesForRequest()
        assertPairingValid(exported)
        assertEquals(listOf("user", "assistant", "user", "assistant", "tool", "assistant"), exported.map { it.role })
        assertEquals("回复 新问题", exported.last().content)
    }

    @Test
    fun `恢复空序列等于clear`() {
        val memory = ConversationMemory()
        fillOneTurn(memory, "旧问题", 1)
        memory.restoreFrom(emptyList())
        assertTrue(memory.messagesForRequest().isEmpty())
    }
}
