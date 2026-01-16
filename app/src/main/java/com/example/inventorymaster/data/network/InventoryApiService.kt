package com.example.inventorymaster.data.network

import com.example.inventorymaster.data.dto.StockRecordDto
import com.example.inventorymaster.data.dto.SyncData
import com.example.inventorymaster.data.entity.StockRecord
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface InventoryApiService {
    // --- 旧接口 (全量) ---
    @POST("/api/upload") // 假设旧路径
    suspend fun uploadData(@Body data: SyncData): Response<Map<String, Any>>

    @GET("/api/download")
    suspend fun downloadData(@Query("sessionId") sessionId: Long): Response<SyncData>

    @GET("/api/sessions")
    suspend fun getSessionList(): Response<List<com.example.inventorymaster.data.dto.SessionDto>>

    // --- 🔥 新接口 (增量同步 - 原 InventorySyncApi 的内容移到这里) ---
    @POST("/api/sync/push")
    suspend fun pushData(@Body records: List<StockRecord>): Response<Map<String, Any>>

    @GET("/api/sync/pull")
    suspend fun pullData(@Query("sessionId") sessionId: Long): Response<List<StockRecord>>

    // --- 统一的构建器 ---
    companion object {
        fun create(ip: String): InventoryApiService {
            // 自动补全 http 前缀，防止报错
            val validIp = if (ip.startsWith("http")) ip else "http://$ip:8080/"

            return Retrofit.Builder()
                .baseUrl(validIp)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(InventoryApiService::class.java)
        }
    }
}