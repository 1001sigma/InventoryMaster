package com.example.inventorymaster.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.InventoryAiAgent
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.memory.ChatThreadCodec
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.ai.provider.LocalAiService
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.data.AppDatabase
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.repository.InventoryRepository
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

/** 历史会话栏条目 */
data class ChatThreadInfo(
    val threadId: String,
    val title: String,
    val lastTime: Long
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val activeProvider: AiProviderConfig? = null,
    val isConfigured: Boolean = false,
    val pendingActions: List<UiAction> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,
    val currentThreadId: String = "global"
)

class ChatViewModel(
    application: Application,
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val chatDb = AppDatabase.getDatabase(application)
    private val chatMessageDao = chatDb.chatMessageDao()

    private var agent: InventoryAiAgent? = null
    private val memory = ConversationMemory()
    private var currentJob: Job? = null

    /** 当前线程：任务ID字符串（任务内对话）或 THREAD_GLOBAL（无任务全局对话） */
    private var currentThreadId: String = THREAD_GLOBAL

    /** memory 相对磁盘是否有未落库内容（只在真实对话后置位，避免空快照覆盖历史） */
    private var memoryDirty = false

    private val persistenceMutex = Mutex()

    private val _chatThreads = MutableStateFlow<List<ChatThreadInfo>>(emptyList())
    val chatThreads: StateFlow<List<ChatThreadInfo>> = _chatThreads.asStateFlow()

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
        // 启动即恢复全局线程（未绑定任务时的对话）
        viewModelScope.launch {
            loadThread(currentThreadId)
            refreshThreads()
        }
    }

    /** 仅在 provider 实际变化时重建（AiProviderConfig 是 data class，含 Key 编辑在内的一切变更都会触发重建） */
    private fun initAgent(config: AiProviderConfig) {
        currentJob?.cancel()
        val aiService = if (config.isLocal) LocalAiService(config) else CloudAiService(config)
        val registry = ToolRegistry.createDefault(aiService, repository, settingsRepository)
        agent = InventoryAiAgent(aiService, registry, memory)
    }

    /**
     * 绑定当前任务：切换到该任务的专属会话线程（每任务一条历史，互不串上下文）。
     * 切换前取消在飞生成并等待其收尾落库，避免内存/磁盘错写。
     */
    fun setCurrentSession(session: InventorySession?) {
        agent?.setCurrentSession(session)
        val newThreadId = session?.id?.toString() ?: THREAD_GLOBAL
        if (newThreadId == currentThreadId) return

        val oldThreadId = currentThreadId
        currentThreadId = newThreadId
        _uiState.update { it.copy(currentThreadId = newThreadId) }
        currentJob?.cancel()
        viewModelScope.launch {
            currentJob?.join()
            if (memoryDirty) {
                persistThread(oldThreadId)
                memoryDirty = false
            }
            if (currentThreadId != newThreadId) return@launch
            loadThread(newThreadId)
            refreshThreads()
        }
    }

    /** 从历史会话栏切换线程：数字线程ID 解析回任务并绑定上下文 */
    fun openThread(threadId: String) {
        viewModelScope.launch {
            val session = threadId.toLongOrNull()?.let { repository.getSessionById(it) }
            setCurrentSession(session)
        }
    }

    private suspend fun threadTitle(threadId: String): String =
        if (threadId == THREAD_GLOBAL) "全局对话"
        else threadId.toLongOrNull()?.let { repository.getSessionById(it)?.name } ?: "任务 $threadId"

    private fun refreshThreads() {
        viewModelScope.launch {
            val fromDb = chatMessageDao.getThreadSummaries().map { summary ->
                ChatThreadInfo(summary.threadId, threadTitle(summary.threadId), summary.lastTime)
            }
            // 当前线程即使还没有任何落库消息也显示在最前
            val current = ChatThreadInfo(currentThreadId, threadTitle(currentThreadId), Long.MAX_VALUE)
            _chatThreads.value = (listOf(current) + fromDb.filter { it.threadId != currentThreadId })
        }
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

        val threadIdAtSend = currentThreadId
        memoryDirty = true
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
            } finally {
                // 每轮结束整线程落库（取消路径也在内——Agent 侧已把未完轮次补齐关闭）
                withContext(NonCancellable) {
                    persistThread(threadIdAtSend)
                    memoryDirty = false
                    refreshThreads()
                }
            }
        }
    }

    fun clearChat() {
        val job = currentJob
        currentJob = null
        job?.cancel()
        memory.clear()
        memoryDirty = false
        val threadId = currentThreadId
        _uiState.update { it.copy(messages = emptyList(), error = null, pendingConfirm = null) }
        viewModelScope.launch {
            // 等被取消的在飞任务收尾（其 finally 会先持久化），再删除该线程历史
            job?.join()
            chatMessageDao.deleteThread(threadId)
            refreshThreads()
        }
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

    // ── 聊天持久化（按任务分线程）────────────────────────────

    /** 从磁盘加载线程快照：恢复 memory（含 tool 配对）并回显用户/助手气泡 */
    private suspend fun loadThread(threadId: String) = persistenceMutex.withLock {
        val messages = ChatThreadCodec.toMessages(chatMessageDao.getMessages(threadId))
        memory.restoreFrom(messages)
        _uiState.update { it.copy(messages = displayMessages(messages)) }
        memoryDirty = false
    }

    /** 整线程快照落库（delete+insert 事务），与内存结构严格一致 */
    private suspend fun persistThread(threadId: String) = persistenceMutex.withLock {
        val rows = ChatThreadCodec.toRows(threadId, memory.messagesForRequest())
        chatDb.withTransaction {
            chatMessageDao.deleteThread(threadId)
            chatMessageDao.insertAll(rows)
        }
    }

    /** 恢复时的 UI 映射：只回显用户与助手最终回复，工具调用中间过程不进气泡 */
    private fun displayMessages(messages: List<AiMessage>): List<ChatMessage> =
        messages.mapNotNull { m ->
            when {
                m.role == "user" -> ChatMessage(role = "user", content = m.content ?: "", isUser = true)
                m.role == "assistant" && m.toolCalls == null ->
                    ChatMessage(role = "assistant", content = m.content ?: "", isUser = false)
                else -> null
            }
        }

    companion object {
        private const val THREAD_GLOBAL = "global"
    }
}
