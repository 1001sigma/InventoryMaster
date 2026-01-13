package com.example.inventorymaster.data.model

import com.example.inventorymaster.data.entity.ProductBase
// 定义解决策略枚举
enum class ConflictAction {
    PENDING,   // 待处理
    OVERWRITE, // 覆盖 (使用 Excel 的新数据)
    IGNORE     // 忽略 (保留数据库的旧数据)
}

// 冲突数据包裹
data class ProductConflict(
    val di: String,
    val oldProduct: ProductBase, // 数据库里的
    val newProduct: ProductBase, // Excel 里的
    var resolveAction: ConflictAction = ConflictAction.PENDING // 默认状态
)