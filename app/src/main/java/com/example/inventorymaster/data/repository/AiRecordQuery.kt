package com.example.inventorymaster.data.repository

import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 结构化查询条件。
 *
 * LLM 只提供人类可读值（ISO 日期、关键词），SQL 由 [AiRecordSqlBuilder] 构造、
 * 值全部走绑定参数——LLM 永不接触 SQL 文本（docs/AI_V3_REDESIGN.md §4.5，
 * 关闭 TECH_DEBT P0-1 动态 SQL 注入面）。
 *
 * ⚠️ 日期存储形态：expiryDate/productionDate 列在实际数据中存在三种形态
 * （毫秒时间戳 / 8位 yyyyMMdd / 6位 yyMMdd，与 utils/DateUtils 的判定同源），
 * 日期条件与显示均由 [AiRecordSqlBuilder] 做三形态适配，LLM 无感知。
 */
data class AiRecordQuery(
    val sessionId: Long? = null,
    val productKeyword: String? = null,
    val batchNumber: String? = null,
    val location: String? = null,
    /** expiryDate < 该日（不含当天） */
    val expiryBefore: LocalDate? = null,
    /** expiryDate >= 该日（含当天） */
    val expiryAfter: LocalDate? = null,
    /** 未来 N 天内到期（不含已过期） */
    val expiryWithinDays: Int? = null,
    val quantityMin: Double? = null,
    val quantityMax: Double? = null,
    val groupBy: GroupBy? = null,
    val aggregate: Aggregate? = null,
    val orderBy: OrderBy? = null,
    val limit: Int = 20
) {

    /** 分组维度白名单（列名不来自 LLM 输入） */
    enum class GroupBy(val column: String, val label: String) {
        PRODUCT_NAME("p.productName", "产品名称"),
        LOCATION("r.location", "库位"),
        MANUFACTURER("p.manufacturer", "厂家"),
        CATEGORY("p.categoryCode", "分类")
    }

    enum class Aggregate(val sql: String, val label: String) {
        COUNT("COUNT(*)", "记录条数"),
        SUM_QUANTITY("SUM(r.quantity)", "数量合计")
    }

    enum class OrderBy(val sql: String) {
        /** :expirySort: 由 Builder 替换为三形态规范化的可排序日期表达式 */
        EXPIRY_ASC("(r.expiryDate IS NULL) ASC, :expirySort: ASC"),
        EXPIRY_DESC(":expirySort: DESC"),
        QUANTITY_DESC("r.quantity DESC"),
        NAME_ASC("p.productName ASC")
    }
}

/**
 * 纯 SQL 构造器（无 Android 依赖，可 JVM 单测）。
 * 全部列名/排序/分组来自白名单，用户值只进绑定参数。
 *
 * 日期三形态适配（与 utils/DateUtils.toLocalDate 判定同源，三种数值域天然不重叠）：
 * - 毫秒时间戳：946684800000 ~ 4102444800000（2000~2100）
 * - 8 位 yyyyMMdd 整数：Excel 导入链路
 * - 6 位 yyMMdd 整数：GS1 扫码链路（年+2000）
 * 日期过滤 = 三种形态各自区间 OR；日期显示/排序用规范化 CASE 表达式。
 */
object AiRecordSqlBuilder {

    data class BuiltQuery(val sql: String, val bindArgs: List<Any?>)

    private const val MAX_LIMIT = 100

    /** 展示用规范化日期表达式：NULL/0 → NULL，毫秒/8位/6位 → 'YYYY-MM-DD' 文本（可按文本正确排序） */
    private fun displayDateExpr(col: String): String {
        val t = "CAST($col AS TEXT)"
        return "CASE " +
            "WHEN $col IS NULL OR $col = 0 THEN NULL " +
            "WHEN $col > 946684800000 AND $col < 4102444800000 THEN date($col/1000, 'unixepoch', 'localtime') " +
            "WHEN LENGTH($t) = 8 THEN substr($t,1,4) || '-' || substr($t,5,2) || '-' || substr($t,7,2) " +
            "WHEN LENGTH($t) = 6 THEN '20' || substr($t,1,2) || '-' || substr($t,3,2) || '-' || substr($t,5,2) " +
            "ELSE NULL END"
    }

