package com.example.inventorymaster.ui.home // 你的包名

import androidx.compose.material3.AlertDialog
import com.example.inventorymaster.viewmodel.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.isActive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.example.inventorymaster.utils.permission.AppPermission
import com.example.inventorymaster.utils.permission.rememberPermissionAction
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.model.ImportDiagnostics
import com.example.inventorymaster.data.model.MappingTemplate
import com.example.inventorymaster.data.model.SessionWithProgress
import com.example.inventorymaster.data.model.isPendingStart
import com.example.inventorymaster.ui.analyzer.ScanScreen
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.ui.components.BadgeSeverity
import com.example.inventorymaster.ui.components.StatusBadge
import com.example.inventorymaster.ui.components.semanticFg
import com.example.inventorymaster.ui.inventory.components.MappingConfigDialog
import com.example.inventorymaster.ui.inventory.components.TemplateSelectionDialog
import com.example.inventorymaster.ui.session.CloudTaskDialog
import com.example.inventorymaster.ui.sync.DownloadConflictDialog
import com.example.inventorymaster.ui.theme.StatusArchived
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusInfo
import com.example.inventorymaster.ui.theme.StatusPending
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import com.example.inventorymaster.utils.ExcelUtils
import com.example.inventorymaster.utils.NetworkUtils
import com.example.inventorymaster.utils.QRCodeUtil
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SyncIntent
import com.example.inventorymaster.viewmodel.SyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

// M9：新建任务带 Excel 导入的分流模式（原 ImportMode 从 InventoryScreen 迁来）
private enum class SessionImportMode { TEMPLATE, NEW_MAPPING, QUICK }


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    syncViewModel: SyncViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onSessionClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val actualSettingsVm: SettingsViewModel = settingsViewModel ?: viewModel()
    val settingsState by actualSettingsVm.uiState.collectAsState()
    val sessionState by sessionViewModel.uiState.collectAsState()
    val inventoryState by inventoryViewModel.uiState.collectAsState()
    val syncState by syncViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    // M9：新建任务带 Excel 导入的流水线状态（任务先创建拿到 ID，再走 模板选择→映射→导入）
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportSessionId by remember { mutableStateOf<Long?>(null) }
    var showTemplateSelectionDialog by remember { mutableStateOf(false) }
    var showMappingConfigDialog by remember { mutableStateOf(false) }
    var excelHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTemplateForMapping by remember { mutableStateOf<MappingTemplate?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // 控制长按菜单的显示
    var showMenuDialog by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<InventorySession?>(null) }
    // 控制二次确认弹窗的显示
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnlockConfirm by remember { mutableStateOf(false) }
    // 新增状态控制大厅弹窗
    var showCloudHall by remember { mutableStateOf(false) }
    // 1. 状态管理
    var showScanner by remember { mutableStateOf(false) } // 扫码器开关
    var showQRCodeSessionId by remember { mutableStateOf<Long?>(null) } // 二维码弹窗开关
    var showManualIpDialog by remember { mutableStateOf<Long?>(null) } // 手动输IP弹窗开关 (存目标ID)
    var joinApprovalData by remember { mutableStateOf<JoinApprovalState?>(null) }
    // 2. 监听 ViewModel 数据
    val lastIp = syncState.lastServerIp
    val isLoading = syncState.isSyncing

    LaunchedEffect(syncState.syncMessage) {
        syncState.syncMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            syncViewModel.clearMessage()
        }
    }

    val requestCameraScan = rememberPermissionAction(AppPermission.CAMERA) {
        showScanner = true
    }

    // --- 2. 界面布局 (Scaffold 全屏) ---
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("库存任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // A. 列表层
        if (sessionState.sessions.isEmpty()) {
            // 空状态提示
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                ExpandableActionCard(
                    onCreateLocalTask = { showCreateDialog = true },
                    onOpenCloudHall = { showCloudHall = true },
                    onScanJoin = requestCameraScan
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无任务，请点击上方 + 号创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 16.dp,
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ExpandableActionCard(
                        onCreateLocalTask = { showCreateDialog = true },
                        onOpenCloudHall = { showCloudHall = true },
                        onScanJoin = requestCameraScan
                    )
                }
                items(sessionState.sessions, key = { it.session.id }) { item ->
                    val session = item.session
                    SessionItem(
                        item = item,
                        onClick = { onSessionClick(session.id) },
                        onLongClick = {
                            selectedSession = session
                            showMenuDialog = true
                        },
                        onShowQRCode = { id -> showQRCodeSessionId = id }
                    )
                }
            }
        }
    }
}


