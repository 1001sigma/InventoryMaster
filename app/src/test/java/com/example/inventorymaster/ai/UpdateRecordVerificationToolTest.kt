package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.UpdateRecordVerificationTool
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class UpdateRecordVerificationToolTest {

    private fun toolCall(args: String) = AiToolCall(
        id = "call_update",
        name = "update_record_verification",
        arguments = JsonParser.parseString(args).asJsonObject
    )

    @Test
    fun `标记查验：成功更新查验状态与实盘数量`() = runBlocking {
        var savedRecord: StockRecord? = null
        val existing = StockRecord(
            id = 10L,
            sessionId = 1L,
            productKey = "KEY10",
            batchNumber = "B10",
            expiryDate = 20261231L,
            quantity = 20.0,
            location = "LOC-1",
            isVerified = false,
            actualQuantity = null,
            operator = "Tester"
        )

        val repo = Proxy.newProxyInstance(
            InventoryRepository::class.java.classLoader,
            arrayOf(InventoryRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getRecordById" -> existing
                "updateRecord" -> {
                    savedRecord = args[0] as StockRecord
                    Unit
                }
                else -> null
            }
        } as InventoryRepository

        val tool = UpdateRecordVerificationTool(repo)
        val result = tool.execute(toolCall("""{"record_id": 10, "is_verified": true, "actual_quantity": 18.0}"""))

        assertTrue(result.success)
        assertTrue(result.content.contains("标记为【已查验】"))
        assertTrue(savedRecord?.isVerified == true)
        assertEquals(18.0, savedRecord?.actualQuantity ?: 0.0, 0.001)
    }

    @Test
    fun `撤销查验：自动备份原实盘数量到备注并清空实盘数`() = runBlocking {
        var savedRecord: StockRecord? = null
        val existing = StockRecord(
            id = 20L,
            sessionId = 1L,
            productKey = "KEY20",
            batchNumber = "B20",
            expiryDate = 20261231L,
            quantity = 50.0,
            location = "LOC-2",
            isVerified = true,
            actualQuantity = 48.0,
            remarks = "原初备注",
            operator = "Tester"
        )

        val repo = Proxy.newProxyInstance(
            InventoryRepository::class.java.classLoader,
            arrayOf(InventoryRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getRecordById" -> existing
                "updateRecord" -> {
                    savedRecord = args[0] as StockRecord
                    Unit
                }
                else -> null
            }
        } as InventoryRepository

        val tool = UpdateRecordVerificationTool(repo)
        val result = tool.execute(toolCall("""{"record_id": 20, "is_verified": false}"""))

        assertTrue(result.success)
        assertTrue(result.content.contains("撤销查验"))
        assertTrue(result.content.contains("备份转存至备注"))
        assertFalse(savedRecord?.isVerified ?: true)
        assertNull(savedRecord?.actualQuantity)
        assertEquals("原实盘: 48; 原初备注", savedRecord?.remarks)
    }
}
