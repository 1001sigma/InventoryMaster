package com.example.inventorymaster.data.sync

import android.util.Log
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.mergeInto
import com.example.inventorymaster.data.dto.toEntity
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.network.InventoryApiService

/**
 * 增量下载策略：根据 M0 拉取游标（服务端 change_seq），增量拉取变更记录/产品/墓碑。
 * 服务端按 change_seq > 游标 过滤（服务端盖章序号，无时钟偏差问题），本地通过 UUID 去重更新。
 * 服务端地址由调用方经 ServerConnectionManager 解析后放入 [SyncContext.ip]。
 */
class IncrementalDownloadStrategy(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao,
    private val timestampManager: TimestampManager
) : SyncStrategy {

    override suspend fun execute(context: SyncContext): Result<String> {
        return try {
            val api = InventoryApiService.Companion.create(context.ip)
            val retryPolicy = RetryPolicy()
            val session = sessionDao.getSessionById(context.sessionId)
                ?: return Result.failure(Exception("任务不存在"))
            val currentUuid = session.uuid

            // M0: 使用 change_seq 拉取游标
            val lastSeq = timestampManager.getLastPullSeq(context.sessionId)
            val response = retryPolicy.execute { api.pullData(currentUuid, lastSeq) }

            if (response.isSuccessful) {
                val pullResponse = response.body()
                val remoteRecords = pullResponse?.records ?: emptyList()
                val remoteProducts = pullResponse?.products ?: emptyList()
                val deletedRecords = pullResponse?.deletedRecords ?: emptyList()
                val deletedUuids = deletedRecords.map { it.recordUuid }

                // M6：归档感知——服务端已归档而本地仍进行中 → 本地置 1 只读（doc §6.5 M6）
                var archivedNotice = ""
                val serverStatus = pullResponse?.sessionStatus
                if (serverStatus != null && serverStatus == 1 && session.status == 0) {
                    sessionDao.updateStatus(context.sessionId, 1)
                    archivedNotice = "；服务端已归档，本地转为只读"
                    Log.w("SYNC", "检测到服务端已归档，本地任务自动转为只读")
                }

                // 待开始状态对齐——服务端已点开始而本地尚无 startedAt → 落库并解除写入门禁
                // （只在服务端非空时写，避免 null 回写清掉本地状态；started_at 一旦设置不会回退。
                //   startedAt 落库对本地任务无害（isPendingStart 要求 source=1），提示只给服务端任务）
                var startedNotice = ""
                val serverStartedAt = pullResponse?.startedAt
                if (serverStartedAt != null && session.startedAt == null) {
                    sessionDao.updateStartedAt(context.sessionId, serverStartedAt)
                    if (session.status == 0 && session.sessionSource == 1) {
                        startedNotice = "；服务端任务已开始，本地恢复可编辑"
                        Log.w("SYNC", "检测到服务端任务已开始，本地解锁写入")
                    }
                }

                // 远端key → 本地组合键 的重定向映射（兼容服务端残留旧格式 key，支持智能晋升合并）
                val keyMapping = mutableMapOf<String, String>()
                for (pDto in remoteProducts) {
                    ProductMergeHelper.saveOrUpdateSyncedProduct(
                        productDao = productDao,
                        stockRecordDao = stockRecordDao,
                        pDto = pDto,
                        keyMapping = keyMapping
                    )
                }

                // 处理服务端删除的记录
                if (deletedUuids.isNotEmpty()) {
                    stockRecordDao.deleteRecordsByUuids(deletedUuids)
                    Log.d("SYNC", "已删除 ${deletedUuids.size} 条服务端标记删除的记录")
                }

                // M0: 游标推进——取三类变更的最大 changeSeq，任一非空即推进
                // （修正旧逻辑"仅记录变更才推进锚点"导致删除/产品变更重复拉取的小毛病）
                val maxSeq = maxOf(
                    remoteRecords.maxOfOrNull { it.changeSeq ?: 0L } ?: 0L,
                    remoteProducts.maxOfOrNull { it.changeSeq ?: 0L } ?: 0L,
                    deletedRecords.maxOfOrNull { it.changeSeq } ?: 0L
                )
                if (maxSeq > 0) {
                    timestampManager.saveLastPullSeq(context.sessionId, maxSeq)
                    Log.d("SYNC", "拉取游标已推进到 changeSeq: $maxSeq")
                }

                if (remoteRecords.isNotEmpty()) {
                    val processedCount = remoteRecords.size
                    val conflicts = mutableListOf<com.example.inventorymaster.data.dto.ConflictItem>()

                    for (remoteRecord in remoteRecords) {
                        val localRecord = stockRecordDao.getRecordByUuid(remoteRecord.uuid ?: "")

                        if (localRecord != null) {
                            if (localRecord.syncStatus == 1) {
                                // 冲突检测：记录字段级差异
                                if (localRecord.quantity != remoteRecord.quantity) {
                                    conflicts.add(com.example.inventorymaster.data.dto.ConflictItem(
                                        uuid = remoteRecord.uuid ?: "", field = "quantity",
                                        localValue = localRecord.quantity.toString(), serverValue = remoteRecord.quantity.toString()
                                    ))
                                }
                                if (localRecord.actualQuantity != remoteRecord.actualQuantity) {
                                    conflicts.add(com.example.inventorymaster.data.dto.ConflictItem(
                                        uuid = remoteRecord.uuid ?: "", field = "actualQuantity",
                                        localValue = localRecord.actualQuantity.toString(), serverValue = remoteRecord.actualQuantity.toString()
                                    ))
                                }
                                if (localRecord.remarks != remoteRecord.remarks) {
                                    conflicts.add(com.example.inventorymaster.data.dto.ConflictItem(
                                        uuid = remoteRecord.uuid ?: "", field = "remarks",
                                        localValue = localRecord.remarks, serverValue = remoteRecord.remarks
                                    ))
                                }
                                if (localRecord.operator != remoteRecord.operator) {
                                    conflicts.add(com.example.inventorymaster.data.dto.ConflictItem(
                                        uuid = remoteRecord.uuid ?: "", field = "operator",
                                        localValue = localRecord.operator, serverValue = remoteRecord.operator
                                    ))
                                }

                                // 服务端覆盖，保留本地修改痕迹；归属键保留本地值（本地改 key 不被远端旧值回滚）
                                val conflictLog = "server_overwrite: qty=${localRecord.quantity}, actual=${localRecord.actualQuantity}, remarks=${localRecord.remarks}, operator=${localRecord.operator}"
                                val recordToUpdate = StockRecord(
                                    id = localRecord.id,
                                    uuid = remoteRecord.uuid ?: "",
                                    sessionId = context.sessionId,
                                    productKey = localRecord.productKey,
                                    batchNumber = remoteRecord.batchNumber,
                                    expiryDate = remoteRecord.expiryDate,
                                    quantity = remoteRecord.quantity,
                                    actualQuantity = remoteRecord.actualQuantity,
                                    location = remoteRecord.location ?: "",
                                    remarks = remoteRecord.remarks,
                                    isVerified = localRecord.isVerified || remoteRecord.actualQuantity != null || remoteRecord.remarks?.contains("已查验") == true,
                                    lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                    sourceType = remoteRecord.sourceType,
                                    syncStatus = 0,
                                    operator = remoteRecord.operator ?: localRecord.operator,
                                    historyLog = (localRecord.historyLog ?: "") + " | " + conflictLog,
                                    productionDate = remoteRecord.productionDate
                                )
                                stockRecordDao.insertRecord(recordToUpdate)
                            } else {
                                // 本地已有该 uuid：字段按服务端更新，归属键保留本地值
                                val recordToUpdate = StockRecord(
                                    id = localRecord.id,
                                    uuid = remoteRecord.uuid ?: "",
                                    sessionId = context.sessionId,
                                    productKey = localRecord.productKey,
                                    batchNumber = remoteRecord.batchNumber,
                                    expiryDate = remoteRecord.expiryDate,
                                    quantity = remoteRecord.quantity,
                                    actualQuantity = remoteRecord.actualQuantity,
                                    location = remoteRecord.location ?: "",
                                    remarks = remoteRecord.remarks,
                                    isVerified = localRecord.isVerified || remoteRecord.actualQuantity != null || remoteRecord.remarks?.contains("已查验") == true,
                                    lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                    sourceType = remoteRecord.sourceType,
                                    syncStatus = 0,
                                    operator = remoteRecord.operator ?: localRecord.operator,
                                    historyLog = remoteRecord.historyLog ?: localRecord.historyLog ?: "",
                                    productionDate = remoteRecord.productionDate
                                )
                                stockRecordDao.insertRecord(recordToUpdate)
                            }
                        } else {
                            val recordToInsert = StockRecord(
                                id = 0,
                                uuid = remoteRecord.uuid ?: "",
                                sessionId = context.sessionId,
                                // 新记录按产品映射重定向到本地组合键
                                productKey = keyMapping[remoteRecord.productKey] ?: remoteRecord.productKey,
                                batchNumber = remoteRecord.batchNumber,
                                expiryDate = remoteRecord.expiryDate,
                                quantity = remoteRecord.quantity,
                                actualQuantity = remoteRecord.actualQuantity,
                                location = remoteRecord.location ?: "",
                                remarks = remoteRecord.remarks,
                                isVerified = remoteRecord.actualQuantity != null || remoteRecord.remarks?.contains("已查验") == true,
                                lastUpdateTime = remoteRecord.lastUpdateTime ?: 0L,
                                sourceType = remoteRecord.sourceType,
                                syncStatus = 0,
                                operator = remoteRecord.operator ?: "",
                                historyLog = remoteRecord.historyLog,
                                productionDate = remoteRecord.productionDate
                            )
                            stockRecordDao.insertRecord(recordToInsert)
                        }
                    }

                    // 记录冲突摘要到日志
                    if (conflicts.isNotEmpty()) {
                        Log.w("SYNC", "⚠️ 检测到 ${conflicts.size} 个字段冲突:")
                        for (c in conflicts) {
                            Log.w("SYNC", "  uuid=${c.uuid}, field=${c.field}, local=${c.localValue}, server=${c.serverValue}")
                        }
                    }

                    return Result.success("更新了$processedCount 条数据, ${remoteProducts.size} 个产品" + archivedNotice + startedNotice)
                }
                return if (remoteProducts.isNotEmpty() || deletedRecords.isNotEmpty()) {
                    Result.success("更新了 ${remoteProducts.size} 个产品, ${deletedRecords.size} 条删除" + archivedNotice + startedNotice)
                } else {
                    Result.success("已是最新" + archivedNotice + startedNotice)
                }
            } else {
                Result.failure(Exception("下载失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