// --- 弹窗逻辑区 ---


// 1. 新建任务弹窗
    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showCreateDialog = false
            },
            onConfirm = { name, excelUri ->
                showCreateDialog = false
                if (excelUri == null) {
                    sessionViewModel.createNewSession(name)
                } else {
                    // M9：导入只在新建本地任务时进行——先创建拿到 ID，再进入导入流水线
                    pendingImportUri = excelUri
                    sessionViewModel.createNewSession(name) { newId ->
                        pendingImportSessionId = newId
                        inventoryViewModel.loadMappingTemplates()
                        showTemplateSelectionDialog = true
                    }
                }
            }
        )
    }

    // M9：Excel 导入流水线（自任务详情页更多菜单迁来；取消模板/映射弹窗会留下空任务，可长按删除）
    pendingImportUri?.let { importUri ->
        if (showTemplateSelectionDialog) {
            TemplateSelectionDialog(
                templates = inventoryViewModel.getCachedTemplates(),
                onTemplateSelected = { template ->
                    showTemplateSelectionDialog = false
                    selectedTemplateForMapping = template
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val headers = ExcelUtils.parseHeaders(context, importUri)
                            withContext(Dispatchers.Main) {
                                excelHeaders = headers
                                showMappingConfigDialog = true
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "读取表头失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onNewMapping = {
                    showTemplateSelectionDialog = false
                    selectedTemplateForMapping = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val headers = ExcelUtils.parseHeaders(context, importUri)
                            withContext(Dispatchers.Main) {
                                excelHeaders = headers
                                showMappingConfigDialog = true
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "读取表头失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onQuickImport = {
                    showTemplateSelectionDialog = false
                    pendingImportSessionId?.let { id ->
                        inventoryViewModel.importExcelFile(context, importUri, id)
                        Toast.makeText(context, "已导入文件", Toast.LENGTH_SHORT).show()
                    }
                    pendingImportUri = null
                },
                onDismiss = {
                    showTemplateSelectionDialog = false
                    pendingImportUri = null
                }
            )
        }

        if (showMappingConfigDialog && excelHeaders.isNotEmpty()) {
            MappingConfigDialog(
                headers = excelHeaders,
                initialMapping = selectedTemplateForMapping?.toFieldMap() ?: emptyMap(),
                initialTemplateName = selectedTemplateForMapping?.name ?: "",
                existingTemplateId = selectedTemplateForMapping?.id,
                onConfirm = { mapping, saveAsTemplateName ->
                    showMappingConfigDialog = false
                    if (saveAsTemplateName != null) {
                        inventoryViewModel.saveMappingTemplate(
                            name = saveAsTemplateName,
                            fieldMap = mapping,
                            existingId = selectedTemplateForMapping?.id
                        )
                    }
                    pendingImportSessionId?.let { id ->
                        inventoryViewModel.importExcelFileWithMapping(
                            context, importUri, id, mapping
                        )
                        Toast.makeText(context, "正在导入...", Toast.LENGTH_SHORT).show()
                    }
                    selectedTemplateForMapping = null
                    pendingImportUri = null
                },
                onDismiss = {
                    showMappingConfigDialog = false
                    selectedTemplateForMapping = null
                    pendingImportUri = null
                }
            )
        }
    }

    // M9：导入确认弹窗（产品冲突摘要 + 诊断），状态在共享的 InventoryViewModel 里
    inventoryState.importSummary?.let { summaryText ->
        ModernAlertDialog(
            onDismiss = { inventoryViewModel.cancelImport() },
            title = "导入确认",
            content = {
                Column {
                    Text(
                        text = summaryText + "\n\n以导入文件为准，将覆盖本地数据，是否继续？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 显示导入诊断信息（DI重复/跳过行）
                    inventoryState.importDiagnostics?.let { diagnostics ->
                        if (diagnostics.hasIssues) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ImportDiagnosticsContent(diagnostics)
                        }
                    }
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = { inventoryViewModel.confirmImport() },
                    onDismiss = { inventoryViewModel.confirmImport() },
                    confirmText = "确认导入",
                    dismissText = "取消"
                )
            }
        )
    }

    // M9：导入产生的 DI 校验异常提示（详情页保留同款弹窗用于「校验DI」菜单项）
    if (inventoryState.invalidDiList.isNotEmpty()) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
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
                        items(inventoryState.invalidDiList) { item ->
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
                        val textToCopy = inventoryState.invalidDiList.joinToString("\n") {
                            "${it.product?.productName ?: "未知产品"} - ${it.record.productKey}"
                        }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("一键复制")
                    }
                    TextButton(onClick = { inventoryViewModel.clearInvalidDiList() }) {
                        Text("关闭警告")
                    }
                }
            }
        )
    }