    /**
     * 日期条件（三形态 OR）。[lowerInclusive] 含当天，[upperExclusive] 不含。
     * 每个形态先限定自身数值域再比较——防止跨形态误命中（如毫秒值恒大于任何 8 位日期）。
     */
    private fun dateCondition(
        col: String,
        lowerInclusive: LocalDate?,
        upperExclusive: LocalDate?,
        zone: ZoneId
    ): Pair<String, List<Any?>> {
        val forms: List<Triple<String, String, (LocalDate) -> Long>> = listOf(
            Triple("946684800000", "4102444800000") { d -> d.atStartOfDay(zone).toInstant().toEpochMilli() },
            Triple("20000101", "29991231") { d -> d.year * 10_000L + d.monthValue * 100L + d.dayOfMonth },
            Triple("0", "991231") { d -> (d.year % 100) * 10_000L + d.monthValue * 100L + d.dayOfMonth }
        )
        val parts = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        for ((domainLow, domainHigh, toValue) in forms) {
            val conds = mutableListOf("$col >= $domainLow AND $col <= $domainHigh")
            lowerInclusive?.let { conds.add("$col >= ?"); args.add(toValue(it)) }
            upperExclusive?.let { conds.add("$col < ?"); args.add(toValue(it)) }
            parts.add("(" + conds.joinToString(" AND ") + ")")
        }
        return "(" + parts.joinToString(" OR ") + ")" to args
    }

    private val SELECT_ROWS =
        "SELECT r.id AS \"记录ID\", r.sessionId AS \"任务ID\", r.productKey AS \"产品编码\", " +
            "p.productName AS \"产品名称\", p.specification AS \"规格\", p.manufacturer AS \"厂家\", " +
            "p.materialCode AS \"物料编码\", p.di AS \"DI\", p.unit AS \"单位\", " +
            "r.batchNumber AS \"批号\", r.quantity AS \"数量\", r.location AS \"库位\", " +
            "r.remarks AS \"备注\", " +
            displayDateExpr("r.productionDate") + " AS \"生产日期\", " +
            displayDateExpr("r.expiryDate") + " AS \"有效期\""

    private const val BASE_FROM =
        " FROM stock_records r LEFT JOIN product_base p ON r.productKey = p.productKey"

    fun buildCount(query: AiRecordQuery, zone: ZoneId = ZoneId.systemDefault()): BuiltQuery {
        val (where, args) = buildWhere(query, zone)
        return BuiltQuery("SELECT COUNT(*)$BASE_FROM$where", args)
    }

    fun build(query: AiRecordQuery, zone: ZoneId = ZoneId.systemDefault()): BuiltQuery {
        val (where, args) = buildWhere(query, zone)
        val limit = query.limit.coerceIn(1, MAX_LIMIT)

        return if (query.aggregate != null) {
            val agg = query.aggregate
            val group = query.groupBy
            val select = if (group != null) {
                "SELECT ${group.column} AS \"${group.label}\", ${agg.sql} AS \"${agg.label}\""
            } else {
                "SELECT ${agg.sql} AS \"${agg.label}\""
            }
            val groupBy = if (group != null) " GROUP BY ${group.column}" else ""
            // 聚合结果按聚合值倒序最有用；orderBy（面向明细行）此时无意义
            val orderBy = if (group != null) " ORDER BY 2 DESC" else ""
            BuiltQuery("$select$BASE_FROM$where$groupBy$orderBy LIMIT $limit", args)
        } else {
            val orderBy = (query.orderBy?.sql ?: AiRecordQuery.OrderBy.EXPIRY_ASC.sql)
                .replace(":expirySort:", displayDateExpr("r.expiryDate"))
            BuiltQuery("$SELECT_ROWS$BASE_FROM$where ORDER BY $orderBy LIMIT $limit", args)
        }
    }

    private fun buildWhere(query: AiRecordQuery, zone: ZoneId): Pair<String, List<Any?>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any?>()

        query.sessionId?.let {
            conditions.add("r.sessionId = ?")
            args.add(it)
        }
        query.batchNumber?.takeIf { it.isNotBlank() }?.let {
            conditions.add("r.batchNumber = ?")
            args.add(it)
        }
        query.location?.takeIf { it.isNotBlank() }?.let {
            conditions.add("r.location LIKE ?")
            args.add("%$it%")
        }
        query.productKeyword?.takeIf { it.isNotBlank() }?.let { kw ->
            conditions.add(
                "(p.productName LIKE ? OR p.specification LIKE ? OR p.manufacturer LIKE ? " +
                    "OR p.materialCode LIKE ? OR p.di LIKE ?)"
            )
            repeat(5) { args.add("%$kw%") }
        }
        if (query.expiryBefore != null || query.expiryAfter != null || query.expiryWithinDays != null) {
            val lower = query.expiryAfter ?: query.expiryWithinDays?.let { LocalDate.now(zone) }
            val upperExclusive = query.expiryBefore
                ?: query.expiryWithinDays?.let { LocalDate.now(zone).plusDays(it.toLong() + 1) }
            val (sql, dateArgs) = dateCondition("r.expiryDate", lower, upperExclusive, zone)
            conditions.add(sql)
            args.addAll(dateArgs)
        }
        query.quantityMin?.let {
            conditions.add("r.quantity >= ?")
            args.add(it)
        }
        query.quantityMax?.let {
            conditions.add("r.quantity <= ?")
            args.add(it)
        }

        return if (conditions.isEmpty()) "" to args
        else " WHERE ${conditions.joinToString(" AND ")}" to args
    }
}
