package com.example.inventorymaster.data.repository

import android.util.Log
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.PushRequest
import com.example.inventorymaster.data.dto.SessionDto
import com.example.inventorymaster.data.dto.SyncData
import com.example.inventorymaster.data.dto.toDto
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.model.SessionWithProgress
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.data.network.InventoryApiService
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import com.example.inventorymaster.data.sync.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.inventorymaster.data.sync.TimestampManager

/**
 * 库存仓库实现类
 *
 * ⚠️ 重要变更：移除了 SharedPreferences 依赖，所有持久化配置统一通过 SettingsRepository (DataStore) 管理。
 * 包括：服务器 IP 地址、同步时间戳等。
 */
class InventoryRepositoryImpl(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao,
    private val settingsRepository: SettingsRepository // [重构] 用 SettingsRepository 替代 SharedPreferences
, private val timestampManager: TimestampManager) : InventoryRepository {
    // Phase 3: 同步协调器 (策略模式)
    private val syncOrchestrator = SyncOrchestrator().apply {
        register(SyncType.FULL_UPLOAD, FullUploadStrategy(sessionDao, stockRecordDao, productDao))
        register(SyncType.INCREMENTAL_UPLOAD, IncrementalUploadStrategy(
            sessionDao, stockRecordDao, productDao, settingsRepository, timestampManager
        ))
        register(SyncType.FULL_DOWNLOAD, FullDownloadStrategy(sessionDao, stockRecordDao, productDao))
        register(SyncType.INCREMENTAL_DOWNLOAD, IncrementalDownloadStrategy(
            sessionDao, stockRecordDao, productDao, settingsRepository, timestampManager
        ))
    }


    // --- Session ---
    override fun getAllSessions() = sessionDao.getAllSessions()
    override fun getAllSessionsWithProgress(): Flow<List<SessionWithProgress>> {
        return sessionDao.getAllSessionsWithProgress()
    }
    override suspend fun createSession(name: String) { sessionDao.insertSession(
        InventorySession(
            name = name,
            date = System.currentTimeMillis()
        )
    )}
    override suspend fun updateSessionStatus(sessionId: Long, status: Int) = sessionDao.updateStatus(sessionId, status)
    override suspend fun deleteSession(session: InventorySession) = sessionDao.deleteSession(session)

    // --- Records (Read) ---
    override fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>> {
        return stockRecordDao.getRecordsBySession(sessionId)
    }

    override suspend fun searchRecords(sessionId: Long, query: String): List<StockRecordCombined> {
        return stockRecordDao.searchRecordsByQuery(sessionId, query)
    }

    override suspend fun getRecordsByUdi(sessionId: Long, productKey: String, batch: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByUdi(sessionId, productKey, batch)
    }
    override suspend fun getRecordsBybatchorexpiryDate(sessionId: Long, batch: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsBybatchorexpiryDate(sessionId,batch)
    }

    override suspend fun getRecordsByKey(sessionId: Long, productKey: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByKey(sessionId, productKey)
    }

    // --- Records (Write) ---
    override suspend fun saveRecord(record: StockRecord) {
        // 插入记录前确保 ProductBase 中有对应 productKey 的产品
        val existingProduct = productDao.getProductByKey(record.productKey)
        if (existingProduct == null) {
            val placeholder = ProductBase(
                productKey = record.productKey,
                productName = "未知产品(扫码)",
                specification = null, model = null, manufacturer = "未知厂家",
                registrationCert = null, materialCode = null, unit = null, categoryCode = null,
                source = "scan_auto"
            )
            productDao.insertProduct(placeholder)
        }
        stockRecordDao.insertRecord(record)
    }

    override suspend fun updateRecord(record: StockRecord) { stockRecordDao.updateRecord(record)}
    override suspend fun deleteRecord(record: StockRecord) = stockRecordDao.deleteRecord(record)

    // --- Excel Import (核心) ---
    override suspend fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>) {
        saveImportDataWithResolution(products,records)
    }

    override suspend fun getUnverifiedCount(sessionId: Long) = stockRecordDao.getUnverifiedCount(sessionId)
    override suspend fun getExportData(sessionId: Long) = stockRecordDao.getExportData(sessionId)

    override suspend fun getProductByKey(productKey: String) = productDao.getProductByKey(productKey)
    override suspend fun searchProducts(query: String) = productDao.searchProducts(query)
    override suspend fun insertProduct(product: ProductBase) = productDao.insertProduct(product)
    override suspend fun updateProduct(product: ProductBase) { productDao.updateProduct(product) }
    override suspend fun getAllProducts() = productDao.getAllProducts()
    override suspend fun saveProductsOnly(products: List<ProductBase>) {
        for (prod in products) {
            val cleanKey = prod.productKey.trim()
            val safeProd = prod.copy(productKey = cleanKey)
            val existing = productDao.getProductByKey(cleanKey)
            if (existing != null) productDao.updateProduct(safeProd)
            else productDao.insertProduct(safeProd)
        }
    }
    override suspend fun deleteProductByKey(productKey: String) {
        try {
            productDao.deleteProductByKey(productKey)
        } catch (e: Exception) {
            throw Exception("产品 $productKey 有关联的库存记录，无法删除")
        }
    }
    override suspend fun deleteProductsByKey(productKeyList: List<String>) {
        try {
            productDao.deleteProductsByKey(productKeyList)
        } catch (e: Exception) {
            throw Exception("所选产品中有关联库存记录的，无法删除")
        }
    }

    override suspend fun checkProductConflicts(newProducts: List<ProductBase>): List<ProductConflict> {
        val conflictList = mutableListOf<ProductConflict>()
        val uniqueNewProducts = newProducts.distinctBy { it.productKey.trim() }

        for (newProd in uniqueNewProducts) {
            val cleanKey = newProd.productKey.trim()
            val oldProd = productDao.getProductByKey(cleanKey)

            if (oldProd != null) {
                val isDiff = (oldProd.productName.trim() != newProd.productName.trim()) ||
                        ((oldProd.specification ?: "").trim() != (newProd.specification ?: "").trim()) ||
                        ((oldProd.manufacturer ?: "").trim() != (newProd.manufacturer ?: "").trim())

                if (isDiff) {
                    conflictList.add(
                        ProductConflict(
                            newProd.productKey,
                            oldProd,
                            newProd,
                            ConflictAction.PENDING
                        )
                    )
                }
            }
        }
        return conflictList
    }

    override suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>) {
        // 第一步：保存产品 (ProductBase)
        for (prod in finalProducts) {
            val cleanKey = prod.productKey.trim()
            val safeProd = prod.copy(productKey = cleanKey)

            val existingProd = productDao.getProductByKey(cleanKey)
            if (existingProd != null) {
                productDao.updateProduct(safeProd)
                Log.d("DEBUG_SAVE", "更新产品: $cleanKey")
            } else {
                productDao.insertProduct(safeProd)
                Log.d("DEBUG_SAVE", "新增产品: $cleanKey")
            }
        }

        // 第二步：保存库存记录 (StockRecord) + 孤儿救助
        for (record in records) {
            val cleanKey = record.productKey.trim()
            val cleanBatch = record.batchNumber.trim()
            val cleanLoc = record.location.trim()

            val fatherProduct = productDao.getProductByKey(cleanKey)

            if (fatherProduct == null) {
                Log.w("DEBUG_SAVE", "⚠️ 发现缺失产品信息: $cleanKey，自动补全...")
                val dummyProduct = ProductBase(
                    productKey = cleanKey,
                    productName = "未录入产品 ($cleanKey)",
                    manufacturer = "Excel导入自动生成",
                    specification = "-",
                    categoryCode = "",
                    materialCode = "",
                    registrationCert = "",
                    unit = "",
                    model = "",
                )
                productDao.insertProduct(dummyProduct)
            }

            val existingRecord = stockRecordDao.findExistingRecord(
                sessionId = record.sessionId,
                productKey = cleanKey,
                batch = cleanBatch,
                location = cleanLoc
            )

            if (existingRecord != null) {
                val recordToUpdate = existingRecord.copy(
                    quantity = record.quantity,
                    expiryDate = record.expiryDate,
                    remarks = record.remarks
                )
                stockRecordDao.updateRecord(recordToUpdate)
                Log.d("DEBUG_STOCK", "覆盖更新记录: $cleanKey")
            } else {
                val newRecord = record.copy(
                    productKey = cleanKey,
                    batchNumber = cleanBatch,
                    location = cleanLoc
                )
                stockRecordDao.insertRecord(newRecord)
                Log.d("DEBUG_STOCK", "插入新记录: $cleanKey")
            }
        }
    }

    // ============================================================
    // 🆕 IP 地址存储（已从 SharedPreferences 迁移至 DataStore）
    // ============================================================

    /**
     * 保存服务器 IP 地址
     * 委托给 SettingsRepository，统一使用 DataStore 存储
     */
    override suspend fun saveServerIp(ip: String) {
        settingsRepository.saveServerIp(ip)
    }

    /**
     * 读取服务器 IP 地址
     * 委托给 SettingsRepository，统一使用 DataStore 存储
     */
    override suspend fun getServerIp(): String {
        return settingsRepository.getServerIp()
    }

    // 全量上传
        override suspend fun exportFullSession(ip: String, sessionId: Long): Result<String> {
        return syncOrchestrator.execute(SyncType.FULL_UPLOAD, SyncContext(ip, sessionId))
    }
