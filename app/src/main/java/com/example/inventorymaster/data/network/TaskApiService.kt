package com.example.inventorymaster.data.network

import com.example.inventorymaster.batchscanner.TargetDocument
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

// --- 响应数据模型 ---
data class TaskSummaryResponse(val status: String, val data: List<TaskSummary>)

data class TaskSummary(
    val documentId: String,
    val documentName: String,
    val documentDate: String,
    val documentNumber: Int,
    val status: Int
)

data class TaskDetailResponse(val status: String, val data: TargetDocument)
data class UploadResponse(val status: String, val message: String?)

// --- 接口定义 ---
interface TaskApiService {
    @GET("api/task/list")
    suspend fun getTaskList(): Response<TaskSummaryResponse>

    @GET("api/task/{documentId}")
    suspend fun getTaskDetail(@Path("documentId") documentId: String): Response<TaskDetailResponse>

    @Multipart
    @POST("api/task/upload")
    suspend fun uploadPdfResult(
        @Part("documentIds") documentIds: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    companion object {
        fun create(ip: String): TaskApiService {
            // 清理 IP 格式，强制指向 Python 的 8000 端口
            val cleanIp = ip.replace(Regex("http(s)?://"), "").substringBefore(":")
            val validUrl = "http://$cleanIp:8000/"

            return Retrofit.Builder()
                .baseUrl(validUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TaskApiService::class.java)
        }
    }
}