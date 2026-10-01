package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.tools.anomaly.AnomalyDetector
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first

class AnalyzeAbnormalTool(
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : AiTool {
    override val name = "analyze_abnormal"
    override val description = "分析当前盘存任务中的异常情况，包括：过期产品、近效期产品、空批号、同库位重复、数量差异等"
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

    override suspend fun execute(arguments: JsonObject): String {
        val sessionId = arguments.get("session_id")?.asString?.toLongOrNull()
            ?: return "错误：需要提供有效的盘存任务ID"

        val records = repository.getSessionRecords(sessionId)
        if (records.isEmpty()) return "该盘存任务暂无记录"

        val settings = settingsRepository.settingsFlow.first()
        val nearExpiryDays = settings.nearExpiryThresholdDays

        val result = AnomalyDetector.analyze(records, nearExpiryDays)

        if (result.summary.anomalyCount == 0) {
            return "未发现异常，共 ${result.summary.totalCount} 条记录全部正常。"
        }

        return buildString {
            appendLine("异常分析报告:")
            appendLine("共扫描 ${result.summary.totalCount} 条记录，发现 ${result.summary.anomalyCount} 条异常")
            appendLine()

            if (result.expiredRecords.isNotEmpty()) {
                appendLine("已过期（${result.expiredRecords.size}条）:")
                result.expiredRecords.take(10).forEach { appendLine("- ${it.message}") }
                if (result.expiredRecords.size > 10) appendLine("  ...等共 ${result.expiredRecords.size} 条")
                appendLine()
            }

            if (result.nearExpiryRecords.isNotEmpty()) {
                appendLine("近效期（${result.nearExpiryRecords.size}条）:")
                result.nearExpiryRecords.take(10).forEach { appendLine("- ${it.message}") }
                if (result.nearExpiryRecords.size > 10) appendLine("  ...等共 ${result.nearExpiryRecords.size} 条")
                appendLine()
            }

            if (result.emptyBatchRecords.isNotEmpty()) {
                appendLine("空批号（${result.emptyBatchRecords.size}条）:")
                result.emptyBatchRecords.take(5).forEach { appendLine("- ${it.message}") }
                if (result.emptyBatchRecords.size > 5) appendLine("  ...等共 ${result.emptyBatchRecords.size} 条")
                appendLine()
            }

            if (result.duplicateLocationRecords.isNotEmpty()) {
                appendLine("同库位重复（${result.duplicateLocationRecords.size}条）:")
                result.duplicateLocationRecords.take(5).forEach { appendLine("- ${it.message}") }
                if (result.duplicateLocationRecords.size > 5) appendLine("  ...等共 ${result.duplicateLocationRecords.size} 条")
                appendLine()
            }

            if (result.quantityMismatchRecords.isNotEmpty()) {
                appendLine("数量差异（${result.quantityMismatchRecords.size}条）:")
                result.quantityMismatchRecords.take(5).forEach { appendLine("- ${it.message}") }
                if (result.quantityMismatchRecords.size > 5) appendLine("  ...等共 ${result.quantityMismatchRecords.size} 条")
            }
        }
    }
}