// 2. 长按操作菜单 (核心逻辑)
    if (showMenuDialog && selectedSession != null) {
        val session = selectedSession!!
        SessionActionDialog(
            session = session,
            onDismiss = { showMenuDialog = false },
            onFinishSession = {
                showMenuDialog = false
                coroutineScope.launch {
                    sessionViewModel.tryFinishSession(session.id, force = true)
                    Toast.makeText(context, "已完成个人盘点，已切换为查询模式", Toast.LENGTH_SHORT).show()
                }
            },
            onToggleLock = {
                if (session.status == 2) {
                    // 如果是解锁，需要二次确认
                    showMenuDialog = false
                    showUnlockConfirm = true
                } else {
                    // 如果是锁定，直接锁
                    sessionViewModel.toggleLockSession(session)
                    showMenuDialog = false
                }
            },
            onDelete = {
                showMenuDialog = false
                showDeleteConfirm = true
            }
        )
    }

// 3. 删除确认警告 (红色预警)
    if (showDeleteConfirm && selectedSession != null) {
        ModernAlertDialog(
            onDismiss = { showDeleteConfirm = false },
            title = "危险操作",
            icon = Icons.Default.Warning,
            content = {
                Text("确定要删除任务【${selectedSession!!.name}】吗？\n\n此操作将永久删除该任务下的所有库存记录，不可恢复！")
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        sessionViewModel.deleteSession(selectedSession!!)
                        showDeleteConfirm = false
                    },
                    onDismiss = { showDeleteConfirm = false },
                    confirmText = "彻底删除",
                    confirmColor = MaterialTheme.colorScheme.error
                )
            }
        )
    }

// 4. 解锁确认弹窗
    if (showUnlockConfirm && selectedSession != null) {
        ModernAlertDialog(
            onDismiss = { showUnlockConfirm = false },
            title = "解锁确认",
            content = {
                Text("解锁后，该任务将允许被删除。\n(注：数据内容依然是只读的)")
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        sessionViewModel.toggleLockSession(selectedSession!!)
                        showUnlockConfirm = false
                    },
                    onDismiss = { showUnlockConfirm = false },
                    confirmText = "确认解锁"
                )
            }
        )
    }

//云端任务大厅弹窗
    if (showCloudHall) {
        CloudTaskDialog(
            onDismiss = { showCloudHall = false },
            cloudSessions = sessionState.cloudSessions,
            initialIp = syncState.lastServerIp.ifEmpty { "192.168." },
            initialToken = syncState.lastServerToken,
            onFetchList = { ip, token ->
                syncViewModel.saveTokenDirect(token)
                sessionViewModel.fetchCloudTasks(ip)
            },
            onJoinTask = { ip, token, sessionId ->
                syncViewModel.saveTokenDirect(token)
                syncViewModel.handleIntent(SyncIntent.DownloadFromPC(ip, token, sessionId))
                showCloudHall = false
            }
        )
    }

// 1. 全局 Loading 弹窗 (防止用户以为死机)
    if (isLoading) {
        ModernAlertDialog(
            onDismiss = {},
            title = "请稍候",
            showContentCard = false,
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("正在连接服务器...")
                }
            },
            confirmButton = {
                // 加载中不需要按钮，但 ModernAlertDialog 需要非空
                Spacer(modifier = Modifier.height(0.dp))
            },
            dismissButton = null
        )
    }

// 2. 发送端：增强版二维码弹窗 (带 IP)
    if (showQRCodeSessionId != null) {
        val targetId = showQRCodeSessionId!!

        val isWifi = NetworkUtils.isWifiOrEthernetConnected(context)
        Log.d("DEBUG_IP", "Current IP: '$lastIp', isWifi: $isWifi")

        // 二维码内容：JSON 格式，包含 IP、sessionId、token
        val qrContent = if (isWifi && lastIp.isNotEmpty()) {
            """{"ip":"$lastIp","sessionId":$targetId,"token":"${syncState.lastServerToken}"}"""
        } else {
            "$targetId"
        }
        EnhancedQRCodeDialog(
            content = qrContent,
            displayId = targetId,
            isWifiConnected = isWifi,
            missingIpReason = if (lastIp.isEmpty()) "未连接过电脑，无法获取服务器IP" else null,
            onDismiss = { showQRCodeSessionId = null }
        )
    }

