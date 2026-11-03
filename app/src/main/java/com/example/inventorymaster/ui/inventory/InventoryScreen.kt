package com.example.inventorymaster.ui.inventory

// --- 请确保包含以下所有 import ---
import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.text.style.TextOverflow
import com.example.inventorymaster.ui.components.AppTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import com.example.inventorymaster.ui.ai.MarkdownText
import com.example.inventorymaster.viewmodel.ChatViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.ui.analyzer.ScanScreen
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.ModernDropdownMenu
import com.example.inventorymaster.ui.inventory.components.AddRecordDialog
import com.example.inventorymaster.ui.inventory.components.AuditRecordDialog
import com.example.inventorymaster.ui.inventory.components.RecordDetailDialog
import com.example.inventorymaster.ui.inventory.components.RecordOptionDialog
import com.example.inventorymaster.ui.inventory.components.SearchBarArea
import com.example.inventorymaster.ui.inventory.components.StockRecordItem
import com.example.inventorymaster.data.model.isPendingStart
import com.example.inventorymaster.ui.sync.DownloadConflictDialog
import com.example.inventorymaster.ui.sync.SyncDialog
import com.example.inventorymaster.ui.components.EmptyStateView
import com.example.inventorymaster.ui.components.semanticFg
import com.example.inventorymaster.ui.theme.StatusPending
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import com.example.inventorymaster.utils.ExcelUtils
import com.example.inventorymaster.ui.InventoryCalcDialog
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel
import com.example.inventorymaster.viewmodel.SyncIntent
import com.example.inventorymaster.viewmodel.SyncViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    sessionId: Long,
    onBackClick: () -> Unit,
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    syncViewModel: SyncViewModel,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onNavigateToAiChat: (Long) -> Unit = {}
) {
    // 1. 监听数据状态
    val inventoryStateUI by inventoryViewModel.uiState.collectAsState()
    val sessionStateUI by sessionViewModel.uiState.collectAsState()
    val uiState by inventoryViewModel.uiState.collectAsState()
    // 2. 本地 UI 状态
    var searchQuery by remember { mutableStateOf("") }
    // 默认为盘库模式 (true=盘库, false=查询)
    LaunchedEffect(inventoryStateUI.searchQuery) {
        if (searchQuery != inventoryStateUI.searchQuery) {
            searchQuery = inventoryStateUI.searchQuery
        }
    }
    var showScanScreen by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }

    // 快捷计算器：记录最后计算结果，退出盘库时清零
    var lastCalcResult by remember { mutableStateOf<String?>(null) }
    var showQuickCalc by remember { mutableStateOf(false) }

    // [新增] 控制右上角“更多功能”下拉菜单的显示
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFinishConfirmDialog by remember { mutableStateOf(false) }

    val currentSession = sessionStateUI.sessions.find { it.session.id == sessionId }
    val sessionStatus = currentSession?.session?.status ?: 0 // 0=进行中
    // 服务端下发的待开始任务（等待管理员点开始）
    val isPendingStart = currentSession?.session?.isPendingStart ?: false
    // 只读 = 已归档/已锁定，或服务端任务处于待开始
    val isReadOnly = sessionStatus > 0 || isPendingStart
    // M9：去除盘库/查询模式手动切换——可编辑任务恒为盘库模式，只读任务恒为查询视角
    val isInventoryMode = !isReadOnly

    // 进入任务页时初始化选中任务，并加载全部记录（空查询=全量上屏且带效期徽标，空态文案恢复真实语义）
    LaunchedEffect(sessionId) {
        inventoryViewModel.selectSession(sessionId)
        inventoryViewModel.performSearch("", isInventoryMode)
    }

    // 管理键盘焦点
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
            lastCalcResult = null
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    // [新增] 控制“操作选项弹窗”显示
    var showOptionDialog by remember { mutableStateOf(false) }
    // [新增] 记录当前选中的是哪条记录 (为了传给编辑框或删除)
    var selectedRecord by remember { mutableStateOf<StockRecordCombined?>(null) }

    // [新增] 控制“编辑弹窗”显示 (其实就是复用 AddRecordDialog)
    var showEditDialog by remember { mutableStateOf(false) }

    var isQuickCheckMode by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // M9：Excel 导入已迁至「新建任务弹窗」（SessionListScreenNav），详情页不再有导入入口

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. 监听生命周期：进页面自动同步 / 切回来自动同步
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 每次回到页面，自动静默拉取一次最新数据
//                syncViewModel.handleIntent(SyncIntent.Pull(sessionId))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 2. 监听 ViewModel 的同步状态 (用于控制下拉刷新的转圈圈停止)
    val syncState by syncViewModel.uiState.collectAsState()
    val isSyncing = syncState.isSyncing
    LaunchedEffect(syncState.isSuccess) {
        if (syncState.isSuccess) {
            inventoryViewModel.performSearch(searchQuery, isInventoryMode)
        }
    }
    LaunchedEffect(syncState.syncMessage) {
        syncState.syncMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            syncViewModel.clearMessage() // 弹完必须清空，不然屏幕旋转又会弹
        }
    }
    // 自动上传已改为 InventoryViewModel 内防抖直推（M0 修复），此处不再监听 autoPushSessionId 信号

    val onScanClick = com.example.inventorymaster.utils.permission.rememberPermissionAction(
        permission = com.example.inventorymaster.utils.permission.AppPermission.CAMERA
    ) {
        showScanScreen = true
    }

    // M9：Excel 导入文件选择器已随导入流程迁至 SessionListScreenNav（新建任务弹窗）

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            inventoryViewModel.exportExcelFile(context, it, sessionId)
            Toast.makeText(context, "后台已导出", Toast.LENGTH_SHORT).show()
        }
    }

    // 👇 新增：剪贴板管理器 (用于一键复制)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // 👇 新增：异常 DI 专用导出 Launcher
    val errorExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            // 直接在 UI 层开个后台协程把错误列表写进 Excel
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        com.example.inventorymaster.utils.ExcelUtils.exportExcel(
                            outputStream = outputStream,
                            dataList = uiState.invalidDiList, // 只导出错误数据
                            originalSchema = null,
                            operatorName = "异常校验导出"
                        )
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "异常数据已成功导出", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // M9：导入确认弹窗已迁至 SessionListScreenNav（导入只在新建任务时触发）

    // 👇 控制弹窗显示的开关
    var showUploadDialog by remember { mutableStateOf(false) }
    // 👇 监听消息：如果 ViewModel 发消息了，就弹 Toast
    LaunchedEffect(inventoryStateUI.userMessage) {
        inventoryStateUI.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            inventoryViewModel.clearUserMessage() // 弹完就清空
            showUploadDialog = false     // 关闭弹窗
        }
    }

    var showSyncDialog by remember { mutableStateOf(false) }



    // ── 悬浮 AI 球（长按顶栏图标脱落并可直接拖动；拖动吸边；拖回顶栏/长按收回）──
    // active/offset 用 rememberSaveable：进聊天再返回（导航出组合）后悬浮球保持不丢（实测反馈修复）
    val aiDensity = LocalDensity.current
    val aiScope = rememberCoroutineScope()
    var aiBubbleActive by rememberSaveable { mutableStateOf(false) }
    var aiBubbleOffsetX by rememberSaveable { mutableStateOf(0f) }
    var aiBubbleOffsetY by rememberSaveable { mutableStateOf(0f) }
    var aiIconDragInProgress by remember { mutableStateOf(false) }
    var aiSurfaceState by remember { mutableStateOf(AiSurfaceState.FLOATING) }
    // 本面板发出的提问（回答卡显示开关）；回 FLOATING 时清空
    var aiQuickAsked by remember { mutableStateOf<String?>(null) }
    var aiIconCenter by remember { mutableStateOf(Offset.Zero) }
    var aiScreenPx by remember { mutableStateOf(IntSize.Zero) }
    var aiSnapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun aiCancelSnap() {
        aiSnapJob?.cancel()
    }

    /** 松手后 X 轴吸附到最近的左/右边缘（150ms 动画） */
    fun aiSnapToEdge() {
        val bubblePx = with(aiDensity) { AiSurfaceDimens.BubbleSize.toPx() }
        val marginPx = with(aiDensity) { 8.dp.toPx() }
        val targetX = if (aiBubbleOffsetX + bubblePx / 2 < aiScreenPx.width / 2f) marginPx
        else aiScreenPx.width - bubblePx - marginPx
        aiSnapJob = aiScope.launch {
            Animatable(aiBubbleOffsetX).animateTo(targetX, tween(150)) {
                aiBubbleOffsetX = value
            }
        }
    }

    /**
     * 图标上长按拖动结束：几乎未移动 = 仅脱落（悬浮球停在原地）；已移动 = 按位置决定
     * 收回顶栏（球心进入顶栏区）或吸附边缘
     */
    fun aiFinishIconDrag(movedPx: Float) {
        aiIconDragInProgress = false
        if (movedPx <= 40f) return
        val bubblePx = with(aiDensity) { AiSurfaceDimens.BubbleSize.toPx() }
        val dockTopPx = aiIconCenter.y + with(aiDensity) { 28.dp.toPx() }
        if (aiBubbleOffsetY + bubblePx / 2 < dockTopPx) {
            aiBubbleActive = false
        } else {
            aiSnapToEdge()
        }
    }

    // Morph 状态推进：动画播完进入稳定态；回 FLOATING 清掉本面板提问（回答卡随之收起）
    LaunchedEffect(aiSurfaceState) {
        when (aiSurfaceState) {
            AiSurfaceState.EXPANDING -> {
                delay(AiSurfaceMotion.EXPAND_TOTAL.toLong())
                aiSurfaceState = AiSurfaceState.QUICK_INPUT
            }
            AiSurfaceState.COLLAPSING -> {
                delay(AiSurfaceMotion.COLLAPSE_TOTAL.toLong())
                aiSurfaceState = AiSurfaceState.FLOATING
                aiQuickAsked = null
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { aiScreenPx = it.size }
    ) {
    Scaffold(
        topBar = {
            Column {
                // 顶部标题栏
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isInventoryMode) Icons.Default.Inventory2 else Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isInventoryMode) "盘库模式" else "查询模式")
                            // [新增] 状态标签
                            if (isPendingStart) {
                                Text(" (等待开始)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (sessionStatus == 1) {
                                Text(" (已归档)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (sessionStatus == 2) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(" 已锁定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    // [新增 actions]
                    actions = {
                        //region 按钮 1: 测试增量上传
//                        Button(onClick = {
//                            inventoryViewModel.triggerPush(inventoryStateUI.currentSessionId!!)
//                        }) {
//                            Text("测上传(Push)")
//                        }
//
//                        // 按钮 2: 测试增量下载
//                        Button(onClick = {
//                            inventoryViewModel.refreshData(inventoryStateUI.currentSessionId!!)
//                        }) {
//                            Text("测下载(Pull)")
//                        }
//                        // 显示同步结果提示
//                        inventoryStateUI.userMessage?.let {
//                            Text(
//                                text = "状态: $it",
//                                color = Color.Red,
//                                modifier = Modifier.padding(8.dp),
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
                        //endregion 🔥🔥🔥【临时测试区】结束 🔥🔥🔥
                        // 快捷计算器入口（盘库模式可用；计算完成后以胶囊显示结果，退出盘库时清零恢复图标）
                        if (isInventoryMode) {
                            if (lastCalcResult == null) {
                                IconButton(onClick = { showQuickCalc = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = "计算器",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { showQuickCalc = true }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = "计算器",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lastCalcResult ?: "",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 64.dp)
                                    )
                                }
                            }
                        }

                        // AI 助手入口：单击进聊天；长按脱落成悬浮球且不松手可直接拖动
                        // （拖动期间图标保留但透明——立即隐藏会销毁手势节点、中断拖拽流）
                        if (!aiBubbleActive || aiIconDragInProgress) {
                            val haptic = LocalHapticFeedback.current
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer { alpha = if (aiBubbleActive) 0f else 1f }
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onNavigateToAiChat(sessionId) })
                                    }
                                    .pointerInput(Unit) {
                                        var movedPx = 0f
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { _ ->
                                                aiCancelSnap()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                movedPx = 0f
                                                val bubblePx = with(aiDensity) { AiSurfaceDimens.BubbleSize.toPx() }
                                                if (aiIconCenter != Offset.Zero) {
                                                    aiBubbleOffsetX = aiIconCenter.x - bubblePx / 2
                                                    aiBubbleOffsetY = aiIconCenter.y - bubblePx / 2
    
                                                    aiBubbleOffsetX = aiScreenPx.width - bubblePx - with(aiDensity) { 8.dp.toPx() }
                                                    aiBubbleOffsetY = with(aiDensity) { 160.dp.toPx() }
                                                }
                                                aiIconDragInProgress = true
                                                aiBubbleActive = true
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                movedPx += abs(dragAmount.x) + abs(dragAmount.y)
                                                aiBubbleOffsetX += dragAmount.x
                                                aiBubbleOffsetY += dragAmount.y
                                            },
                                            onDragEnd = { aiFinishIconDrag(movedPx) },
                                            onDragCancel = { aiFinishIconDrag(movedPx) }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI 助手（长按拖出悬浮球）",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.onGloballyPositioned { aiIconCenter = it.boundsInRoot().center }
                                )
                            }
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多功能")
                        }

                        // 展开的下拉菜单
                        ModernDropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            // --- 第一部分：原来的顶部操作按钮 ---
                            if (!isReadOnly) {
                                DropdownMenuItem(
                                    text = { Text("校验DI") },
                                    leadingIcon = { Icon(Icons.Default.ContactMail, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        inventoryViewModel.validateCurrentTaskDIs()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("同步数据") },
                                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showSyncDialog = true
                                    }
                                )
                                // M9：「导入Excel」入口已移除——导入只在新建本地任务时进行（SessionListScreenNav）
                            }

                            DropdownMenuItem(
                                text = { Text("导出Excel") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    exportLauncher.launch("${LocalDate.now()}_盘点数据_${sessionId}.xlsx")
                                }
                            )

                            if (!isReadOnly) {
                                DropdownMenuItem(
                                    text = { Text("完成盘点 (切换只读)") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            sessionViewModel.tryFinishSession(sessionId, force = true)
                                            Toast.makeText(context, "已完成个人盘点，已切换为查询模式", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            // --- 分割线 ---
                            if (!isReadOnly) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // --- 第二部分：原来的滑动开关 (Switch) ---
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (uiState.isDiValidationEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (uiState.isDiValidationEnabled) "DI 严格校验" else "DI 校验关闭",
                                                color = if (uiState.isDiValidationEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = uiState.isDiValidationEnabled,
                                            onCheckedChange = { inventoryViewModel.toggleDiValidation(it) }
                                        )
                                    },
                                    onClick = { inventoryViewModel.toggleDiValidation(!uiState.isDiValidationEnabled) }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isQuickCheckMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isQuickCheckMode) "极速模式" else "普通模式",
                                                color = if (isQuickCheckMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = isQuickCheckMode,
                                            onCheckedChange = { isQuickCheckMode = it }
                                        )
                                    },
                                    onClick = { isQuickCheckMode = !isQuickCheckMode }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isInventoryMode)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    )
                )

                // M9：盘库/查询模式手动切换已移除——可编辑任务恒为盘库模式，仅只读任务显示横幅
                if (isReadOnly) {
                    val bannerColor = if (isPendingStart) StatusPending else StatusWarning
                    Box(modifier = Modifier.fillMaxWidth().background(bannerColor.copy(alpha = 0.15f)).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (isPendingStart) "任务等待管理员开始，暂仅供查询" else "当前为归档状态，仅供查询",
                            color = semanticFg(bannerColor)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // 只在盘库模式下显示“盲盘/新增”按钮
            if (isInventoryMode) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "手动新增")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 3. 搜索栏区域
            SearchBarArea(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    // 触发 ViewModel 的搜索逻辑
                    inventoryViewModel.performSearch(searchQuery, isInventoryMode)
                    focusManager.clearFocus() // 收起键盘
                },
                onClear = {
                    inventoryViewModel.performSearch("", isInventoryMode)
                },
                isInventoryMode = isInventoryMode,
                onScanClick = onScanClick
            )

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isInventoryMode && inventoryStateUI.searchResults.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            // M9：当前搜索结果的数量合计（只加账面数量 quantity，随搜索实时变化）
                            val totalQuantity = inventoryStateUI.searchResults.sumOf { it.combined.record.quantity }
                            val totalText = if (totalQuantity == totalQuantity.toLong().toDouble()) {
                                totalQuantity.toLong().toString()
                            } else {
                                totalQuantity.toString()
                            }
                            Text(
                                text = "合计: $totalText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    @OptIn(ExperimentalMaterial3Api::class)
                    PullToRefreshBox(
                        isRefreshing = isSyncing, // 直接绑定 ViewModel 的状态
                        onRefresh = {
                            // 触发刷新逻辑
                            syncViewModel.handleIntent(SyncIntent.Pull(sessionId))
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { // 4. 结果列表区域
                        LazyColumn(modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)) {
                            items(items = inventoryStateUI.searchResults, key = { it.combined.record.id }) { uiModel ->
                                // 判断是否已查验 (备注里包含 "已查验")
                                val combined = uiModel.combined
                                val record = combined.record
                                val isChecked = record.isVerified || record.actualQuantity != null || record.remarks?.contains("已查验") == true
                                val actionWidth = 84.dp
                                Card(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp) // 卡片间距
                                        .clip(MaterialTheme.shapes.small) // 【关键】裁剪内容，保证右边按钮的角也是圆的
                                        .combinedClickable(
                                            onClick = {
                                                /* 普通点击逻辑（M9：模式切换已移除） */
                                                when {
                                                    // 1. 只读任务：点击直接看详情
                                                    isReadOnly -> {
                                                        selectedRecord = combined
                                                        showDetailDialog = true
                                                    }
                                                    // 2. 盘点-普通模式：手动记录弹操作菜单，Excel/云端弹编辑弹窗（详情从操作弹窗进）
                                                    !isQuickCheckMode -> {
                                                        selectedRecord = combined
                                                        if (record.sourceType == 0) {
                                                            showOptionDialog = true
                                                        } else {
                                                            showEditDialog = true
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                // 盘点-极速模式：手动记录弹出操作菜单，Excel/云端弹出查验弹窗
                                                if (!isReadOnly && isQuickCheckMode) {
                                                    selectedRecord = combined
                                                    if (record.sourceType == 0) {
                                                        showOptionDialog = true
                                                    } else {
                                                        showEditDialog = true
                                                    }
                                                }
                                            }
                                        ),
                                    shape = MaterialTheme.shapes.small,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    // 与任务卡/设置页预览卡统一：surface 底 + outlineVariant 边框 + 轻阴影
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    // 2. 内部使用 Row + IntrinsicSize.Min
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min), // 【关键】让高度跟随内容，且允许子元素填满高度
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // === 左边：内容区域 ===
                                        // 使用 weight(1f)，当右边没有东西时，它会自动占满 100% 宽度
                                        Box(modifier = Modifier.weight(1f)) {
                                            // 这里引用刚才修改过的、没有 Card 包裹的 StockRecordItem
                                            StockRecordItem(uiModel = uiModel, cardConfig = inventoryStateUI.cardConfig)
                                        }
                                        // === 右边：查验按钮 (条件显示) ===
                                        if (isInventoryMode && isQuickCheckMode && !isReadOnly) {
                                            // 只有满足条件时，这个 block 才会渲染
                                            // 渲染时，左边的 weight(1f) 会让出空间；不渲染时，左边占满
                                            if (isChecked) {
                                                val haptic = LocalHapticFeedback.current
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .background(StatusSuccess.copy(alpha = 0.12f))
                                                        .width(actionWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                Toast.makeText(context, "当前已查验（长按可撤销）", Toast.LENGTH_SHORT).show()
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                inventoryViewModel.cancelVerification(combined)
                                                                Toast.makeText(context, "已撤销查验，实盘数已转存备注", Toast.LENGTH_SHORT).show()
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "已查验",
                                                            tint = semanticFg(StatusSuccess),
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "已查验",
                                                            color = semanticFg(StatusSuccess),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "长按撤销",
                                                            color = semanticFg(StatusSuccess).copy(alpha = 0.75f),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            fontWeight = FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    onClick = { inventoryViewModel.quickCheck(combined) },
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(actionWidth),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "查验",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
//                           HorizontalDivider()
                            }
                            if (!inventoryStateUI.isLoading && inventoryStateUI.searchResults.isEmpty()) {
                                item(key = "empty_state") {
                                    EmptyStateView(
                                        message = if (searchQuery.isNotBlank()) "未找到匹配的记录，试试其他关键词"
                                        else if (inventoryStateUI.emptySearchShowsNothing) "已开启空搜索隐藏，请输入关键词或扫码查看物料"
                                        else "暂无盘点记录，点击右下角按钮开始扫码",
                                        icon = if (searchQuery.isNotBlank()) Icons.Default.SearchOff
                                        else if (inventoryStateUI.emptySearchShowsNothing) Icons.Default.Search
                                        else Icons.Default.Inventory2,
                                        modifier = Modifier.fillParentMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (inventoryStateUI.isLoading && !isSyncing) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 外部容器：处理嵌套滚动

            if (showSyncDialog) {
                SyncDialog(
                    onDismiss = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        showSyncDialog = false
                    },
                    initialIp = syncState.lastServerIp.ifEmpty { "192.168." },
                    initialToken = syncState.lastServerToken,
                    uploadLocked = isReadOnly,  // M7（R5）：归档任务上传置灰；待开始任务同样锁定上传
                    lockReason = if (isPendingStart) "等待开始" else "已归档",
                    onUpload = { ip, token ->
                        syncViewModel.handleIntent(SyncIntent.UploadToServer(ip, token, sessionId))
                        showSyncDialog = false
                    },
                    onDownload = { ip, token ->
                        syncViewModel.handleIntent(SyncIntent.DownloadFromPC(ip, token, sessionId))
                        showSyncDialog = false
                    }
                )
            }

            syncState.pendingDownloadConflict?.let { conflict ->
                DownloadConflictDialog(
                    dirtyCount = conflict.dirtyCount,
                    onForceDownload = {
                        syncViewModel.handleIntent(SyncIntent.ConfirmForceDownload(conflict.ip, conflict.sessionId))
                    },
                    onPushThenDownload = {
                        syncViewModel.handleIntent(SyncIntent.PushThenDownload(conflict.ip, conflict.sessionId))
                    },
                    onDismiss = { syncViewModel.dismissConflict() }
                )
            }
        }
    }

        // AI Surface（悬浮球 ⇄ 快捷输入胶囊 Morph 一体对象）：scrim + 回答卡 + 球/胶囊统一渲染
        if (aiBubbleActive) {
            AiAssistantSurface(
                state = aiSurfaceState,
                screenPx = aiScreenPx,
                bubbleOffset = Offset(aiBubbleOffsetX, aiBubbleOffsetY),
                onBubbleOffsetChange = {
                    aiBubbleOffsetX = it.x
                    aiBubbleOffsetY = it.y
                },
                onClickBubble = {
                    // 绑定当前任务线程（快捷问答走该任务上下文）
                    chatViewModel.enterFromSession(sessionId)
                    aiSurfaceState = AiSurfaceState.EXPANDING
                },
                onDock = { aiBubbleActive = false },
                onDragStarted = { aiCancelSnap() },
                onDragFinished = {
                    val bubblePx = with(aiDensity) { AiSurfaceDimens.BubbleSize.toPx() }
                    val dockTopPx = aiIconCenter.y + with(aiDensity) { 28.dp.toPx() }
                    if (aiBubbleOffsetY + bubblePx / 2 < dockTopPx) {
                        aiBubbleActive = false
                    } else {
                        aiSnapToEdge()
                    }
                },
                onDismiss = {
                    keyboardController?.hide()
                    aiSurfaceState = AiSurfaceState.COLLAPSING
                },
                onOpenFullChat = { onNavigateToAiChat(sessionId) },
                onSend = { text ->
                    aiQuickAsked = text
                    chatViewModel.sendMessage(text)
                },
                asked = aiQuickAsked,
                viewModel = chatViewModel
            )
        }
    }

    // M9：模板选择/列映射对话框已迁至 SessionListScreenNav

    //挂载对话框
    if (showAddDialog) {
        AddRecordDialog(
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showAddDialog = false
            },
            sessionId = sessionId,
            onConfirm = { record ->
                inventoryViewModel.addRecord(record)
                showAddDialog = false
            }
        )
    }

    // 1. 操作选项弹窗 (盘库模式-普通模式用)
    if (showOptionDialog && selectedRecord != null) {
        // 判断能不能删：只有 (sourceType == 0) 也就是手动的数据才能删
        val canDelete = (selectedRecord!!.record.sourceType == 0)

        RecordOptionDialog(
            onDismiss = { showOptionDialog = false },
            onShowDetail = {
                showOptionDialog = false
                showDetailDialog = true
            },
            onEdit = {
                showOptionDialog = false
                showEditDialog = true
            },
            onDelete = {
                selectedRecord?.let { inventoryViewModel.deleteRecord(it.record) }
                showOptionDialog = false
            },
            canDelete = canDelete // 传入权限判断
        )
    }

    // 2. 详情弹窗 (查询模式用)
    if (showDetailDialog && selectedRecord != null) {
        RecordDetailDialog(
            combined = selectedRecord!!,
            onDismiss = { showDetailDialog = false }
        )
    }

    // [新增] 编辑弹窗 (复用 AddRecordDialog，但带入初始值)
    if (showEditDialog && selectedRecord != null) {
        AuditRecordDialog(
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showEditDialog = false
            },
            combined = selectedRecord!!, // [新增参数] 传入旧数据
            onConfirm = { updatedRecord ->
                inventoryViewModel.updateRecord(updatedRecord)
                showEditDialog = false
            }
        )
    }

    if (showQuickCalc) {
        val retain = settingsViewModel?.uiState?.collectAsState()?.value?.calcRetainLastResult ?: true
        val initialVal = if (retain) (lastCalcResult ?: "") else ""
        InventoryCalcDialog(
            initialValue = initialVal,
            canBackfill = false,
            onDismiss = { showQuickCalc = false },
            onConfirm = { resultStr ->
                lastCalcResult = resultStr.ifBlank { null }
            }
        )
    }

    if (showScanScreen) {
        BackHandler { showScanScreen = false } // 拦截返回键
        ScanScreen(
            onScanResult = { result ->
                showScanScreen = false
                // 核心逻辑：扫码 -> 填入搜索框 -> 自动触发搜索
                searchQuery = result
                inventoryViewModel.performSearch(result, isInventoryMode)
                focusManager.clearFocus() // 收起键盘
                active = false // 关闭搜索建议框（如果需要）
            },
            onClose = { showScanScreen = false }
        )
    }

    // 👇 新增：DI 校验异常弹窗
    if (uiState.invalidDiList.isNotEmpty()) {
        ModernAlertDialog(
            onDismiss = { inventoryViewModel.clearInvalidDiList() },
            title = "DI 校验异常",
            showContentCard = false,
            content = {
                Column {
                    Text(
                        "以下数据的 DI (GS1 格式) 校验未通过，系统已自动放行，但请留意核对：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        items(uiState.invalidDiList) { item ->
                            val pName = item.product?.productName ?: "未知产品"
                            val key = item.record.productKey
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(text = pName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(text = "Key: $key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val textToCopy = uiState.invalidDiList.joinToString("\n") {
                            "${it.product?.productName ?: "未知产品"} - ${it.record.productKey}"
                        }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("一键复制")
                    }
                    TextButton(onClick = {
                        errorExportLauncher.launch("${java.time.LocalDate.now()}_异常DI数据.xlsx")
                    }) {
                        Text("导出Excel")
                    }
                    TextButton(onClick = { inventoryViewModel.clearInvalidDiList() }) {
                        Text("关闭警告")
                    }
                }
            },
            dismissButton = null
        )
    }
}

/** AI Surface 状态机：悬浮球 ⇄ 快捷输入胶囊 连续 Morph（展开/收缩动画期间输入一律忽略） */
private enum class AiSurfaceState {
    /** 悬浮球：可拖拽/吸边/拖回顶栏收回 */
    FLOATING,
    /** 球 → 胶囊 展开动画中 */
    EXPANDING,
    /** 快捷输入胶囊（稳定态，键盘可弹出） */
    QUICK_INPUT,
    /** 胶囊 → 球 收缩动画中 */
    COLLAPSING
}

/** Morph 动画参数（集中管理；基准值来自 Morph 实施方案 §36/§55） */
private object AiSurfaceMotion {
    const val MORPH_DURATION = 220      // 球→胶囊 位移+展宽
    const val MORPH_DELAY = 40          // 按压反馈先行，Morph 重叠启动
    const val COLLAPSE_DURATION = 200   // 胶囊→球
    const val EXPAND_TOTAL = MORPH_DELAY + MORPH_DURATION       // 状态推进等待 260ms
    const val COLLAPSE_TOTAL = 240
    const val INPUT_FADE_DURATION = 120
    const val INPUT_FADE_DELAY = 120
    const val SEND_FADE_DURATION = 80
    const val SEND_FADE_DELAY = 160
    const val SCRIM_DURATION = 140
    const val HANDLE_FADE_DURATION = 120
    const val PRESS_DOWN_DURATION = 60
    const val PRESS_UP_DURATION = 90
    const val PRESS_SCALE = 0.94f
    const val SCRIM_ALPHA = 0.12f
    const val BUBBLE_ALPHA = 0.8f   // FLOATING 球身半透明度（胶囊态仍为不透明，保证输入可读性）
}

/** AI Surface 尺寸规格 */
private object AiSurfaceDimens {
    val BubbleSize = 56.dp
    val TouchTarget = 64.dp
    val CapsuleHeight = 56.dp
    val HorizontalMargin = 16.dp
    val Radius = 28.dp
    val ChipSize = 40.dp
    val IconSize = 22.dp
    val HandleHeight = 20.dp
    val BottomReserve = 110.dp // FLOATING 拖拽下限：避开右下角扫码 FAB 区
}

/** Morph 主缓动：前段稍快、中段平滑、末尾快速稳定 */
private val AiMorphEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * AI 助手一体 Surface（悬浮球 ⇄ 快捷输入胶囊 Morph，单实例跨状态存活）：
 * FLOATING = 可拖拽球（56dp 视觉 + 64dp 触控环），其余三态 = 底部胶囊（宽=屏宽-32、高 56dp、
 * 圆角恒定 28dp——球半径即胶囊圆角，无需动画）；球色⇄面色、chip 56→40dp、chip 中心球心→左端
 * 全程连续，图标永不消失重建。scrim/回答卡/上拉细横条（QUICK_INPUT 稳定后淡入）同层管理。
 * 位置基于根布局实测尺寸：adjustResize 下随键盘收缩即时跟随（项目未开 edge-to-edge，
 * WindowInsets.ime 恒 0，不适用文档的 WindowInsets 方案）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiAssistantSurface(
    state: AiSurfaceState,
    screenPx: IntSize,
    bubbleOffset: Offset,
    onBubbleOffsetChange: (Offset) -> Unit,
    onClickBubble: () -> Unit,
    onDock: () -> Unit,
    onDragStarted: () -> Unit,
    onDragFinished: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullChat: () -> Unit,
    onSend: (String) -> Unit,
    asked: String?,
    viewModel: ChatViewModel
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    var input by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Focus 时序：Morph 完成（进入 QUICK_INPUT）才请求焦点，键盘随展开完成弹出
    LaunchedEffect(state) {
        if (state == AiSurfaceState.QUICK_INPUT) focusRequester.requestFocus()
    }
    // 返回键两段式：有文字且聚焦先只收键盘；空/未聚焦直接收起胶囊
    if (state == AiSurfaceState.QUICK_INPUT) {
        BackHandler {
            if (input.isNotBlank() && inputFocused) {
                keyboard?.hide()
                focusManager.clearFocus()
            } else {
                onDismiss()
            }
        }
    }

    // ── 几何（px，实时派生自 screenPx 与 Insets；QUICK_INPUT 下键盘弹出平滑上移）──
    val bubblePx = with(density) { AiSurfaceDimens.BubbleSize.toPx() }
    val capsuleHeightPx = with(density) { AiSurfaceDimens.CapsuleHeight.toPx() }
    val marginPx = with(density) { AiSurfaceDimens.HorizontalMargin.toPx() }
    val capsuleWidthPx = (screenPx.width - marginPx * 2).coerceAtLeast(bubblePx)
    val capsuleLeftPx = marginPx
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val bottomInsetPx = maxOf(imeBottomPx, navBottomPx)
    val capsuleTopPx = (screenPx.height - capsuleHeightPx - marginPx - bottomInsetPx).coerceAtLeast(0f)
    // chip 落位：左端 4dp 起、40dp 宽 → 中心 24dp（与左停靠球心 36dp 差 4dp，Morph 视觉连续即可）
    val chipSettledCenterPx = with(density) { (4.dp + AiSurfaceDimens.ChipSize / 2).toPx() }
    val chipTargetScale = AiSurfaceDimens.ChipSize / AiSurfaceDimens.BubbleSize
    val chipRowStartDp = 4.dp + AiSurfaceDimens.ChipSize + 8.dp // 输入行左让位（chip 右缘 + 间隙）

    // ── Morph：updateTransition 按状态驱动各属性；FLOATING/QUICK_INPUT 用 tween(0) 实时跟随 ──
    val transition = updateTransition(targetState = state, label = "ai_surface")
    val surfaceLeft by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION, AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "surface_left"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) capsuleLeftPx else bubbleOffset.x }
    val surfaceTop by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION, AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "surface_top"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) capsuleTopPx else bubbleOffset.y }
    val surfaceWidth by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION, AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "surface_width"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) capsuleWidthPx else bubblePx }
    val surfaceColor by transition.animateColor(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION + AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "surface_color"
    ) {
        if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = AiSurfaceMotion.BUBBLE_ALPHA)
    }
    val chipColor by transition.animateColor(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION + AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "chip_color"
    ) {
        // FLOATING 态 chip 透明：半透明球身由 Surface 本体承担，避免 chip 双层叠色加深
        if (it == AiSurfaceState.FLOATING) Color.Transparent else MaterialTheme.colorScheme.primaryContainer
    }
    val scrimAlpha by transition.animateFloat(
        transitionSpec = { tween(AiSurfaceMotion.SCRIM_DURATION) },
        label = "scrim_alpha"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) AiSurfaceMotion.SCRIM_ALPHA else 0f }
    val inputAlpha by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.INPUT_FADE_DURATION, AiSurfaceMotion.INPUT_FADE_DELAY)
                AiSurfaceState.COLLAPSING -> tween(90)
                else -> tween(0)
            }
        },
        label = "input_alpha"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) 1f else 0f }
    val sendAlpha by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.SEND_FADE_DURATION, AiSurfaceMotion.SEND_FADE_DELAY)
                AiSurfaceState.COLLAPSING -> tween(60)
                else -> tween(0)
            }
        },
        label = "send_alpha"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) 1f else 0f }
    val chipScale by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION, AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "chip_scale"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) chipTargetScale else 1f }
    val chipCenterX by transition.animateFloat(
        transitionSpec = {
            when (targetState) {
                AiSurfaceState.EXPANDING -> tween(AiSurfaceMotion.MORPH_DURATION, AiSurfaceMotion.MORPH_DELAY, easing = AiMorphEasing)
                AiSurfaceState.COLLAPSING -> tween(AiSurfaceMotion.COLLAPSE_DURATION, easing = FastOutSlowInEasing)
                else -> tween(0)
            }
        },
        label = "chip_center_x"
    ) { if (it == AiSurfaceState.EXPANDING || it == AiSurfaceState.QUICK_INPUT) chipSettledCenterPx else bubblePx / 2 }
    val handleAlpha by transition.animateFloat(
        transitionSpec = { tween(if (targetState == AiSurfaceState.QUICK_INPUT) AiSurfaceMotion.HANDLE_FADE_DURATION else 60) },
        label = "handle_alpha"
    ) { if (it == AiSurfaceState.QUICK_INPUT) 1f else 0f }

    // ── 按压反馈（点击瞬间与 Morph 重叠并发）──
    val pressScale = remember { Animatable(1f) }
    val surfaceScope = rememberCoroutineScope()

    // ── 上拉细横条手势（QUICK_INPUT 专属；与回答卡下拉延伸各自独立状态）──
    val barPullPx = remember { mutableStateOf(0f) }
    val pullScope = rememberCoroutineScope()
    // 触发距离缩短：由 100dp 优化为 56dp，轻拉即可触发
    val pullThresholdPx = with(density) { 56.dp.toPx() }
    val pullMaxPx = with(density) { 120.dp.toPx() }
    val currentOpenFullChat by rememberUpdatedState(onOpenFullChat)
    val handleHeightPx = with(density) { AiSurfaceDimens.HandleHeight.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // scrim：非 FLOATING 淡入；QUICK_INPUT 点按收起，EXPANDING/COLLAPSING 吞掉点击防误触
        if (state != AiSurfaceState.FLOATING) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color.Black)
                    .pointerInput(state) {
                        if (state != AiSurfaceState.FLOATING) {
                            detectTapGestures(onTap = { if (state == AiSurfaceState.QUICK_INPUT) onDismiss() })
                        }
                    }
            )
        }

        // 回答卡：发送后出现（上限 = 胶囊上沿再让出细横条）
        if (asked != null && state != AiSurfaceState.FLOATING) {
            AiQuickAnswerCard(
                asked = asked,
                screenPx = screenPx,
                capsuleTopPx = capsuleTopPx,
                onOpenFullChat = onOpenFullChat,
                onDismiss = onDismiss,
                viewModel = viewModel
            )
        }

        // 上拉细横条：QUICK_INPUT 稳定后淡入，叠绘于胶囊上方；仅小杠单独被向上拖起，胶囊保持原位稳固
        if (state == AiSurfaceState.QUICK_INPUT || state == AiSurfaceState.COLLAPSING) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(capsuleLeftPx.roundToInt(), (capsuleTopPx - handleHeightPx).roundToInt()) }
                    .width(with(density) { capsuleWidthPx.toDp() })
                    .height(with(density) { (handleHeightPx + barPullPx.value).toDp() })
                    .graphicsLayer {
                        alpha = handleAlpha
                        translationY = -barPullPx.value
                    }
                    // 点击小横杠不收回键盘，避免误触打断输入流程；仅处理上拉拖拽
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                barPullPx.value = (barPullPx.value - dragAmount * 0.7f)
                                    .coerceIn(0f, pullMaxPx)
                            },
                            onDragEnd = {
                                if (barPullPx.value >= pullThresholdPx) {
                                    barPullPx.value = 0f
                                    if (input.isNotBlank()) {
                                        viewModel.setDraftMessage(input)
                                    }
                                    keyboard?.hide()
                                    currentOpenFullChat()
                                } else if (barPullPx.value > 0f) {
                                    pullScope.launch {
                                        Animatable(barPullPx.value).animateTo(0f, tween(150)) {
                                            barPullPx.value = value
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (barPullPx.value > 0f) {
                                    pullScope.launch {
                                        Animatable(barPullPx.value).animateTo(0f, tween(150)) {
                                            barPullPx.value = value
                                        }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (barPullPx.value > 8f) {
                        Text(
                            text = if (barPullPx.value >= pullThresholdPx) "↑ 松开进入完整对话" else "↑ 继续上拉进入完整对话",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .graphicsLayer {
                                    alpha = (barPullPx.value / 24f).coerceIn(0f, 1f)
                                }
                        )
                    }
                    // 小横杠：尺寸加大（48dp x 5dp）、半透明柔和视觉（透明度 0.45，拉起达到阈值时高亮）
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(5.dp)
                            .background(
                                if (barPullPx.value >= pullThresholdPx) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.5.dp)
                            )
                    )
                }
            }
        }

        // Surface 本体：球 ⇄ 胶囊 一体（圆角恒定；chip 即球身；输入行延迟淡入、SEND 最后出现）
        Surface(
            shape = RoundedCornerShape(AiSurfaceDimens.Radius),
            color = surfaceColor,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(surfaceLeft.roundToInt(), surfaceTop.roundToInt()) }
                .size(with(density) { surfaceWidth.toDp() }, AiSurfaceDimens.CapsuleHeight)
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
        ) {
            Box {
                // chip：球身/胶囊左端图标座（中心点球心→左端连续移动；FLOATING 即整球）
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (chipCenterX - bubblePx * chipScale / 2).roundToInt(),
                                ((capsuleHeightPx - bubblePx * chipScale) / 2).roundToInt()
                            )
                        }
                        .size(with(density) { (bubblePx * chipScale).toDp() })
                        .background(chipColor, CircleShape)
                        .clickable(enabled = state == AiSurfaceState.QUICK_INPUT) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = if (state == AiSurfaceState.FLOATING) "AI 助手悬浮球" else "收起快捷问答",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(AiSurfaceDimens.IconSize)
                    )
                }
                // 输入行（FLOATING 不组合；按时间轴延迟淡入）
                if (state != AiSurfaceState.FLOATING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = chipRowStartDp, end = 4.dp)
                            .graphicsLayer { alpha = inputAlpha }
                    ) {
                        AppTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { inputFocused = it.isFocused },
                            placeholder = "输入问题...",
                            enabled = state == AiSurfaceState.QUICK_INPUT && !uiState.isProcessing,
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3
                        )
                        if (uiState.isProcessing) {
                            // 中性圆形停止钮，与全屏聊天页同款：视觉 36dp 圆 + 48dp 触控区
                            IconButton(
                                onClick = { viewModel.stopGeneration() },
                                modifier = Modifier.graphicsLayer { alpha = sendAlpha }
                            ) {
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
                                    val text = input.trim()
                                    if (text.isEmpty()) return@IconButton
                                    onSend(text)
                                    input = ""
                                    keyboard?.hide()
                                },
                                enabled = state == AiSurfaceState.QUICK_INPUT && input.isNotBlank(),
                                modifier = Modifier.graphicsLayer { alpha = sendAlpha }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // FLOATING 触控环（64dp > 56dp 视觉）：点击/长按收回/拖拽，叠绘于球之上独占手势
        if (state == AiSurfaceState.FLOATING) {
            val touchPadPx = with(density) { ((AiSurfaceDimens.TouchTarget - AiSurfaceDimens.BubbleSize) / 2).toPx() }
            val bottomReservePx = with(density) { AiSurfaceDimens.BottomReserve.toPx() }
            // pointerInput(Unit) 闭包只捕获首次组合值，用 rememberUpdatedState 保证读到最新
            val currentBubbleOffset by rememberUpdatedState(bubbleOffset)
            val currentScreenPx by rememberUpdatedState(screenPx)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (bubbleOffset.x - touchPadPx).roundToInt(),
                            (bubbleOffset.y - touchPadPx).roundToInt()
                        )
                    }
                    .size(AiSurfaceDimens.TouchTarget)
                    .combinedClickable(
                        onClick = {
                            surfaceScope.launch {
                                pressScale.animateTo(AiSurfaceMotion.PRESS_SCALE, tween(AiSurfaceMotion.PRESS_DOWN_DURATION))
                                pressScale.animateTo(1f, tween(AiSurfaceMotion.PRESS_UP_DURATION))
                            }
                            onClickBubble()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDock()
                        }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStarted() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxX = (currentScreenPx.width - bubblePx).coerceAtLeast(0f)
                                val maxY = (currentScreenPx.height - bubblePx - bottomReservePx).coerceAtLeast(0f)
                                onBubbleOffsetChange(
                                    Offset(
                                        (currentBubbleOffset.x + dragAmount.x).coerceIn(0f, maxX),
                                        (currentBubbleOffset.y + dragAmount.y).coerceIn(0f, maxY)
                                    )
                                )
                            },
                            onDragEnd = { onDragFinished() },
                            onDragCancel = { onDragFinished() }
                        )
                    }
            )
        }
    }
}

