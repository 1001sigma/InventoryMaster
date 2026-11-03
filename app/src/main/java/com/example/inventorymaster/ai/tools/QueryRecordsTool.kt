package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiToolCall
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
        库存查询引擎（唯一能查实物库存的工具）。查库存数量、批号、有效期、近效期、过期、
        库位、盘点记录，一律用本工具；查近效期优先用 expiry_within_days。
        注意：产品库（产品档案）里没有数量、批号、有效期——这些只在库存（盘点记录）里；
        查产品档案（名称/厂家/DI/注册证号）或统计物料种数用 search_products。
        可按 product_keyword（产品名称/规格/厂家/物料编码/DI 模糊匹配）过滤，
        不传 session_id 时查询全部任务。
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

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val query = parseQuery(toolCall.arguments)
            ?: return ToolResult.failure(
                toolCall,
                ToolErrorKind.INVALID_ARGUMENTS,
                "参数错误：日期必须为 YYYY-MM-DD 格式，数字必须为数值。请修正后重试。"
            )

        val result = repository.queryRecordsForAi(query)
        val json = try {
            JsonParser.parseString(result).asJsonObject
        } catch (e: Exception) {
            return ToolResult.failure(toolCall, ToolErrorKind.BUSINESS_ERROR, "数据库查询结果解析失败：${e.message}")
        }

        when (json.get("status")?.asString) {
            "error" -> return ToolResult.failure(
                toolCall,
                ToolErrorKind.BUSINESS_ERROR,
                "数据库查询失败：${json.get("message")?.asString}。请告知用户遇到了技术错误。"
            )
        }

        val total = json.get("total")?.asLong ?: 0L
        if (total == 0L) {
            return ToolResult.success(toolCall, zeroResultDiagnosis(query, repository))
        }

        val returned = json.get("returned")?.asLong ?: 0L
        val note = if (json.get("truncated")?.asBoolean == true) {
            "注意：共 $total 条，仅返回前 $returned 条。"
        } else ""

        // 各司其职：完整明细由界面产品卡片呈现（可点击），模型只做概要+重点提醒，
        // 避免与卡片内容重复（用户选型 a）。聚合查询的统计结果即答案，保持 JSON 输出。
        val content = if (query.aggregate != null) {
            "查询成功，共 $total 条。$note\n统计结果：\n$result"
        } else {
            val rows = json.getAsJsonArray("rows")
            buildString {
                append("查询成功，共 $total 条。$note\n")
                append("完整明细已通过本次回复下方的产品卡片展示给用户（可点击查看详情），请勿在回答中逐条罗列。\n")
                append("请向用户给出简明概要（总数、效期范围），并重点提醒以下最近到期的记录：\n")
                rows?.take(3)?.forEachIndexed { i, element ->
                    val row = element.asJsonObject
                    append(
                        "${i + 1}. ${row.text("产品名称")}（批号 ${row.text("批号")}）" +
                            "｜效期 ${row.text("有效期")}｜库位 ${row.text("库位")}" +
                            "｜记录ID ${row.text("记录ID")}（任务ID ${row.text("任务ID")}）\n"
                    )
                }
                val returnedCount = rows?.size() ?: 0
                append("（卡片已展示明细；用户明确要求跳转时才调用 execute_ui_action）\n")
            }
        }

        // 明细查询结果直接携带产品卡片（主要呈现载体）
        val cardAction = buildCardAction(json)
        return ToolResult.success(toolCall, content, data = json, uiAction = cardAction)
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: "—"

    private fun JsonObject.num(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.let { v ->
            try {
                v.asDouble.let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() }
            } catch (_: Exception) {
                v.asString
            }
        } ?: "—"

    /** 明细行（带 记录ID）转为产品卡片；聚合结果无 记录ID 字段，不出卡片 */
    private fun buildCardAction(json: JsonObject): UiAction? {
        val rows = json.getAsJsonArray("rows") ?: return null
        if (rows.size() == 0 || rows[0].asJsonObject.get("记录ID") == null) return null

        val cardProducts = mutableListOf<ExpiredProductCardData>()
        for (element in rows.take(MAX_CARDS)) {
            val obj = element.asJsonObject
            val remarks = obj.get("备注")?.takeIf { !it.isJsonNull }?.asString ?: ""
            cardProducts.add(
                ExpiredProductCardData(
                    productKey = obj.get("产品编码")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    productName = obj.get("产品名称")?.takeIf { !it.isJsonNull }?.asString ?: "未知医疗器械",
                    batchNumber = obj.get("批号")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    expiryDateStr = obj.get("有效期")?.takeIf { !it.isJsonNull }?.asString ?: "未标明",
                    quantity = obj.get("数量")?.takeIf { !it.isJsonNull }?.let {
                        try { it.asDouble } catch (e: Exception) { 0.0 }
                    } ?: 0.0,
                    sessionId = obj.get("任务ID")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    recordId = obj.get("记录ID")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    sessionName = obj.get("任务名称")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    location = obj.get("库位")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    manufacturer = obj.get("厂家")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    specification = obj.get("规格")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    unit = obj.get("单位")?.takeIf { !it.isJsonNull }?.asString ?: "件",
                    isVerified = obj.get("is_verified")?.takeIf { !it.isJsonNull }?.let { it.asInt == 1 } ?: remarks.contains("已查验"),
                    productionDateStr = obj.get("生产日期")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    remarks = remarks,
                    materialCode = obj.get("物料编码")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    di = obj.get("DI")?.takeIf { !it.isJsonNull }?.asString ?: ""
                )
            )
        }
        return if (cardProducts.isNotEmpty()) {
            UiAction.ShowProductCardList(title = "查询结果中的重点物资：", products = cardProducts)
        } else null
    }

    /** 0 结果时的自诊断：放宽日期条件、放开任务限定各数一次，把线索直接喂给模型（Agent Recovery） */
    private suspend fun zeroResultDiagnosis(query: AiRecordQuery, repository: InventoryRepository): String {
        val hints = mutableListOf("按当前条件未找到匹配记录。")

        val hasExpiryFilter = query.expiryBefore != null || query.expiryAfter != null || query.expiryWithinDays != null
        if (hasExpiryFilter) {
            val relaxedTotal = countOf(repository.queryRecordsForAi(query.copy(expiryBefore = null, expiryAfter = null, expiryWithinDays = null)))
            if (relaxedTotal > 0) {
                hints.add(
                    "忽略有效期条件后，同条件下共 $relaxedTotal 条——这些记录的有效期缺失或不在指定范围内。" +
                        "可去掉日期条件、用 product_keyword 列出后向用户人工核对。"
                )
            }
        }
        if (query.sessionId != null) {
            val globalTotal = countOf(
                repository.queryRecordsForAi(
                    query.copy(expiryBefore = null, expiryAfter = null, expiryWithinDays = null, sessionId = null)
                )
            )
            if (globalTotal > 0) {
                hints.add("不限任务时同产品条件下共 $globalTotal 条——记录可能在其他任务中，可先用 list_sessions 查任务ID。")
            }
        }
        return "查询成功，但没有符合条件的记录。\n" + hints.joinToString("\n")
    }

    private fun countOf(jsonStr: String): Long = try {
        JsonParser.parseString(jsonStr).asJsonObject.get("total")?.asLong ?: 0L
    } catch (_: Exception) {
        0L
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
        private const val MAX_CARDS = 10
    }
}
