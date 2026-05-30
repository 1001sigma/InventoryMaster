package com.example.inventorymaster.data.sync

import android.util.Log
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.toEntity
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.network.InventoryApiService

/**
 * 增量下载策略：根据 Phase 2 的拉取锚点，从服务端增量拉取变更记录。
 * 服务端查询使用 >= 锚点（Phase 2），本地通过 UUID 去重更新。
 */
class IncrementalDownloadStrategy(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao,
    private val settingsRepository: SettingsRepository,
    private val timestampManager: TimestampManager
) : SyncStrategy {

    override suspend fun execute(context: SyncContext): Result<String> {
        return try {
            val ip = settingsRepository.getServerIp()
            if (ip.isBlank()) {
                return Result.failure(Exception("未设置服务器 IP 地址"))
            }
            val api = InventoryApiService.Companion.create(ip)
            val retryPolicy = RetryPolicy()
            val session = sessionDao.getSessionById(context.sessionId)
                ?: return Result.failure(Exception("任务不存在"))
            val currentUuid = session.uuid

            // Phase 2: 使用拉取锚点
            val lastSyncTime = timestampManager.getLastPullAnchor(context.sessionId)
            val response = retryPolicy.execute { api.pullData(currentUuid, lastSyncTime) }

            if (response.isSuccessful) {
                val pullResponse = response.body()
                val remoteRecords = pullResponse?.records ?: emptyList()
                val remoteProducts = pullResponse?.products ?: emptyList()

                for (pDto in remoteProducts) {
                    val existing = productDao.getProductByKey(pDto.productKey)
                    if (existing != null) {
                        productDao.updateProduct(pDto.toEntity())
                    } else {
                        productDao.insertProduct(pDto.toEntity())
                    }
                }

                if (remoteRecords.isNotEmpty()) {
                    val processedCount = remoteRecords.size
                    for (remoteRecord in remoteRecords) {
                        val localRecord = stockRecordDao.getRecordByUuid(remoteRecord.uuid ?: "")

                        if (localRecord != null) {
                            // Phase 5: 冲突检测 — 如果本地有未推送的修改，记录到 historyLog
                            if (localRecord.syncStatus == 1) {
                                // Phase 5: 冲突 — 服务端覆盖，保留本地修改痕迹
                                val conflictLog = "server_overwrite: qty=${localRecord.quantity}, actual=${localRecord.actualQuantity}, remarks=${localRecord.remarks}, operator=${localRecord.operator}"
                                val recordToUpdate = StockRecord(
                                    id = localRecord.id,
                                    uuid = remoteRecord.uuid ?: "",
                                    sessionId = context.sessionId,
                                    productKey = remoteRecord.productKey,
                                    batchNumber = remoteRecord.batchNumber,
                                    expiryDate = remoteRecord.expiryDate,
                                    quantity = remoteRecord.quantity,
                                    actualQuantity = remoteRecord.actualQuantity,
                                    location = remoteRecord.location ?: "",
                                    remarks = remoteRecord.remarks,
                                    lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                    sourceType = remoteRecord.sourceType,
                                    syncStatus = 0,
                                    operator = remoteRecord.operator ?: localRecord.operator,
                                    historyLog = (localRecord.historyLog ?: "") + " | " + conflictLog
                                )
                                stockRecordDao.insertRecord(recordToUpdate)
                            } else {
                                val recordToUpdate = StockRecord(
                                    id = localRecord.id,
                                    uuid = remoteRecord.uuid ?: "",
                                    sessionId = context.sessionId,
                                    productKey = remoteRecord.productKey,
                                    batchNumber = remoteRecord.batchNumber,
                                    expiryDate = remoteRecord.expiryDate,
                                    quantity = remoteRecord.quantity,
                                    actualQuantity = remoteRecord.actualQuantity,
                                    location = remoteRecord.location ?: "",
                                    remarks = remoteRecord.remarks,
                                    lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                    sourceType = remoteRecord.sourceType,
                                    syncStatus = 0,
                                    operator = remoteRecord.operator ?: localRecord.operator,
                                    historyLog = localRecord.historyLog ?: ""
                                )
                                stockRecordDao.insertRecord(recordToUpdate)
                            }
                        } else {
                            val recordToInsert = StockRecord(
                                id = 0,
                                uuid = remoteRecord.uuid ?: "",
                                sessionId = context.sessionId,
                                productKey = remoteRecord.productKey,
                                batchNumber = remoteRecord.batchNumber,
                                expiryDate = remoteRecord.expiryDate,
                                quantity = remoteRecord.quantity,
                                actualQuantity = remoteRecord.actualQuantity,
                                location = remoteRecord.location ?: "",
                                remarks = remoteRecord.remarks,
                                lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                sourceType = remoteRecord.sourceType,
                                syncStatus = 0,
                                operator = remoteRecord.operator ?: ""
                            )
                            stockRecordDao.insertRecord(recordToInsert)
                        }
                    }

                    // Phase 2: 使用防倒退的拉取锚点更新
                    val maxServerTime = remoteRecords.maxOfOrNull { it.lastUpdateTime ?: 0L } ?: 0L
                    if (maxServerTime > 0) {
                        timestampManager.saveLastPullAnchor(context.sessionId, maxServerTime)
                        Log.d("SYNC", "同步锚点已更新为服务器时间: $maxServerTime")
                    }

                    return Result.success("更新了$processedCount 条数据, ${remoteProducts.size} 个产品")
                }
                return Result.success("已是最新")
            } else {
                Result.failure(Exception("下载失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
