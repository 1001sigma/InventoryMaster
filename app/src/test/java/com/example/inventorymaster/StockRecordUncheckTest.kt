package com.example.inventorymaster

import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.uncheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StockRecordUncheckTest {

    @Test
    fun `当实盘数量为整数时撤销查验正确备份到备注并置空实盘数`() {
        val record = StockRecord(
            id = 1L,
            sessionId = 10L,
            productKey = "KEY1",
            batchNumber = "BATCH01",
            expiryDate = 20261231L,
            quantity = 100.0,
            location = "A-01",
            actualQuantity = 50.0,
            remarks = null,
            isVerified = true,
            operator = "Tester"
        )

        val unchecked = record.uncheck()

        assertFalse(unchecked.isVerified)
        assertNull(unchecked.actualQuantity)
        assertEquals("原实盘: 50", unchecked.remarks)
        assertEquals(1, unchecked.syncStatus)
    }

    @Test
    fun `当实盘数量为小数且已有备注时撤销查验智能拼接备注`() {
        val record = StockRecord(
            id = 2L,
            sessionId = 10L,
            productKey = "KEY2",
            batchNumber = "BATCH02",
            expiryDate = 20261231L,
            quantity = 100.0,
            location = "A-02",
            actualQuantity = 45.5,
            remarks = "外包装轻微破损",
            isVerified = true,
            operator = "Tester"
        )

        val unchecked = record.uncheck()

        assertFalse(unchecked.isVerified)
        assertNull(unchecked.actualQuantity)
        assertEquals("原实盘: 45.5; 外包装轻微破损", unchecked.remarks)
        assertEquals(1, unchecked.syncStatus)
    }

    @Test
    fun `当实盘数量为空时撤销查验保持备注不变`() {
        val record = StockRecord(
            id = 3L,
            sessionId = 10L,
            productKey = "KEY3",
            batchNumber = "BATCH03",
            expiryDate = 20261231L,
            quantity = 100.0,
            location = "A-03",
            actualQuantity = null,
            remarks = "初始备注",
            isVerified = true,
            operator = "Tester"
        )

        val unchecked = record.uncheck()

        assertFalse(unchecked.isVerified)
        assertNull(unchecked.actualQuantity)
        assertEquals("初始备注", unchecked.remarks)
        assertEquals(1, unchecked.syncStatus)
    }
}
