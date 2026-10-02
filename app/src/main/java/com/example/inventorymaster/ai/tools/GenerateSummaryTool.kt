package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.anomaly.AnomalyDetector
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenerateSummaryTool(
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : AiTool {
    override val name = "generate_summary"
    override val description = "自动生成当前盘存任务的盘点总结报告，包括完成度、异常统计、关键指标等"
    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("session_id", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "盘存任务ID")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("session_id") })
    }

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val sessionId = toolCall.arguments.get("session_id")?.takeIf { !it.isJsonNull }?.asLong
            ?: return ToolResult.failure(
                toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                "需要提供有效的盘存任务ID（数值）。可先用 list_sessions 查询任务ID。"
            )

        val records = repository.getSessionRecords(sessionId)
        if (records.isEmpty()) return ToolResult.success(toolCall, "该盘存任务暂无记录")

        val settings = settingsRepository.settingsFlow.first()
        val nearExpiryDays = settings.nearExpiryThresholdDays

        val totalQuantity = records.sumOf { it.record.quantity }
        val productTypes = records.map { it.record.productKey }.distinct().size
        val batchCount = records.map { it.record.batchNumber }.distinct().size
        val unverifiedCount = repository.getUnverifiedCount(sessionId)
        val verifiedCount = records.size - unverifiedCount
        val verifiedPercent = if (records.isNotEmpty()) (verifiedCount * 100 / records.size) else 0

        // 厂家分布统计
        val manufacturerGroups = records.filter { it.product != null }
            .groupBy { it.product!!.manufacturer }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        // 异常检测
        val anomalyResult = AnomalyDetector.analyze(records, nearExpiryDays)

        return ToolResult.success(toolCall, buildString {
            appendLine("盘点总结报告")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            appendLine("核心指标")
            appendLine("- 总记录数: ${records.size} 条")
            appendLine("- 产品种类: $productTypes 种")
            appendLine("- 批号数量: $batchCount 个")
            appendLine("- 总数量: ${"%.0f".format(totalQuantity)}")
            appendLine("- 已查验: $verifiedCount 条 ($verifiedPercent%)")
            appendLine("- 待确认: $unverifiedCount 条")
            appendLine()

            if (manufacturerGroups.isNotEmpty()) {
                appendLine("厂家分布（Top 5）:")
                manufacturerGroups.forEach { (mfrName, mfrCount) ->
                    appendLine("- ${mfrName}: ${mfrCount}条")
                }
                appendLine()
            }

            if (anomalyResult.summary.anomalyCount > 0) {
                appendLine("异常汇总:")
                if (anomalyResult.summary.expiredCount > 0) appendLine("- 已过期: ${anomalyResult.summary.expiredCount}条")
                if (anomalyResult.summary.nearExpiryCount > 0) appendLine("- 近效期: ${anomalyResult.summary.nearExpiryCount}条")
                if (anomalyResult.emptyBatchRecords.isNotEmpty()) appendLine("- 空批号: ${anomalyResult.emptyBatchRecords.size}条")
                if (anomalyResult.quantityMismatchRecords.isNotEmpty()) appendLine("- 数量差异: ${anomalyResult.quantityMismatchRecords.size}条")
                appendLine()

                appendLine("建议:")
                if (anomalyResult.summary.expiredCount > 0) {
                    appendLine("- 优先处理 ${anomalyResult.summary.expiredCount} 条已过期产品")
                }
                if (anomalyResult.summary.nearExpiryCount > 0) {
                    appendLine("- ${anomalyResult.summary.nearExpiryCount} 条近效期产品建议尽快使用")
                }
                if (anomalyResult.emptyBatchRecords.isNotEmpty()) {
                    appendLine("- ${anomalyResult.emptyBatchRecords.size} 条记录缺少批号，建议补充")
                }
            } else {
                appendLine("未发现异常数据。")
            }
        })
    }
}
