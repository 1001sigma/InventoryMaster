package com.example.inventorymaster.data.dto
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