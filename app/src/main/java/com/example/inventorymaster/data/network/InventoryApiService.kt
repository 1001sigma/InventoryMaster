package com.example.inventorymaster.data.network

import com.example.inventorymaster.data.dto.SyncData
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface InventoryApiService {

    // 定义上传接口：POST 请求，路径是 /sync/upload
    @POST("/sync/upload")
    suspend fun uploadData(@Body data: SyncData): Response<ResponseBody>

    companion object {
        // 创建连接的工厂方法
        fun create(ipAddress: String): InventoryApiService {
            // 组装 URL: http://192.168.1.5:8080/
            val baseUrl = "http://$ipAddress:8080/"

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(InventoryApiService::class.java)
        }
    }

    // [新增] 下载接口
    // 返回值是 Response<SyncData>，Retrofit 会自动把 JSON 转成对象
    @retrofit2.http.GET("/sync/download")
    suspend fun downloadData(@retrofit2.http.Query("sessionId") sessionId: Long): Response<SyncData>

    // 获取云端任务列表
    @retrofit2.http.GET("/session/list")
    suspend fun getSessionList(): Response<List<com.example.inventorymaster.data.dto.SessionDto>>
}