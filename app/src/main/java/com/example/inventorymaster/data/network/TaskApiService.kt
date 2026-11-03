package com.example.inventorymaster.data.network

import com.example.inventorymaster.batchscanner.task.models.TargetDocument
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- 响应数据模型 ---
data class TaskSummaryResponse(val status: String, val data: List<TaskSummary>)

data class TaskSummary(
    val documentId: String,
    val documentName: String,
    val documentDate: String,
    val billType: String? = null,
    val orderNo: String? = null,
    val documentNumber: Int,
    val status: Int
)

data class TaskDetailResponse(val status: String, val data: TargetDocument)
data class UploadResponse(val status: String, val message: String?)

data class CustomDataSubmitRequest(
    val title: String,
    val detail: com.google.gson.JsonObject
)
data class CustomDataSubmitResponse(val status: String, val message: String?)

// --- 实时录入 entry_jobs（契约见《实时录入功能方案 v1.1》：0待处理 1录入中 2已完成 3已失败）---
// --- 序列号范围生成（v1.3）：template=清洗后 UDI 前缀（含末尾 "21"），起止为纯数字流水号 ---
data class SerialRangeSpec(val template: String, val startSerial: String, val endSerial: String)

/** 任务上的范围元数据（服务端 serial_range 列），摘要卡据此显示「流水 xxx-xxx · n/N」 */
data class SerialRangeMeta(
    val template: String,
    val startSerial: String,
    val endSerial: String,
    val count: Int = 0
)

data class SerialExpandRequest(val serialRange: SerialRangeSpec)
data class SerialExpandData(
    val count: Int = 0,
    val firstValue: String? = null,
    val lastValue: String? = null,
    val values: List<String> = emptyList(),
    val duplicates: List<EntryJobDuplicate> = emptyList()
)
data class SerialExpandResponse(val status: String, val message: String?, val data: SerialExpandData?)

data class EntryJobSubmitRequest(
    val title: String,
    val rawItems: List<String>,
    val serialRange: SerialRangeSpec? = null  // v1.3：有值=范围生成任务，服务端展开
)
data class EntryJobSubmitData(val jobId: Long, val totalCount: Int)
data class EntryJobSubmitResponse(val status: String, val message: String?, val data: EntryJobSubmitData?)

data class EntryJobItemResult(
    val seq: Int,
    val rawValue: String,
    val status: String,          // pending | success | failed | skipped
    val reason: String? = null,
    val enteredAt: Long? = null
)

data class EntryJobDto(
    val jobId: Long,
    val title: String,
    val status: Int,
    val totalCount: Int,
    val doneCount: Int,
    val failCount: Int,
    val skipCount: Int,
    val createdAt: String? = null,
    val claimedAt: String? = null,
    val finishedAt: String? = null,
    val stale: Boolean = false,
    val scriptOnline: Boolean = false,
    val serialRange: SerialRangeMeta? = null,
    val itemResults: List<EntryJobItemResult> = emptyList()
)
data class EntryJobListResponse(val status: String, val message: String?, val data: List<EntryJobDto>?)
data class EntryJobDetailResponse(val status: String, val message: String?, val data: EntryJobDto?)

data class EntryJobAppendRequest(
    val rawItems: List<String>,
    val serialRange: SerialRangeSpec? = null  // v1.3：携带时展开追加并合并范围元数据
)
data class EntryJobAppendData(val totalCount: Int)
data class EntryJobAppendResponse(val status: String, val message: String?, val data: EntryJobAppendData?)

data class EntryJobDuplicate(
    val rawValue: String,
    val jobId: Long,
    val title: String? = null,
    val jobStatus: Int = 0,
    val itemStatus: String? = null,
    val at: String? = null
)
data class EntryJobCheckDupRequest(val rawItems: List<String>)
data class EntryJobCheckDupResponse(val status: String, val message: String?, val data: List<EntryJobDuplicate>?)