override suspend fun pushUnsyncedData(sessionId: Long): Result<String> {
        val ip = settingsRepository.getServerIp()
        return syncOrchestrator.execute(SyncType.INCREMENTAL_UPLOAD, SyncContext(ip, sessionId))
    }
    override suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String> {
        return syncOrchestrator.execute(SyncType.FULL_DOWNLOAD, SyncContext(ip, sessionId))
    }
    override suspend fun pullNewData(sessionId: Long): Result<String> {
        val ip = settingsRepository.getServerIp()
        return syncOrchestrator.execute(SyncType.INCREMENTAL_DOWNLOAD, SyncContext(ip, sessionId))
    }
override suspend fun fetchCloudSessions(ip: String): Result<List<SessionDto>> {
        return try {
            val api = InventoryApiService.Companion.create(ip)
            val response = api.getSessionList()

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("获取列表失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ============================================================
    // 🆕 Python 本地网关 (端口 8000) 业务对接
    // ============================================================
    override suspend fun fetchPythonTaskList(): Result<List<com.example.inventorymaster.data.network.TaskSummary>> {
        return try {
            val ip = settingsRepository.getServerIp()
            if (ip.isBlank()) return Result.failure(Exception("未设置服务器 IP"))

            val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)
            val response = api.getTaskList()

            if (response.isSuccessful && response.body()?.status == "success") {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("拉取列表失败: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchPythonTaskDetail(documentId: String): Result<com.example.inventorymaster.batchscanner.TargetDocument> {
        return try {
            val ip = settingsRepository.getServerIp()
            val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)
            val response = api.getTaskDetail(documentId)

            if (response.isSuccessful && response.body()?.status == "success") {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("获取单据明细失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadPythonTaskPdf(documentIds: String, file: java.io.File): Result<String> {
        return try {
            val ip = settingsRepository.getServerIp()
            val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)

            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
            val idBody = documentIds.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadPdfResult(idBody, body)
            if (response.isSuccessful && response.body()?.status == "success") {
                Result.success("归档成功")
            } else {
                Result.failure(Exception("归档失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
