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
import com.example.inventorymaster.data.dto.mergeInto
import com.example.inventorymaster.data.dto.toDto
import com.example.inventorymaster.data.dto.toEntity
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.model.SessionWithProgress
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.connection.ServerConnectionManager
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.data.model.ProductImportItem
import com.example.inventorymaster.data.model.ProductDeleteResult
import com.example.inventorymaster.data.network.InventoryApiService
import com.example.inventorymaster.data.network.CollabApiException
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import com.example.inventorymaster.data.sync.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.inventorymaster.data.sync.TimestampManager
import com.example.inventorymaster.data.AppDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val settingsRepository: SettingsRepository,
    private val timestampManager: TimestampManager,
    private val database: AppDatabase,
    private val connectionManager: ServerConnectionManager
) : InventoryRepository {

    init {
        // 启动时加载 token 与设备身份到 API 服务
        kotlinx.coroutines.runBlocking {
            val token = settingsRepository.getServerToken()
            if (token.isNotBlank()) {
                InventoryApiService.setToken(token)
            }
            val deviceUuid = settingsRepository.ensureDeviceUuid()  // M1：首次生成并永久保存
            InventoryApiService.setDeviceUuid(deviceUuid)
        }
    }

    // Phase 3: 同步协调器 (策略模式)
    private val syncOrchestrator = SyncOrchestrator().apply {
        register(SyncType.FULL_UPLOAD, FullUploadStrategy(sessionDao, stockRecordDao, productDao))
        register(SyncType.INCREMENTAL_UPLOAD, IncrementalUploadStrategy(
            sessionDao, stockRecordDao, productDao, timestampManager
        ))
        register(SyncType.FULL_DOWNLOAD, FullDownloadStrategy(sessionDao, stockRecordDao, productDao))
        register(SyncType.INCREMENTAL_DOWNLOAD, IncrementalDownloadStrategy(
            sessionDao, stockRecordDao, productDao, timestampManager
        ))
    }


    // --- Session ---
    override fun getAllSessions() = sessionDao.getAllSessions()
    override fun getAllSessionsWithProgress(): Flow<List<SessionWithProgress>> {
        return sessionDao.getAllSessionsWithProgress()
    }
    override suspend fun createSession(name: String): Long = sessionDao.insertSession(
        InventorySession(
            name = name,
            date = System.currentTimeMillis()
        )
    )
    override suspend fun updateSessionStatus(sessionId: Long, status: Int) = sessionDao.updateStatus(sessionId, status)
    override suspend fun deleteSession(session: InventorySession) = sessionDao.deleteSession(session)
    override suspend fun getSessionById(sessionId: Long): InventorySession? = sessionDao.getSessionById(sessionId)
    override suspend fun getAllSessionsOnce(): List<InventorySession> = sessionDao.getAllSessions().first()
    override suspend fun hasOngoingSessions(): Boolean = sessionDao.countOngoingSessions() > 0

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

    override suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByDi(sessionId, di)
    }

    override suspend fun getRecordsByDiAndBatch(sessionId: Long, di: String, batch: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByDiAndBatch(sessionId, di, batch)
    }

    override suspend fun getRecordsByKeyOrDiOrMat(sessionId: Long, query: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByKeyOrDiOrMat(sessionId, query)
    }

    // --- Records (Write) ---
    override suspend fun saveRecord(record: StockRecord) {
        // 归一化记录 key：组合键 "DI|MAT" 原样保留，裸输入视为 DI（与扫码行为一致）
        val (di, mat) = ProductBase.parseProductKey(record.productKey)
        val normalizedKey = ProductBase.computeProductKey(di, mat)
        val normalizedRecord =
            if (normalizedKey != record.productKey) record.copy(productKey = normalizedKey) else record

        // 插入记录前确保 ProductBase 中有对应 productKey 的产品
        val existingProduct = productDao.getProductByKey(normalizedKey)
        if (existingProduct == null) {
            val placeholder = ProductBase(
                productKey = normalizedKey,
                di = di.ifBlank { null },
                materialCode = mat.ifBlank { null },
                productName = "未知产品(扫码)",
                specification = null, model = null, manufacturer = "未知厂家",
                registrationCert = null, unit = null, categoryCode = null,
                source = "scan_auto"
            )
            productDao.insertProduct(placeholder)
        }
        stockRecordDao.insertRecord(normalizedRecord)
    }

    override suspend fun updateRecord(record: StockRecord) { stockRecordDao.updateRecord(record)}
    override suspend fun getRecordById(id: Long): StockRecord? = stockRecordDao.getRecordById(id)
    override suspend fun deleteRecord(record: StockRecord) {
        stockRecordDao.deleteRecord(record)
        // 尝试通知服务端删除事件（best effort，连接失败静默处理，不弹发现弹窗）
        if (record.uuid.isBlank()) return
        try {
            val session = sessionDao.getSessionById(record.sessionId) ?: return
            connectionManager.withServer(userInitiated = false) { ip ->
                val api = InventoryApiService.Companion.create(ip)
                val deleteRequest = com.example.inventorymaster.data.dto.DeleteRequest(
                    recordUuids = listOf(record.uuid),
                    sessionUuid = session.uuid
                )
                api.deleteRecords(session.uuid, deleteRequest)
            }
        } catch (e: Exception) {
            // 服务端不可达时静默失败，下次同步时记录会通过增量拉取同步
        }
    }

    // --- Excel Import (核心) ---
    override suspend fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>) {
        saveImportDataWithResolution(products,records)
    }

    override suspend fun getUnverifiedCount(sessionId: Long) = stockRecordDao.getUnverifiedCount(sessionId)
    override suspend fun getExportData(sessionId: Long) = stockRecordDao.getExportData(sessionId)
    override suspend fun getSessionRecords(sessionId: Long) = stockRecordDao.getExportData(sessionId)

    override suspend fun getProductByKey(productKey: String) = productDao.getProductByKey(productKey)
    override suspend fun getProductsByDi(di: String) = productDao.getProductsByDi(di)
    override suspend fun getProductsByMaterialCode(materialCode: String) = productDao.getProductsByMaterialCode(materialCode)
    override suspend fun searchProducts(query: String) = productDao.searchProducts(query)
    override suspend fun insertProduct(product: ProductBase) {
        productDao.insertProduct(product)
        ProductMergeHelper.promoteAndMergeIncompleteProducts(productDao, stockRecordDao, product)
    }
    override suspend fun updateProduct(product: ProductBase) {
        productDao.updateProduct(product)
        ProductMergeHelper.promoteAndMergeIncompleteProducts(productDao, stockRecordDao, product)
    }
    override suspend fun migrateProductKey(oldKey: String, newProduct: ProductBase): Result<String> {
        val newKey = newProduct.productKey
        if (oldKey == newKey) {
            productDao.updateProduct(newProduct)
            return Result.success("无变更")
        }
        val collision = productDao.getProductByKey(newKey)
        if (collision != null) {
            return Result.failure(Exception("关联键 $newKey 已被其他产品使用"))
        }
        stockRecordDao.updateProductKeyForRecords(oldKey, newKey)
        productDao.deleteProductByKey(oldKey)
        productDao.insertProduct(newProduct)
        return Result.success("关联键已从 $oldKey 更新为 $newKey")
    }
    override suspend fun getAllProducts() = productDao.getAllProducts()
    override suspend fun getProductCount() = productDao.getProductCount()

    /**
     * 智能晋升合并：导入/新增完整产品时，若存在同 DI 或同物料编码的过渡半成品，
     * 级联平移其名下的库存记录并删除过渡条目。
     */
    private suspend fun mergeScanPlaceholder(newProduct: ProductBase) {
        ProductMergeHelper.promoteAndMergeIncompleteProducts(productDao, stockRecordDao, newProduct)
    }

    /**
     * 组合键未命中后的降级匹配：分别按 DI、物料编码查产品库，
     * 仅当双方字段"非空且不同"才视为不兼容（空视为通配），优先返回带 DI 的候选。
     */
    private suspend fun findCompatibleProduct(di: String?, matCode: String?): ProductBase? {
        val byDi = di?.takeIf { it.isNotBlank() }
            ?.let { productDao.getProductsByDi(it) }
            .orEmpty()
            .filter { it.materialCode.isNullOrBlank() || matCode.isNullOrBlank() || it.materialCode == matCode }
        val byMat = matCode?.takeIf { it.isNotBlank() }
            ?.let { productDao.getProductsByMaterialCode(it) }
            .orEmpty()
            .filter { it.di.isNullOrBlank() || di.isNullOrBlank() || it.di == di }
        return (byDi + byMat).firstOrNull { !it.di.isNullOrBlank() }
            ?: (byDi + byMat).firstOrNull()
    }

    override suspend fun saveProductsOnly(importItems: List<ProductImportItem>) {
        for (item in importItems) {
            val oldKey = item.oldKey
            val newProduct = item.newProduct
            val newKey = newProduct.productKey

            if (oldKey != newKey) {
                // 【核心平移逻辑】组合键变更 (例如补充了 DI 或物料编码)
                Log.d("DEBUG_SYNC", "主键转移: $oldKey -> $newKey")
                // 0. 碰撞检查：newKey 已被其他产品占用时拒绝平移，防止两条产品合并
                val collision = productDao.getProductByKey(newKey)
                if (collision != null) {
                    Log.w("DEBUG_SYNC", "主键转移被拒绝：$newKey 已被其他产品占用")
                    continue
                }
                // 1. 将原依附于 oldKey 的库存记录转移给 newKey
                stockRecordDao.updateProductKeyForRecords(oldKey, newKey)
                // 2. 插入新产品
                productDao.insertProduct(newProduct)
                // 3. 删除旧字典记录 (现在它是幽灵数据了)
                productDao.deleteProductByKey(oldKey)
            } else {
                // 主键没变，常规的覆盖更新
                val existing = productDao.getProductByKey(newKey)
                if (existing != null) {
                    productDao.updateProduct(newProduct)
                } else {
                    productDao.insertProduct(newProduct)
                }
            }
            mergeScanPlaceholder(newProduct)
        }
    }

    override suspend fun deleteProductByKey(productKey: String): Result<Unit> {
        val refCount = stockRecordDao.countRecordsByKey(productKey)
        if (refCount > 0) {
            return Result.failure(Exception("该产品被 $refCount 条库存记录引用，无法删除"))
        }
        return try {
            productDao.deleteProductByKey(productKey)
            if (productDao.getProductCount() == 0) {
                timestampManager.resetProductPullSeq()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("InventoryRepo", "删除产品 $productKey 失败: ${e.message}")
            Result.failure(Exception("删除失败: ${e.message}"))
        }
    }

    override suspend fun deleteProductsByKey(productKeyList: List<String>): ProductDeleteResult {
        val deleted = mutableListOf<String>()
        val blocked = mutableMapOf<String, Int>()
        for (key in productKeyList) {
            val refCount = stockRecordDao.countRecordsByKey(key)
            if (refCount > 0) {
                blocked[key] = refCount
                continue
            }
            try {
                productDao.deleteProductByKey(key)
                deleted.add(key)
            } catch (e: Exception) {
                Log.w("InventoryRepo", "删除产品 $key 失败: ${e.message}")
                blocked[key] = -1
            }
        }
        if (deleted.isNotEmpty() && productDao.getProductCount() == 0) {
            timestampManager.resetProductPullSeq()
        }
        return ProductDeleteResult(deleted, blocked)
    }

    override suspend fun checkProductConflicts(importItems: List<ProductImportItem>): String? {
        var newCount = 0
        var updateCount = 0
        var keyChangedCount = 0 // 发生了 DI/物料编码 变更

        // 参与冲突判定的业务字段
        fun differs(old: ProductBase, new: ProductBase): Boolean =
            old.productName != new.productName ||
                old.specification != new.specification ||
                old.model != new.model ||
                old.manufacturer != new.manufacturer ||
                old.registrationCert != new.registrationCert ||
                old.unit != new.unit ||
                old.categoryCode != new.categoryCode

        for (item in importItems) {
            val newProd = item.newProduct
            val newKey = newProd.productKey
            val oldProd = productDao.getProductByKey(item.oldKey)
                ?: productDao.getProductByKey(newKey)

            when {
                oldProd == null -> newCount++
                item.oldKey != newKey -> {
                    keyChangedCount++
                    updateCount++
                }
                differs(oldProd, newProd) -> updateCount++
                // 组合键相同且业务字段完全一致——不算变更
                else -> {}
            }
        }

        if (newCount == 0 && updateCount == 0) return null

        return "检测到产品变更:\n• 新增产品: $newCount 条\n• 覆盖更新: $updateCount 条" +
                (if (keyChangedCount > 0) "\n\n⚠️ 包含 $keyChangedCount 个系统码变更(将自动迁移关联的库存记录)" else "")
    }


    override suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>): Int {
        // 用于追踪 productKey 变更：原始key → 最终key
        val keyMapping = mutableMapOf<String, String>()
        var insertedCount = 0

        // 第一步：为每行盘点数据解析产品库中的目标产品。
        // 盘点导入不修改产品库：命中已有产品只做挂靠（记录指向该产品），三级都未命中才兜底插入新产品。
        for (prod in finalProducts) {
            val originalKey = prod.productKey.trim()
            val cleanDi = prod.di?.trim()
            val cleanMatCode = prod.materialCode?.trim()

            // 如果 DI 和物料编码都为空，跳过
            if (cleanDi.isNullOrBlank() && cleanMatCode.isNullOrBlank()) {
                Log.w("DEBUG_SAVE", "跳过无效产品（DI和物料编码都为空）")
                continue
            }

            // 匹配链：精确组合键"{DI}|{物料编码}" → DI/物料编码降级匹配
            val exactKey = ProductBase.resolveProductKey(cleanDi, cleanMatCode).first
            val target = productDao.getProductByKey(exactKey)
                ?: findCompatibleProduct(cleanDi, cleanMatCode)

            val finalKey: String
            if (target != null) {
                finalKey = target.productKey
                Log.d("DEBUG_SAVE", "挂靠已有产品: $originalKey → $finalKey")
            } else {
                finalKey = exactKey
                val safeProd = prod.copy(productKey = finalKey, di = cleanDi?.ifBlank { null })
                productDao.insertProduct(safeProd)
                insertedCount++
                ProductMergeHelper.promoteAndMergeIncompleteProducts(productDao, stockRecordDao, safeProd, keyMapping)
                Log.d("DEBUG_SAVE", "未匹配到产品库，兜底新增产品: $finalKey")
            }

            // 记录 key 映射
            if (originalKey != finalKey) {
                keyMapping[originalKey] = finalKey
                Log.d("DEBUG_SAVE", "productKey 变更: $originalKey → $finalKey")
            }
        }

        // 第二步：保存库存记录 (StockRecord) + 孤儿救助
        for (record in records) {
            // 应用 key 映射
            val originalKey = record.productKey.trim()
            val cleanKey = keyMapping[originalKey] ?: originalKey
            val cleanBatch = record.batchNumber.trim()
            val cleanLoc = record.location.trim()

            val fatherProduct = productDao.getProductByKey(cleanKey)

            if (fatherProduct == null) {
                Log.w("DEBUG_SAVE", "⚠️ 发现缺失产品信息: $cleanKey，自动补全...")
                val (orphanDi, orphanMat) = ProductBase.parseProductKey(cleanKey)
                val dummyProduct = ProductBase(
                    productKey = ProductBase.computeProductKey(orphanDi, orphanMat),
                    di = orphanDi.ifBlank { null },
                    materialCode = orphanMat.ifBlank { null },
                    productName = "未录入产品 ($cleanKey)",
                    manufacturer = "Excel导入自动生成",
                    specification = "-",
                    categoryCode = "",
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
        return insertedCount
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

    override suspend fun getServerToken(): String {
        return settingsRepository.getServerToken()
    }

    // 全量上传
        override suspend fun exportFullSession(ip: String, sessionId: Long): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true, preferredIp = ip) { resolvedIp ->
                syncOrchestrator.execute(SyncType.FULL_UPLOAD, SyncContext(resolvedIp, sessionId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun pushUnsyncedData(sessionId: Long, userInitiated: Boolean): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = userInitiated) { resolvedIp ->
                syncOrchestrator.execute(SyncType.INCREMENTAL_UPLOAD, SyncContext(resolvedIp, sessionId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true, preferredIp = ip) { resolvedIp ->
                syncOrchestrator.execute(SyncType.FULL_DOWNLOAD, SyncContext(resolvedIp, sessionId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun pullNewData(sessionId: Long, userInitiated: Boolean): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = userInitiated) { resolvedIp ->
                syncOrchestrator.execute(SyncType.INCREMENTAL_DOWNLOAD, SyncContext(resolvedIp, sessionId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetProductPullSeq() {
        timestampManager.resetProductPullSeq()
    }

    override suspend fun pullProductLibrary(userInitiated: Boolean, forceFull: Boolean): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = userInitiated) { resolvedIp ->
                val api = InventoryApiService.Companion.create(resolvedIp)
                val retryPolicy = RetryPolicy()
                val localCount = productDao.getProductCount()
                val shouldReset = forceFull || localCount == 0
                val lastSeq = if (shouldReset) {
                    timestampManager.resetProductPullSeq()
                    0L
                } else {
                    timestampManager.getLastProductPullSeq()
                }
                val response = retryPolicy.execute { api.pullProducts(lastSeq) }

                if (!response.isSuccessful) {
                    val code = response.code()
                    val errorBody = response.errorBody()?.string() ?: ""
                    return@withServer Result.failure(Exception("拉取产品库失败 (HTTP $code): $errorBody"))
                }

                val pullData = response.body()?.data
                    ?: return@withServer Result.failure(Exception("服务端返回空数据"))

                val remoteProducts = pullData.products
                val maxSeq = pullData.maxSeq

                var updatedCount = 0
                var insertedCount = 0

                for (pDto in remoteProducts) {
                    val (_, isInserted) = ProductMergeHelper.saveOrUpdateSyncedProduct(
                        productDao = productDao,
                        stockRecordDao = stockRecordDao,
                        pDto = pDto,
                        keyMapping = null,
                        updateSyncTime = true
                    )
                    if (isInserted) insertedCount++ else updatedCount++
                }

                if (maxSeq > lastSeq || shouldReset) {
                    timestampManager.saveLastProductPullSeq(maxSeq)
                    Log.d("SYNC", "产品库拉取游标已推进到 changeSeq: $maxSeq")
                }

                val msg = if (remoteProducts.isEmpty()) {
                    "产品库已是最新，无新增或变更"
                } else {
                    "产品库同步完成：新增 $insertedCount 款，更新 $updatedCount 款"
                }
                Result.success(msg)
            }
        } catch (e: Exception) {
            Log.e("SYNC", "同步产品库异常", e)
            Result.failure(e)
        }
    }

    // --- M1/M2 协作：绑定 / 加入 / 归档上报 ---

    private fun parseCollabError(eBody: String?): CollabApiException {
        return try {
            val obj = Gson().fromJson(eBody, JsonObject::class.java)
            CollabApiException(
                obj?.get("code")?.takeIf { it.isJsonPrimitive }?.asString ?: "UNKNOWN",
                obj?.get("detail")?.takeIf { it.isJsonPrimitive }?.asString ?: "操作失败"
            )
        } catch (e: Exception) {
            CollabApiException("UNKNOWN", eBody ?: "操作失败")
        }
    }

    override suspend fun bindOperator(bindingCode: String): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val deviceUuid = settingsRepository.ensureDeviceUuid()
                val resp = InventoryApiService.create(ip).bindOperator(
                    com.example.inventorymaster.data.dto.BindOperatorRequest(
                        bindingCode.trim().uppercase(), deviceUuid, android.os.Build.MODEL
                    )
                )
                if (!resp.isSuccessful) throw parseCollabError(resp.errorBody()?.string())
                val name = (resp.body()?.get("data") as? Map<*, *>)
                    ?.get("operatorName") as? String ?: ""
                settingsRepository.saveOperatorName(name)
                Result.success(name)
            }
        } catch (e: CollabApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinTask(sessionId: Long): Result<Unit> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val deviceUuid = settingsRepository.getDeviceUuid()
                val operatorName = settingsRepository.getOperatorName()
                val resp = InventoryApiService.create(ip).requestJoinTask(
                    com.example.inventorymaster.data.dto.JoinTaskRequestDto(
                        sessionId = sessionId,
                        deviceUuid = deviceUuid,
                        deviceModel = android.os.Build.MODEL,
                        operatorName = operatorName
                    )
                )
                if (!resp.isSuccessful) throw parseCollabError(resp.errorBody()?.string())
                val data = resp.body()?.get("data") as? Map<*, *>
                val status = (data?.get("approvalStatus") as? Number)?.toInt() ?: 0
                if (status != 1) {
                    throw CollabApiException("DEVICE_NOT_APPROVED", "设备尚未在此任务中获得管理员批准")
                }
                Result.success(Unit)
            }
        } catch (e: CollabApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun requestJoinTask(ip: String, sessionId: Long, operatorName: String): Result<com.example.inventorymaster.data.dto.JoinStatusResponse> {
        return try {
            val deviceUuid = settingsRepository.ensureDeviceUuid()
            val api = InventoryApiService.create(ip)
            val resp = api.requestJoinTask(
                com.example.inventorymaster.data.dto.JoinTaskRequestDto(
                    sessionId = sessionId,
                    deviceUuid = deviceUuid,
                    deviceModel = android.os.Build.MODEL,
                    operatorName = operatorName
                )
            )
            if (!resp.isSuccessful) {
                throw parseCollabError(resp.errorBody()?.string())
            }
            val data = resp.body()?.get("data") as? Map<*, *>
            val status = (data?.get("approvalStatus") as? Number)?.toInt() ?: 0
            val sessionName = (data?.get("sessionName") as? String) ?: ""
            val token = data?.get("token") as? String
            Result.success(com.example.inventorymaster.data.dto.JoinStatusResponse(approvalStatus = status, sessionName = sessionName, token = token))
        } catch (e: CollabApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkJoinStatus(ip: String, sessionId: Long): Result<com.example.inventorymaster.data.dto.JoinStatusResponse> {
        return try {
            val deviceUuid = settingsRepository.getDeviceUuid()
            val api = InventoryApiService.create(ip)
            val resp = api.getJoinStatus(sessionId, deviceUuid)
            if (!resp.isSuccessful) {
                throw parseCollabError(resp.errorBody()?.string())
            }
            val data = resp.body()?.get("data") as? Map<*, *>
            val status = (data?.get("approvalStatus") as? Number)?.toInt() ?: 0
            val sessionName = (data?.get("sessionName") as? String) ?: ""
            val token = data?.get("token") as? String
            val msg = data?.get("message") as? String
            Result.success(com.example.inventorymaster.data.dto.JoinStatusResponse(approvalStatus = status, sessionName = sessionName, token = token, message = msg))
        } catch (e: CollabApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSessionByUuid(uuid: String): InventorySession? {
        return sessionDao.getSessionByUuid(uuid)
    }

    override suspend fun reportTaskStatus(sessionUuid: String, localStatus: Int): Result<Unit> {
        return try {
            connectionManager.withServer(userInitiated = false) { ip ->
                val deviceUuid = settingsRepository.getDeviceUuid()
                val resp = InventoryApiService.create(ip).reportTaskStatus(
                    com.example.inventorymaster.data.dto.ReportTaskRequest(
                        sessionUuid, deviceUuid, localStatus,
                        UUID.randomUUID().toString(), System.currentTimeMillis()
                    )
                )
                if (!resp.isSuccessful) throw parseCollabError(resp.errorBody()?.string())
                Result.success(Unit)
            }
        } catch (e: CollabApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 补报离线队列；服务端终态拒绝（任务不存在/未参与）也出队，网络失败保留 */
    override suspend fun reportPendingTaskStatuses(): Result<Int> {
        var done = 0
        for (sessionUuid in settingsRepository.getPendingReports()) {
            val result = reportTaskStatus(sessionUuid, 1)
            if (result.isSuccess || result.exceptionOrNull() is CollabApiException) {
                settingsRepository.removePendingReport(sessionUuid)
                if (result.isSuccess) done++
            }
        }
        return Result.success(done)
    }

    override suspend fun getDirtyRecordsCount(sessionId: Long): Int {
        return stockRecordDao.getDirtyRecords(sessionId).size
    }
override suspend fun fetchCloudSessions(ip: String): Result<List<SessionDto>> {
        return try {
            connectionManager.withServer(userInitiated = true, preferredIp = ip) { resolvedIp ->
                val api = InventoryApiService.Companion.create(resolvedIp)
                val response = api.getSessionList()

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("获取列表失败: ${response.code()}"))
                }
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
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)
                val response = api.getTaskList()

                if (response.isSuccessful && response.body()?.status == "success") {
                    Result.success(response.body()!!.data)
                } else {
                    Result.failure(Exception("拉取列表失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchPythonTaskDetail(documentId: String): Result<com.example.inventorymaster.batchscanner.task.models.TargetDocument> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)
                val response = api.getTaskDetail(documentId)

                if (response.isSuccessful && response.body()?.status == "success") {
                    Result.success(response.body()!!.data)
                } else {
                    Result.failure(Exception("获取单据明细失败"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadPythonTaskPdf(documentIds: String, file: java.io.File): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitUdiData(title: String, barcodes: List<String>): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = com.example.inventorymaster.data.network.TaskApiService.create(ip)
                val response = api.submitUdiGroups(mapOf(title to barcodes))
                if (response.isSuccessful && response.body()?.status == "success") {
                    Result.success("上传成功")
                } else {
                    Result.failure(Exception("上传失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 实时录入 entry_jobs（v1.1）---
    private fun entryApi(ip: String): com.example.inventorymaster.data.network.TaskApiService {
        if (ip.isBlank()) throw Exception("未设置服务器 IP，请先在设置中配置服务器地址")
        return com.example.inventorymaster.data.network.TaskApiService.create(ip)
    }

    /** 提取服务端错误体里的 {"detail": "..."} 中文原因，取不到回退到 body.message */
    private fun errorDetail(response: retrofit2.Response<*>, fallback: String?): String? {
        return try {
            val raw = response.errorBody()?.string()
            if (!raw.isNullOrBlank()) {
                Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(raw)?.groupValues?.get(1) ?: fallback
            } else fallback
        } catch (e: Exception) {
            fallback
        }
    }

    override suspend fun submitEntryJob(
        title: String,
        rawItems: List<String>,
        serialRange: com.example.inventorymaster.data.network.SerialRangeSpec?
    ): Result<Long> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = entryApi(ip)
                val response = api.submitEntryJob(
                    com.example.inventorymaster.data.network.EntryJobSubmitRequest(title, rawItems, serialRange)
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.data != null) {
                    Result.success(body.data.jobId)
                } else {
                    Result.failure(Exception(errorDetail(response, body?.message) ?: "提交失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchActiveEntryJobs(): Result<List<com.example.inventorymaster.data.network.EntryJobDto>> {
        return try {
            connectionManager.withServer(userInitiated = false) { ip ->
                val api = entryApi(ip)
                val response = api.getActiveEntryJobs()
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Result.success(body.data ?: emptyList())
                } else {
                    Result.failure(Exception(body?.message ?: "获取任务失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun appendEntryJob(
        jobId: Long,
        rawItems: List<String>,
        serialRange: com.example.inventorymaster.data.network.SerialRangeSpec?
    ): Result<Int> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = entryApi(ip)
                val response = api.appendEntryJob(
                    jobId, com.example.inventorymaster.data.network.EntryJobAppendRequest(rawItems, serialRange)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.status == "success" && body.data != null) {
                        Result.success(body.data.totalCount)
                    } else {
                        Result.failure(Exception(body?.message ?: "追加失败"))
                    }
                } else if (response.code() == 409) {
                    Result.failure(
                        com.example.inventorymaster.data.network.EntryJobTerminalException("任务已结束，请开新批次")
                    )
                } else {
                    Result.failure(Exception(errorDetail(response, null) ?: "追加失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: com.example.inventorymaster.data.network.EntryJobTerminalException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun abandonEntryJob(jobId: Long): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = entryApi(ip)
                val response = api.abandonEntryJob(jobId)
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Result.success(body.message ?: "任务已终止")
                } else {
                    Result.failure(Exception(body?.message ?: "终止失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkEntryJobDuplicates(rawItems: List<String>): Result<List<com.example.inventorymaster.data.network.EntryJobDuplicate>> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = entryApi(ip)
                val response = api.checkEntryJobDuplicates(
                    com.example.inventorymaster.data.network.EntryJobCheckDupRequest(rawItems)
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Result.success(body.data ?: emptyList())
                } else {
                    Result.failure(Exception(body?.message ?: "查重失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun expandSerialRange(
        template: String,
        startSerial: String,
        endSerial: String
    ): Result<com.example.inventorymaster.data.network.SerialExpandData> {
        return try {
            connectionManager.withServer(userInitiated = true) { ip ->
                val api = entryApi(ip)
                val response = api.expandSerialRange(
                    com.example.inventorymaster.data.network.SerialExpandRequest(
                        com.example.inventorymaster.data.network.SerialRangeSpec(template, startSerial, endSerial)
                    )
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(errorDetail(response, body?.message) ?: "展开失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyAndSaveToken(ip: String, token: String): Result<String> {
        return try {
            connectionManager.withServer(userInitiated = true, preferredIp = ip) { resolvedIp ->
                val api = InventoryApiService.Companion.create(resolvedIp)
                val response = api.verifyToken(token)
                if (response.isSuccessful) {
                    settingsRepository.saveServerToken(token)
                    InventoryApiService.setToken(token)
                    Result.success("认证成功")
                } else {
                    Result.failure(Exception("Token 无效"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveToken(token: String) {
        settingsRepository.saveServerToken(token)
        InventoryApiService.setToken(token)
    }

    // --- AI 结构化查询（v3，构造器见 AiRecordQuery.kt） ---
    override suspend fun queryRecordsForAi(query: AiRecordQuery): String = withContext(Dispatchers.IO) {
        try {
            val db = database.openHelper.readableDatabase

            val total = AiRecordSqlBuilder.buildCount(query).let { built ->
                db.query(SimpleSQLiteQuery(built.sql, built.bindArgs.toTypedArray())).use { c ->
                    if (c.moveToFirst()) c.getLong(0) else 0L
                }
            }

            val rows = JsonArray()
            AiRecordSqlBuilder.build(query).let { built ->
                db.query(SimpleSQLiteQuery(built.sql, built.bindArgs.toTypedArray())).use { c ->
                    val columnNames: Array<String> = c.columnNames
                    while (c.moveToNext()) {
                        val rowObject = JsonObject()
                        for (i in columnNames.indices) {
                            when (c.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> rowObject.addProperty(columnNames[i], c.getLong(i))
                                android.database.Cursor.FIELD_TYPE_FLOAT -> rowObject.addProperty(columnNames[i], c.getDouble(i))
                                android.database.Cursor.FIELD_TYPE_STRING -> rowObject.addProperty(columnNames[i], c.getString(i))
                                else -> rowObject.add(columnNames[i], JsonNull.INSTANCE)
                            }
                        }
                        rows.add(rowObject)
                    }
                }
            }

            JsonObject().apply {
                addProperty("status", "ok")
                addProperty("total", total)
                addProperty("returned", rows.size())
                addProperty("truncated", total > rows.size())
                add("rows", rows)
            }.toString()
        } catch (e: Exception) {
            JsonObject().apply {
                addProperty("status", "error")
                addProperty("message", e.message ?: "未知错误")
            }.toString()
        }
    }
}
