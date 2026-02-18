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
    val uuid: String,
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
    val uuid: String?,
    val di: String,
    val batchNumber: String,
    val expiryDate: Long,
    val quantity: Double,
    val actualQuantity: Double? = null,
    val location: String? = null,
    val remarks: String? = null,
    val sourceType: Int = 0,
    val lastUpdateTime: Long? = 0
)

//增量上传的专用请求包
data class PushRequest(
    val records: List<StockRecord>, // 或者 StockRecordDto，取决于你API定义，你目前用的是 Entity
    val products: List<ProductDto>  // 顺便带上产品资料
)

// 扩展函数：将 Entity 转为 DTO
fun StockRecord.toDto(): StockRecordDto {
    return StockRecordDto(
        sessionId = this.sessionId,
        uuid = this.uuid,
        di = this.di,
        batchNumber = this.batchNumber,
        expiryDate = this.expiryDate,
        quantity = this.quantity,
        actualQuantity = this.actualQuantity,
        location = this.location,
        remarks = this.remarks,
        sourceType = this.sourceType,
        lastUpdateTime = this.lastUpdateTime
    )
}

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

