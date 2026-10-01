package com.example.inventorymaster.data.repository

import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 结构化查询条件。
 *
 * LLM 只提供人类可读值（ISO 日期、关键词），SQL 由 [AiRecordSqlBuilder] 构造、
 * 值全部走绑定参数——LLM 永不接触 SQL 文本（docs/AI_V3_REDESIGN.md §4.5，
 * 关闭 TECH_DEBT P0-1 动态 SQL 注入面）。
 */
data class AiRecordQuery(
    val sessionId: Long? = null,
    val productKeyword: String? = null,
    val batchNumber: String? = null,
    val location: String? = null,
    /** expiryDate < 该日 00:00（不含当天） */
    val expiryBefore: LocalDate? = null,
    /** expiryDate >= 该日 00:00（含当天） */
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
        EXPIRY_ASC("(r.expiryDate IS NULL) ASC, r.expiryDate ASC"),
        EXPIRY_DESC("r.expiryDate DESC"),
        QUANTITY_DESC("r.quantity DESC"),
        NAME_ASC("p.productName ASC")
    }
}

/**
 * 纯 SQL 构造器（无 Android 依赖，可 JVM 单测）。
 * 全部列名/排序/分组来自白名单，用户值只进绑定参数。
 */
object AiRecordSqlBuilder {

    data class BuiltQuery(val sql: String, val bindArgs: List<Any?>)

    private const val MAX_LIMIT = 100

    private const val SELECT_ROWS =
        "SELECT r.id AS \"记录ID\", r.sessionId AS \"任务ID\", r.productKey AS \"产品编码\", " +
            "p.productName AS \"产品名称\", p.specification AS \"规格\", p.manufacturer AS \"厂家\", " +
            "p.materialCode AS \"物料编码\", p.di AS \"DI\", p.unit AS \"单位\", " +
            "r.batchNumber AS \"批号\", r.quantity AS \"数量\", r.location AS \"库位\", " +
            "r.remarks AS \"备注\", " +
            "CASE WHEN r.productionDate IS NULL OR r.productionDate = 0 THEN NULL " +
            "ELSE date(r.productionDate/1000, 'unixepoch', 'localtime') END AS \"生产日期\", " +
            "CASE WHEN r.expiryDate IS NULL OR r.expiryDate = 0 THEN NULL " +
            "ELSE date(r.expiryDate/1000, 'unixepoch', 'localtime') END AS \"有效期\""

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
            val orderBy = query.orderBy?.sql ?: AiRecordQuery.OrderBy.EXPIRY_ASC.sql
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
        query.expiryBefore?.let {
            conditions.add("r.expiryDate < ?")
            args.add(startOfDay(it, zone))
        }
        query.expiryAfter?.let {
            conditions.add("r.expiryDate >= ?")
            args.add(startOfDay(it, zone))
        }
        query.expiryWithinDays?.let { days ->
            val today = LocalDate.now(zone)
            conditions.add("r.expiryDate >= ? AND r.expiryDate < ?")
            args.add(startOfDay(today, zone))
            args.add(startOfDay(today.plusDays(days.toLong() + 1), zone))
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

    private fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
