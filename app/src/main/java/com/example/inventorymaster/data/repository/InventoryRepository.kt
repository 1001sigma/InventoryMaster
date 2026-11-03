package com.example.inventorymaster.data.repository

import com.example.inventorymaster.data.dto.SessionDto
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.data.model.ProductConflict
import kotlinx.coroutines.flow.Flow
import com.example.inventorymaster.data.model.SessionWithProgress

interface InventoryRepository {
    // Session 相关
    fun getAllSessions(): Flow<List<InventorySession>>
    fun getAllSessionsWithProgress(): Flow<List<SessionWithProgress>>
    suspend fun createSession(name: String): Long
    suspend fun updateSessionStatus(sessionId: Long, status: Int)
    suspend fun deleteSession(session: InventorySession)
    suspend fun getSessionById(sessionId: Long): InventorySession?
    suspend fun getAllSessionsOnce(): List<InventorySession>

    // 是否存在进行中（status=0）的盘点任务——产品库修改守卫用
    suspend fun hasOngoingSessions(): Boolean

    // StockRecord 相关 (注意返回值变了!)
    fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>>

    suspend fun searchRecords(sessionId: Long, query: String): List<StockRecordCombined>
    suspend fun getRecordsByUdi(sessionId: Long, productKey: String, batch: String): List<StockRecordCombined>
    suspend fun getRecordsByKey(sessionId: Long, productKey: String): List<StockRecordCombined>
    suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined>
    suspend fun getRecordsByDiAndBatch(sessionId: Long, di: String, batch: String): List<StockRecordCombined>
    suspend fun getRecordsByKeyOrDiOrMat(sessionId: Long, query: String): List<StockRecordCombined>

    // 写入/更新
    suspend fun saveRecord(record: StockRecord)
    suspend fun updateRecord(record: StockRecord)
    suspend fun getRecordById(id: Long): StockRecord?
    suspend fun deleteRecord(record: StockRecord)