/** append 命中终态（job 已结束，服务端 409）时抛出；上层据此自动降级为 submit 开新批次 */
class EntryJobTerminalException(message: String) : Exception(message)

// --- UDI 看板（服务端 udi_groups 表：名称唯一，同名整体覆盖并把状态重置为待完成）---
// 提交契约为扁平 Map：{"名称": ["UDI1", "UDI2", ...]}，不要包 wrapper，否则服务端 422
data class UdiSubmitResponse(val status: String, val message: String?)

data class UdiGroupSummary(
    val id: Long,
    val name: String,
    val count: Int,
    val status: Int,             // 0待完成 1已完成 2已跳过
    val created_at: String? = null,
    val updated_at: String? = null
)
data class UdiListResponse(val status: String, val message: String?, val data: List<UdiGroupSummary>?)

data class UdiDetailData(val name: String, val udis: List<String>, val status: Int)
data class UdiDetailResponse(val status: String, val message: String?, val data: UdiDetailData?)

data class UdiMutationResponse(val status: String, val message: String?)

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

    @POST("api/custom-data/submit")
    suspend fun submitCustomData(@Body body: CustomDataSubmitRequest): Response<CustomDataSubmitResponse>

    // --- UDI 看板 ---
    // 注意：Kotlin List 协变会在 JVM 签名生成 ? extends 通配符，Retrofit 会直接抛
    // "Parameter type must not include a type variable or wildcard"，必须用 @JvmSuppressWildcards 压掉
    @POST("api/udi/submit")
    suspend fun submitUdiGroups(@Body body: Map<String, @JvmSuppressWildcards List<String>>): Response<UdiSubmitResponse>

    @GET("api/udi/list")
    suspend fun getUdiGroups(): Response<UdiListResponse>

    @GET("api/udi/detail")
    suspend fun getUdiGroupDetail(@Query("name") name: String): Response<UdiDetailResponse>

    @PUT("api/udi/status")
    suspend fun updateUdiStatus(@Query("name") name: String, @Query("status") status: Int): Response<UdiMutationResponse>

    @DELETE("api/udi")
    suspend fun deleteUdiGroup(@Query("name") name: String): Response<UdiMutationResponse>

    // --- 实时录入（v1.1）---
    @POST("api/entry-jobs/submit")
    suspend fun submitEntryJob(@Body body: EntryJobSubmitRequest): Response<EntryJobSubmitResponse>

    @GET("api/entry-jobs/active")
    suspend fun getActiveEntryJobs(): Response<EntryJobListResponse>

    @GET("api/entry-jobs/{jobId}")
    suspend fun getEntryJob(@Path("jobId") jobId: Long): Response<EntryJobDetailResponse>

    @POST("api/entry-jobs/{jobId}/append")
    suspend fun appendEntryJob(@Path("jobId") jobId: Long, @Body body: EntryJobAppendRequest): Response<EntryJobAppendResponse>

    @POST("api/entry-jobs/{jobId}/abandon")
    suspend fun abandonEntryJob(@Path("jobId") jobId: Long): Response<EntryJobAppendResponse>

    @POST("api/entry-jobs/check-duplicates")
    suspend fun checkEntryJobDuplicates(@Body body: EntryJobCheckDupRequest): Response<EntryJobCheckDupResponse>

    // --- 序列号范围生成（v1.3）：只读预览（展开 + 查重），提交仍走 submit/append ---
    @POST("api/serial/expand")
    suspend fun expandSerialRange(@Body body: SerialExpandRequest): Response<SerialExpandResponse>

    companion object {
        fun create(ip: String): TaskApiService {
            // 清理 IP 格式，强制指向 Python 的 8000 端口
            val cleanIp = ip.replace(Regex("http(s)?://"), "").substringBefore(":")
            val validUrl = "http://$cleanIp:8000/"

            // 连接失败快速返回（配合 ServerConnectionManager 的探测），避免无超时挂死
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(validUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TaskApiService::class.java)
        }
    }
}