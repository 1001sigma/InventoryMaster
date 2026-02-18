package com.example.inventorymaster.data.repository

import com.example.inventorymaster.data.dto.SessionDto
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.data.model.ProductConflict
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    // Session 相关
    fun getAllSessions(): Flow<List<InventorySession>>
    suspend fun createSession(name: String)
    suspend fun updateSessionStatus(sessionId: Long, status: Int)
    suspend fun deleteSession(session: InventorySession)

    // StockRecord 相关 (注意返回值变了!)
    fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>>

    suspend fun searchRecords(sessionId: Long, query: String): List<StockRecordCombined>
    suspend fun getRecordsByUdi(sessionId: Long, di: String, batch: String): List<StockRecordCombined>
    suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined>

    // 写入/更新
    suspend fun saveRecord(record: StockRecord)
    suspend fun updateRecord(record: StockRecord)
    suspend fun deleteRecord(record: StockRecord)

    // 导入 (注意这里现在接收两个List)
    suspend fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>)

    // 辅助
    suspend fun getUnverifiedCount(sessionId: Long): Int
    suspend fun getExportData(sessionId: Long): List<StockRecordCombined>

    // [新增] 专门给扫码用的：如果本地没有产品资料，可能需要手动查一下(预留)
    suspend fun getProductByDi(di: String): ProductBase?

    suspend fun searchProducts(query: String): List<ProductBase>
    //临时无UDI搜索接口 批号or 效期
    suspend fun getRecordsBybatchorexpiryDate(sessionId: Long,batch: String): List<StockRecordCombined>

    suspend fun insertProduct(product: ProductBase)
    //对比接口
    suspend fun checkProductConflicts(newProducts: List<ProductBase>): List<ProductConflict>
    suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>)

    suspend fun updateProduct(product: ProductBase)

    //上传数据到电脑
    suspend fun exportFullSession(ip: String, sessionId: Long): Result<String>

    // 返回值 Boolean 代表是否成功，String 代表错误信息(如果有)
    suspend fun pushUnsyncedData(sessionId: Long): Result<String>


    //从电脑下载数据
    suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String>

    suspend fun pullNewData(sessionId: Long): Result<String>

    //拉取云端列表
    suspend fun fetchCloudSessions(ip: String): Result<List<SessionDto>>

    fun saveServerIp(ip: String)


}