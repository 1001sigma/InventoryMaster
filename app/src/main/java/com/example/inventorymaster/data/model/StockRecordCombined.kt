package com.example.inventorymaster.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord

data class StockRecordCombined(
    @Embedded
    val record: StockRecord,

    @Relation(
        parentColumn = "di",
        entityColumn = "di"
    )
    val product: ProductBase?
)
