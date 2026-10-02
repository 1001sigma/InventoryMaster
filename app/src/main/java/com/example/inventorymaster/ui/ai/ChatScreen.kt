package com.example.inventorymaster.ui.ai

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.pendingActions) {
        uiState.pendingActions.firstOrNull()?.let { action ->
            when (action) {
                is UiAction.NavigateToRecord -> onNavigateToSession(action.sessionId)
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.messages) { message ->
                            ChatMessageItem(message, showThinking)
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
