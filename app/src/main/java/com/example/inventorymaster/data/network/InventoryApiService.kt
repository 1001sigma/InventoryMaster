package com.example.inventorymaster.data.network

import com.example.inventorymaster.data.dto.PullResponse
import com.example.inventorymaster.data.dto.PushRequest
import com.example.inventorymaster.data.dto.SyncData
import com.example.inventorymaster.data.entity.StockRecord
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface InventoryApiService {
    // --- 旧接口 (全量) ---
    @POST("/api/upload")
    suspend fun uploadData(@Body data: SyncData): Response<Map<String, Any>>

    @GET("/api/download")
    suspend fun downloadData(
        @Query("sessionUuid") sessionUuid: String? = null,
        @Query("sessionId") sessionId: Long? = null
    ): Response<SyncData>

    @GET("/api/sessions")
    suspend fun getSessionList(): Response<List<com.example.inventorymaster.data.dto.SessionDto>>

    // --- 新接口 (增量同步) ---
    @POST("/api/sync/push")
    suspend fun pushData(
        @Query("sessionUuid") sessionUuid: String,
        @Body data: PushRequest
    ): Response<Map<String, Any>>

    @GET("/api/sync/pull")
    suspend fun pullData(
        @Query("sessionUuid") sessionUuid: String,
        @Query("lastSeq") lastSeq: Long
    ): Response<PullResponse>

    @GET("/api/products/pull")
    suspend fun pullProducts(
        @Query("lastSeq") lastSeq: Long
    ): Response<com.example.inventorymaster.data.dto.ProductPullResponse>

    @POST("/api/sync/delete")
    suspend fun deleteRecords(
        @Query("sessionUuid") sessionUuid: String,
        @Body data: com.example.inventorymaster.data.dto.DeleteRequest
    ): Response<Map<String, Any>>

    // --- 认证接口 ---
    @GET("/api/auth/verify")
    suspend fun verifyToken(@Query("token") token: String): Response<Map<String, Any>>

    // --- 协作身份/加入/上报（M1/M2，错误契约 403/409/428 + {"detail","code"}）---
    @POST("/api/operator/bind")
    suspend fun bindOperator(@Body data: com.example.inventorymaster.data.dto.BindOperatorRequest): Response<Map<String, Any>>

    @POST("/api/task/join")
    suspend fun joinTask(@Body data: com.example.inventorymaster.data.dto.JoinTaskRequest): Response<Map<String, Any>>

    @POST("/api/task/join-request")
    suspend fun requestJoinTask(@Body data: com.example.inventorymaster.data.dto.JoinTaskRequestDto): Response<Map<String, Any>>

    @GET("/api/task/join-status")
    suspend fun getJoinStatus(
        @Query("sessionId") sessionId: Long,
        @Query("deviceUuid") deviceUuid: String
    ): Response<Map<String, Any>>

    @POST("/api/task/report")
    suspend fun reportTaskStatus(@Body data: com.example.inventorymaster.data.dto.ReportTaskRequest): Response<Map<String, Any>>

    // --- 统一的构建器 ---
    companion object {
        private var cachedToken: String = ""
        private var cachedDeviceUuid: String = ""

        fun setToken(token: String) {
            cachedToken = token
        }

        fun setDeviceUuid(uuid: String) {
            cachedDeviceUuid = uuid
        }

        fun create(ip: String): InventoryApiService {
            val validIp = if (ip.startsWith("http")) ip else "http://$ip:8080/"

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    if (cachedToken.isNotBlank()) {
                        requestBuilder.header("Authorization", "Bearer $cachedToken")
                    }
                    if (cachedDeviceUuid.isNotBlank()) {
                        requestBuilder.header("X-Device-UUID", cachedDeviceUuid)  // M1：设备身份
                    }
                    chain.proceed(requestBuilder.build())
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(validIp)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(InventoryApiService::class.java)
        }
    }
}