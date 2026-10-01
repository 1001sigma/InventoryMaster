package com.example.inventorymaster.ai.tools.anomaly

import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object AnomalyDetector {

    enum class AnomalyType { EXPIRED, NEAR_EXPIRY, EMPTY_BATCH, DUPLICATE_LOCATION, QUANTITY_MISMATCH }
    enum class Severity { HIGH, MEDIUM, LOW }

    data class AnomalyResult(
        val expiredRecords: List<AnomalyRecord>,
        val nearExpiryRecords: List<AnomalyRecord>,
        val emptyBatchRecords: List<AnomalyRecord>,
        val duplicateLocationRecords: List<AnomalyRecord>,
        val quantityMismatchRecords: List<AnomalyRecord>,
        val summary: AnomalySummary
    )

    data class AnomalyRecord(
        val record: StockRecordCombined,
        val anomalyType: AnomalyType,
        val severity: Severity,
        val message: String
    )

    data class AnomalySummary(
        val totalCount: Int,
        val anomalyCount: Int,
        val expiredCount: Int,
        val nearExpiryCount: Int,
        val otherIssuesCount: Int
    )

    suspend fun analyze(
        records: List<StockRecordCombined>,
        nearExpiryDays: Int
    ): AnomalyResult = withContext(Dispatchers.Default) {
        val expiredRecords = mutableListOf<AnomalyRecord>()
        val nearExpiryRecords = mutableListOf<AnomalyRecord>()
        val emptyBatchRecords = mutableListOf<AnomalyRecord>()
        val duplicateLocationRecords = mutableListOf<AnomalyRecord>()
        val quantityMismatchRecords = mutableListOf<AnomalyRecord>()

        val todayLocal = LocalDate.now()
        val expiryFormat = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

        // 按库位分组用于重复检测
        val locationGroups = records.groupBy { it.record.location }

        for (combined in records) {
            val record = combined.record
            val product = combined.product ?: continue

            // 1+2. 效期检测（DateUtils.toLocalDate 与徽标判定同源：毫秒时间戳/8位/6位均兼容；
            //     单条解析失败跳过，不让一条脏数据废掉整次分析）
            val expiryLocal = DateUtils.toLocalDate(record.expiryDate)
            if (expiryLocal != null) {
                val diffDays = ChronoUnit.DAYS.between(todayLocal, expiryLocal)
                val expiryText = expiryLocal.format(expiryFormat)
                if (diffDays < 0) {
                    expiredRecords.add(
                        AnomalyRecord(
                            record = combined,
                            anomalyType = AnomalyType.EXPIRED,
                            severity = Severity.HIGH,
                            message = "产品 ${product.productName}, 批号 ${record.batchNumber}, 库位 ${record.location}, 已于 ${expiryText} 过期"
                        )
                    )
                } else if (diffDays <= nearExpiryDays) {
                    nearExpiryRecords.add(
                        AnomalyRecord(
                            record = combined,
                            anomalyType = AnomalyType.NEAR_EXPIRY,
                            severity = if (diffDays <= 7) Severity.HIGH else Severity.MEDIUM,
                            message = "产品 ${product.productName}, 批号 ${record.batchNumber}, 库位 ${record.location}, 将于 ${diffDays} 天后过期"
                        )
                    )
                }
            }

            // 3. 空批号检测
            if (record.batchNumber.isBlank()) {
                emptyBatchRecords.add(
                    AnomalyRecord(
                        record = combined,
                        anomalyType = AnomalyType.EMPTY_BATCH,
                        severity = Severity.MEDIUM,
                        message = "产品 ${product.productName}, 库位 ${record.location}, 缺少批号信息"
                    )
                )
            }

            // 4. 数量差异检测
            val actualQty = record.actualQuantity
            if (actualQty != null && record.quantity > 0) {
                val diff = kotlin.math.abs(actualQty - record.quantity) / record.quantity
                if (diff > 0.1) {
                    quantityMismatchRecords.add(
                        AnomalyRecord(
                            record = combined,
                            anomalyType = AnomalyType.QUANTITY_MISMATCH,
                            severity = if (diff > 0.3) Severity.HIGH else Severity.MEDIUM,
                            message = "产品 ${product.productName}, 批号 ${record.batchNumber}, 库位 ${record.location}, 账面 ${record.quantity} 实盘 ${actualQty}, 差异 ${(diff * 100).toInt()}%"
                        )
                    )
                }
            }
        }

        // 5. 同库位重复检测
        for ((location, group) in locationGroups) {
            if (location.isBlank()) continue
            val productKeys = group.map { it.record.productKey }.distinct()
            if (productKeys.size > 1) {
                for (combined in group) {
                    duplicateLocationRecords.add(
                        AnomalyRecord(
                            record = combined,
                            anomalyType = AnomalyType.DUPLICATE_LOCATION,
                            severity = Severity.MEDIUM,
                            message = "库位 $location 存放了 ${productKeys.size} 种不同产品: ${combined.product?.productName ?: "未知"}"
                        )
                    )
                }
            }
        }

        val allAnomalies = expiredRecords + nearExpiryRecords + emptyBatchRecords +
                duplicateLocationRecords + quantityMismatchRecords

        AnomalyResult(
            expiredRecords = expiredRecords,
            nearExpiryRecords = nearExpiryRecords,
            emptyBatchRecords = emptyBatchRecords,
            duplicateLocationRecords = duplicateLocationRecords,
            quantityMismatchRecords = quantityMismatchRecords,
            summary = AnomalySummary(
                totalCount = records.size,
                anomalyCount = allAnomalies.size,
                expiredCount = expiredRecords.size,
                nearExpiryCount = nearExpiryRecords.size,
                otherIssuesCount = allAnomalies.size - expiredRecords.size - nearExpiryRecords.size
            )
        )
    }
}
