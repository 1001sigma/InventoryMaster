package com.example.inventorymaster.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.InventoryAiAgent
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.agent.PendingQuestion
import com.example.inventorymaster.ai.agent.ProductCardListCodec
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.ai.agent.UserQuestionAnswer
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.memory.ChatThreadCodec
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.AiService
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChatMessage(
    /** LazyColumn 稳定 key（流式内容增长/插入时保持条目身份，避免状态丢失与视口跳动） */
    val id: Int = 0,
    val role: String,
    val content: String,
    val isUser: Boolean,
    val isToolCall: Boolean = false,
    val isLoading: Boolean = false,
    val cardAction: UiAction.ShowProductCardList? = null,
    /** 思维链折叠内容（工具调用的完整参数与结果；null=不可展开） */
    val detail: String? = null,
    /** AI 最终回复的整轮耗时（毫秒，操作条展示）；null=未统计/历史数据 */
    val durationMs: Long? = null,
    /** 生成该回复的模型 ID（操作条展示）；null=未统计/历史数据 */
    val model: String? = null
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
    val lastTime: Long,
    /** 任务绑定会话（threadId 编码了任务ID） */
    val isTask: Boolean = false,
    /** 所属任务名（仅任务会话非空，侧栏徽标用） */
    val taskName: String? = null
)

/**
 * 会话线程 ID 编解码（v2 多会话模型，docs/AI_UI_OPTIMIZATION.md P1-6）。
 * - 自由会话：`g_<创建时间戳>`；旧 "global" 视为一条普通自由会话（历史数据兼容，不迁移）
 * - 任务会话：`t<任务ID>_<创建时间戳>`，一个任务下可有多条对话
 */
private object ChatThreadIds {
    fun newFree(): String = "g_${System.currentTimeMillis()}"
    fun newTask(sessionId: Long): String = "t${sessionId}_${System.currentTimeMillis()}"

    /** 任务会话 → 任务ID；自由会话 → null。旧纯数字线程ID按任务ID兼容解析 */
    fun taskSessionId(threadId: String): Long? =
        when {
            threadId.startsWith("t") -> threadId.drop(1).substringBefore('_').toLongOrNull()
            else -> threadId.toLongOrNull()
        }

    fun isFree(threadId: String): Boolean = taskSessionId(threadId) == null
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val activeProvider: AiProviderConfig? = null,
    val isConfigured: Boolean = false,
    val pendingActions: List<UiAction> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,
    val pendingQuestion: PendingQuestion? = null,
    val currentThreadId: String = "global",
    val draftMessage: String = ""
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
    /** 当前 agent 使用的服务实例：本地模型点「停止」时据此主动中断原生生成 */
    private var activeAiService: AiService? = null
    private val memory = ConversationMemory()
    private var currentJob: Job? = null

    /** 消息稳定 id 发号器（LazyColumn key 用） */
    private var nextMessageId = 0
    private fun nextMessageId(): Int = ++nextMessageId

    /** 最近一条用户消息（重新生成/错误重试共用；切线程/清空时重置） */
    private var lastUserMessage: String? = null

    /** 当前线程：`t<任务ID>_<时间戳>`（任务会话）/ `g_<时间戳>`（自由会话）/ 旧 "global"（视为自由会话） */
    private var currentThreadId: String = THREAD_GLOBAL

    /** memory 相对磁盘是否有未落库内容（只在真实对话后置位，避免空快照覆盖历史） */
    private var memoryDirty = false

    private val persistenceMutex = Mutex()

    private val _chatThreads = MutableStateFlow<List<ChatThreadInfo>>(emptyList())
    val chatThreads: StateFlow<List<ChatThreadInfo>> = _chatThreads.asStateFlow()

