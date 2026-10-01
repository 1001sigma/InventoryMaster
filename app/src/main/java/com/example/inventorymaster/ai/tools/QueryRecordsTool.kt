package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.data.repository.AiRecordQuery
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 结构化库存查询（v3）：LLM 只提供人类可读条件，SQL 由 App 构造（docs/AI_V3_REDESIGN.md §4.5）。
 * 替代 v2 的动态 SQL 工具（LLM 自由拼 where + 13位时间戳心算，均已废除）。
 */
class QueryRecordsTool(
    private val repository: InventoryRepository
) : AiTool {

    override val name = "query_records"

    override val description = """
        库存记录查询引擎。日期参数一律用 YYYY-MM-DD 格式，无需换算时间戳。
        支持按产品关键词（名称/规格/厂家/物料编码/DI 模糊匹配）、批号、库位、
        有效期区间、数量区间过滤；支持按产品名称/库位/厂家/分类分组统计。
        可查询的表：stock_records（盘点记录，含产品信息 JOIN）。
    """.trimIndent()

    override val parameters: JsonObject = JsonParser.parseString("""
        {
          "type": "object",
          "properties": {
            "session_id": {
              "type": "integer",
              "description": "盘存任务ID。查询限定在某个任务内时传入；缺省查全部任务。"
            },
            "product_keyword": {
              "type": "string",
              "description": "产品关键词，对产品名称/规格/厂家/物料编码/DI 模糊匹配。"
            },
            "batch_number": {"type": "string", "description": "批号精确匹配。"},
            "location": {"type": "string", "description": "库位，模糊匹配。"},
            "expiry_before": {
              "type": "string",
              "description": "只看有效期早于该日的记录（不含当天），格式 YYYY-MM-DD。"
            },
            "expiry_after": {
              "type": "string",
              "description": "只看有效期在该日当天及以后的记录，格式 YYYY-MM-DD。"
            },
            "expiry_within_days": {
              "type": "integer",
              "description": "只看未来 N 天内到期的记录（不含已过期）。查近效期优先用这个。"
            },
            "quantity_min": {"type": "number", "description": "数量下限。"},
            "quantity_max": {"type": "number", "description": "数量上限。"},
            "group_by": {
              "type": "string",
              "enum": ["productName", "location", "manufacturer", "categoryCode"],
              "description": "分组维度（配合 aggregate 做统计）。"
            },
            "aggregate": {
              "type": "string",
              "enum": ["count", "sum_quantity"],
              "description": "聚合方式：count=记录条数，sum_quantity=数量合计。"
            },
            "order_by": {
              "type": "string",
              "enum": ["expiry_asc", "expiry_desc", "quantity_desc", "name_asc"],
              "description": "排序方式，默认有效期升序。"
            },
            "limit": {"type": "integer", "description": "返回条数上限，默认 20，最大 100。"}
          }
        }
    """).asJsonObject

    override suspend fun execute(arguments: JsonObject): String {
        val query = parseQuery(arguments)
            ?: return "参数错误：日期必须为 YYYY-MM-DD 格式，数字必须为数值。请修正后重试。"

        val result = repository.queryRecordsForAi(query)
        return try {
            val json = JsonParser.parseString(result).asJsonObject
            when (json.get("status")?.asString) {
                "error" -> "数据库查询失败：${json.get("message")?.asString}。请告知用户遇到了技术错误。"
                else -> {
                    val total = json.get("total")?.asLong ?: 0L
                    if (total == 0L) {
                        "查询成功，但未找到任何匹配记录。请如实告知用户未查到相关数据。"
                    } else {
                        val returned = json.get("returned")?.asLong ?: 0L
                        val note = if (json.get("truncated")?.asBoolean == true) {
                            "注意：共 $total 条，仅返回前 $returned 条。"
                        } else ""
                        "查询成功，共 $total 条。$note\n请用 Markdown 表格向用户呈现以下数据（日期已是 YYYY-MM-DD 格式）：\n$result"
                    }
                }
            }
        } catch (e: Exception) {
            "数据库查询结果解析失败：${e.message}"
        }
    }

    private fun parseQuery(args: JsonObject): AiRecordQuery? {
        return try {
            AiRecordQuery(
                sessionId = args.optLong("session_id"),
                productKeyword = args.optString("product_keyword"),
                batchNumber = args.optString("batch_number"),
                location = args.optString("location"),
                expiryBefore = args.optLocalDate("expiry_before"),
                expiryAfter = args.optLocalDate("expiry_after"),
                expiryWithinDays = args.optInt("expiry_within_days"),
                quantityMin = args.optDouble("quantity_min"),
                quantityMax = args.optDouble("quantity_max"),
                groupBy = args.optString("group_by")?.let { groupName(it) },
                aggregate = args.optString("aggregate")?.let { aggName(it) },
                orderBy = args.optString("order_by")?.let { orderName(it) },
                limit = args.optInt("limit") ?: DEFAULT_LIMIT
            )
        } catch (e: DateTimeParseException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun groupName(v: String) = when (v) {
        "productName" -> AiRecordQuery.GroupBy.PRODUCT_NAME
        "location" -> AiRecordQuery.GroupBy.LOCATION
        "manufacturer" -> AiRecordQuery.GroupBy.MANUFACTURER
        "categoryCode" -> AiRecordQuery.GroupBy.CATEGORY
        else -> null
    }

    private fun aggName(v: String) = when (v) {
        "count" -> AiRecordQuery.Aggregate.COUNT
        "sum_quantity" -> AiRecordQuery.Aggregate.SUM_QUANTITY
        else -> null
    }

    private fun orderName(v: String) = when (v) {
        "expiry_asc" -> AiRecordQuery.OrderBy.EXPIRY_ASC
        "expiry_desc" -> AiRecordQuery.OrderBy.EXPIRY_DESC
        "quantity_desc" -> AiRecordQuery.OrderBy.QUANTITY_DESC
        "name_asc" -> AiRecordQuery.OrderBy.NAME_ASC
        else -> null
    }

    // ---- JsonObject 安全取值 ----

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.optLong(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
            try { it.asLong } catch (e: NumberFormatException) { null }
        }

    private fun JsonObject.optInt(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
            try { it.asInt } catch (e: NumberFormatException) { null }
        }

    private fun JsonObject.optDouble(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
            try { it.asDouble } catch (e: NumberFormatException) { null }
        }

    private fun JsonObject.optLocalDate(key: String): LocalDate? =
        optString(key)?.let { LocalDate.parse(it) }

    companion object {
        private const val DEFAULT_LIMIT = 20
    }
}