// 3. 接收端：手动输入 IP 弹窗 (当扫码失败时弹出)
    if (showManualIpDialog != null) {
        val targetId = showManualIpDialog!!
        ManualIpDialog(
            initialIp = lastIp, // 预填旧 IP
            onDismiss = { showManualIpDialog = null },
            onConfirm = { ip ->
                showManualIpDialog = null
                syncViewModel.handleIntent(SyncIntent.DownloadFromPC(ip, syncState.lastServerToken, targetId))
            }
        )
    }

// 4. 接收端：扫码器逻辑 (智能解析)
    if (showScanner) {
        BackHandler { showScanner = false }
        ScanScreen(
            onClose = { showScanner = false },
            onScanResult = { resultRaw ->
                showScanner = false
                val clean = resultRaw.trim().removePrefix("﻿").trim()
                val jsonStartIndex = clean.indexOf('{')
                val jsonEndIndex = clean.lastIndexOf('}')
                val result = if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                    clean.substring(jsonStartIndex, jsonEndIndex + 1)
                } else {
                    clean
                }

                try {
                    // 情况 A: JSON 格式 (服务端/新版客户端)
                    if (result.startsWith("{")) {
                        val json = org.json.JSONObject(result)
                        val ip = json.optString("ip", "")
                        val sessionId = json.optLong("sessionId", 0)
                        val token = json.optString("token", "")

                        if (ip.isNotEmpty() && sessionId > 0) {
                            val currentOperator = settingsState.operatorName.trim()
                            if (currentOperator.isEmpty()) {
                                Toast.makeText(context, "请先设置操作者昵称", Toast.LENGTH_SHORT).show()
                            } else {
                                val sessionName = json.optString("sessionName", "")
                                joinApprovalData = JoinApprovalState(
                                    ip = ip,
                                    sessionId = sessionId,
                                    sessionName = sessionName,
                                    operatorName = currentOperator,
                                    status = ApprovalStatus.SUBMITTING
                                )
                            }
                        } else {
                            Toast.makeText(context, "二维码数据不完整", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // 情况 B: 逗号分隔旧格式 (IP,sessionId)
                    else if (result.contains(",")) {
                        val parts = result.split(",")
                        val ip = parts[0]
                        val id = parts[1].toLong()
                        syncViewModel.handleIntent(SyncIntent.DownloadFromPC(ip, syncState.lastServerToken, id))
                    }
                    // 情况 C: 竖线分隔旧格式 (IP|sessionId)
                    else if (result.contains("|")) {
                        val parts = result.split("|")
                        val ip = parts[0]
                        val id = parts[1].toLong()
                        if (NetworkUtils.isValidIpAddress(ip)) {
                            syncViewModel.handleIntent(SyncIntent.DownloadFromPC(ip, syncState.lastServerToken, id))
                        } else {
                            Toast.makeText(context, "二维码中的 IP 格式无效", Toast.LENGTH_LONG).show()
                        }
                    }
                    // 情况 D: 只有 ID
                    else {
                        val id = result.toLongOrNull()
                        if (id != null) {
                            showManualIpDialog = id
                        } else {
                            Toast.makeText(context, "无法识别的任务码", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "扫码解析错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ===== 扫码加入审批状态机与弹窗 =====
    LaunchedEffect(joinApprovalData?.status) {
        val data = joinApprovalData ?: return@LaunchedEffect
        if (data.status == ApprovalStatus.SUBMITTING) {
            val reqResult = syncViewModel.requestJoinTask(data.ip, data.sessionId, data.operatorName)
            reqResult.onSuccess { res ->
                if (res.approvalStatus == 1) {
                    res.token?.let { syncViewModel.saveTokenDirect(it) }
                    Toast.makeText(context, "已获批准，正在同步任务数据...", Toast.LENGTH_SHORT).show()
                    val effToken = res.token ?: syncState.lastServerToken
                    syncViewModel.handleIntent(SyncIntent.DownloadFromPC(data.ip, effToken, data.sessionId))
                    joinApprovalData = null
                } else if (res.approvalStatus == 2) {
                    joinApprovalData = data.copy(status = ApprovalStatus.REJECTED, errorMessage = res.message ?: "管理员已拒绝加入申请")
                } else {
                    joinApprovalData = data.copy(
                        status = ApprovalStatus.WAITING_APPROVAL,
                        sessionName = if (res.sessionName.isNotEmpty()) res.sessionName else data.sessionName
                    )
                }
            }.onFailure { e ->
                val code = (e as? com.example.inventorymaster.data.network.CollabApiException)?.code
                if (code == "NAME_EXISTS") {
                    joinApprovalData = data.copy(status = ApprovalStatus.NAME_CONFLICT, errorMessage = e.message)
                } else {
                    joinApprovalData = data.copy(status = ApprovalStatus.ERROR, errorMessage = e.message ?: "提交申请失败")
                }
            }
        } else if (data.status == ApprovalStatus.WAITING_APPROVAL) {
            while (joinApprovalData?.status == ApprovalStatus.WAITING_APPROVAL) {
                kotlinx.coroutines.delay(1500)
                val checkRes = syncViewModel.checkJoinStatus(data.ip, data.sessionId)
                checkRes.onSuccess { res ->
                    if (res.approvalStatus == 1) {
                        res.token?.let { syncViewModel.saveTokenDirect(it) }
                        Toast.makeText(context, "管理员已批准加入！正在同步任务数据...", Toast.LENGTH_SHORT).show()
                        val effToken = res.token ?: syncState.lastServerToken
                        syncViewModel.handleIntent(SyncIntent.DownloadFromPC(data.ip, effToken, data.sessionId))
                        joinApprovalData = null
                    } else if (res.approvalStatus == 2) {
                        joinApprovalData = data.copy(status = ApprovalStatus.REJECTED, errorMessage = res.message ?: "管理员已拒绝加入申请")
                    }
                }
            }
        }
    }

    joinApprovalData?.let { state ->
        AlertDialog(
            onDismissRequest = {
                joinApprovalData = null
            },
            title = {
                Text(
                    text = when (state.status) {
                        ApprovalStatus.SUBMITTING -> "正在提交申请"
                        ApprovalStatus.WAITING_APPROVAL -> "等待管理员批准"
                        ApprovalStatus.NAME_CONFLICT -> "操作员名称冲突"
                        ApprovalStatus.REJECTED -> "申请已被拒绝"
                        ApprovalStatus.ERROR -> "申请失败"
                        else -> "协同任务申请"
                    }
                )
            },
            text = {
                Column {
                    if (state.sessionName.isNotBlank()) {
                        Text("目标任务：${state.sessionName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text("申请人：${state.operatorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    when (state.status) {
                        ApprovalStatus.SUBMITTING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("正在连接服务器提交申请...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        ApprovalStatus.WAITING_APPROVAL -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "已提交加入申请，请在电脑端任务详情页面点击【批准】。",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        ApprovalStatus.NAME_CONFLICT -> {
                            Text(
                                state.errorMessage ?: "该任务中已存在同名操作员，请在设置中修改昵称后再加入！",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        ApprovalStatus.REJECTED -> {
                            Text(
                                state.errorMessage ?: "管理员已拒绝该设备的加入申请。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        ApprovalStatus.ERROR -> {
                            Text(
                                state.errorMessage ?: "网络连接异常，请检查服务器连接后重试。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (state.status == ApprovalStatus.WAITING_APPROVAL || state.status == ApprovalStatus.SUBMITTING) {
                    TextButton(onClick = { joinApprovalData = null }) {
                        Text("取消等待")
                    }
                } else {
                    TextButton(onClick = { joinApprovalData = null }) {
                        Text("确定")
                    }
                }
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

// --- 可折叠操作卡片 ---
@Composable
fun ExpandableActionCard(
    onCreateLocalTask: () -> Unit,
    onOpenCloudHall: () -> Unit,
    onScanJoin: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dashedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // 虚线保持可见但不喧宾夺主

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 关键：对齐下方卡片的左右边距，让宽度完全一致
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (expanded) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable { expanded = !expanded }
            // 折叠时从 80.dp 缩减到 68.dp，让结构更紧凑
            .height(if (expanded) 80.dp else 68.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!expanded) {
            // 折叠状态：虚线边框 + 居中 + 号
            Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                val strokeWidth = 1.5.dp.toPx() // 稍微细一点的虚线更精致
                val dashLength = 8.dp.toPx()
                val gapLength = 6.dp.toPx()
                val cornerRadius = 12.dp.toPx()

                drawRoundRect(
                    color = dashedBorderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(cornerRadius),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashLength, gapLength), 0f
                        )
                    )
                )
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "展开操作",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            // 展开状态：3个操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded = false; onCreateLocalTask() }) {
                    Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建盘点")
                }
                VerticalDivider(modifier = Modifier.height(32.dp))
                TextButton(onClick = { expanded = false; onOpenCloudHall() }) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("云端大厅")
                }
                VerticalDivider(modifier = Modifier.height(32.dp))
                TextButton(onClick = { expanded = false; onScanJoin() }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("扫码加入")
                }
            }
        }
    }
}

@Composable
private fun VerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

// --- 组件：单条任务卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    item: SessionWithProgress,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowQRCode: (Long) -> Unit
) {
    val session = item.session
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    // 服务端下发的待开始任务（status=0 但服务端尚未点开始）单独显示，避免误标"进行中"
    val isPending = session.isPendingStart
    val statusColor = when {
        isPending -> StatusPending       // 待开始
        session.status == 0 -> StatusSuccess  // 进行中
        session.status == 1 -> StatusArchived  // 已归档（与详情页/主题语义一致，归档=灰）
        session.status == 2 -> StatusError    // 已锁定
        else -> StatusArchived
    }

    val statusText = when {
        isPending -> "待开始"
        session.status == 0 -> "进行中"
        session.status == 1 -> "已归档"
        session.status == 2 -> "已锁定"
        else -> "未知"
    }

    // 来源标签颜色：服务端下发=蓝，本地=灰
    val sourceColor = if (session.sessionSource == 1) StatusInfo else StatusArchived

    // 定义统一的圆角，防止硬编码不一致
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape, // 显式指定 Shape
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // 1. 进度条背景层
            if (item.totalCount > 0) {
                Box(modifier = Modifier
                    .matchParentSize()
                    .clip(cardShape) // 【核心修复】强制进行圆角裁剪，不让半透明颜色溢出到边框外
                ) {
                    // 内层 Box 负责按比例渲染颜色
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        statusColor.copy(alpha = 0.12f), // 稍微降低一点透明度，让底色更清爽
                                        statusColor.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }
            }

            // 2. 原来的 Row 内容层
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sdf.format(Date(session.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.totalCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "进度: ${item.verifiedCount} / ${item.totalCount}  (${item.percentageText})",
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticFg(statusColor)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "暂无库存数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { onShowQRCode(session.id) }) {
                    Icon(Icons.Default.QrCode, contentDescription = "分享任务")
                }

                // 来源标签（本地 / 服务端）
                StatusBadge(
                    text = if (session.sessionSource == 1) "服务端" else "本地",
                    severity = BadgeSeverity.Custom(
                        background = sourceColor.copy(alpha = 0.12f),
                        foreground = semanticFg(sourceColor)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))

                // 状态标签
                StatusBadge(
                    text = statusText,
                    severity = BadgeSeverity.Custom(
                        background = statusColor.copy(alpha = 0.12f),
                        foreground = semanticFg(statusColor)
                    ),
                    leadingIcon = if (session.status == 2) Icons.Default.Lock else null
                )
            }
        }
    }
}

// --- 组件：操作菜单对话框 ---
@Composable
fun SessionActionDialog(
    session: InventorySession,
    onDismiss: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    onFinishSession: (() -> Unit)? = null
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "操作选项: ${session.name}",
        showContentCard = false,
        content = {
            Column {
                if (session.status == 0 && onFinishSession != null) {
                    ListItem(
                        headlineContent = {
                            Text("完成盘点 (切换只读)")
                        },
                        supportingContent = {
                            Text("上报个人离场，本地切换为只读查询模式")
                        },
                        modifier = Modifier.clickable { onFinishSession() }
                    )
                    HorizontalDivider()
                }

                if (session.status >= 1) {
                    ListItem(
                        headlineContent = {
                            Text(if (session.status == 2) "解锁任务" else "锁定任务")
                        },
                        supportingContent = {
                            Text(if (session.status == 2) "解锁后允许删除" else "锁定后禁止删除")
                        },
                        modifier = Modifier.clickable { onToggleLock() }
                    )
                    HorizontalDivider()
                }

                if (session.status != 2) {
                    ListItem(
                        headlineContent = {
                            Text("删除任务", color = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { onDelete() }
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("删除任务", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        supportingContent = { Text("请先解锁后才能删除", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onDismiss,
                confirmText = "关闭",
                dismissText = "",
                onDismiss = null
            )
        }
    )
}

// --- 简单的创建对话框 (如果你还没有的话) ---
@Composable
fun CreateSessionDialog(onDismiss: () -> Unit, onConfirm: (String, Uri?) -> Unit) {
    var text by remember { mutableStateOf("${LocalDate.now()} 盘点") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // M9：可选 Excel 导入——只有新建本地盘点需要导入
    val excelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedFileName = queryDisplayName(context, it)
        }
    }
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "新建盘点任务",
        content = {
            Column {
                ModernDialogTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "任务名称 (如: xxxx-xx-xx 盘点任务)"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        excelPicker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            )
                        )
                    }) {
                        Text(if (selectedUri == null) "选择 Excel（可选）" else "重新选择")
                    }
                    selectedFileName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selectedUri = null; selectedFileName = null }) {
                            Text("清除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = { if (text.isNotBlank()) onConfirm(text, selectedUri) },
                onDismiss = onDismiss,
                confirmText = "创建"
            )
        }
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

// M9：导入诊断内容（自 InventoryScreen 迁来，随导入确认弹窗展示）
@Composable
private fun ImportDiagnosticsContent(diagnostics: ImportDiagnostics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 组合键重复提示
        if (diagnostics.duplicateGroups.isNotEmpty()) {
            Text(
                "以下 ${diagnostics.duplicateGroups.size} 组产品存在重复，DI与物料编码完全相同仅保留最后一条：",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            diagnostics.duplicateGroups.forEach { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "组合键: ${group.keyLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        group.entries.forEachIndexed { index, entry ->
                            val isLast = index == group.entries.lastIndex
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLast) "保留" else "覆盖",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(30.dp)
                                )
                                Text(
                                    text = "行${entry.rowNumber}: ${entry.productName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 跳过行提示
        if (diagnostics.skippedRows.isNotEmpty()) {
            Text(
                "以下 ${diagnostics.skippedRows.size} 行被跳过未导入：",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            diagnostics.skippedRows.forEach { row ->
                Text(
                    "行${row.rowNumber}: ${row.productName} — ${row.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- 简单的时间格式化工具 ---
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun EnhancedQRCodeDialog(
    content: String,
    displayId: Long,
    isWifiConnected: Boolean,
    missingIpReason: String? = null,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(content) { QRCodeUtil.generateQRCode(content) }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "扫码加入任务",
        showContentCard = false,
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isWifiConnected) {
                    Text(
                        "警告：当前未使用 WiFi/局域网，对方可能无法连接。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(240.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "任务 ID: $displayId",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Data: $content",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (content.contains("|")) {
                    Text(
                        "已包含 IP，扫码即连",
                        color = StatusSuccess,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "未包含 IP，扫码后需手动输入",
                        color = StatusError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (missingIpReason != null) {
                    Text(
                        "($missingIpReason)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onDismiss,
                confirmText = "关闭",
                dismissText = "",
                onDismiss = null
            )
        }
    )
}

@Composable
fun ManualIpDialog(
    initialIp: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var ip by remember { mutableStateOf(initialIp) }
    val context = LocalContext.current

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "请输入服务器 IP",
        content = {
            Column {
                Text("二维码未包含地址，请输入电脑 IP：", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ModernDialogTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "例如 192.168.1.xxx",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    if (NetworkUtils.isValidIpAddress(ip)) onConfirm(ip)
                    else {
                        Toast.makeText(context, "IP 格式错误 (例如 192.168.1.5)", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onDismiss = onDismiss,
                confirmText = "确定连接"
            )
        }
    )
}

// ==========================================
// 扫码加入审批状态定义
// ==========================================
enum class ApprovalStatus {
    SUBMITTING,
    WAITING_APPROVAL,
    APPROVED,
    REJECTED,
    NAME_CONFLICT,
    ERROR
}

data class JoinApprovalState(
    val ip: String,
    val sessionId: Long,
    val sessionName: String,
    val operatorName: String,
    val status: ApprovalStatus = ApprovalStatus.SUBMITTING,
    val errorMessage: String? = null
)
