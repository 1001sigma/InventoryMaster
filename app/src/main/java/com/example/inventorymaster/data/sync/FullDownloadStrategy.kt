package com.example.inventorymaster.data.sync

import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.dto.toEntity
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.network.InventoryApiService
import java.util.UUID

/**
 * 全量下载策略：从服务端拉取指定 session 的全部数据并覆盖本地。
 * 使用 Phase 1 的 overwriteSessionData（@Transaction）保证原子性。
 */
class FullDownloadStrategy(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao
) : SyncStrategy {

    override suspend fun execute(context: SyncContext): Result<String> {
        return try {
            val api = InventoryApiService.Companion.create(context.ip)
            val retryPolicy = RetryPolicy()
            val localSession = sessionDao.getSessionById(context.sessionId)
                ?: return Result.failure(Exception("本地任务不存在"))
            val currentUuid = localSession.uuid

            val response = retryPolicy.execute { api.downloadData(currentUuid) }

            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("下载失败: ${response.code()}"))
            }

            val data = response.body()!!

            // 1. 保存/更新 Session
            val serverSession = data.session
            val localByuuid = sessionDao.getSessionByUuid(serverSession.uuid)
            val finalLocalId: Long

            if (localByuuid != null) {
                finalLocalId = localByuuid.id
                val sessionToUpdate = localByuuid.copy(
                    name = serverSession.name,
                    date = serverSession.date,
                    status = serverSession.status,
                    isLocked = serverSession.isLocked
                )
                sessionDao.updateSession(sessionToUpdate)
            } else {
                val sessionToInsert = InventorySession(
                    id = 0,
                    uuid = serverSession.uuid,
                    name = serverSession.name,
                    date = serverSession.date,
                    status = serverSession.status,
                    isLocked = serverSession.isLocked
                )
                finalLocalId = sessionDao.insertSession(sessionToInsert)
            }

            // 2. 保存产品
            for (pDto in data.products) {
                val existingProduct = productDao.getProductByKey(pDto.productKey)
                if (existingProduct != null) {
                    productDao.updateProduct(pDto.toEntity())
                } else {
                    productDao.insertProduct(pDto.toEntity())
                }
            }

            // 3. 事务性覆写记录
            val newRecords = data.records.map { rDto ->
                StockRecord(
                    sessionId = finalLocalId,
                    uuid = rDto.uuid ?: UUID.randomUUID().toString(),
                    productKey = rDto.productKey,
                    batchNumber = rDto.batchNumber,
                    expiryDate = rDto.expiryDate,
                    quantity = rDto.quantity,
                    actualQuantity = rDto.actualQuantity,
                    location = rDto.location ?: "",
                    remarks = rDto.remarks,
                    operator = rDto.operator ?: "",
                    lastUpdateTime = rDto.lastUpdateTime ?: 0L,
                    sourceType = 2
                )
            }
            stockRecordDao.overwriteSessionData(finalLocalId, newRecords)

            Result.success("同步成功！任务：${serverSession.name}")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
