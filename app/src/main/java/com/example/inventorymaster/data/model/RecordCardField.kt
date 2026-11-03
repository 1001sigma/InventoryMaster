package com.example.inventorymaster.data.model

import androidx.annotation.DrawableRes
import com.example.inventorymaster.R
import com.example.inventorymaster.data.entity.HighlightField
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.utils.DateUtils

/**
 * 记录卡片（StockRecordItem）可配置显示的字段。
 * id 持久化到 DataStore；label 同时用作卡片上「label: 值」的前缀。
 * 图标统一为 res/drawable 下的 24dp 单色描边矢量图，随 tint 着色。
 */
enum class RecordCardField(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int? = null
) {
    UDI("udi", "UDI", iconRes = R.drawable.ic_udi),
    BATCH("batch", "批号", iconRes = R.drawable.ic_batch),
    EXPIRY("expiry", "效期", iconRes = R.drawable.use_bydate),
    LOCATION("location", "仓库名称", iconRes = R.drawable.ic_warehouse),
    MANUFACTURER("manufacturer", "生产厂家", iconRes = R.drawable.ic_manufacturer2),
    SPECIFICATION("spec", "规格", iconRes = R.drawable.ic_specification),
    MODEL("model", "型号", iconRes = R.drawable.ic_model),
    REGISTRATION_CERT("cert", "注册证号", iconRes = R.drawable.ic_registration_cert),
    MATERIAL_CODE("material_code", "物料编码", iconRes = R.drawable.ic_material_code),
    UNIT("unit", "单位", iconRes = R.drawable.ic_unit),
    PRODUCTION_DATE("production_date", "生产日期", iconRes = R.drawable.ic_production_date),
    OPERATOR("operator", "操作员", iconRes = R.drawable.ic_operator),
    REMARKS("remarks", "备注", iconRes = R.drawable.ic_remarks),
    NONE("none", "不显示");
}

/**
 * 记录卡片上的五个可配置信息位（位置固定，显示哪个字段由 RecordCardConfig 决定）。
 * title 用于设置页的位置名称。
 */
enum class RecordCardSlot(val title: String, val defaultField: RecordCardField) {
    UDI("UDI", RecordCardField.UDI),
    BATCH("批号", RecordCardField.BATCH),
    EXPIRY("效期", RecordCardField.EXPIRY),
    LOCATION("仓库名称", RecordCardField.LOCATION),
    MANUFACTURER("生产厂家", RecordCardField.MANUFACTURER);
}

/**
 * 五个信息位 → 字段 id 的映射配置。存 String id（而非枚举）便于后续增删字段不破坏旧数据。
 */
data class RecordCardConfig(
    val udiField: String = RecordCardField.UDI.id,
    val batchField: String = RecordCardField.BATCH.id,
    val expiryField: String = RecordCardField.EXPIRY.id,
    val locationField: String = RecordCardField.LOCATION.id,
    val manufacturerField: String = RecordCardField.MANUFACTURER.id
) {
    fun fieldFor(slot: RecordCardSlot): RecordCardField {
        val raw = when (slot) {
            RecordCardSlot.UDI -> udiField
            RecordCardSlot.BATCH -> batchField
            RecordCardSlot.EXPIRY -> expiryField
            RecordCardSlot.LOCATION -> locationField
            RecordCardSlot.MANUFACTURER -> manufacturerField
        }
        return RecordCardField.entries.firstOrNull { it.id == raw } ?: slot.defaultField
    }

    private fun withRaw(slot: RecordCardSlot, fieldId: String): RecordCardConfig = when (slot) {
        RecordCardSlot.UDI -> copy(udiField = fieldId)
        RecordCardSlot.BATCH -> copy(batchField = fieldId)
        RecordCardSlot.EXPIRY -> copy(expiryField = fieldId)
        RecordCardSlot.LOCATION -> copy(locationField = fieldId)
        RecordCardSlot.MANUFACTURER -> copy(manufacturerField = fieldId)
    }

    /**
     * 字段唯一裁决：把 fieldId 配到 slot；
     * 若该字段已被其他位置占用，原位置自动置为「不显示」（留空）。
     */
    fun withSlotField(slot: RecordCardSlot, fieldId: String): RecordCardConfig {
        val assigned = RecordCardField.entries.firstOrNull { it.id == fieldId } ?: return this
        var result = this
        if (assigned != RecordCardField.NONE) {
            RecordCardSlot.entries
                .filter { it != slot }
                .forEach { other ->
                    if (result.fieldFor(other) == assigned) {
                        result = result.withRaw(other, RecordCardField.NONE.id)
                    }
                }
        }
        return result.withRaw(slot, fieldId)
    }
}

/** 搜索高亮跟随字段值而非位置：字段与高亮目标匹配时对应文本加高亮背景 */
fun RecordCardField.matchesHighlight(highlight: HighlightField): Boolean = when (this) {
    RecordCardField.UDI -> highlight == HighlightField.DI
    RecordCardField.BATCH -> highlight == HighlightField.BATCH_NUMBER
    RecordCardField.LOCATION -> highlight == HighlightField.LOCATION
    else -> false
}

/** 卡片渲染取值：返回 null 表示该位置留空（整行不显示） */
fun resolveFieldValue(field: RecordCardField, record: StockRecord, product: ProductBase?): String? = when (field) {
    RecordCardField.UDI -> product?.let { "${it.di ?: ""} ${it.model ?: ""}".trim().ifBlank { null } }
    RecordCardField.BATCH -> record.batchNumber
    RecordCardField.EXPIRY -> DateUtils.formatDateForDisplay(record.expiryDate)
    RecordCardField.LOCATION -> record.location
    RecordCardField.MANUFACTURER -> product?.manufacturer ?: "未知厂家"
    RecordCardField.SPECIFICATION -> product?.specification?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.MODEL -> product?.model?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.REGISTRATION_CERT -> product?.registrationCert?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.MATERIAL_CODE -> product?.materialCode?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.UNIT -> product?.unit?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.PRODUCTION_DATE -> record.productionDate?.let { DateUtils.formatDateForDisplay(it) } ?: "—"
    RecordCardField.OPERATOR -> record.operator.ifBlank { "—" }
    RecordCardField.REMARKS -> record.remarks?.takeIf { it.isNotBlank() } ?: "—"
    RecordCardField.NONE -> null
}
