package com.example.inventorymaster

import com.example.inventorymaster.data.UserSettings
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptySearchSettingTest {

    private fun createDummyRecord(id: Long, name: String): StockRecordCombined {
        val record = StockRecord(
            id = id,
            sessionId = 1L,
            productKey = "KEY_$id",
            quantity = 5.0,
            batchNumber = "BATCH_$id",
            expiryDate = 1893456000000L,
            location = "A-01",
            operator = "Tester"
        )
        return StockRecordCombined(record = record, product = null)
    }

    private fun simulateQueryResolution(
        cleanQuery: String,
        emptySearchShowsNothing: Boolean,
        sessionRecords: List<StockRecordCombined>
    ): List<StockRecordCombined> {
        return if (cleanQuery.isBlank()) {
            if (emptySearchShowsNothing) {
                emptyList()
            } else {
                sessionRecords
            }
        } else {
            sessionRecords.filter { it.record.batchNumber.contains(cleanQuery) }
        }
    }

    @Test
    fun testDefaultSettingIsOff() {
        val defaultSettings = UserSettings()
        assertEquals(false, defaultSettings.emptySearchShowsNothing)
    }

    @Test
    fun testEmptyQueryReturnsAllRecords_whenSettingIsDisabled() {
        val allRecords = listOf(
            createDummyRecord(1L, "Item 1"),
            createDummyRecord(2L, "Item 2")
        )

        // 开关关闭（默认）：空搜索返回全量记录
        val result = simulateQueryResolution("", emptySearchShowsNothing = false, allRecords)
        assertEquals(2, result.size)
        assertEquals(1L, result[0].record.id)
        assertEquals(2L, result[1].record.id)
    }

    @Test
    fun testEmptyQueryReturnsEmptyList_whenSettingIsEnabled() {
        val allRecords = listOf(
            createDummyRecord(1L, "Item 1"),
            createDummyRecord(2L, "Item 2")
        )

        // 开关开启（方案 B）：空搜索返回 0 条数据
        val resultBlank = simulateQueryResolution("", emptySearchShowsNothing = true, allRecords)
        assertTrue(resultBlank.isEmpty())

        val resultWhitespace = simulateQueryResolution("   ", emptySearchShowsNothing = true, allRecords)
        assertTrue(resultWhitespace.isEmpty())
    }

    @Test
    fun testKeywordQueryReturnsMatchedRecords_whenSettingIsEnabled() {
        val allRecords = listOf(
            createDummyRecord(1L, "Item 1"),
            createDummyRecord(2L, "Item 2")
        )

        // 开关开启：输入批号关键词能正常匹配返回
        val result = simulateQueryResolution("BATCH_2", emptySearchShowsNothing = true, allRecords)
        assertEquals(1, result.size)
        assertEquals(2L, result[0].record.id)
    }
}
