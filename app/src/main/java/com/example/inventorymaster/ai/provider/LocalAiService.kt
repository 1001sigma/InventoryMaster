package com.example.inventorymaster.ai.provider

import android.util.Log
import com.example.inventorymaster.ai.local.LocalLlmRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 本地模型服务（LiteRT-LM 引擎，见 ai/local/LocalLlmRuntime）。
 *
 * 纯文本进出：工具协议由 AiRuntime 按 FC=false 路由到 JsonToolCallStrategy
 * （systemPrompt 已内嵌 JSON 工具约定）。这里**绝不再**注入自有工具说明或解析
 * 回复——旧实现的双重协议注入（{"name":..}）与剥空 content 的 parseResponse
 * 会让 JSON 策略丢掉工具调用意图。
 */
class LocalAiService(
    private val config: AiProviderConfig
) : AiService {

    /**
     * 本地模型不支持原生 Function Calling（走 JSON 协议）；流式已接通（LiteRT-LM
     * sendMessageAsync 逐 token 增量），配合 AiRuntime 的信封抑制规则外显。
     */
    override val capabilities = AiCapabilities(functionCalling = false, streaming = true)

    /** 本地推理（2.5GB 级 int4 模型）慢于云端一个量级：600s 超时预算 */
    override val chatTimeoutMs: Long = 600_000

    override val modelId: String get() = config.model

    private fun requireModelPath(): String {
        val path = config.localModelPath
        if (path.isNullOrBlank() || !File(path).exists()) {
            throw AiServiceException(
                AiErrorKind.NOT_CONFIGURED,
                "本地模型文件未就绪，请到 AI 设置页下载或导入（${config.model}）"
            )
        }
        return path
    }

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): AiResponse {
        val modelPath = requireModelPath()
        return try {
            AiResponse(content = LocalLlmRuntime.chat(modelPath, messages, systemPrompt))
        } catch (e: Exception) {
            if (e is AiServiceException) throw e
            Log.e("LocalAiService", "Local inference failed", e)
            throw AiServiceException(AiErrorKind.UNKNOWN, "本地模型推理失败: ${e.message}", e)
        }
    }

    override fun streamChat(
        messages: List<AiMessage>,
        systemPrompt: String?,
        tools: List<AiToolDefinition>?
    ): Flow<StreamChunk> = flow {
        try {
            val modelPath = requireModelPath()
            LocalLlmRuntime.chatStream(modelPath, messages, systemPrompt).collect { delta ->
                emit(StreamChunk.Text(delta))
            }
            emit(StreamChunk.Completed)
        } catch (e: Exception) {
            Log.e("LocalAiService", "Stream failed", e)
            emit(StreamChunk.Error(e.message ?: "本地模型推理失败"))
        }
    }

    override suspend fun isAvailable(): Boolean {
        val path = config.localModelPath ?: return false
        return File(path).exists()
    }

    override fun isLocal() = true

    /** 用户点停止时主动打断原生生成（不等协程取消传播，见 LocalLlmRuntime.interruptActiveGeneration） */
    fun interruptGeneration() = LocalLlmRuntime.interruptActiveGeneration()
}
