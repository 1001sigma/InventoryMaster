package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.data.repository.AiRecordQuery
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 查验状态与库存明细查询工具。
 * 支持查验状态（已查验/未查验）、仓库名称/库位（如冷藏库、常温库等）、生产厂家（如西门子等）多维联合筛选。
 *
 * 交互呈现规范：
 * 1. 统计模式（query_mode = "count"）：当用户询问“还有多少条/有多少未查验”时使用，直接返回统计数量，不展示卡片，不逐条罗列；
 * 2. 清单模式（query_mode = "list"）：当用户询问“有哪些物品未查验/已查验”时使用：
 *    - 若总条数 <= 20：通过 UI 卡片（ShowProductCardList）直观呈现重点物资；
 *    - 若总条数 > 20：通过自适应 Markdown 表格展示前 20 条，不弹出卡片。
 */
class QueryVerificationRecordsTool(
    private val repository: InventoryRepository
) : AiTool {

    override val name = "query_verification_records"

    override val description = """
        查验状态与库存明细查询引擎。查询指定条件下的已查验/未查验记录数及详细明细。
        支持查验状态（已查验/未查验）、仓库名称或库位（如冷藏库、常温库等）、生产厂家（如西门子、迈瑞等）、
        盘存任务ID、产品关键词的多维联合搜索。
        当用户只询问数量时（如'还有多少条未查验'），请将 query_mode 设为 'count'；
        当用户询问具体有哪些物品时，将 query_mode 设为 'list'。
    """.trimIndent()

    override val parameters: JsonObject = JsonParser.parseString("""
        {
          "type": "object",
          "properties": {
            "verification_status": {
              "type": "string",
              "enum": ["unverified", "verified", "all"],
              "description": "查验状态：unverified=未查验（默认），verified=已查验，all=全部。"
            },
            "query_mode": {
              "type": "string",
              "enum": ["list", "count"],
              "description": "查询模式：count=仅统计数量（当用户问'还有多少条/有多少个已查验/未查验'时必须用count）；list=查询具体物品清单（当用户问'有哪些/列出物品'时用list）。默认为 list。"
            },
            "warehouse_name": {
              "type": "string",
              "description": "仓库名称或存放库位（如冷藏库、常温库、A区等），模糊匹配。"
            },
            "manufacturer": {
              "type": "string",
              "description": "生产厂家名称（如西门子、迈瑞等），模糊匹配。"
            },
            "session_id": {
              "type": "integer",
              "description": "盘存任务ID。缺省查全部任务。"
            },
            "product_keyword": {
              "type": "string",
              "description": "产品关键词（产品名称/规格/型号/物料编码/DI），模糊匹配。"
            },
            "limit": {
              "type": "integer",
              "description": "返回明细条数，默认 20，最大 20（本地模型防御上限）。"
            }
          }
        }
    """).asJsonObject

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val args = toolCall.arguments
        val statusRaw = (args.optString("verification_status") ?: "").lowercase()
        val isVerified: Boolean? = when {
            statusRaw.contains("unverified") || statusRaw.contains("未查验") -> false
            statusRaw.contains("verified") || statusRaw.contains("已查验") -> true
            statusRaw == "all" || statusRaw == "全部" -> null
            else -> false // 默认查询未查验
        }

        val statusLabel = when (isVerified) {
            true -> "已查验"
            false -> "未查验"
            null -> "全部"
        }

        val queryMode = args.optString("query_mode")?.lowercase() ?: "list"
        val isCountOnly = queryMode == "count" || queryMode == "统计" || queryMode == "数量"

        val warehouse = args.optString("warehouse_name") ?: args.optString("location")
        val manufacturer = args.optString("manufacturer")
        val sessionId = args.optLong("session_id")
        val keyword = args.optString("product_keyword")

        val requestedLimit = if (isCountOnly) 1 else (args.optInt("limit") ?: DEFAULT_LIMIT)
        val effectiveLimit = requestedLimit.coerceIn(1, MAX_DEFENSE_LIMIT)

        val query = AiRecordQuery(
            sessionId = sessionId,
            productKeyword = keyword,
            location = warehouse,
            manufacturer = manufacturer,
            isVerified = isVerified,
            limit = effectiveLimit
        )

        val result = repository.queryRecordsForAi(query)
        val json = try {
            JsonParser.parseString(result).asJsonObject
        } catch (e: Exception) {
            return ToolResult.failure(toolCall, ToolErrorKind.BUSINESS_ERROR, "查询结果解析失败：${e.message}")
        }

        if (json.get("status")?.asString == "error") {
            return ToolResult.failure(
                toolCall,
                ToolErrorKind.BUSINESS_ERROR,
                "查询失败：${json.get("message")?.asString}"
            )
        }

        val total = json.get("total")?.asLong ?: 0L
        val conditionsDesc = buildString {
            append("【状态：$statusLabel】")
            warehouse?.let { append("【仓库/库位：$it】") }
            manufacturer?.let { append("【厂家：$it】") }
            sessionId?.let { append("【任务ID：$it】") }
            keyword?.let { append("【关键词：$it】") }
        }

        if (total == 0L) {
            val content = if (isCountOnly) {
                "统计完成：在条件 $conditionsDesc 下没有找到匹配的记录（共 0 条）。请直接简洁回答用户对应数量为 0。"
            } else {
                "查询完成：在条件 $conditionsDesc 下未找到匹配的库存记录。请如实向用户反馈。"
            }
            return ToolResult.success(toolCall, content, data = json, uiAction = null)
        }

        // 1. 如果用户只问还有多少条（count 模式），直接返回总数，严禁逐条罗列明细，不产生卡片
        if (isCountOnly) {
            val content = buildString {
                append("统计完成：在条件 $conditionsDesc 下，符合条件的【$statusLabel】记录共有 $total 条。\n")
                append("【自然语言回复要求】请直接告知用户对应数量（共 $total 条），切勿逐条罗列明细！")
            }
            return ToolResult.success(toolCall, content, data = json, uiAction = null)
        }

        val rows = json.getAsJsonArray("rows")

        // 2. 如果结果 <= 20 条，使用 UI 卡片呈现重点物资
        if (total <= 20L) {
            val cardProducts = mutableListOf<ExpiredProductCardData>()
            if (rows != null) {
                for (element in rows.take(effectiveLimit)) {
                    val obj = element.asJsonObject
                    val remarks = obj.get("备注")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val rowVerified = obj.get("is_verified")?.takeIf { !it.isJsonNull }?.let { it.asInt == 1 }
                        ?: remarks.contains("已查验")

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
                            isVerified = rowVerified,
                            productionDateStr = obj.get("生产日期")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            remarks = remarks,
                            materialCode = obj.get("物料编码")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            di = obj.get("DI")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )
                    )
                }
            }

            val content = buildString {
                append("查询成功！符合条件的【$statusLabel】记录共 $total 条（已在下方卡片中完整呈现）。\n")
                append("【自然语言回复要求】请用简明自然的口吻告诉用户查询总数（共 $total 条），")
                append("概括涉及的厂家或库位分布即可，切勿在回复正文中逐条罗列明细列表，卡片已向用户完整展示。")
            }

            val cardAction = if (cardProducts.isNotEmpty()) {
                UiAction.ShowProductCardList(
                    title = "【$statusLabel】明细列表（共 $total 条）：",
                    products = cardProducts
                )
            } else null

            return ToolResult.success(toolCall, content, data = json, uiAction = cardAction)
        }

        // 3. 如果结果 > 20 条，按规定不生成卡片，直接组织 Markdown 表格输出
        val tableMarkdown = buildString {
            append("| 产品名称 | 规格 | 批号 | 库位 | 数量 | 有效期 |\n")
            append("| :--- | :--- | :--- | :--- | :--- | :--- |\n")
            if (rows != null) {
                for (element in rows.take(effectiveLimit)) {
                    val obj = element.asJsonObject
                    val name = obj.get("产品名称")?.takeIf { !it.isJsonNull }?.asString ?: "-"
                    val spec = obj.get("规格")?.takeIf { !it.isJsonNull }?.asString ?: "-"
                    val batch = obj.get("批号")?.takeIf { !it.isJsonNull }?.asString ?: "-"
                    val loc = obj.get("库位")?.takeIf { !it.isJsonNull }?.asString ?: "-"
                    val qty = obj.get("数量")?.takeIf { !it.isJsonNull }?.let {
                        try {
                            val d = it.asDouble
                            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                        } catch (e: Exception) { it.asString }
                    } ?: "-"
                    val exp = obj.get("有效期")?.takeIf { !it.isJsonNull }?.asString ?: "-"
                    append("| $name | $spec | $batch | $loc | $qty | $exp |\n")
                }
            }
        }

        val content = buildString {
            append("查询成功！符合条件的【$statusLabel】记录共 $total 条。\n")
            append("（因记录条数超过 20 条，按规定不展示卡片，以下通过表格呈现前 ${rows?.size() ?: 0} 条明细）\n\n")
            append(tableMarkdown)
            append("\n【自然语言回复要求】请告知用户总共有 $total 条【$statusLabel】记录，并向用户展示上述 Markdown 表格，提示用户可提供库位或厂家进一步缩小范围。")
        }

        return ToolResult.success(toolCall, content, data = json, uiAction = null)
    }

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

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_DEFENSE_LIMIT = 20
    }
}
