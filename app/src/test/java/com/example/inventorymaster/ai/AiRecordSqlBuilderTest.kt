package com.example.inventorymaster.ai

import com.example.inventorymaster.data.repository.AiRecordQuery
import com.example.inventorymaster.data.repository.AiRecordSqlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 结构化查询 SQL 构造器：白名单 + 绑定参数，LLM 输入永不拼接进 SQL 结构
 * （docs/AI_V3_REDESIGN.md §4.5，关闭 TECH_DEBT P0-1）。
 */
class AiRecordSqlBuilderTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `日期条件覆盖三种存储形态且各自限定数值域`() {
        val query = AiRecordQuery(expiryBefore = LocalDate.of(2026, 12, 31))
        val built = AiRecordSqlBuilder.build(query, zone)

        // 三形态数值域（与 utils/DateUtils.toLocalDate 判定同源）
        assertTrue(built.sql.contains("r.expiryDate >= 946684800000 AND r.expiryDate <= 4102444800000"))
        assertTrue(built.sql.contains("r.expiryDate >= 20000101 AND r.expiryDate <= 29991231"))
        assertTrue(built.sql.contains("r.expiryDate >= 0 AND r.expiryDate <= 991231"))
        // 每形态各自的"不含当天"上界：毫秒 / 8位 / 6位
        val ms = LocalDate.of(2026, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(ms, built.bindArgs[0])
        assertEquals(20261231L, built.bindArgs[1])
        assertEquals(261231L, built.bindArgs[2])
    }

    @Test
    fun `8位日期数据可被近效期窗口命中`() {
        // 库中实际形态：expiryDate=20261128（Excel 导入 8 位整数）应落入未来 90 天窗口
        val built = AiRecordSqlBuilder.build(AiRecordQuery(expiryWithinDays = 90), zone)

        val today = LocalDate.now(zone)
        fun ymd8(d: LocalDate) = d.year * 10_000L + d.monthValue * 100L + d.dayOfMonth
        // 参数顺序：毫秒(下,上) → 8位(下,上) → 6位(下,上)
        assertEquals(ymd8(today), built.bindArgs[2])
        assertEquals(ymd8(today.plusDays(91)), built.bindArgs[3])
        assertEquals(
            (today.year % 100) * 10_000L + today.monthValue * 100L + today.dayOfMonth,
            built.bindArgs[4]
        )
    }

    @Test
    fun `注入样例只进绑定参数不进 SQL 文本`() {
        val evil = "x' OR 1=1 --"
        val query = AiRecordQuery(
            productKeyword = evil,
            batchNumber = evil,
            location = evil
        )
        val built = AiRecordSqlBuilder.build(query, zone)

        // SQL 结构中只有占位符和 LIKE，没有拼接的用户值
        assertFalse(built.sql.contains("OR 1=1"))
        assertFalse(built.sql.contains(evil))
        // 三个 evil 值 + 5 个 keyword LIKE 参数
        assertEquals(evil, built.bindArgs[0])          // batch
        assertEquals("%$evil%", built.bindArgs[1])     // location
        assertTrue(built.bindArgs.drop(2).all { it == "%$evil%" })
    }

    @Test
    fun `limit 强制收窄到白名单上限`() {
        val built = AiRecordSqlBuilder.build(AiRecordQuery(limit = 5000), zone)
        assertTrue(built.sql.endsWith("LIMIT 100"))
    }

    @Test
    fun `分组聚合查询结构正确且忽略明细排序`() {
        val query = AiRecordQuery(
            aggregate = AiRecordQuery.Aggregate.COUNT,
            groupBy = AiRecordQuery.GroupBy.LOCATION,
            orderBy = AiRecordQuery.OrderBy.EXPIRY_ASC
        )
        val built = AiRecordSqlBuilder.build(query, zone)

        assertTrue(built.sql.contains("SELECT r.location AS \"库位\", COUNT(*) AS \"记录条数\""))
        assertTrue(built.sql.contains("GROUP BY r.location"))
        assertTrue(built.sql.contains("ORDER BY 2 DESC"))
        assertFalse(built.sql.contains("r.expiryDate ASC"))
    }

    @Test
    fun `明细查询默认按有效期升序且空值靠后`() {
        val built = AiRecordSqlBuilder.build(AiRecordQuery(), zone)
        assertTrue(built.sql.contains("ORDER BY (r.expiryDate IS NULL) ASC, CASE WHEN r.expiryDate IS NULL"))
    }

    @Test
    fun `count 查询与明细查询使用相同过滤条件`() {
        val query = AiRecordQuery(
            sessionId = 3L,
            productKeyword = "注射器",
            expiryBefore = LocalDate.of(2027, 1, 1)
        )
        val count = AiRecordSqlBuilder.buildCount(query, zone)
        val detail = AiRecordSqlBuilder.build(query, zone)

        val countWhere = count.sql.substringAfter("WHERE").substringBefore("LIMIT").trim()
        val detailWhere = detail.sql.substringAfter("WHERE").substringBefore("ORDER").trim()
        assertEquals(countWhere, detailWhere)
        assertEquals(count.bindArgs, detail.bindArgs)
    }
}
