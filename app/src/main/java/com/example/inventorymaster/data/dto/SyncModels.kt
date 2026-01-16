package com.example.inventorymaster.data.dto

import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord

// 1. 总包裹
data class SyncData(
    val session: SessionDto,
    val products: List<ProductDto>,
    val records: List<StockRecordDto>
)

// 2. [新增] 任务数据模型 (DTO)
data class SessionDto(
    val id: Long,
    val name: String,
    val date: Long,
    val status: Int,
    val isLocked: Boolean
)

// 2. 产品数据模型
data class ProductDto(
    val di: String,
    val productName: String,
    val specification: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val materialCode: String? = null,
    val unit: String? = null,
    val categoryCode: String? = null,
    val registrationCert: String? = null
)

// 3. 库存数据模型
data class StockRecordDto(
    val sessionId: Long,
    val di: String,
    val batchNumber: String,
    val expiryDate: Long,
    val quantity: Double,
    val actualQuantity: Double? = null,
    val location: String,
    val remarks: String? = null,
    val sourceType: Int = 0
)

// 扩展函数：将 Entity 转为 DTO
fun StockRecord.toDto(): StockRecordDto {
    return StockRecordDto(
        sessionId = this.sessionId,
        di = this.di,
        batchNumber = this.batchNumber,
        expiryDate = this.expiryDate,
        quantity = this.quantity,
        actualQuantity = this.actualQuantity,
        location = this.location,
        remarks = this.remarks,
        sourceType = this.sourceType
    )
}

// 在 SyncModels.kt 中添加

fun ProductBase.toDto(): ProductDto {
    return ProductDto(
        di = this.di,
        productName = this.productName,
        specification = this.specification ?: "",
        model = this.model ?: "",
        manufacturer = this.manufacturer ?: "",
        materialCode = this.materialCode ?: "",
        unit = this.unit ?: "",
        categoryCode = this.categoryCode ?: "",
        registrationCert = this.registrationCert ?: ""
    )
}