    // 导入 (注意这里现在接收两个List)
    suspend fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>)

    // 辅助
    suspend fun getUnverifiedCount(sessionId: Long): Int
    suspend fun getExportData(sessionId: Long): List<StockRecordCombined>
    suspend fun getSessionRecords(sessionId: Long): List<StockRecordCombined>

    // 根据 productKey 查找产品资料
    suspend fun getProductByKey(productKey: String): ProductBase?

    // 根据 DI 查找产品列表（组合键下同 DI 可对应多个物料编码不同的产品）
    suspend fun getProductsByDi(di: String): List<ProductBase>

    // 根据 materialCode 查找产品列表（组合键下同物料编码可对应多个 DI 不同的产品）
    suspend fun getProductsByMaterialCode(materialCode: String): List<ProductBase>

    suspend fun searchProducts(query: String): List<ProductBase>
    //临时无UDI搜索接口 批号or 效期
    suspend fun getRecordsBybatchorexpiryDate(sessionId: Long,batch: String): List<StockRecordCombined>

    suspend fun insertProduct(product: ProductBase)
    //对比接口
    /**
     * 保存盘点导入数据。产品库只读匹配：按「精确组合键 → DI → 物料编码」挂靠已有产品且不修改其字段，
     * 三级都未命中才兜底插入新产品。
     * @return 兜底插入的新产品数量
     */
    suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>): Int

    suspend fun updateProduct(product: ProductBase)
    suspend fun migrateProductKey(oldKey: String, newProduct: ProductBase): Result<String>
    suspend fun getAllProducts(): List<ProductBase>
    suspend fun getProductCount(): Int
    // 删除产品：被库存记录引用时拒删并返回失败原因（引用数）
    suspend fun deleteProductByKey(productKey: String): Result<Unit>
    suspend fun deleteProductsByKey(productKeyList: List<String>): com.example.inventorymaster.data.model.ProductDeleteResult

    //上传数据到电脑
    suspend fun exportFullSession(ip: String, sessionId: Long): Result<String>

    // 返回值 Boolean 代表是否成功，String 代表错误信息(如果有)
    // userInitiated=false 时为后台静默推送（如盘存页自动推送），连接失败不弹搜索弹窗
    suspend fun pushUnsyncedData(sessionId: Long, userInitiated: Boolean = true): Result<String>


    //从电脑下载数据
    suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String>

    // userInitiated=false 时为门铃触发的静默拉取，连接失败不弹搜索弹窗
    suspend fun pullNewData(sessionId: Long, userInitiated: Boolean = true): Result<String>

    // 全局产品库同步（从服务端拉取全量/增量产品，含金蝶物料主档）
    suspend fun pullProductLibrary(userInitiated: Boolean = true, forceFull: Boolean = false): Result<String>

    suspend fun resetProductPullSeq()

    // --- M1/M2 协作：绑定操作员 / 加入任务 / 归档上报（错误码见 CollabApiException）---
    suspend fun bindOperator(bindingCode: String): Result<String>
    suspend fun joinTask(sessionId: Long): Result<Unit>
    suspend fun requestJoinTask(ip: String, sessionId: Long, operatorName: String): Result<com.example.inventorymaster.data.dto.JoinStatusResponse>
    suspend fun checkJoinStatus(ip: String, sessionId: Long): Result<com.example.inventorymaster.data.dto.JoinStatusResponse>
    suspend fun reportTaskStatus(sessionUuid: String, localStatus: Int): Result<Unit>
    suspend fun reportPendingTaskStatuses(): Result<Int>
    suspend fun getSessionByUuid(uuid: String): InventorySession?

    //拉取云端列表
    suspend fun fetchCloudSessions(ip: String): Result<List<SessionDto>>

    suspend fun saveServerIp(ip: String)

    suspend fun getServerIp(): String

    suspend fun getServerToken(): String

    // --- 新增：Python 本地网关对接接口 ---
    suspend fun fetchPythonTaskList(): Result<List<com.example.inventorymaster.data.network.TaskSummary>>
    suspend fun fetchPythonTaskDetail(documentId: String): Result<com.example.inventorymaster.batchscanner.task.models.TargetDocument>
    suspend fun uploadPythonTaskPdf(documentIds: String, file: java.io.File): Result<String>
    // --- UDI 看板上传（走 /api/udi/submit，服务端 udi_groups 表，WEB 看板可见）---
    suspend fun submitUdiData(title: String, barcodes: List<String>): Result<String>

    // --- 实时录入 entry_jobs（v1.2，契约见方案文档 §5）---
    suspend fun submitEntryJob(title: String, rawItems: List<String>, serialRange: com.example.inventorymaster.data.network.SerialRangeSpec? = null): Result<Long>
    suspend fun fetchActiveEntryJobs(): Result<List<com.example.inventorymaster.data.network.EntryJobDto>>
    suspend fun appendEntryJob(jobId: Long, rawItems: List<String>, serialRange: com.example.inventorymaster.data.network.SerialRangeSpec? = null): Result<Int>
    suspend fun checkEntryJobDuplicates(rawItems: List<String>): Result<List<com.example.inventorymaster.data.network.EntryJobDuplicate>>
    suspend fun abandonEntryJob(jobId: Long): Result<String>

    // --- 序列号范围生成（v1.3）：只读预览（展开 + 查重），提交走上面的 submit/append ---
    suspend fun expandSerialRange(template: String, startSerial: String, endSerial: String): Result<com.example.inventorymaster.data.network.SerialExpandData>

    suspend fun checkProductConflicts(importItems: List<com.example.inventorymaster.data.model.ProductImportItem>): String?
    suspend fun saveProductsOnly(importItems: List<com.example.inventorymaster.data.model.ProductImportItem>)

    suspend fun getDirtyRecordsCount(sessionId: Long): Int

    suspend fun verifyAndSaveToken(ip: String, token: String): Result<String>

    suspend fun saveToken(token: String)

    // --- AI 结构化查询（v3：SQL 由 App 构造、值走绑定参数，LLM 不接触 SQL，见 docs/AI_V3_REDESIGN.md §4.5） ---
    suspend fun queryRecordsForAi(query: AiRecordQuery): String
}