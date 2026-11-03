package com.example.inventorymaster.data.dto

import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord

// 1. 总包装
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
    val isLocked: Boolean,
    val startedAt: Long? = null  // 服务端任务开始时间（空=待开始），全量下载时落库用
)

// 2. 产品数据模型
data class ProductDto(
    val productKey: String,
    val di: String? = null,
    val productName: String,
    val specification: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val materialCode: String? = null,
    val unit: String? = null,
    val categoryCode: String? = null,
    val registrationCert: String? = null,
    val changeSeq: Long? = null  // M0：服务端变更序号（仅拉取方向使用，本地不落库）
)

// 3. 库存数据模型
data class StockRecordDto(
    val id: Long = 0,
    val sessionId: Long,
    val uuid: String?,
    val productKey: String,
    val batchNumber: String,
    val expiryDate: Long,
    val quantity: Double,
    val actualQuantity: Double? = null,
    val location: String? = null,
    val remarks: String? = null,
    val operator: String? = null,
    val sourceType: Int = 0,
    val lastUpdateTime: Long? = 0,
    val syncStatus: Int = 0,
    val historyLog: String? = null,
    val productionDate: Long? = null,
    val changeSeq: Long? = null  // M0：服务端变更序号（仅拉取方向使用，本地不落库）
)

// Phase 5: 增量拉取响应（记录 + 关联产品；M0 起游标改为 change_seq，墓碑携带序号；M2 起携带任务生命周期状态）
data class PullResponse(
    val records: List<StockRecordDto>,
    val products: List<ProductDto>,
    val deletedRecords: List<DeletedRecordDto> = emptyList(),
    val conflictSummary: List<ConflictItem> = emptyList(),
    val sessionStatus: Int? = null,
    val startedAt: Long? = null,
    val archivedAt: Long? = null
)

// M0：删除墓碑（uuid + 变更序号，客户端据此推进游标）
data class DeletedRecordDto(
    val recordUuid: String,
    val changeSeq: Long = 0
)

data class ConflictItem(
    val uuid: String,
    val field: String,
    val localValue: String? = null,
    val serverValue: String? = null
)

//增量上传的专用请求包
data class PushRequest(
    val records: List< StockRecordDto>, // 或者StockRecordDto，取决于你API定义，你目前用的是Entity
    val products: List<ProductDto>  // 顺便带上产品资料
)

// 删除请求
data class DeleteRequest(
    val recordUuids: List<String>,
    val sessionUuid: String
)

// ==========================================
// M1 协作身份/加入/上报 请求体
// ==========================================
data class BindOperatorRequest(
    val bindingCode: String,
    val deviceUuid: String,
    val deviceModel: String? = null
)

data class JoinTaskRequest(
    val sessionId: Long,
    val deviceUuid: String
)

data class JoinTaskRequestDto(
    val sessionId: Long,
    val deviceUuid: String,
    val deviceModel: String? = null,
    val operatorName: String
)

data class JoinStatusResponse(
    val approvalStatus: Int, // 0=待审批, 1=已批准, 2=已拒绝
    val sessionName: String = "",
    val token: String? = null,
    val message: String? = null
)

data class ReportTaskRequest(
    val sessionUuid: String,
    val deviceUuid: String,
    val localStatus: Int,
    val eventId: String,
    val reportedAt: Long
)

// 扩展函数：将 Entity 转为 DTO
fun StockRecord.toDto(): StockRecordDto {
    return StockRecordDto(
        id = this.id,
        sessionId = this.sessionId,
        uuid = this.uuid,
        productKey = this.productKey,
        batchNumber = this.batchNumber,
        expiryDate = this.expiryDate,
        quantity = this.quantity,
        actualQuantity = this.actualQuantity,
        location = this.location,
        remarks = this.remarks,
        operator = this.operator,
        sourceType = this.sourceType,
        lastUpdateTime = this.lastUpdateTime,
        syncStatus = this.syncStatus,
        historyLog = this.historyLog,
        productionDate = this.productionDate
    )
}

fun ProductBase.toDto(): ProductDto {
    return ProductDto(
        productKey = this.productKey,
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

fun ProductDto.toEntity(): com.example.inventorymaster.data.entity.ProductBase {
    return com.example.inventorymaster.data.entity.ProductBase(
        productKey = this.productKey,
        di = this.di?.trim()?.ifBlank { null },
        productName = this.productName,
        specification = this.specification ?: "",
        model = this.model ?: "",
        manufacturer = this.manufacturer ?: "",
        materialCode = this.materialCode?.trim()?.ifBlank { null },
        unit = this.unit ?: "",
        categoryCode = this.categoryCode ?: "",
        registrationCert = this.registrationCert ?: ""
    )
}

/**
 * 将远端 ProductDto 与本地已有 ProductBase 合并。
 * 核心原则：远端有真实值则更新，但远端空值/缺省值（如无 DI、无规格）绝不冲掉本地已有有效数据。
 */
fun ProductDto.mergeInto(
    existing: com.example.inventorymaster.data.entity.ProductBase?,
    localKey: String = existing?.productKey ?: this.productKey
): com.example.inventorymaster.data.entity.ProductBase {
    val cleanDi = this.di?.trim()?.ifBlank { null }
    val cleanMat = this.materialCode?.trim()?.ifBlank { null }
    val finalDi = cleanDi ?: existing?.di
    val finalMat = cleanMat ?: existing?.materialCode?.ifBlank { null }

    if (existing == null) {
        return this.toEntity().copy(
            di = finalDi,
            materialCode = finalMat,
            productKey = localKey
        )
    }

    return existing.copy(
        di = finalDi,
        materialCode = finalMat,
        productName = if (this.productName.isNotBlank() && this.productName != "未命名产品") this.productName else existing.productName,
        specification = this.specification?.takeIf { it.isNotBlank() } ?: existing.specification,
        model = this.model?.takeIf { it.isNotBlank() } ?: existing.model,
        manufacturer = this.manufacturer?.takeIf { it.isNotBlank() && it != "未知厂家" } ?: existing.manufacturer,
        registrationCert = this.registrationCert?.takeIf { it.isNotBlank() } ?: existing.registrationCert,
        unit = this.unit?.takeIf { it.isNotBlank() } ?: existing.unit,
        categoryCode = this.categoryCode?.takeIf { it.isNotBlank() } ?: existing.categoryCode,
        productKey = localKey
    )
}

// 全局产品库增量拉取响应
data class ProductPullResponse(
    val status: String,
    val data: ProductPullData
)

data class ProductPullData(
    val maxSeq: Long,
    val products: List<ProductDto>
)
