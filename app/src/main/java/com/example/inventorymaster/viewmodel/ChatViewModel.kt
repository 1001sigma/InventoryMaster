package com.example.inventorymaster.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.InventoryAiAgent
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.ai.provider.LocalAiService
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.repository.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,
    val content: String,
    val isUser: Boolean,
    val isToolCall: Boolean = false,
    val isLoading: Boolean = false,
    val cardAction: UiAction.ShowProductCardList? = null
)

/** CONFIRM 权限工具的待确认请求 */
data class PendingConfirm(
    val requestId: String,
    val toolName: String,
    val argumentsSummary: String
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val activeProvider: AiProviderConfig? = null,
    val isConfigured: Boolean = false,
    val pendingActions: List<UiAction> = emptyList(),
    val pendingConfirm: PendingConfirm? = null
)

class ChatViewModel(
    application: Application,
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var agent: InventoryAiAgent? = null
    private val memory = ConversationMemory()
    private var currentJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .map { it.getActiveProvider() }
                .distinctUntilChanged()
                .collect { provider ->
                    _uiState.update {
                        it.copy(activeProvider = provider, isConfigured = provider != null)
                    }
                    if (provider != null) {
                        initAgent(provider)
                    }
                }
        }
    }

    /** 仅在 provider 实际变化时重建（AiProviderConfig 是 data class，含 Key 编辑在内的一切变更都会触发重建） */
    private fun initAgent(config: AiProviderConfig) {
        currentJob?.cancel()
        val aiService = if (config.isLocal) LocalAiService(config) else CloudAiService(config)
        val registry = ToolRegistry.createDefault(aiService, repository, settingsRepository)
        agent = InventoryAiAgent(aiService, registry, memory)
    }

    fun setCurrentSession(session: InventorySession?) {
        agent?.setCurrentSession(session)
    }

    fun loadSessionById(sessionId: Long) {
        if (sessionId <= 0) return
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            setCurrentSession(session)
        }
    }

    /** 用户点击"停止"：取消当前生成（Agent 侧会补齐轮次并保持记忆合法） */
    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
    }

    fun respondConfirm(requestId: String, approved: Boolean) {
        _uiState.update { it.copy(pendingConfirm = null) }
        agent?.respondConfirm(requestId, approved)
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        val currentAgent = agent ?: run {
            _uiState.update { it.copy(error = "请先在设置中配置 AI 服务") }
            return
        }

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(
                        role = "user",
                        content = message,
                        isUser = true
                    ),
                    isProcessing = true,
                    error = null,
                    pendingConfirm = null
                )
            }

            try {
                currentAgent.chat(message).collect { event ->
                    when (event) {
                        is AgentEvent.Thinking -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + ChatMessage(
                                        role = "assistant",
                                        content = event.message,
                                        isUser = false,
                                        isLoading = true
                                    )
                                )
                            }
                        }
                        is AgentEvent.ToolCallStart -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + ChatMessage(
                                        role = "tool",
                                        content = "正在调用 ${event.toolName}...",
                                        isUser = false,
                                        isToolCall = true,
                                        isLoading = true
                                    )
                                )
                            }
                        }
                        is AgentEvent.ToolCallResult -> {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.dropLast(1) + ChatMessage(
                                    role = "tool",
                                    content = "${event.toolName} 返回结果",
                                    isUser = false,
                                    isToolCall = true
                                )
                                state.copy(messages = updatedMessages)
                            }
                        }
                        is AgentEvent.ConfirmRequired -> {
                            _uiState.update {
                                it.copy(
                                    pendingConfirm = PendingConfirm(
                                        requestId = event.requestId,
                                        toolName = event.toolName,
                                        argumentsSummary = event.argumentsSummary
                                    )
                                )
                            }
                        }
                        is AgentEvent.Response -> {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.filterNot { msg -> msg.isLoading } + ChatMessage(
                                    role = "assistant",
                                    content = event.content,
                                    isUser = false,
                                    cardAction = event.cardAction
                                )
                                state.copy(
                                    messages = updatedMessages,
                                    isProcessing = false
                                )
                            }
                        }
                        is AgentEvent.ResponseChunk -> {
                            _uiState.update { state ->
                                val lastMsg = state.messages.lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser && lastMsg.isLoading) {
                                    val updatedMessages = state.messages.dropLast(1) + lastMsg.copy(
                                        content = lastMsg.content + event.content
                                    )
                                    state.copy(messages = updatedMessages)
                                } else {
                                    state.copy(
                                        messages = state.messages + ChatMessage(
                                            role = "assistant",
                                            content = event.content,
                                            isUser = false,
                                            isLoading = true
                                        )
                                    )
                                }
                            }
                        }
                        is AgentEvent.Error -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages.filterNot { msg -> msg.isLoading } + ChatMessage(
                                        role = "error",
                                        content = event.message,
                                        isUser = false
                                    ),
                                    isProcessing = false
                                )
                            }
                        }
                        is AgentEvent.ActionExecuted -> {
                            _uiState.update { state ->
                                state.copy(pendingActions = state.pendingActions + event.action)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // 用户停止：气泡提示，Agent 侧已负责补齐记忆
                _uiState.update {
                    it.copy(
                        messages = it.messages.filterNot { msg -> msg.isLoading } + ChatMessage(
                            role = "assistant",
                            content = "（已停止本次回答）",
                            isUser = false
                        ),
                        isProcessing = false,
                        pendingConfirm = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        messages = it.messages.filterNot { msg -> msg.isLoading } + ChatMessage(
                            role = "error",
                            content = "发送失败: ${e.message}",
                            isUser = false
                        ),
                        isProcessing = false
                    )
                }
            }
        }
    }

    fun clearChat() {
        currentJob?.cancel()
        currentJob = null
        memory.clear()
        _uiState.update { it.copy(messages = emptyList(), error = null, pendingConfirm = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissAction(action: UiAction) {
        _uiState.update { state ->
            state.copy(pendingActions = state.pendingActions - action)
        }
    }

    fun dismissAllActions() {
        _uiState.update { it.copy(pendingActions = emptyList()) }
    }

    fun handleCardActionClick(productData: ExpiredProductCardData) {
        if (productData.recordId <= 0) return
        _uiState.update { currentState ->
            currentState.copy(
                pendingActions = currentState.pendingActions + UiAction.OpenEditDialog(productData.recordId)
            )
        }
    }
}
