package com.example.inventorymaster.ai.agent

import com.google.gson.JsonParser

object ActionResultExtractor {
    fun extract(toolName: String, result: String): UiAction? {
        // 处理 execute_ui_action 工具
        if (toolName == "execute_ui_action" && result.startsWith("ACTION:")) {
            val parts = result.removePrefix("ACTION:").split(":", limit = 2)
            if (parts.size == 2) {
                val actionType = parts[0]
                val jsonStr = parts[1]
                return try {
                    val json = JsonParser.parseString(jsonStr).asJsonObject
                    when (actionType) {
                        "navigate_to_record" -> UiAction.NavigateToRecord(
                            sessionId = json.get("sessionId")?.asLong ?: 0,
                            recordId = json.get("recordId")?.asLong ?: 0
                        )
                        "highlight_field" -> UiAction.HighlightField(
                            recordId = json.get("recordId")?.asLong ?: 0,
                            field = json.get("field")?.asString ?: ""
                        )
                        "open_edit_dialog" -> UiAction.OpenEditDialog(
                            recordId = json.get("recordId")?.asLong ?: 0
                        )
                        "show_anomalies" -> UiAction.ShowAnomalies(
                            sessionId = json.get("sessionId")?.asLong ?: 0,
                            anomalyType = json.get("anomalyType")?.let {
                                if (it.isJsonNull) null else it.asString
                            }
                        )
                        "show_summary" -> UiAction.ShowSummary(
                            sessionId = json.get("sessionId")?.asLong ?: 0
                        )
                        "navigate_to_product" -> UiAction.NavigateToProduct(
                            productKey = json.get("productKey")?.asString ?: ""
                        )
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        // 拦截 query_records 的明细查询结果（v3 中文键格式，见 docs/AI_V3_REDESIGN.md §4.5）
        if (toolName == "query_records") {
            try {
                val parsed = JsonParser.parseString(result)
                if (!parsed.isJsonObject) return null
                val root = parsed.asJsonObject
                if (root.get("status")?.asString != "ok") return null
                val rows = root.getAsJsonArray("rows") ?: return null
                // 聚合结果没有 记录ID 字段，不出卡片
                if (rows.size() == 0 || rows[0].asJsonObject.get("记录ID") == null) return null

                val cardProducts = mutableListOf<ExpiredProductCardData>()
                for (element in rows.take(MAX_CARDS)) {
                    val obj = element.asJsonObject
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
                            recordId = obj.get("记录ID")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                        )
                    )
                }

                if (cardProducts.isNotEmpty()) {
                    return UiAction.ShowProductCardList(
                        title = "查询结果中的重点物资：",
                        products = cardProducts
                    )
                }
            } catch (e: Exception) {
                return null
            }
        }

        return null
    }

    private const val MAX_CARDS = 10
}
