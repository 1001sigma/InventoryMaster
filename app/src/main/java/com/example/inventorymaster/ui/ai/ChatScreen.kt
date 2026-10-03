package com.example.inventorymaster.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.UiAction
import com.example.inventorymaster.viewmodel.ChatViewModel
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
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 产品卡片点击 → 本地详情弹窗（卡片自带数据，无需查询；按需求不强求跳编辑）
    var cardDetail by remember { mutableStateOf<ExpiredProductCardData?>(null) }

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
                Text(
                    "历史会话",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                if (threads.isEmpty()) {
                    Text(
                        "暂无历史会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                threads.forEach { thread ->
                    NavigationDrawerItem(
                        label = { Text(thread.title) },
                        selected = thread.threadId == uiState.currentThreadId,
                        onClick = {
                            viewModel.openThread(thread.threadId)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
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
                            Text("AI 助手")
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.History, contentDescription = "历史会话")
                        }
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空对话")
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
                                    onRetry = { viewModel.retryLast() }
                                )
                                message.cardAction?.let { cardPayload ->
                                    ProductCardListWidget(
                                        cardPayload = cardPayload,
                                        onCardProductClick = { productData ->
                                            cardDetail = productData
                                        },
                                        modifier = Modifier.padding(start = 16.dp, end = 48.dp, bottom = 8.dp)
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = "输入问题....",
                            enabled = !uiState.isProcessing,
                            singleLine = false,
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (uiState.isProcessing) {
                            // 生成中：发送按钮变停止按钮（全程可取消，docs/AI_V3_REDESIGN.md §4.11）
                            IconButton(onClick = { viewModel.stopGeneration() }) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "停止",
                                    tint = MaterialTheme.colorScheme.error
                                )
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

    // CONFIRM 权限工具确认弹窗（docs/AI_V3_REDESIGN.md §4.7）
    uiState.pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { viewModel.respondConfirm(confirm.requestId, approved = false) },
            title = { Text("确认执行操作") },
            text = {
                Text("AI 请求执行：${confirm.toolName}\n\n参数：${confirm.argumentsSummary}")
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

    // 产品卡片详情弹窗（数据直接来自卡片，不依赖 recordId 查询）
    cardDetail?.let { card ->
        AlertDialog(
            onDismissRequest = { cardDetail = null },
            title = { Text(card.productName) },
            text = {
                Text(
                    "批号：${card.batchNumber.ifBlank { "无" }}\n" +
                        "数量：${"%.0f".format(card.quantity)}\n" +
                        "有效期：${card.expiryDateStr}\n" +
                        "产品编码：${card.productKey.ifBlank { "无" }}"
                )
            },
            confirmButton = {
                TextButton(onClick = { cardDetail = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

/** 距底多少 px 内视为"在底部"（docs/AI_UI_OPTIMIZATION.md 设计基线） */
private const val BOTTOM_STICK_THRESHOLD_PX = 200