/**
 * 快捷问答回答卡：屏幕顶部起始向下生长（上限 = 胶囊上沿再让出上拉细横条），随流式内容向下生长
 * （超出转内部滚动，圆角常驻）。回答 = 最后一条用户消息之后的助手/错误气泡（流式中 isLoading 同样计入）。
 * 卡底拖拽手柄：下拉延伸卡片，松手 ≥100dp 进入完整对话，否则回弹；单击手柄收起软键盘。
 */
@Composable
private fun AiQuickAnswerCard(
    asked: String,
    screenPx: IntSize,
    capsuleTopPx: Float,
    onOpenFullChat: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pullPx = remember { mutableStateOf(0f) }
    val pullThresholdPx = with(density) { 100.dp.toPx() }
    val pullMaxPx = with(density) { 180.dp.toPx() }
    val currentOpenFullChat by rememberUpdatedState(onOpenFullChat)

    // 回答 = 最后一条用户消息之后的助手/错误气泡（流式中的 isLoading 助手气泡同样计入）
    val messages = uiState.messages
    val lastUserIdx = messages.indexOfLast { it.isUser }
    val answer = if (lastUserIdx >= 0) {
        messages.drop(lastUserIdx + 1)
            .filter { it.role == "assistant" || it.role == "error" }
            .joinToString("\n\n") { it.content }
    } else ""

    // ── 几何：卡顶在状态栏下方 8dp 起，上限 = 胶囊上沿 - 细横条(20dp) - 间隙 ──
    val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
    val marginPx = with(density) { 8.dp.toPx() }
    val cardMarginPx = with(density) { 16.dp.toPx() }
    val handleReservePx = with(density) { (AiSurfaceDimens.HandleHeight + 8.dp).toPx() }
    val cardTopPx = statusBarPx + marginPx
    val cardWidthPx = screenPx.width - cardMarginPx * 2
    val cardMaxHeightPx = (capsuleTopPx - handleReservePx - cardTopPx - marginPx)
        .coerceAtLeast(with(density) { 120.dp.toPx() })

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        modifier = Modifier
            .offset { IntOffset(cardMarginPx.roundToInt(), cardTopPx.roundToInt()) }
            .width(with(density) { cardWidthPx.toDp() })
            .heightIn(max = with(density) { cardMaxHeightPx.toDp() })
    ) {
        // 卡片外壳：内容区（可滚动、随内容生长）+ 底部拖拽手柄（固定在卡底）
        // （verticalScroll 挂内层 Column——挂在 Surface 本体会破坏 shape 裁切、圆角只在滚动到头出现）
        Column(modifier = Modifier) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    asked,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (answer.isNotEmpty()) {
                    MarkdownText(text = answer)
                } else if (uiState.isProcessing) {
                    Text(
                        "思考中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row {
                    TextButton(onClick = { onDismiss(); onOpenFullChat() }) {
                        Text("查看完整对话", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("收起", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            // 延伸区：卡片顶端固定，下拉时向下"延长"，提示在延长区域内渐显
            if (pullPx.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pullPx.value.toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "↓ 松开进入完整对话",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = (pullPx.value / pullThresholdPx).coerceIn(0f, 1f)
                        }
                    )
                }
            }
            // 拖拽手柄：按住下拉延伸卡片，松手超阈值进入完整对话，否则回弹；单击收起软键盘
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { keyboard?.hide() })
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                pullPx.value = (pullPx.value + dragAmount * 0.6f)
                                    .coerceIn(0f, pullMaxPx)
                            },
                            onDragEnd = {
                                if (pullPx.value >= pullThresholdPx) {
                                    pullPx.value = 0f
                                    currentOpenFullChat()
                                } else if (pullPx.value > 0f) {
                                    scope.launch {
                                        Animatable(pullPx.value).animateTo(0f, tween(150)) {
                                            pullPx.value = value
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (pullPx.value > 0f) {
                                    scope.launch {
                                        Animatable(pullPx.value).animateTo(0f, tween(150)) {
                                            pullPx.value = value
                                        }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
