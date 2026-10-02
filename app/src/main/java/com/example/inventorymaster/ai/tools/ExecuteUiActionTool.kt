package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ExecuteUiActionTool : AiTool {
    override val name = "execute_ui_action"
    override val description = """
        【仅用于界面控制】真正控制手机/前端执行UI动作（页面跳转、高亮、显示弹窗等）。
        当用户的核心意图是“跳转到”“进入”“打开”某界面时，在获取到必要的ID后，必须且只能通过调用本工具来完成真正的界面切换。
        各动作所需参数：
        - navigate_to_session：跳转到任务盘点页。只需 session_id（任务ID，可用 list_sessions 查询，或取自 query_records 结果中的“任务ID”字段）。
        - navigate_to_record：跳转到某条库存记录。需 session_id + record_id 两个数值（记录ID 取自 query_records 结果中的“记录ID”字段，禁止臆造）。
        - highlight_field：需 record_id（数值）+ field（字段名）。
        - open_edit_dialog：需 record_id（数值）。
        - show_anomalies：需 session_id（数值），可选 anomaly_type。
        - show_summary：需 session_id（数值）。
        - navigate_to_product：需 product_key（产品关联键）。
        用户说“跳转盘库页面”“跳转任务N”这类不指定具体记录的跳转，用 navigate_to_session，不要用 navigate_to_record。
    """.trimIndent()
    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("action_type", JsonObject().apply {
                addProperty("type", "string")
                add("enum", JsonArray().apply {
                    add("navigate_to_session")
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
        add("required", JsonArray().apply { add("action_type") })
    }

    /**
     * 各动作的必需参数在工具内做业务校验并明确报错（不默认 0/""），
     * UiAction 直接放进 ToolResult.uiAction，UI 动作不再走字符串协议。
     */
    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val arguments = toolCall.arguments
        val actionType = arguments.get("action_type")?.asString
            ?: return ToolResult.failure(toolCall, ToolErrorKind.INVALID_ARGUMENTS, "缺少 action_type")

        fun requireNumber(vararg keys: String): List<Long>? {
            val values = mutableListOf<Long>()
            for (key in keys) {
                val element = arguments.get(key) ?: return null
                if (!element.isJsonPrimitive || element.isJsonNull) return null
                val asLong = try {
                    element.asLong
                } catch (_: Exception) {
                    return null
                }
                values += asLong
            }
            return values
        }

        val action = when (actionType) {
            "navigate_to_session" -> {
                val sessionId = arguments.get("session_id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "navigate_to_session 需要提供 session_id（数值）。可先用 list_sessions 查询任务ID。"
                    )
                UiAction.NavigateToSession(sessionId)
            }
            "navigate_to_record" -> {
                val ids = requireNumber("session_id", "record_id")
                    ?: return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "navigate_to_record 需要提供 session_id 与 record_id 两个数值参数。请先查询获得后重试。"
                    )
                UiAction.NavigateToRecord(ids[0], ids[1])
            }
            "highlight_field" -> {
                val recordId = arguments.get("record_id")?.takeIf { !it.isJsonNull }?.asLong
                val field = arguments.get("field")?.takeIf { it.isJsonPrimitive }?.asString
                if (recordId == null || field.isNullOrBlank()) {
                    return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "highlight_field 需要提供 record_id（数值）与 field（字段名）。"
                    )
                }
                UiAction.HighlightField(recordId, field)
            }
            "open_edit_dialog" -> {
                val recordId = arguments.get("record_id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "open_edit_dialog 需要提供 record_id（数值）。请先用 query_records 查询获得。"
                    )
                UiAction.OpenEditDialog(recordId)
            }
            "show_anomalies" -> {
                val sessionId = arguments.get("session_id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "show_anomalies 需要提供 session_id（数值）。可先用 list_sessions 查询任务ID。"
                    )
                val anomalyType = arguments.get("anomaly_type")?.takeIf { it.isJsonPrimitive }?.asString
                UiAction.ShowAnomalies(sessionId, anomalyType)
            }
            "show_summary" -> {
                val sessionId = arguments.get("session_id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "show_summary 需要提供 session_id（数值）。可先用 list_sessions 查询任务ID。"
                    )
                UiAction.ShowSummary(sessionId)
            }
            "navigate_to_product" -> {
                val productKey = arguments.get("product_key")?.takeIf { it.isJsonPrimitive }?.asString
                if (productKey.isNullOrBlank()) {
                    return ToolResult.failure(
                        toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                        "navigate_to_product 需要提供 product_key（产品关联键）。"
                    )
                }
                UiAction.NavigateToProduct(productKey)
            }
            else -> return ToolResult.failure(
                toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                "未知操作类型 $actionType。可选值：navigate_to_session / navigate_to_record / highlight_field / open_edit_dialog / show_anomalies / show_summary / navigate_to_product。"
            )
        }

        return ToolResult.success(toolCall, "已发起界面动作：$actionType", uiAction = action)
    }
}
