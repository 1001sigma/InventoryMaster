package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.UiAction
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ExecuteUiActionTool : AiTool {
    override val name = "execute_ui_action"
    override val description = "【仅用于界面控制】真正控制手机/前端执行UI动作（如页面跳转、高亮、显示总结弹窗等）。当用户的核心意图是“跳转到”、“进入”、时，在获取到必要的ID后，必须且只能通过调用本工具来完成真正的界面切换。"
    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("action_type", JsonObject().apply {
                addProperty("type", "string")
                add("enum", JsonArray().apply {
                    add("navigate_to_record")
                    add("highlight_field")
                    add("open_edit_dialog")
                    add("show_anomalies")
                    add("show_summary")
                    add("navigate_to_product")
                })
                addProperty("description", "操作类型")
            })
            add("session_id", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "盘存任务ID（部分操作需要）")
            })
            add("record_id", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "记录ID（部分操作需要）")
            })
            add("product_key", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "产品关联键（导航到产品时需要）")
            })
            add("field", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "要高亮的字段名")
            })
            add("anomaly_type", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "异常类型筛选")
            })
        })
    }

    override suspend fun execute(arguments: JsonObject): String {
        val actionType = arguments.get("action_type")?.asString
            ?: return "ACTION_ERROR:缺少 action_type"

        val action = when (actionType) {
            "navigate_to_record" -> {
                val sessionId = arguments.get("session_id")?.asLong ?: 0
                val recordId = arguments.get("record_id")?.asLong ?: 0
                UiAction.NavigateToRecord(sessionId, recordId)
            }
            "highlight_field" -> {
                val recordId = arguments.get("record_id")?.asLong ?: 0
                val field = arguments.get("field")?.asString ?: ""
                UiAction.HighlightField(recordId, field)
            }
            "open_edit_dialog" -> {
                val recordId = arguments.get("record_id")?.asLong ?: 0
                UiAction.OpenEditDialog(recordId)
            }
            "show_anomalies" -> {
                val sessionId = arguments.get("session_id")?.asLong ?: 0
                val anomalyType = arguments.get("anomaly_type")?.asString
                UiAction.ShowAnomalies(sessionId, anomalyType)
            }
            "show_summary" -> {
                val sessionId = arguments.get("session_id")?.asLong ?: 0
                UiAction.ShowSummary(sessionId)
            }
            "navigate_to_product" -> {
                val productKey = arguments.get("product_key")?.asString ?: ""
                UiAction.NavigateToProduct(productKey)
            }
            else -> return "ACTION_ERROR:未知操作类型 $actionType"
        }

        return "ACTION:${actionType}:${serializeAction(action)}"
    }

    private fun serializeAction(action: UiAction): String {
        return when (action) {
            is UiAction.NavigateToRecord -> """{"sessionId":${action.sessionId},"recordId":${action.recordId}}"""
            is UiAction.HighlightField -> """{"recordId":${action.recordId},"field":"${action.field}"}"""
            is UiAction.OpenEditDialog -> """{"recordId":${action.recordId}}"""
            is UiAction.ShowAnomalies -> """{"sessionId":${action.sessionId},"anomalyType":${action.anomalyType?.let { "\"$it\"" } ?: "null"}}"""
            is UiAction.ShowSummary -> """{"sessionId":${action.sessionId}}"""
            is UiAction.NavigateToProduct -> """{"productKey":"${action.productKey}"}"""
            is UiAction.ShowProductCardList -> """{"title":"${action.title}","count":${action.products.size}}"""
        }
    }
}
