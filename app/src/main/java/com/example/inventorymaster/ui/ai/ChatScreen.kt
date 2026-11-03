package com.example.inventorymaster.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.viewmodel.ChatViewModel
import com.example.inventorymaster.viewmodel.ChatThreadInfo
import com.example.inventorymaster.ui.components.AppTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateToSession: (Long) -> Unit = {},
    onNavigateToProduct: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val threads by viewModel.chatThreads.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    var inputText by remember { mutableStateOf("") }
    LaunchedEffect(uiState.draftMessage) {
        if (uiState.draftMessage.isNotEmpty()) {
            inputText = uiState.draftMessage
            viewModel.clearDraftMessage()
        }
    }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 产品卡片点击 → 本地详情弹窗（卡片自带数据，无需查询；按需求不强求跳编辑）
    var cardDetail by remember { mutableStateOf<ExpiredProductCardData?>(null) }

    // ── 侧栏会话管理（docs/AI_UI_OPTIMIZATION.md P1-6）──────────────
    // 长按弹出的删除菜单所属线程；null = 无菜单打开
    var menuThreadId by remember { mutableStateOf<String?>(null) }
    // 待确认删除的线程；非空时显示红色确认弹窗
    var deleteTarget by remember { mutableStateOf<ChatThreadInfo?>(null) }
    // 侧栏会话筛选页签（打开抽屉时自动跟随当前线程类型）
    var threadFilter by remember { mutableStateOf(ThreadFilter.FREE) }

    // ── 滚动锚定与脱钩（docs/AI_UI_OPTIMIZATION.md P0-1）──────────────
    // 列表 reverseLayout：数据末条在视觉底部（index 0），scrollToItem(0) 恒贴底。
    var stickToBottom by remember { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= BOTTOM_STICK_THRESHOLD_PX
        }
    }
    // 用户拖动即脱钩：绝不在翻历史时强行拉回底部
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) stickToBottom = false
        }
    }
    // 拖回底部阈值内 → 自动重新锚定
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) stickToBottom = true
    }
    // 新消息到达：锚定时平滑贴底
    LaunchedEffect(uiState.messages.size, stickToBottom) {
        if (stickToBottom && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    // 流式内容增长：锚定时瞬时贴底（无动画，跟随打字机）
    val lastContentLength = uiState.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(lastContentLength, stickToBottom) {
        if (stickToBottom && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    // 切换线程重置锚定
    LaunchedEffect(uiState.currentThreadId) {
        stickToBottom = true
    }

    LaunchedEffect(uiState.pendingActions) {
        uiState.pendingActions.firstOrNull()?.let { action ->
            when (action) {
                is UiAction.NavigateToRecord -> onNavigateToSession(action.sessionId)
                is UiAction.NavigateToSession -> onNavigateToSession(action.sessionId)
                is UiAction.NavigateToProduct -> onNavigateToProduct(action.productKey)
                is UiAction.ShowAnomalies -> onNavigateToSession(action.sessionId)
                is UiAction.ShowSummary -> onNavigateToSession(action.sessionId)
                else -> {}
            }
            viewModel.dismissAction(action)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // 滚动必须挂内层 Column：挂在 Sheet 本体会让 fillMaxHeight 失效、面板高度变包裹内容（实测半截抽屉）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("历史会话", style = MaterialTheme.typography.titleMedium)
                    }
                    // 会话筛选（P1-6）：全局=自由会话 / 任务=任务会话
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        SegmentedButton(
                            selected = threadFilter == ThreadFilter.FREE,
                            onClick = { threadFilter = ThreadFilter.FREE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("全局") }
                        )
                        SegmentedButton(
                            selected = threadFilter == ThreadFilter.TASK,
                            onClick = { threadFilter = ThreadFilter.TASK },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("任务") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val visibleThreads = threads.filter { thread ->
                        if (threadFilter == ThreadFilter.TASK) thread.isTask else !thread.isTask
                    }
                    if (visibleThreads.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (threadFilter == ThreadFilter.TASK) "暂无任务会话" else "暂无全局会话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 时间分组（今天/7天内/更早）；长按弹删除菜单（P1-6）
                    groupThreadsByTime(visibleThreads).forEach { group ->
                        Text(
                            group.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp)
                        )
                        group.items.forEach { thread ->
                            ChatThreadRow(
                                info = thread,
                                selected = thread.threadId == uiState.currentThreadId,
                                menuExpanded = menuThreadId == thread.threadId,
                                onMenuExpandChange = { opened ->
                                    menuThreadId = if (opened) thread.threadId else null
                                },
                                onClick = {
                                    viewModel.openThread(thread.threadId)
                                    scope.launch { drawerState.close() }
                                },
                                onDelete = {
                                    menuThreadId = null
                                    deleteTarget = thread
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // 标题跟随线程：任务会话显示任务名，自由会话显示会话命名
                            val currentInfo = threads.firstOrNull { it.threadId == uiState.currentThreadId }
                            Text(
                                text = currentInfo?.taskName ?: currentInfo?.title ?: "AI 助手",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // 打开抽屉时筛选页签跟随当前线程类型（任务会话 → 任务页签）
                            threadFilter = if (
                                threads.firstOrNull { it.threadId == uiState.currentThreadId }?.isTask == true
                            ) ThreadFilter.TASK else ThreadFilter.FREE
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.History, contentDescription = "历史会话")
                        }
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "新建会话")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!uiState.isConfigured) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "请先在设置中配置 AI 服务",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "设置 → AI 助手 → 填写 API Key",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (uiState.messages.isEmpty()) {
                            // 空对话引导：填充居中空白，给出具体建议与上下文指引
                            val currentInfo = threads.firstOrNull { it.threadId == uiState.currentThreadId }
                            val isTask = currentInfo?.isTask == true
                            val suggestions = if (isTask) {
                                listOf(
                                    "📊 统计当前盘点进度",
                                    "⚠️ 查询近效期与过期记录",
                                    "🔍 检查未盘与异常产品"
                                )
                            } else {
                                listOf(
                                    "🔍 搜索产品库字典",
                                    "📋 列出所有盘点任务",
                                    "💡 医疗器械 UDI 解码规则"
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isTask) "任务专属助手" else "AI 库存助手",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isTask) "已连接任务【${currentInfo?.taskName ?: "当前任务"}】"
                                    else "支持查询产品字典、盘点记录与多任务分析",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                suggestions.forEach { suggestion ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                inputText = suggestion.substringAfter(" ")
                                            }
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                reverseLayout = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                            ) {
                            items(uiState.messages.asReversed(), key = { it.id }) { message ->
                                ChatMessageItem(
                                    message = message,
                                    showThinking = showThinking,
                                    isProcessing = uiState.isProcessing,
                                    canRetry = uiState.messages.any { it.isUser },
                                    onRegenerate = { viewModel.regenerateLast() },
                                    onRetry = { viewModel.retryLast() },
                                    onCardProductClick = { productData ->
                                        cardDetail = productData
                                    }
                                )
                            }
                        }
                    }

                        // 回底悬浮钮：脱离底部时淡入；流式中有新内容时右上角呼吸点提示
                        // （全限定调用：避免命中外层 ColumnScope 的 AnimatedVisibility 扩展重载）
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isAtBottom && uiState.messages.isNotEmpty(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp),
                            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f)
                        ) {
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 2.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch { listState.animateScrollToItem(0) }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "回到底部",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (uiState.isProcessing) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(10.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    // Edge-to-Edge 下键盘弹起时覆盖导航栏，底边距取 ime 与 navigationBars 较大值，紧贴键盘且收起时不压手势条
                    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottomInset = maxOf(imeBottom, navBottom)

                    // 问答交互全宽底部面板（ask_question 挂起态独占底部操作区）
                    if (uiState.pendingQuestion != null) {
                        InteractiveQuestionCard(
                            pending = uiState.pendingQuestion!!,
                            onSubmit = { answers ->
                                viewModel.submitQuestionAnswer(uiState.pendingQuestion!!.requestId, answers)
                            },
                            onSkip = {
                                viewModel.skipQuestion(uiState.pendingQuestion!!.requestId)
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = bottomInset)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = "输入问题....",
                                enabled = !uiState.isProcessing,
                                singleLine = false,
                                // P1-9 输入框自动增高（docs/AI_UI_OPTIMIZATION.md）：随内容 1→6 行生长，超出内部滚动
                                minLines = 1,
                                maxLines = 6
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (uiState.isProcessing) {
                                // 生成中：发送位变中性圆形停止钮（全程可取消，docs/AI_V3_REDESIGN.md §4.11）
                                // 视觉 36dp 圆 + IconButton 默认 48dp 触控区（不缩水）
                                IconButton(onClick = { viewModel.stopGeneration() }) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Stop,
                                                contentDescription = "停止生成",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    },
                                    enabled = inputText.isNotBlank()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "发送",
                                        tint = if (inputText.isNotBlank())
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CONFIRM 权限工具确认弹窗（docs/AI_V3_REDESIGN.md §4.7）
    uiState.pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { viewModel.respondConfirm(confirm.requestId, approved = false) },
            title = { Text("确认执行操作") },
            text = {
                Column {
                    Text(
                        "AI 请求调用「${confirm.toolName}」，请确认以下参数：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            confirm.argumentsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondConfirm(confirm.requestId, approved = true) }) {
                    Text("执行")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondConfirm(confirm.requestId, approved = false) }) {
                    Text("取消")
                }
            }
        )
    }

    // 产品卡片详情弹窗（现代化全字段卡片弹窗，数据直接来自卡片）
    cardDetail?.let { card ->
        CardDetailDialog(
            card = card,
            onDismiss = { cardDetail = null }
        )
    }

    // 侧栏长按删除 → 红色确认弹窗（仅删聊天记录，不动任务数据）
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除会话") },
            text = {
                Text(
                    buildString {
                        append("「${target.title}」的对话记录将被删除，且不可恢复。")
                        if (target.isTask) append("仅删除对话记录，不影响任务数据。")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteThread(target.threadId)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 距底多少 px 内视为"在底部"（docs/AI_UI_OPTIMIZATION.md 设计基线） */
private const val BOTTOM_STICK_THRESHOLD_PX = 200

private data class ChatThreadGroup(val label: String, val items: List<ChatThreadInfo>)

/** 侧栏会话筛选页签：全局 = 自由会话，任务 = 任务会话（P1-6） */
private enum class ThreadFilter { FREE, TASK }

/** 会话按最近活跃时间分组：今天 / 7 天内 / 更早（threads 已按 lastTime 降序，组内顺序保留） */
private fun groupThreadsByTime(threads: List<ChatThreadInfo>): List<ChatThreadGroup> {
    if (threads.isEmpty()) return emptyList()
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val startOfToday = calendar.timeInMillis
    val startOf7Days = startOfToday - 6L * 24 * 3600 * 1000
    val byLabel = threads.groupBy { thread ->
        when {
            thread.lastTime >= startOfToday -> "今天"
            thread.lastTime >= startOf7Days -> "7 天内"
            else -> "更早"
        }
    }
    return listOf("今天", "7 天内", "更早").mapNotNull { label ->
        byLabel[label]?.let { ChatThreadGroup(label, it) }
    }
}

/** 会话行相对时间：今天显示 HH:mm，今年显示 M月d日，更早带年份；0/负值与未来时间（脏数据/哨兵值）不显示 */
private fun formatThreadTime(lastTime: Long): String {
    if (lastTime <= 0L || lastTime > System.currentTimeMillis() + 60_000L) return ""
    val calendar = java.util.Calendar.getInstance()
    val nowYear = calendar.get(java.util.Calendar.YEAR)
    val nowDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    calendar.timeInMillis = lastTime
    val year = calendar.get(java.util.Calendar.YEAR)
    val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    val format = when {
        nowYear == year && nowDay == day -> "HH:mm"
        nowYear == year -> "M月d日"
        else -> "yyyy/M/d"
    }
    return java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).format(java.util.Date(lastTime))
}

/** 侧栏会话条目：NavigationDrawerItem 不支持长按，用 Surface + combinedClickable 自绘（样式对齐 M3 抽屉行） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRow(
    info: ChatThreadInfo,
    selected: Boolean,
    menuExpanded: Boolean,
    onMenuExpandChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { onMenuExpandChange(true) }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 任务会话徽标：所属任务名
                    if (info.isTask) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = info.taskName ?: "任务",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 96.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formatThreadTime(info.lastTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = onDelete
            )
        }
    }
}

/** 现代产品卡片全字段详情弹窗 */
@Composable
private fun CardDetailDialog(
    card: ExpiredProductCardData,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. 标题与规格
                Text(
                    text = card.productName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (card.specification.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = card.specification,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. 徽标行（任务、库位、查验状态）
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val taskLabel = card.sessionName.ifBlank {
                        if (card.sessionId.isNotBlank()) "任务 #${card.sessionId}" else ""
                    }
                    if (taskLabel.isNotBlank()) {
                        StatusTag(
                            text = taskLabel,
                            bgColor = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (card.location.isNotBlank()) {
                        StatusTag(
                            text = card.location,
                            bgColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (card.isVerified) {
                        StatusTag(
                            text = "已查验",
                            bgColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF2E7D32)
                        )
                    } else {
                        StatusTag(
                            text = "未查验",
                            bgColor = Color(0xFFFFF3E0),
                            textColor = Color(0xFFE65100)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 核心指标卡片（数量与有效期至）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 库存数量
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${formatCardQty(card.quantity)} ${card.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "库存数量",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 有效期至
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = card.expiryDateStr.ifBlank { "未标明" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "有效期至",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 详细字段网格列表（带滚动保护，防止长内容溢出）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductDetailGridRow("生产厂家", card.manufacturer.ifBlank { "—" })
                    ProductDetailGridRow("存放库位", card.location.ifBlank { "未指定" })
                    ProductDetailGridRow("生产批号", card.batchNumber.ifBlank { "无" })
                    ProductDetailGridRow("生产日期", card.productionDateStr.ifBlank { "—" })
                    ProductDetailGridRow("物料编码", card.materialCode.ifBlank { "—" })
                    ProductDetailGridRow("DI / 编码", card.di.ifBlank { card.productKey.ifBlank { "—" } })
                    ProductDetailGridRow(
                        "所属任务",
                        card.sessionName.ifBlank { if (card.sessionId.isNotBlank()) "任务 #${card.sessionId}" else "—" }
                    )
                    if (card.remarks.isNotBlank()) {
                        ProductDetailGridRow("备注信息", card.remarks)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 底部关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailGridRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun formatCardQty(qty: Double): String {
    return if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)
}
