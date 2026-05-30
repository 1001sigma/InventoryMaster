package com.example.inventorymaster.data.sync

import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.SessionDto
import com.example.inventorymaster.data.dto.SyncData
import com.example.inventorymaster.data.dto.toDto
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.network.InventoryApiService

/**
 * 全量上传策略：将本地 session 的全部货品 + 库存记录打包上传至服务端。
 * 服务端在事务中先删后插（Phase 1 已加固），此策略仅负责数据组装与发送。
 */
class FullUploadStrategy(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao
) : SyncStrategy {

    override suspend fun execute(context: SyncContext): Result<String> {
        return try {
            val sessionEntity = sessionDao.getSessionById(context.sessionId)
                ?: return Result.failure(Exception("找不到任务 ${context.sessionId}"))

            val sessionDto = SessionDto(
                id = sessionEntity.id,
                uuid = sessionEntity.uuid,
                name = sessionEntity.name,
                date = sessionEntity.date,
                status = sessionEntity.status,
                isLocked = sessionEntity.isLocked
            )

            val combinedList = stockRecordDao.getExportData(context.sessionId)
            if (combinedList.isEmpty()) {
                return Result.failure(Exception("当前盘点任务没有任何数据"))
            }

            val recordDtoList = combinedList.map { it.record.toDto() }

            val productDtoList = combinedList
                .map { combined ->
                    combined.product ?: ProductBase(
                        productKey = combined.record.productKey,
                        productName = "未录入产品(上传补齐)",
                        manufacturer = "未知",
                        source = "upload_auto_fix",
                        specification = "",
                        model = "",
                        materialCode = "",
                        unit = "",
                        categoryCode = "",
                        registrationCert = ""
                    )
                }
                .distinctBy { it.productKey }
                .map { it.toDto() }

            val syncData = SyncData(
                session = sessionDto,
                products = productDtoList,
                records = recordDtoList
            )

            val api = InventoryApiService.Companion.create(context.ip)
            val retryPolicy = RetryPolicy()
            val response = retryPolicy.execute { api.uploadData(syncData) }

            if (response.isSuccessful) {
                Result.success("上传成功！")
            } else {
                Result.failure(Exception("服务器拒绝: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
