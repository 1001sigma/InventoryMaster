package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.QueryVerificationRecordsTool
import com.example.inventorymaster.data.repository.AiRecordQuery
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class QueryVerificationRecordsToolTest {

    @Suppress("UNCHECKED_CAST")
    private fun fakeRepository(onQuery: (AiRecordQuery) -> String): InventoryRepository {
        return Proxy.newProxyInstance(
            InventoryRepository::class.java.classLoader,
            arrayOf(InventoryRepository::class.java)
        ) { _, method, args ->
            if (method.name == "queryRecordsForAi") {
                onQuery(args[0] as AiRecordQuery)
            } else {
                null
            }
        } as InventoryRepository
    }

    private fun toolCall(args: String) = AiToolCall(
        id = "call_test",
        name = "query_verification_records",
        arguments = JsonParser.parseString(args).asJsonObject
    )

    @Test
    fun `参数解析：未查验、厂家、仓库与limit硬截断为20`() = runBlocking {
        var capturedQuery: AiRecordQuery? = null
        val repo = fakeRepository { query ->
            capturedQuery = query
            """{"status":"ok","total":0,"returned":0,"rows":[]}"""
        }
        val tool = QueryVerificationRecordsTool(repo)

        tool.execute(
            toolCall(
                """{
                    "verification_status": "unverified",
                    "warehouse_name": "冷藏库",
                    "manufacturer": "西门子",
                    "limit": 100
                }"""
            )
        )

        assertNotNull(capturedQuery)
        assertEquals(false, capturedQuery?.isVerified)
        assertEquals("冷藏库", capturedQuery?.location)
        assertEquals("西门子", capturedQuery?.manufacturer)
        // 关键防御：limit 被强制收窄为 20，不可突破
        assertEquals(20, capturedQuery?.limit)
    }

    @Test
    fun `问还有多少条（count模式）：直接返回对应数量，不产生卡片`() = runBlocking {
        var capturedLimit: Int? = null
        val repo = fakeRepository { query ->
            capturedLimit = query.limit
            """{"status":"ok","total":42,"returned":1,"rows":[]}"""
        }
        val tool = QueryVerificationRecordsTool(repo)

        val result = tool.execute(
            toolCall(
                """{
                    "verification_status": "unverified",
                    "query_mode": "count"
                }"""
            )
        )

        assertTrue(result.success)
        assertEquals(1, capturedLimit) // count 模式 limit 为 1
        assertTrue(result.content.contains("共有 42 条"))
        assertTrue(result.content.contains("切勿逐条罗列明细"))
        assertNull(result.uiAction) // 严禁产生卡片
    }

    @Test
    fun `清单查询数量小于等于20条：生成产品卡片展示`() = runBlocking {
        val fakeJson = """
            {
              "status": "ok",
              "total": 1,
              "returned": 1,
              "rows": [
                {
                  "记录ID": 101,
                  "任务ID": 3,
                  "任务名称": "十月盘点",
                  "产品编码": "P123",
                  "产品名称": "医用防护服",
                  "规格": "175(L)",
                  "厂家": "稳健医疗",
                  "物料编码": "MAT-001",
                  "DI": "0697123456",
                  "单位": "套",
                  "批号": "LOT2024",
                  "数量": 50.0,
                  "库位": "冷藏库A-1",
                  "is_verified": 0,
                  "备注": "未查验包装完好",
                  "生产日期": "2024-01-01",
                  "有效期": "2026-12-31"
                }
              ]
            }
        """.trimIndent()

        val repo = fakeRepository { fakeJson }
        val tool = QueryVerificationRecordsTool(repo)

        val result = tool.execute(toolCall("""{"verification_status":"unverified","query_mode":"list"}"""))

        assertTrue(result.success)
        assertTrue(result.content.contains("共 1 条"))

        val action = result.uiAction as? UiAction.ShowProductCardList
        assertNotNull(action)
        assertEquals(1, action?.products?.size)
        val card = action?.products?.first()
        assertNotNull(card)
        assertEquals("医用防护服", card?.productName)
        assertEquals("稳健医疗", card?.manufacturer)
        assertEquals("十月盘点", card?.sessionName)
        assertEquals("冷藏库A-1", card?.location)
        assertEquals("175(L)", card?.specification)
        assertEquals("套", card?.unit)
        assertEquals(false, card?.isVerified)
        assertEquals("MAT-001", card?.materialCode)
        assertEquals("0697123456", card?.di)
        assertEquals("2024-01-01", card?.productionDateStr)
        assertEquals("2026-12-31", card?.expiryDateStr)
        assertEquals(50.0, card?.quantity ?: 0.0, 0.001)
    }

    @Test
    fun `清单查询数量大于20条：生成Markdown表格，不产生卡片`() = runBlocking {
        val fakeJson = """
            {
              "status": "ok",
              "total": 35,
              "returned": 2,
              "rows": [
                {
                  "记录ID": 101,
                  "产品名称": "注射器",
                  "规格": "20ml",
                  "批号": "B01",
                  "库位": "A-01",
                  "数量": 100.0,
                  "有效期": "2027-01-01"
                },
                {
                  "记录ID": 102,
                  "产品名称": "采血管",
                  "规格": "5ml",
                  "批号": "B02",
                  "库位": "A-02",
                  "数量": 200.0,
                  "有效期": "2026-06-01"
                }
              ]
            }
        """.trimIndent()

        val repo = fakeRepository { fakeJson }
        val tool = QueryVerificationRecordsTool(repo)

        val result = tool.execute(toolCall("""{"verification_status":"unverified","query_mode":"list"}"""))

        assertTrue(result.success)
        assertTrue(result.content.contains("共 35 条"))
        assertTrue(result.content.contains("| 产品名称 | 规格 | 批号 | 库位 | 数量 | 有效期 |"))
        assertTrue(result.content.contains("| 注射器 | 20ml | B01 | A-01 | 100 | 2027-01-01 |"))
        assertTrue(result.content.contains("| 采血管 | 5ml | B02 | A-02 | 200 | 2026-06-01 |"))
        assertNull(result.uiAction) // 大于20条按规则绝不生成卡片
    }

    @Test
    fun `0条结果返回友好的自然语言提示`() = runBlocking {
        val repo = fakeRepository { """{"status":"ok","total":0,"returned":0,"rows":[]}""" }
        val tool = QueryVerificationRecordsTool(repo)

        val result = tool.execute(toolCall("""{"verification_status":"verified","warehouse_name":"冷藏库"}"""))

        assertTrue(result.success)
        assertTrue(result.content.contains("未找到匹配的库存记录"))
        assertNull(result.uiAction)
    }
}
