package com.example.inventorymaster.ai.provider

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudAiService(
    private val config: AiProviderConfig,
    private val client: OkHttpClient = defaultClient()
) : AiService {

    private val gson = Gson()
    private val model = config.model
    private val streamClient = defaultStreamClient()

    override val capabilities = AiCapabilities(
        functionCalling = config.supportsTools,
        streaming = true
    )

    private fun validateConfig() {
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            throw AiServiceException(AiErrorKind.NOT_CONFIGURED, "服务商配置不完整：地址或模型名为空")
        }
    }

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): AiResponse {
        validateConfig()
        // 仅对服务端 5xx 重试：瞬时故障（网关抖动/过载）重试一次即可恢复；
        // 鉴权/限流/解析错误重试无意义，立即抛出
        repeat(MAX_HTTP_ATTEMPTS) { attempt ->
            try {
                return executeChat(messages, systemPrompt, tools)
            } catch (e: AiServiceException) {
                if (e.kind != AiErrorKind.SERVER || attempt == MAX_HTTP_ATTEMPTS - 1) throw e
                Log.w("CloudAiService", "服务端错误，${RETRY_DELAY_MS}ms 后重试（第 ${attempt + 1} 次）", e)
                delay(RETRY_DELAY_MS)
            }
        }
        throw AiServiceException(AiErrorKind.SERVER, "服务商持续返回错误")
    }

    private suspend fun executeChat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): AiResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(messages, systemPrompt, tools, stream = false)
            val url = "${config.baseUrl}/chat/completions"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            // runInterruptible：协程取消（用户点停止）→ 线程中断 → OkHttp 阻塞调用立刻断开
            val (code, body) = runInterruptible {
                client.newCall(request).execute().use { resp ->
                    resp.code to resp.body?.string()
                }
            }

            when {
                code == 401 || code == 403 ->
                    throw AiServiceException(AiErrorKind.AUTH_FAILED, "API Key 无效或已过期")
                code == 429 ->
                    throw AiServiceException(AiErrorKind.RATE_LIMITED, "请求过于频繁，请稍后再试")
                code >= 500 -> {
                    // 带上响应体片段：能区分"瞬时抖动"与"模型不支持该请求形态"（如 Input validation error）
                    val detail = body?.take(150)?.trim()
                    throw AiServiceException(
                        AiErrorKind.SERVER,
                        if (detail.isNullOrBlank()) "服务商服务器错误（HTTP $code），请稍后重试"
                        else "服务商服务器错误（HTTP $code）：$detail"
                    )
                }
                code !in 200..299 ->
                    throw AiServiceException(AiErrorKind.SERVER, "API 错误 HTTP $code: ${body?.take(200) ?: ""}")
                body.isNullOrEmpty() ->
                    throw AiServiceException(AiErrorKind.PARSE_ERROR, "AI 返回了空响应")
            }

            parseResponse(body)
        } catch (e: AiServiceException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: JsonParseException) {
            throw AiServiceException(AiErrorKind.PARSE_ERROR, "AI 返回格式异常，无法解析", e)
        } catch (e: IOException) {
            val msg = if (e.message?.contains("SSL", ignoreCase = true) == true ||
                e.message?.contains("certificate", ignoreCase = true) == true
            ) {
                "网络连接失败：SSL 证书验证错误，请检查手机系统时间是否正确"
            } else {
                "网络连接失败，请检查网络后重试"
            }
            throw AiServiceException(AiErrorKind.UNREACHABLE, msg, e)
        } catch (e: Exception) {
            throw AiServiceException(AiErrorKind.UNKNOWN, "AI 服务出错: ${e.message}", e)
        }
    }

    /**
     * 流式（SSE）：正文与 tool_calls 均以增量事件发出（docs/AI_V3_REDESIGN.md §4.8）。
     * 使用独立的 [streamClient]——SSE 中途重试不安全，流式 client 不带重试拦截器。
     */
    override fun streamChat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): Flow<StreamChunk> = callbackFlow {
        validateConfig()
        val requestBody = buildRequestBody(messages, systemPrompt, tools, stream = true)
        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = streamClient.newCall(request)
        launch(Dispatchers.IO) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val detail = resp.body?.string()?.take(150)?.trim()
                        trySend(StreamChunk.Error("HTTP ${resp.code}${if (detail.isNullOrBlank()) "" else "：$detail"}"))
                        return@launch
                    }
                    val reader = resp.body?.charStream()?.buffered()
                    if (reader == null) {
                        trySend(StreamChunk.Error("AI 返回了空响应"))
                        return@launch
                    }
                    var done = false
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        for (chunk in SseParser.parse(line.removePrefix("data:").trim())) {
                            if (chunk is StreamChunk.Completed) done = true
                            trySend(chunk)
                        }
                        if (done) break
                    }
                    if (!done) trySend(StreamChunk.Completed)
                }
            } catch (e: IOException) {
                trySend(StreamChunk.Error(networkErrorMessage(e)))
            } catch (e: Exception) {
                trySend(StreamChunk.Error("AI 服务出错: ${e.message}"))
            } finally {
                runCatching { close() }
            }
        }
        awaitClose { call.cancel() }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val requestBody = buildRequestBody(
                messages = listOf(AiMessage("user", "Hi")),
                systemPrompt = null,
                tools = null,
                stream = false
            )
            val url = "${config.baseUrl}/chat/completions"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val endpoint = config.modelsEndpoint
        if (endpoint.isBlank()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.w("CloudAiService", "Fetch models failed: ${response.code} $body")
                return@withContext emptyList()
            }

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: return@withContext emptyList()

            data.mapNotNull { element ->
                try {
                    element.asJsonObject.get("id")?.asString
                } catch (e: Exception) {
                    null
                }
            }.sorted()
        } catch (e: Exception) {
            Log.e("CloudAiService", "Fetch models failed", e)
            emptyList()
        }
    }

    // ─── 核心修改点 1：序列化兼容 ───
    private fun buildRequestBody(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?,
        stream: Boolean
    ): String {
        val json = JsonObject()

        json.addProperty("model", model)
        json.addProperty("stream", stream)

        val messagesArray = JsonArray()
        systemPrompt?.let {
            messagesArray.add(gson.toJsonTree(AiMessage("system", it)))
        }

        messages.forEach { msg ->
            when (msg.role) {
                "assistant" -> {
                    val obj = JsonObject()
                    obj.addProperty("role", "assistant")
                    // 完美兼容英伟达：如果是 null 强制转为空字符串，防止反序列化异常
                    obj.addProperty("content", msg.content ?: "")

                    msg.toolCalls?.let { tcList ->
                        val arr = JsonArray()
                        tcList.forEach { tc ->
                            val tcObj = JsonObject()
                            tcObj.addProperty("id", tc.id)
                            tcObj.addProperty("type", "function")
                            val fnObj = JsonObject()
                            fnObj.addProperty("name", tc.name)
                            // OpenAI 规范：arguments 必须是 JSON 字符串（不是对象）。
                            // 之前回传成对象，NVIDIA 等严格网关校验失败直接 500
                            fnObj.addProperty("arguments", tc.arguments.toString())
                            tcObj.add("function", fnObj)
                            arr.add(tcObj)
                        }
                        obj.add("tool_calls", arr)
                    }
                    messagesArray.add(obj)
                }
                "tool" -> {
                    val obj = JsonObject()
                    obj.addProperty("role", "tool")
                    obj.addProperty("tool_call_id", msg.toolCallId ?: "")
                    obj.addProperty("content", msg.content ?: "")
                    messagesArray.add(obj)
                }
                else -> {
                    messagesArray.add(gson.toJsonTree(msg))
                }
            }
        }
        json.add("messages", messagesArray)

        tools?.let { toolDefs ->
            if (toolDefs.isNotEmpty()) {
                val toolsArray = JsonArray()
                toolDefs.forEach { def ->
                    val toolObj = JsonObject()
                    toolObj.addProperty("type", "function")
                    val functionObj = JsonObject()
                    functionObj.addProperty("name", def.name)
                    functionObj.addProperty("description", def.description)
                    functionObj.add("parameters", def.parameters)
                    toolObj.add("function", functionObj)
                    toolsArray.add(toolObj)
                }
                json.add("tools", toolsArray)
            }
        }

        return gson.toJson(json)
    }

    // ─── 核心修改点 2：反序列化硬防御 ───
    private fun parseResponse(body: String): AiResponse {
        val json = JsonParser.parseString(body).asJsonObject
        val choices = json.getAsJsonArray("choices")

        if (choices == null || choices.size() == 0) {
            if (json.has("error")) {
                val errorMsg = json.getAsJsonObject("error").get("message")?.asString ?: "未知 API 错误"
                throw AiServiceException(AiErrorKind.SERVER, "服务商网关错误: $errorMsg")
            }
            throw AiServiceException(AiErrorKind.PARSE_ERROR, "AI 响应中没有 choices 字段")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message")

        // 增加安全提取逻辑，防止 content 字段被 Gson 解析为 JsonNull 对象导致 asString 报错
        val contentElement = message.get("content")
        val content = if (contentElement != null && !contentElement.isJsonNull) {
            contentElement.asString
        } else {
            ""
        }

        val toolCalls = if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull) {
            val tcArray = message.getAsJsonArray("tool_calls")
            tcArray.map { tc ->
                val tcObj = tc.asJsonObject
                val function = tcObj.getAsJsonObject("function")
                AiToolCall(
                    id = tcObj.get("id").asString,
                    name = function.get("name").asString,
                    arguments = JsonParser.parseString(function.get("arguments").asString).asJsonObject
                )
            }
        } else null

        return AiResponse(content = content, toolCalls = toolCalls)
    }

    override fun isLocal() = false

    companion object {
        private const val MAX_HTTP_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 800L

        /** 流式专用 client：不带重试拦截器（SSE 中途重试不安全，§4.8） */
        private fun defaultStreamClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        private fun networkErrorMessage(e: IOException): String {
            return if (e.message?.contains("SSL", ignoreCase = true) == true ||
                e.message?.contains("certificate", ignoreCase = true) == true
            ) {
                "网络连接失败：SSL 证书验证错误，请检查手机系统时间是否正确"
            } else {
                "网络连接失败，请检查网络后重试"
            }
        }

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(object : Interceptor {
                    private val maxRetries = 3
                    override fun intercept(chain: Interceptor.Chain): Response {
                        var lastException: Exception? = null
                        repeat(maxRetries) { attempt ->
                            try {
                                return chain.proceed(chain.request())
                            } catch (e: IOException) {
                                lastException = e
                                if (attempt < maxRetries - 1) {
                                    Thread.sleep(1000L * (1 shl attempt))
                                }
                            }
                        }
                        throw lastException!!
                    }
                })
                .build()
        }
    }
}