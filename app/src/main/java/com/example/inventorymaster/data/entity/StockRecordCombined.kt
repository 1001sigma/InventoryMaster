package com.example.inventorymaster.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * L3 聚合对象 (用于 UI 展示)
 * 包含：1条库存记录 + 对应的产品基础信息
 */
data class StockRecordCombined(
    // 嵌入库存记录的所有字段
    @Embedded
    val record: StockRecord,

    // 关联查询产品信息
    // Room 会自动根据 StockRecord.di 去 ProductBase.di 找对应的数据
    @Relation(
        parentColumn = "di",
        entityColumn = "di"
    )
    val product: ProductBase? // 可能为空 (虽然外键约束保证了不为空，但代码层面宽容点)
)