package com.example.inventorymaster.data.sync

import android.util.Log
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.PushRequest
import com.example.inventorymaster.data.dto.toDto
import com.example.inventorymaster.data.network.InventoryApiService

/**
 * 增量上传策略：将本地 sync_status=1 的脏数据推送到服务端，
 * 并根据服务端返回的逐条结果（Phase 1）只标记成功记录为已同步。
 * 服务端地址由调用方经 ServerConnectionManager 解析后放入 [SyncContext.ip]。
 */
class IncrementalUploadStrategy(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao,
    private val timestampManager: TimestampManager
) : SyncStrategy {

    override suspend fun execute(context: SyncContext): Result<String> {
        return try {
            val dirtyRecords = stockRecordDao.getDirtyRecords(context.sessionId)

            if (dirtyRecords.isEmpty()) {
                return Result.success("没有需要同步的数据")
            }

            val session = sessionDao.getSessionById(context.sessionId)
                ?: return Result.failure(Exception("找不到对应的任务信息"))
            val currentUuid = session.uuid

            val distinctKeys = dirtyRecords.map { it.productKey }.distinct()
            val relatedProducts = mutableListOf<ProductDto>()
            for (key in distinctKeys) {
                val product = productDao.getProductByKey(key)
                if (product != null) {
                    relatedProducts.add(product.toDto())
                }
            }

            val pushPackage = PushRequest(
                records = dirtyRecords.map { it.toDto() },
                products = relatedProducts
            )

            val api = InventoryApiService.Companion.create(context.ip)
            val retryPolicy = RetryPolicy()
            val response = retryPolicy.execute { api.pushData(
                sessionUuid = currentUuid,
                data = pushPackage
            ) }

            if (response.isSuccessful) {
                val responseBody = response.body()
                val results = responseBody?.get("results") as? List<Map<String, Any>>

                if (results != null) {
                    val uuidToLocalId = mutableMapOf<String, Long>()
                    for (record in dirtyRecords) {
                        record.uuid?.let { uuidToLocalId[it] = record.id }
                    }

                    val successIds = mutableListOf<Long>()
                    val failedUuids = mutableListOf<String>()

                    for (result in results) {
                        val status = result["status"] as? String ?: "failed"
                        val uuid = result["uuid"] as? String
                        if (status == "inserted" || status == "updated") {
                            uuid?.let { uuidToLocalId[it]?.let { successIds.add(it) } }
                        } else {
                            uuid?.let { failedUuids.add(it) }
                            Log.w("SYNC", "服务端处理失败: uuid=$uuid, reason=${result["reason"]}")
                        }
                    }

                    if (successIds.isNotEmpty()) {
                        stockRecordDao.markAsSynced(successIds)
                    }
                    // M0：推送锚点已移除（push 无游标，脏标记 syncStatus 即真相；时间戳由服务端盖章）

                    val message = if (failedUuids.isEmpty()) {
                        "成功同步 ${successIds.size} 条记录"
                    } else {
                        "成功同步 ${successIds.size} 条, ${failedUuids.size} 条失败"
                    }
                    Result.success(message)
                } else {
                    // 兼容旧版本服务器
                    val successIds = dirtyRecords.map { it.id }
                    stockRecordDao.markAsSynced(successIds)
                    Result.success("成功同步 ${successIds.size} 条记录(兼容)")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                Log.e("SYNC_ERROR", "上传失败 code=${response.code()}, body=$errorBody")
                Result.failure(Exception("服务器错误: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SYNC_ERROR", "连接异常", e)
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }
}
