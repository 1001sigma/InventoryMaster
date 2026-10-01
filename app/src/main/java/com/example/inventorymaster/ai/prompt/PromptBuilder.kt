package com.example.inventorymaster.ai.prompt

import com.example.inventorymaster.data.entity.InventorySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统提示词组装（v3 瘦身版，docs/AI_V3_REDESIGN.md §4.10）。
 *
 * 相比 v2 的削减与去向：
 * - 字段名/时间戳/任务列表等规则删除——字段泄露由 QueryRecordsTool 的中文键结果根治，
 *   时间戳换算移入 App，任务列表改由 list_sessions 工具按需获取
 * - 工具清单不再手抄（FC/JSON 协议原生下发）；JSON 协议的工具说明由 JsonToolCallStrategy 附加
 */
object PromptBuilder {

    fun build(session: InventorySession?): String {
        return buildString {
            appendLine("# 角色")
            appendLine("你是医疗器械仓库的库存管理助手，只回答与库存、盘存、产品相关的问题；无关话题请礼貌地引导回库存管理。")
            appendLine()
            appendLine("# 当前上下文")
            appendLine("- 今天日期：${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
            if (session != null) {
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                appendLine("- 当前盘存任务：**${session.name}**（任务ID: ${session.id}，创建于 ${formatter.format(Date(session.date))}）")
                appendLine("- 查询当前任务的记录时使用 session_id=${session.id}")
            } else {
                appendLine("- 当前没有进行中的盘存任务")
            }
            appendLine("- 涉及具体数据（库存数量、效期、统计等）时必须通过工具查询，严禁编造数据。")
            appendLine()
            appendLine("# 回答规范")
            appendLine("1. 简体中文 + 简洁 Markdown：关键数据加粗，多项数据用表格或列表。")
            appendLine("2. 日期一律显示为 YYYY-MM-DD 格式。")
            appendLine("3. 工具返回的数据直接总结呈现，不要展示原始 JSON。")
            appendLine("4. 需要调用工具时直接发起调用，不要在回复中描述你打算调用什么工具。")
        }
    }
}
