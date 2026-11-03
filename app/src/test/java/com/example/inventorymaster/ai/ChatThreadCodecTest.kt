package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.ProductCardListCodec
import com.example.inventorymaster.ai.agent.UiAction
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

    @Test
    fun `耗时与模型ID随快照往返无损`() {
        val original = ConversationMemory()
        original.openUserTurn("你好")
        original.closeTurn("库存共 12 件", durationMs = 5200L, model = "glm-5.3")

        val restored = ConversationMemory()
        restored.restoreFrom(ChatThreadCodec.toMessages(ChatThreadCodec.toRows("42", original.messagesForRequest())))
        val reply = restored.messagesForRequest().single { it.role == "assistant" }
        assertEquals(5200L, reply.durationMs)
        assertEquals("glm-5.3", reply.model)
    }

    @Test
    fun `卡片JSON随快照往返无损`() {
        val cardJson = ProductCardListCodec.toJson(
            UiAction.ShowProductCardList(
                title = "查询结果中的重点物资：",
                products = listOf(
                    ExpiredProductCardData(
                        productKey = "BG-100",
                        productName = "血气测定试剂盒（电极法）",
                        batchNumber = "260416F",
                        expiryDateStr = "2026-10-12",
                        quantity = 10.0,
                        sessionId = "24",
                        recordId = 77L
                    )
                )
            )
        )
        val original = ConversationMemory()
        original.openUserTurn("查近效期")
        original.closeTurn("共 1 条近效期", cardActionJson = cardJson)

        val restored = ConversationMemory()
        restored.restoreFrom(ChatThreadCodec.toMessages(ChatThreadCodec.toRows("42", original.messagesForRequest())))
        val reply = restored.messagesForRequest().single { it.role == "assistant" }
        assertEquals(cardJson, reply.cardActionJson)

        // 反序列化回卡片对象，字段逐项相等（这是 ChatViewModel.displayMessages 的恢复路径）
        val card = ProductCardListCodec.fromJson(reply.cardActionJson!!)
        requireNotNull(card)
        assertEquals("查询结果中的重点物资：", card.title)
        val p = card.products.single()
        assertEquals("BG-100", p.productKey)
        assertEquals("血气测定试剂盒（电极法）", p.productName)
        assertEquals("260416F", p.batchNumber)
        assertEquals("2026-10-12", p.expiryDateStr)
        assertEquals(10.0, p.quantity, 0.0)
        assertEquals("24", p.sessionId)
        assertEquals(77L, p.recordId)
    }

    @Test
    fun `坏JSON与旧数据反序列化为null不崩`() {
        assertEquals(null, ProductCardListCodec.fromJson("不是JSON"))
        assertEquals(null, ProductCardListCodec.fromJson("""{"type":"unknown"}"""))
        assertEquals(null, ProductCardListCodec.fromJson("""{"type":"productList"}""")) // 缺 products 数组
    }
}
