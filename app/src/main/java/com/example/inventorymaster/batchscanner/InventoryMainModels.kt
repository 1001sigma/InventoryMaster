package com.example.inventorymaster.batchscanner

import java.util.UUID

/**
 * 主界面列表中的单行记录模型
 */
data class InventoryRecord(
    val id: String = UUID.randomUUID().toString(), // 唯一标识，Compose 列表差异比对需要
    val di: String,                                // 产品 DI (GTIN)
    val productName: String,                       // 匹配到的产品名称
    val batch: String,                             // 批号
    val targetQty: Int = 0,                        // 单据预期数量 (0代表无单据模式)
    val actualQty: Int = 0,                        // 实际扫描/填入的数量
    val isManualModified: Boolean = false,         // 是否被手工修改过（用于 UI 变色）
    val isError: Boolean = false,                  // 是否有异常（解析失败或查不到商品）
    val errorMessage: String = ""                  // 异常提示信息
) {
    // 辅助属性，用于 UI 决定如何渲染这行（标红、标绿等）
    val status: RecordStatus
        get() = when {
            isError -> RecordStatus.ERROR
            targetQty > 0 && actualQty > targetQty -> RecordStatus.OVERFLOW // 超出预期
            targetQty > 0 && actualQty < targetQty -> RecordStatus.MISSING  // 少于预期
            targetQty > 0 && actualQty == targetQty -> RecordStatus.MATCHED // 完美匹配
            productName == "未知商品" -> RecordStatus.MISSING
            else -> RecordStatus.NORMAL // 无单据模式下的正常状态
        }
}

enum class RecordStatus {
    NORMAL, MATCHED, MISSING, OVERFLOW, ERROR
}