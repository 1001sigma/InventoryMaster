package com.example.inventorymaster.data

import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.StockRecordDto
import com.example.inventorymaster.data.dto.SyncData
import com.example.inventorymaster.data.dto.toDto
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.data.network.InventoryApiService
import kotlinx.coroutines.flow.Flow

class InventoryRepositoryImpl(
    private val sessionDao: SessionDao,
    private val stockRecordDao: StockRecordDao,
    private val productDao: ProductDao // [新增] 必须注入 ProductDao
) : InventoryRepository {

    // --- Session ---
    override fun getAllSessions() = sessionDao.getAllSessions()
    override suspend fun createSession(name: String) { sessionDao.insertSession(InventorySession(name = name, date = System.currentTimeMillis()))}
    override suspend fun updateSessionStatus(sessionId: Long, status: Int) = sessionDao.updateStatus(sessionId, status)
    override suspend fun deleteSession(session: InventorySession) = sessionDao.deleteSession(session)

    // --- Records (Read) ---
    override fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>> {
        return stockRecordDao.getRecordsBySession(sessionId)
    }

    override suspend fun searchRecords(sessionId: Long, query: String): List<StockRecordCombined> {
        return stockRecordDao.searchRecordsByQuery(sessionId, query)
    }

    override suspend fun getRecordsByUdi(sessionId: Long, di: String, batch: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByUdi(sessionId, di, batch)
    }
    //临时接口实列化
    override suspend fun getRecordsBybatchorexpiryDate(sessionId: Long, batch: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsBybatchorexpiryDate(sessionId,batch)
    }

    override suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined> {
        return stockRecordDao.getRecordsByDi(sessionId, di)
    }

    // --- Records (Write) ---
    override suspend fun saveRecord(record: StockRecord) {
        // [防御性编程] 插入记录前，先确保 ProductBase 有这个 DI 的空壳数据
        // 否则外键约束会报错！
        val existingProduct = productDao.getProductByDi(record.di)
        if (existingProduct == null) {
            // 如果字典里没有，自动创建一个“未知产品”占位，防止崩溃
            val placeholder = ProductBase(
                di = record.di,
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
        // 1. 先更新字典库 (如果有新的产品资料，自动补全；旧的覆盖)
        //productDao.insertProducts(products)
        // 2. 再更新库存账本 (覆盖当前 Session 的数据)
        //stockRecordDao.overwriteSessionData(sessionId, records)
        saveImportDataWithResolution(products,records)
    }

    override suspend fun getUnverifiedCount(sessionId: Long) = stockRecordDao.getUnverifiedCount(sessionId)
    override suspend fun getExportData(sessionId: Long) = stockRecordDao.getExportData(sessionId)

    override suspend fun getProductByDi(di: String) = productDao.getProductByDi(di)
    override suspend fun searchProducts(query: String) = productDao.searchProducts(query)
    override suspend fun insertProduct(product: ProductBase) = productDao.insertProduct(product)
    override suspend fun updateProduct(product: ProductBase) { productDao.updateProduct(product) }

    override suspend fun checkProductConflicts(newProducts: List<ProductBase>): List<ProductConflict> {
        val conflictList = mutableListOf<ProductConflict>()
        val uniqueNewProducts = newProducts.distinctBy { it.di.trim() }

        for (newProd in uniqueNewProducts) {
            // 查旧数据
            val cleanDi = newProd.di.trim()
            val oldProd = productDao.getProductByDi(cleanDi)

            if (oldProd != null) {
                // 比对核心字段 (名称、规格、厂家)
                // 注意：这里使用了 trim() 防止 Excel 里带空格导致的误判
                val isDiff = (oldProd.productName.trim() != newProd.productName.trim()) ||
                        ((oldProd.specification ?: "").trim() != (newProd.specification ?: "").trim()) ||
                        ((oldProd.manufacturer ?: "").trim() != (newProd.manufacturer ?: "").trim())

                if (isDiff) {
                    // 默认策略：让用户自己选，或者你可以默认设为 IGNORE
                    conflictList.add(ProductConflict(newProd.di, oldProd, newProd, ConflictAction.PENDING))
                }
            }
        }
        return conflictList
    }

    override suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>) {
        // =================================================
        // 第一步：保存 Excel 里那些信息齐全的产品 (ProductBase)
        // =================================================
        for (prod in finalProducts) {
            val cleanDi = prod.di.trim()
            val safeProd = prod.copy(di = cleanDi) // 确保 DI 无空格

            val existingProd = productDao.getProductByDi(cleanDi)
            if (existingProd != null) {
                // 数据库已有 -> 走 Update (防止外键冲突闪退)
                productDao.updateProduct(safeProd)
                android.util.Log.d("DEBUG_SAVE", "更新产品: $cleanDi")
            } else {
                // 数据库没有 -> 走 Insert
                productDao.insertProduct(safeProd)
                android.util.Log.d("DEBUG_SAVE", "新增产品: $cleanDi")
            }
        }

        // =================================================
        // 第二步：保存库存记录 (StockRecord) + 🚑 孤儿救助机制
        // =================================================
        for (record in records) {
            // 1. 数据清洗
            val cleanDi = record.di.trim()
            val cleanBatch = record.batchNumber.trim()
            val cleanLoc = record.location.trim()

            // 🚑🚑🚑【关键补丁】孤儿救助行动 🚑🚑🚑
            // 在插入记录前，一定要确认“爸爸”(产品) 是否存在！
            val fatherProduct = productDao.getProductByDi(cleanDi)

            if (fatherProduct == null) {
                // 😱 发现孤儿！Excel 里有这条记录，但产品库里竟然没这个 DI。
                // 这会导致外键崩溃。所以，我们立刻给它造一个“临时爸爸”。
                android.util.Log.w("DEBUG_SAVE", "⚠️ 发现缺失产品信息的记录: $cleanDi，正在自动补全...")

                val dummyProduct = ProductBase(
                    di = cleanDi,
                    productName = "未录入产品 ($cleanDi)", // 临时名称
                    manufacturer = "Excel导入自动生成",
                    specification = "-",
                    // ✅ 补全报错提示缺少的字段 (给它们赋空字符串即可)
                    categoryCode = "",      // 对应 No value passed for categoryCode
                    materialCode = "",      // 对应 No value passed for materialCode
                    registrationCert = "",  // 对应 No value passed for registrationCert
                    unit = "",             // 对应 No value passed for unit (给个默认单位)
                    model = "",            // 对应 No value passed for model
                )
                // 强行插入这个临时产品，确保外键约束满足
                productDao.insertProduct(dummyProduct)
            }
            // 🚑🚑🚑【补丁结束】现在安全了 🚑🚑🚑


            // 2. 查重逻辑 (防止数据越导越多)
            val existingRecord = stockRecordDao.findExistingRecord(
                sessionId = record.sessionId,
                di = cleanDi,
                batch = cleanBatch,
                location = cleanLoc
            )

            if (existingRecord != null) {
                // === 情况 A: 找到了旧记录 -> 覆盖更新 ===
                // 核心原则：更新 Excel 里的账面数，但死保用户的实盘数 (actualQuantity)
                val recordToUpdate = existingRecord.copy(
                    quantity = record.quantity,   // 更新账面数量
                    expiryDate = record.expiryDate, // 更新效期
                    remarks = record.remarks      // 更新备注
                    // ⚠️ 绝对不碰 actualQuantity
                )
                stockRecordDao.updateRecord(recordToUpdate)
                android.util.Log.d("DEBUG_STOCK", "覆盖更新记录: $cleanDi")
            } else {
                // === 情况 B: 纯新记录 -> 插入 ===
                val newRecord = record.copy(
                    di = cleanDi,
                    batchNumber = cleanBatch,
                    location = cleanLoc
                )
                stockRecordDao.insertRecord(newRecord)
                android.util.Log.d("DEBUG_STOCK", "插入新记录: $cleanDi")
            }
        }
    }

    // 全量上传
    override suspend fun exportFullSession(ip: String, sessionId: Long): Result<String> {
        return try {
            // 1. 获取 Session 信息 (核心新增)
            // 注意：因为 sessionDao.getSessionById 可能返回 Flow 或 Entity，
            // 建议在 DAO 里加一个 suspend fun getSessionEntityById(id): InventorySession?
            // 这里假设你能通过某种方式拿到 Entity。
            // 如果你的 DAO 只有 Flow，可以用 .first()
            val sessionEntity = sessionDao.getSessionById(sessionId)
                ?: return Result.failure(Exception("找不到 Session $sessionId"))

            val sessionDto = com.example.inventorymaster.data.dto.SessionDto(
                id = sessionEntity.id,
                name = sessionEntity.name,
                date = sessionEntity.date,
                status = sessionEntity.status,
                isLocked = sessionEntity.isLocked
            )

            // 2. 获取数据 (保持不变)
            val combinedList = stockRecordDao.getExportData(sessionId)
            if (combinedList.isEmpty()) {
                return Result.failure(Exception("当前盘点任务没有任何数据"))
            }

            val recordDtoList = combinedList.map { it.record.toDto() }

            // 2. 提取并转换产品 (利用 Kotlin 链式调用，自动去重)
            // 逻辑：拿出所有非空产品 -> 根据 DI 去重 -> 转成 DTO
            val productDtoList = combinedList
                .mapNotNull { it.product } // 过滤掉没有产品资料的记录
                .distinctBy { it.di }      // 根据 DI 去重 (替代了原来的 map.containsKey 判断)
                .map { it.toDto() }        // 转成 DTO

            // 3. 打包 (现在放入 session)
            val syncData = SyncData(
                session = sessionDto, // 👈 放入 Session
                products = productDtoList,
                records = recordDtoList
            )

            // 4. 发送
            val api = InventoryApiService.create(ip)
            val response = api.uploadData(syncData)

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
    //同步上传（增量上传）
    override suspend fun uploadSessionData(ip: String, sessionId: Long): Result<String> {
        return try {
            // 1. 先去数据库找，有没有 sync_status = 1 的数据
            val unsyncedRecords = stockRecordDao.getUnsyncedRecords(sessionId)

            if (unsyncedRecords.isEmpty()) {
                return Result.success("没有需要同步的数据")
            }
            // 2. (因为 IP 是动态的)
            // 假设电脑端监听端口是 8080，如果不是请修改InventoryApiService文件
            val api = InventoryApiService.create(ip)
            // 3. 发送数据
            val response = api.pushData(unsyncedRecords)

            // 4. 处理结果
            if (response.isSuccessful) {
                // ✅ 电脑说收到了
                // 提取上传成功的 ID 列表
                val successIds = unsyncedRecords.map { it.id }

                // ⚠️ 关键动作：把本地状态改为“已同步 (0)”，防止下次重复传
                stockRecordDao.markRecordsAsSynced(successIds)

                Result.success("成功同步 ${successIds.size} 条记录")
            } else {
                // ❌ 电脑报错
                Result.failure(Exception("服务器错误: ${response.code()} ${response.message()}"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // ❌ 网络不通
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    //全量下载
    override suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String> {
        return try {
            val api = InventoryApiService.create(ip)
            val response = api.downloadData(sessionId)

            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("下载失败: ${response.code()}"))
            }

            val data = response.body()!!

            // 1. 保存/更新 Session (核心修复)
            // 直接使用服务器传回来的完美数据
            val serverSession = data.session
            val sessionEntity = InventorySession(
                id = serverSession.id,
                name = serverSession.name,
                date = serverSession.date,
                status = serverSession.status,
                isLocked = serverSession.isLocked
            )
            // 插入数据库 (如果 ID 存在则替换)
            sessionDao.insertSession(sessionEntity)


            // 2. 保存产品 (保持不变)
            for (pDto in data.products) {
                if (productDao.getProductByDi(pDto.di) == null) {
                    productDao.insertProduct(ProductBase(
                        di = pDto.di, productName = pDto.productName,
                        specification = pDto.specification?:"", model = pDto.model?:"",
                        manufacturer = pDto.manufacturer?:"", materialCode = pDto.materialCode?:"",
                        unit = pDto.unit?:"", categoryCode = pDto.categoryCode?:"", registrationCert = pDto.registrationCert?:""
                    ))
                }
            }

            // 3. 保存记录 (保持不变：先删后插)
            stockRecordDao.deleteRecordsBySession(sessionId)

            val newRecords = data.records.map { rDto ->
                StockRecord(
                    sessionId = rDto.sessionId,
                    di = rDto.di,
                    batchNumber = rDto.batchNumber,
                    expiryDate = rDto.expiryDate,
                    quantity = rDto.quantity,
                    actualQuantity = rDto.actualQuantity,
                    location = rDto.location,
                    remarks = rDto.remarks,
                    sourceType = 2
                )
            }
            for (record in newRecords) {
                stockRecordDao.insertRecord(record)
            }

            Result.success("同步成功！任务：${serverSession.name}")

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥 [核心实现] 下载/同步数据
    override suspend fun downloadFromPC(ip: String, sessionId: Long): Result<String> {
        return try {
            val api = InventoryApiService.create(ip)

            // 2. 发起请求
            val response = api.pullData(sessionId)

            if (response.isSuccessful) {
                val remoteRecords = response.body()
                if (remoteRecords.isNullOrEmpty()) {
                    return Result.success("服务端没有新数据")
                }

                // 3. 处理下载的数据
                // 这里有一个关键点：电脑传回来的数据，sync_status 应该是 0 (已同步)
                // 但为了保险，我们可以强制设为 0，防止手机拿到后又以为是自己改的，再传回去形成死循环
                val cleanRecords = remoteRecords.map { it.copy(syncStatus = 0) }

                // 4. 保存进数据库 (覆盖更新)
                stockRecordDao.insertRecords(cleanRecords)

                Result.success("成功同步 ${cleanRecords.size} 条数据")
            } else {
                Result.failure(Exception("下载失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    //实现拉取列表
    override suspend fun fetchCloudSessions(ip: String): Result<List<com.example.inventorymaster.data.dto.SessionDto>> {
        return try {
            val api = InventoryApiService.create(ip)
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
}