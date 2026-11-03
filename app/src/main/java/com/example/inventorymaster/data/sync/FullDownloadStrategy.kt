package com.example.inventorymaster.data.sync

import android.util.Log
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.dto.mergeInto
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

            // 先尝试按本地 ID 查找
            val localSession = sessionDao.getSessionById(context.sessionId)

            val response = if (localSession != null) {
                // 本地有，用 UUID 下载
                retryPolicy.execute { api.downloadData(sessionUuid = localSession.uuid) }
            } else {
                // 本地没有（扫码加入），用服务端 ID 下载
                retryPolicy.execute { api.downloadData(sessionId = context.sessionId) }
            }

            if (!response.isSuccessful || response.body() == null) {
                val code = response.code()
                val msg = if (code == 404) "服务端任务不存在" else "下载失败: $code"
                return Result.failure(Exception(msg))
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
                    isLocked = serverSession.isLocked,
                    // 服务器下发任务：标记来源并落库服务端状态机（startedAt 空=待开始）
                    sessionSource = 1,
                    startedAt = serverSession.startedAt
                )
                sessionDao.updateSession(sessionToUpdate)
            } else {
                val sessionToInsert = InventorySession(
                    id = 0,
                    uuid = serverSession.uuid,
                    name = serverSession.name,
                    date = serverSession.date,
                    status = serverSession.status,
                    isLocked = serverSession.isLocked,
                    // 服务器下发任务：标记来源并落库服务端状态机（startedAt 空=待开始）
                    sessionSource = 1,
                    startedAt = serverSession.startedAt
                )
                finalLocalId = sessionDao.insertSession(sessionToInsert)
            }

            // 2. 保存产品（组合键归一化/晋升），并构建 远端key→本地组合键 映射
            val keyMapping = mutableMapOf<String, String>()
            for (pDto in data.products) {
                ProductMergeHelper.saveOrUpdateSyncedProduct(
                    productDao = productDao,
                    stockRecordDao = stockRecordDao,
                    pDto = pDto,
                    keyMapping = keyMapping
                )
            }

            // 3. 事务性覆写记录（key 按映射重定向到本地组合键）
            val newRecords = data.records.map { rDto ->
                val remoteKey = rDto.productKey
                StockRecord(
                    sessionId = finalLocalId,
                    uuid = rDto.uuid ?: UUID.randomUUID().toString(),
                    productKey = keyMapping[remoteKey] ?: remoteKey,
                    batchNumber = rDto.batchNumber,
                    expiryDate = rDto.expiryDate,
                    quantity = rDto.quantity,
                    actualQuantity = rDto.actualQuantity,
                    location = rDto.location ?: "",
                    remarks = rDto.remarks,
                    isVerified = rDto.actualQuantity != null || rDto.remarks?.contains("已查验") == true,
                    operator = rDto.operator ?: "",
                    lastUpdateTime = rDto.lastUpdateTime ?: 0L,
                    sourceType = 2,
                    historyLog = rDto.historyLog,
                    productionDate = rDto.productionDate
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
