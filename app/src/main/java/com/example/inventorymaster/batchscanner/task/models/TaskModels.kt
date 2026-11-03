package com.example.inventorymaster.batchscanner.task.models

import android.net.Uri
import java.util.UUID
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ================= 新增：三层状态枚举 =================

// 1. 字段级状态
enum class FieldStatus {
    NOT_CHECKED, // 未校对
    MATCHED,     // 一致
    WARNING,     // 警告 (轻微偏差，如效期不一致、数量未满)
    ERROR        // 异常 (致命错误，如多扫、不在单据中)
}

// 2. 行级状态
enum class RowStatus {
    PASS,    // 全绿通过
    WARNING, // 存在警告瑕疵
    ERROR    // 存在致命异常
}

// 3. 单据级状态 (原 ExportReadiness)
enum class DocStatus {
    PASS,    // 可直接导出
    WARNING, // 需弹窗二次确认
    ERROR    // 强拦截禁止导出
}

// 4. 数据来源（可累加，非互斥）
enum class RecordSource {
    UDI,      // 扫码（含补扫）
    OCR,      // OCR 文字识别
    MANUAL,   // 用户手动修改
    AI,       // 预留：AI 识别
    SYNC,     // 预留：接口同步
    IMPORT    // 预留：Excel 导入
}

// ================= 重构：单行记录模型 =================

data class InventoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String = "",
    val documentName: String = "未分配",
    val di: String,
    val productName: String,
    val batch: String,
    val targetQty: Int = 0,
    val actualQty: Int = 0,
    val productionDate: String? = null,
    val expiryDate: String? = null,
    val sources: Set<RecordSource> = emptySet(),

    // --- 👇 新增：5大核心字段的独立校验状态 👇 ---
    val diStatus: FieldStatus = FieldStatus.NOT_CHECKED,
    val batchStatus: FieldStatus = FieldStatus.NOT_CHECKED,
    val prodDateStatus: FieldStatus = FieldStatus.NOT_CHECKED,
    val expDateStatus: FieldStatus = FieldStatus.NOT_CHECKED,
    val qtyStatus: FieldStatus = FieldStatus.NOT_CHECKED,

    // 保留该字段用于记录业务描述（如 "库中无记录"），但不作为判定标准
    val errorMessage: String = "",

    // --- 方案A / 决策2：多 DI 候选池支持 ---
    val candidateDis: List<String> = emptyList(),
    val actualDi: String? = null
) {
    // 兼容：手动修改 = sources 包含 MANUAL
    val isManualModified: Boolean
        get() = sources.contains(RecordSource.MANUAL)
    // --- 👇 核心：行状态聚合逻辑（短板效应） 👇 ---
    val rowStatus: RowStatus
        get() {
            val statuses = listOf(diStatus, batchStatus,  expDateStatus, qtyStatus)
            return when {
                statuses.any { it == FieldStatus.ERROR } -> RowStatus.ERROR
                statuses.any { it == FieldStatus.WARNING } -> RowStatus.WARNING
                else -> RowStatus.PASS
            }
        }

    // 兼容原有代码中的简单判错逻辑（供一些非 UI 渲染的地方使用）
    val isError: Boolean
        get() = rowStatus == RowStatus.ERROR
}

// ================= JSON 实体与扩展模型 (保持不变) =================

@Serializable
data class TargetDocument(
    val documentId: String,
    val documentName: String,
    val documentDate: String,
    val billType: String? = null,
    val orderNo: String? = null,
    val documentNumber: Int = 0,
    val items: List<TargetItem>
)

@Serializable
data class TargetItem(
    @SerialName("materialCode")
    @SerializedName("materialCode")
    val materialCode: String,
    // 服务端契约允许批次/名称为空（Gson 反序列化会绕过非空校验，直接把 null 塞进字段）
    val batch: String? = null,
    val productName: String? = null,
    val targetQty: Double = 0.0,
    val productionDate: String? = null,
    val expiryDate: String? = null
)

data class PhotoScanResult(
    val photoUri: Uri,
    val barcodes: List<com.example.inventorymaster.batchscanner.core.models.GlobalBarcode>
)