    /** 思维链折叠卡片开关（设置 → AI 助手） */
    val showThinking: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.aiShowThinking }
        .distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

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
        // 启动着陆：最近活跃的自由会话（绝不自动绑任务）；一条都没有则新开空自由会话
        viewModelScope.launch {
            val latestFree = chatMessageDao.getThreadSummaries()
                .firstOrNull { ChatThreadIds.isFree(it.threadId) }
            if (latestFree != null) {
                currentThreadId = latestFree.threadId
                _uiState.update { it.copy(currentThreadId = latestFree.threadId) }
                loadThread(latestFree.threadId)
            } else {
                currentThreadId = ChatThreadIds.newFree()
                _uiState.update { it.copy(currentThreadId = currentThreadId) }
            }
            refreshThreads()
        }
    }

    /** 仅在 provider 实际变化时重建（AiProviderConfig 是 data class，含 Key 编辑在内的一切变更都会触发重建） */
    private fun initAgent(config: AiProviderConfig) {
        currentJob?.cancel()
        val aiService = if (config.isLocal) LocalAiService(config) else CloudAiService(config)
        activeAiService = aiService
        val registry = ToolRegistry.createDefault(aiService, repository, settingsRepository)
        agent = InventoryAiAgent(aiService, registry, memory)
    }

    /**
     * 切换会话线程（v2 多会话核心）：session = 该线程绑定的任务上下文，自由会话传 null。
     * 切换前取消在飞生成并等待其收尾落库，避免内存/磁盘错写。
     */
    private fun switchThread(newThreadId: String, session: InventorySession?) {
        agent?.setCurrentSession(session)
        if (newThreadId == currentThreadId) return

        val oldThreadId = currentThreadId
        currentThreadId = newThreadId
        _uiState.update { it.copy(currentThreadId = newThreadId, pendingConfirm = null, pendingQuestion = null) }
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

    /**
     * 聊天页进入（路由 sessionId 参数）：
     * - sessionId > 0：落到该任务最近活跃的会话，一条都没有则现场创建（任务下多会话）
     * - sessionId = 0（主页/自由侧）：落到最近活跃的自由会话，绝不自动绑任务
     */
    fun enterFromSession(sessionId: Long) {
        viewModelScope.launch {
            if (sessionId <= 0) {
                val latestFree = chatMessageDao.getThreadSummaries()
                    .firstOrNull { ChatThreadIds.isFree(it.threadId) }
                if (latestFree != null) {
                    switchThread(latestFree.threadId, null)
                } else {
                    switchThread(ChatThreadIds.newFree(), null)
                }
            } else {
                val session = repository.getSessionById(sessionId)
                val latestOfTask = chatMessageDao.getThreadSummaries()
                    .firstOrNull { ChatThreadIds.taskSessionId(it.threadId) == sessionId }
                if (latestOfTask != null) {
                    switchThread(latestOfTask.threadId, session)
                } else {
                    switchThread(ChatThreadIds.newTask(sessionId), session)
                }
            }
        }
    }

    /** 从历史会话栏切换线程：按线程类型解析出任务并绑定上下文 */
    fun openThread(threadId: String) {
        viewModelScope.launch {
            val session = ChatThreadIds.taskSessionId(threadId)?.let { repository.getSessionById(it) }
            switchThread(threadId, session)
        }
    }

    /** 顶栏「新建会话」：任务语境开该任务的新对话，自由语境开新自由会话（空会话不落库，切走即弃） */
    fun startNewChat() {
        val sessionId = ChatThreadIds.taskSessionId(currentThreadId)
        if (sessionId == null) {
            switchThread(ChatThreadIds.newFree(), null)
        } else {
            viewModelScope.launch {
                switchThread(ChatThreadIds.newTask(sessionId), repository.getSessionById(sessionId))
            }
        }
    }

    /** 会话标题统一命名：首条用户消息首行截断（20 字）；无落库消息 = 新对话 */
    private fun titleFrom(firstMessage: String?): String {
        val firstLine = firstMessage?.trim()
            ?.lines()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            firstLine.isEmpty() -> "新对话"
            firstLine.length <= 20 -> firstLine
            else -> firstLine.take(20) + "…"
        }
    }

    private fun refreshThreads() {
        viewModelScope.launch {
            val summaries = chatMessageDao.getThreadSummaries()
            val firstMessages = chatMessageDao.getFirstUserMessages().associate { it.threadId to it.content }
            val fromDb = summaries.map { s ->
                val sessionId = ChatThreadIds.taskSessionId(s.threadId)
                ChatThreadInfo(
                    threadId = s.threadId,
                    title = titleFrom(firstMessages[s.threadId]),
                    lastTime = s.lastTime,
                    isTask = sessionId != null,
                    taskName = sessionId?.let { repository.getSessionById(it)?.name }
                )
            }
            // 当前线程即使还没有任何落库消息也置顶显示（此时标题必然是"新对话"）；
            // 置顶靠列表头插（下方 listOf(current) + ...），lastTime 必须是真实时间——
            // 哨兵值（如 Long.MAX_VALUE）会被侧栏相对时间格式化成荒诞日期
            val currentSessionId = ChatThreadIds.taskSessionId(currentThreadId)
            val current = ChatThreadInfo(
                threadId = currentThreadId,
                title = titleFrom(firstMessages[currentThreadId]),
                lastTime = System.currentTimeMillis(),
                isTask = currentSessionId != null,
                taskName = currentSessionId?.let { repository.getSessionById(it)?.name }
            )
            _chatThreads.value = listOf(current) + fromDb.filter { it.threadId != currentThreadId }
        }
    }

    /** 用户点击"停止"：取消当前生成（Agent 侧会补齐轮次并保持记忆合法） */
    fun stopGeneration() {
        _uiState.value.pendingQuestion?.let {
            agent?.respondQuestion(it.requestId, UserQuestionAnswer(isSkipped = true))
        }
        _uiState.update { it.copy(pendingQuestion = null) }
        currentJob?.cancel()
        currentJob = null
        // 协程取消只能在挂起点生效——本地模型 prefill/解码期间数秒无挂起点，被动等待
        // 会让「停止」点了没反应；立刻打断原生侧生成（cancelProcess，跨线程安全）
        (activeAiService as? LocalAiService)?.interruptGeneration()
    }

    fun respondConfirm(requestId: String, approved: Boolean) {
        _uiState.update { it.copy(pendingConfirm = null) }
        agent?.respondConfirm(requestId, approved)
    }

    fun submitQuestionAnswer(requestId: String, answers: Map<Int, List<String>>) {
        _uiState.update { it.copy(pendingQuestion = null) }
        agent?.respondQuestion(requestId, UserQuestionAnswer(isSkipped = false, selectedOptions = answers))
    }

    fun skipQuestion(requestId: String) {
        _uiState.update { it.copy(pendingQuestion = null) }
        agent?.respondQuestion(requestId, UserQuestionAnswer(isSkipped = true))
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        val currentAgent = agent ?: run {
            _uiState.update { it.copy(error = "请先在设置中配置 AI 服务") }
            return
        }
        // 本地模型前置检查：模型文件未下载/导入时直接提示，不走一轮注定失败的推理
        _uiState.value.activeProvider?.takeIf { it.isLocal }?.let { config ->
            val path = config.localModelPath
            if (path.isNullOrBlank() || !java.io.File(path).exists()) {
                _uiState.update {
                    it.copy(error = "本地模型文件未就绪，请到 AI 设置页下载或导入（${config.model}）")
                }
                return
            }
        }

        val threadIdAtSend = currentThreadId
        memoryDirty = true
        lastUserMessage = message
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(
                        id = nextMessageId(),
                        role = "user",
                        content = message,
                        isUser = true
                    ),
                    isProcessing = true,
                    error = null,
                    pendingConfirm = null,
                    pendingQuestion = null
                )
            }

            try {
                currentAgent.chat(message).collect { event ->
                    when (event) {
                        is AgentEvent.Thinking -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + ChatMessage(
                                        id = nextMessageId(),
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
                                        id = nextMessageId(),
                                        role = "tool",
                                        content = if (event.argumentsSummary.isBlank()) "正在调用 ${event.toolName}..."
                                        else "调用 ${event.toolName}(${event.argumentsSummary})",
                                        isUser = false,
                                        isToolCall = true,
                                        isLoading = true
                                    )
                                )
                            }
                        }
                        is AgentEvent.ToolCallResult -> {
                            _uiState.update { state ->
                                val firstLine = event.result.lineSequence().firstOrNull()?.take(60) ?: "返回结果"
                                val updatedMessages = state.messages.dropLast(1) + ChatMessage(
                                    id = nextMessageId(),
                                    role = "tool",
                                    content = "${event.toolName} · $firstLine",
                                    isUser = false,
                                    isToolCall = true,
                                    detail = buildString {
                                        append("结果：\n${event.result}")
                                    }
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
                        is AgentEvent.ConfirmTimedOut -> {
                            // 超时后弹窗已失效（再点"执行"是无效操作）：主动关闭，避免"点了没反应"
                            _uiState.update {
                                if (it.pendingConfirm?.requestId == event.requestId) {
                                    it.copy(pendingConfirm = null)
                                } else it
                            }
                        }
                        is AgentEvent.QuestionRequired -> {
                            _uiState.update {
                                it.copy(
                                    pendingQuestion = PendingQuestion(
                                        requestId = event.requestId,
                                        questions = event.questions
                                    )
                                )
                            }
                        }
                        is AgentEvent.QuestionTimedOut -> {
                            _uiState.update {
                                if (it.pendingQuestion?.requestId == event.requestId) {
                                    it.copy(pendingQuestion = null)
                                } else it
                            }
                        }
                        is AgentEvent.Response -> {
                            _uiState.update { state ->
                                val updatedMessages = state.messages.filterNot { msg -> msg.isLoading } + ChatMessage(
                                    id = nextMessageId(),
                                    role = "assistant",
                                    content = event.content,
                                    isUser = false,
                                    cardAction = event.cardAction,
                                    durationMs = event.durationMs,
                                    model = event.model
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
                                            id = nextMessageId(),
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
                                        id = nextMessageId(),
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
                            id = nextMessageId(),
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
                            id = nextMessageId(),
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

    /**
     * 删除会话（侧栏长按）：只删聊天记录，不动任务数据。
     * 删当前线程需先取消在飞生成并清空内存，再删库，最后落到最近剩余会话（全部删空则新开自由会话）。
     */
    fun deleteThread(threadId: String) {
        if (threadId != currentThreadId) {
            viewModelScope.launch {
                chatMessageDao.deleteThread(threadId)
                refreshThreads()
            }
            return
        }
        val job = currentJob
        currentJob = null
        job?.cancel()
        memory.clear()
        memoryDirty = false
        lastUserMessage = null
        _uiState.update { it.copy(messages = emptyList(), error = null, pendingConfirm = null) }
        viewModelScope.launch {
            // 等被取消的在飞任务收尾（其 finally 会先持久化），再删除该线程
            job?.join()
            chatMessageDao.deleteThread(threadId)
            val remaining = chatMessageDao.getThreadSummaries().firstOrNull()
            if (remaining == null) {
                switchThread(ChatThreadIds.newFree(), null)
            } else {
                openThread(remaining.threadId)
            }
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
        lastUserMessage = null
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
                m.role == "user" -> ChatMessage(
                    id = nextMessageId(), role = "user", content = m.content ?: "", isUser = true
                )
                m.role == "assistant" && m.toolCalls == null -> ChatMessage(
                    id = nextMessageId(), role = "assistant", content = m.content ?: "", isUser = false,
                    durationMs = m.durationMs, model = m.model,
                    cardAction = m.cardActionJson?.let { ProductCardListCodec.fromJson(it) }
                )
                else -> null
            }
        }

    /** 重新生成：重发最近一条用户消息（新一轮，旧回复保留可对比；生成中不可触发） */
    fun regenerateLast() {
        val msg = lastUserMessage ?: return
        if (_uiState.value.isProcessing) return
        sendMessage(msg)
    }

    /** 错误重试：与重新生成同机制（失败轮的兜底文案留在上下文，模型知晓刚才失败过） */
    fun retryLast() = regenerateLast()

    /** 设置输入草稿（如快捷输入框上拉进入全屏聊天时将未发送文字带入） */
    fun setDraftMessage(text: String) {
        _uiState.update { it.copy(draftMessage = text) }
    }

    /** 消费草稿 */
    fun clearDraftMessage() {
        _uiState.update { it.copy(draftMessage = "") }
    }

    companion object {
        private const val THREAD_GLOBAL = "global"
    }
}
