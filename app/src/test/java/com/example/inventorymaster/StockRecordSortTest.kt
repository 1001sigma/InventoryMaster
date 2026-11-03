package com.example.inventorymaster

import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordUiModel
import com.example.inventorymaster.data.model.StockRecordCombined
import org.junit.Assert.assertEquals
import org.junit.Test

class StockRecordSortTest {

    private fun createUiModel(id: Long, isVerified: Boolean): StockRecordUiModel {
        val record = StockRecord(
            id = id,
            sessionId = 1L,
            productKey = "PROD_$id",
            quantity = 10.0,
            batchNumber = "B00$id",
            expiryDate = 1893456000000L,
            location = "A-1",
            isVerified = isVerified,
            operator = "Tester"
        )
        val combined = StockRecordCombined(record = record, product = null)
        return StockRecordUiModel(combined = combined)
    }

    private fun sortList(list: List<StockRecordUiModel>, sinkVerified: Boolean): List<StockRecordUiModel> {
        return if (sinkVerified) {
            list.sortedWith(compareBy({ it.combined.record.isVerified }, { it.combined.record.id }))
        } else {
            list.sortedBy { it.combined.record.id }
        }
    }

    @Test
    fun testOptionA_keepInPlace_whenSinkDisabled() {
        // 初始数据：4 条记录，id 1..4，全部未查验
        val list = listOf(
            createUiModel(1L, isVerified = false),
            createUiModel(2L, isVerified = false),
            createUiModel(3L, isVerified = false),
            createUiModel(4L, isVerified = false)
        )

        // 用户将第 2 条查验
        val updatedList = list.map {
            if (it.combined.record.id == 2L) createUiModel(2L, isVerified = true) else it
        }

        // 方案 A（默认关闭 sinkVerifiedItems）：列表顺序严格按 id 保持原位
        val sorted = sortList(updatedList, sinkVerified = false)
        assertEquals(listOf(1L, 2L, 3L, 4L), sorted.map { it.combined.record.id })
        assertEquals(true, sorted[1].combined.record.isVerified)
    }

    @Test
    fun testOptionB_sinkToBottom_whenSinkEnabled() {
        // 初始数据：4 条记录，id 1..4，全部未查验
        val list = listOf(
            createUiModel(1L, isVerified = false),
            createUiModel(2L, isVerified = false),
            createUiModel(3L, isVerified = false),
            createUiModel(4L, isVerified = false)
        )

        // 用户将第 2 条查验
        val updatedList = list.map {
            if (it.combined.record.id == 2L) createUiModel(2L, isVerified = true) else it
        }

        // 开启自动沉底（sinkVerified = true）：已查验的 2L 沉底至末尾
        val sorted = sortList(updatedList, sinkVerified = true)
        assertEquals(listOf(1L, 3L, 4L, 2L), sorted.map { it.combined.record.id })
        assertEquals(false, sorted[0].combined.record.isVerified)
        assertEquals(false, sorted[1].combined.record.isVerified)
        assertEquals(false, sorted[2].combined.record.isVerified)
        assertEquals(true, sorted[3].combined.record.isVerified)
    }

    @Test
    fun testUncheck_restoresPosition_whenSinkEnabled() {
        // 开启自动沉底：1L, 3L, 4L (未查验), 2L (已查验)
        val list = listOf(
            createUiModel(1L, isVerified = false),
            createUiModel(3L, isVerified = false),
            createUiModel(4L, isVerified = false),
            createUiModel(2L, isVerified = true)
        )

        // 撤销查验 2L
        val updatedList = list.map {
            if (it.combined.record.id == 2L) createUiModel(2L, isVerified = false) else it
        }

        // 撤销后全部为未查验，按 id ASC 重新排回 1, 2, 3, 4
        val sorted = sortList(updatedList, sinkVerified = true)
        assertEquals(listOf(1L, 2L, 3L, 4L), sorted.map { it.combined.record.id })
    }
}
