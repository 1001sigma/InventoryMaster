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

    fun build(session: InventorySession?, isLocal: Boolean = false): String {
        return if (isLocal) buildLocal(session) else buildCloud(session)
    }

    /**
     * 端侧小模型极简提示词（Qwen-4B / Gemma 等）：
     * - 削减 70% token，大幅减轻手机端 prefill 压力与延迟
     * - 移除长篇排版与反向约束规则（OutputFormatRules），避免小模型注意力稀释
     * - 规则绝对化，严禁复述系统规则与调用说明
     */
    private fun buildLocal(session: InventorySession?): String = buildString {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        appendLine("你是医疗器械仓库的库存助手。今天：$today。")
        if (session != null) {
            appendLine("当前盘点任务：${session.name}（session_id=${session.id}）。")
        }
        appendLine("【规则】")
        appendLine("1. 查库存与效期调用 query_records；查未查验/已查验明细与统计调用 query_verification_records；修改查验状态调用 update_record_verification；查盘点任务调用 list_sessions；查产品档案用 search_products。严禁编造数据。")
        appendLine("2. 工具返回结果后，直接用自然语言向用户简明总结（不要重复调用工具，不要输出 JSON）。若结果已在卡片展示则不逐条罗列。")
        appendLine("3. 全程使用简体中文，回答完毕后立即停止，严禁重复输出。")
        appendLine("4. 需要用户澄清模糊意图或在多个选项间二选一时，可调用 ask_question 工具弹出选择卡片。")
    }

    /**
     * 云端大模型全功能提示词（GPT-4 / Qwen-Max / DeepSeek 等）：
     * 具备丰富的角色上下文与全局输出排版规范（OutputFormatRules）。
     */
    private fun buildCloud(session: InventorySession?): String = buildString {
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
        appendLine("- 产品库与库存是两个概念：产品库=产品档案（无数量/效期），库存=盘点记录（有数量/批号/有效期/库位）。查库存与效期用 query_records，查已查验/未查验状态及联合筛选用 query_verification_records（问'还有多少条未查验'时query_mode='count'，问'有哪些未查验'时query_mode='list'），修改查验状态用 update_record_verification，查产品档案（名称/厂家/DI/注册证号）用 search_products。")
        appendLine()
        appendLine("# 回答规范")
        appendLine("1. 全程使用简体中文。")
        appendLine("2. 日期一律显示为 YYYY-MM-DD 格式。")
        appendLine("3. 工具返回的数据直接总结呈现，不要展示原始 JSON。")
        appendLine("4. 需要调用工具时直接发起调用，不要在回复中描述你打算调用什么工具。")
        appendLine("5. 决策澄清与多选交互：当用户意图模糊、存在多种可选方案或需要用户做选择时，严禁自行假设，也不要只输出纯文本提问，必须调用 ask_question 工具展示交互选择卡片。提供 2~4 个清晰明确的选项，最推荐的选项放在第一项。")
        appendLine()
        // 输出格式规则库：意图→格式三形态映射 + 反向约束（全局排版，与工具内呈现规则互补）
        appendLine(OutputFormatRules.render())
    }
}
