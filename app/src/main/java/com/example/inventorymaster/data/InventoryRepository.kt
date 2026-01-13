package com.example.inventorymaster.data

import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import kotlinx.coroutines.flow.Flow
import  com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.utils.NetworkUtils // 假设你有网络工具类
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 1. 定义一个简单的 API 接口 (就在 Repository 文件里定义即可，或者单独文件)

interface InventorySyncApi {
    @POST("/api/sync/push") // 👈 电脑端接收数据的接口路径，要和电脑端开发约定好
    suspend fun pushData(@Body records: List<StockRecord>): retrofit2.Response<Map<String, Any>>

    @GET("/api/sync/pull")
    suspend fun pullData(@Query("sessionId") sessionId: Long): retrofit2.Response<List<StockRecord>>
}

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

    suspend fun insertProduct(product: ProductBase)
    //对比接口
    suspend fun checkProductConflicts(newProducts: List<ProductBase>): List<ProductConflict>
    suspend fun saveImportDataWithResolution(finalProducts: List<ProductBase>, records: List<StockRecord>)

    suspend fun updateProduct(product: ProductBase)

    //上传数据到电脑
    suspend fun exportFullSession(ip: String, sessionId: Long): Result<String>

    // 返回值 Boolean 代表是否成功，String 代表错误信息(如果有)
    suspend fun uploadSessionData(ip: String, sessionId: Long): Result<String>


    //从电脑下载数据
    suspend fun exportdownloadFromPC(ip: String, sessionId: Long): Result<String>

    suspend fun downloadFromPC(ip: String, sessionId: Long): Result<String>

    //拉取云端列表
    suspend fun fetchCloudSessions(ip: String): Result<List<com.example.inventorymaster.data.dto.SessionDto>>


}