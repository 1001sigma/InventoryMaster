package com.example.inventorymaster.ui.home // 你的包名

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.ui.ScanScreen
import com.example.inventorymaster.ui.session.CloudTaskDialog
import com.example.inventorymaster.utils.NetworkUtils
import com.example.inventorymaster.utils.QRCodeUtil
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    onSessionClick: (Long) -> Unit
) {
    val sessionState by sessionViewModel.uiState.collectAsState()
    val inventoryState by inventoryViewModel.uiState.collectAsState()

    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    // 控制长按菜单的显示
    var showMenuDialog by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<InventorySession?>(null) }
    // 控制二次确认弹窗的显示
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnlockConfirm by remember { mutableStateOf(false) }
    // 新增状态控制大厅弹窗
    var showCloudHall by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    // 1. 状态管理
    var showScanner by remember { mutableStateOf(false) } // 扫码器开关
    var showQRCodeSessionId by remember { mutableStateOf<Long?>(null) } // 二维码弹窗开关
    var showManualIpDialog by remember { mutableStateOf<Long?>(null) } // 手动输IP弹窗开关 (存目标ID)
    // 2. 监听 ViewModel 数据
    val lastIp = inventoryViewModel.lastServerIp
    val isLoading = inventoryViewModel.isGlobalLoading

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true // 权限给了，打开扫码器
        } else {
            // 权限被拒，可以在这里弹个 Toast 提示用户去设置开启
            Toast.makeText(
                context,
                "需要相机权限才能扫码",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --- 2. 界面布局 (Box 代替 Scaffold) ---
    Box(modifier = Modifier.fillMaxSize()) {
        // A. 列表层
        if (sessionState.sessions.isEmpty()) {
            // 空状态提示
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无任务，请点击右下角 + 号创建", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 88.dp,
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ), // 底部留出空间给 FAB
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessionState.sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
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

        // B. 悬浮按钮层 (自己定位到右下角)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp) // 距离屏幕边缘的距离
        ) {
            // 主按钮
            FloatingActionButton(
                onClick = { showFabMenu = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "操作菜单")
            }

            // 挂载在按钮旁边的菜单
            DropdownMenu(
                expanded = showFabMenu,
                onDismissRequest = { showFabMenu = false },
                offset = DpOffset(x = 0.dp, y = (-8).dp)
            ) {
                // 选项 A: 新建本地盘点
                DropdownMenuItem(
                    text = { Text("新建本地盘点") },
                    leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) },
                    onClick = {
                        showFabMenu = false
                        showCreateDialog = true
                    }
                )

                // 选项 B: 云端任务大厅
                DropdownMenuItem(
                    text = { Text("云端任务大厅") },
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    onClick = {
                        showFabMenu = false
                        showCloudHall = true
                    }
                )

                // 选项 C: 扫码加入
                DropdownMenuItem(
                    text = { Text("扫码加入") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                    onClick = {
                        showFabMenu = false
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            showScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }
    }


// --- 弹窗逻辑区 ---

// 1. 新建任务弹窗 (你之前应该有，这里略写)
    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                sessionViewModel.createNewSession(name)
                showCreateDialog = false
            }
        )
    }

// 2. 长按操作菜单 (核心逻辑)
    if (showMenuDialog && selectedSession != null) {
        val session = selectedSession!!
        SessionActionDialog(
            session = session,
            onDismiss = { showMenuDialog = false },
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("⚠️ 危险操作") },
            text = { Text("确定要删除任务【${selectedSession!!.name}】吗？\n\n此操作将永久删除该任务下的所有库存记录，不可恢复！") },
            confirmButton = {
                Button(
                    onClick = {
                        sessionViewModel.deleteSession(selectedSession!!)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

// 4. 解锁确认弹窗
    if (showUnlockConfirm && selectedSession != null) {
        AlertDialog(
            onDismissRequest = { showUnlockConfirm = false },
            title = { Text("🔓 解锁确认") },
            text = { Text("解锁后，该任务将允许被删除。\n(注：数据内容依然是只读的)") },
            confirmButton = {
                Button(onClick = {
                    sessionViewModel.toggleLockSession(selectedSession!!)
                    showUnlockConfirm = false
                }) {
                    Text("确认解锁")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockConfirm = false }) { Text("取消") }
            }
        )
    }

//云端任务大厅弹窗
    if (showCloudHall) {
        CloudTaskDialog(
            onDismiss = { showCloudHall = false },
            cloudSessions = sessionState.cloudSessions,
            onFetchList = { ip ->
                sessionViewModel.fetchCloudTasks(ip) // 刷新列表
            },
            onJoinTask = { ip, sessionId ->
                // 点击加入：调用下载逻辑
                inventoryViewModel.downloadDataFromPC(ip, sessionId)
                showCloudHall = false
            }
        )
    }

// 1. 全局 Loading 弹窗 (防止用户以为死机)
    if (isLoading) {
        AlertDialog(
            onDismissRequest = {}, // 禁止点击外部关闭
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("正在连接服务器...")
                }
            },
            confirmButton = {}
        )
    }

// 2. 发送端：增强版二维码弹窗 (带 IP)
    if (showQRCodeSessionId != null) {
        val targetId = showQRCodeSessionId!!

        // A. 检查网络类型
        val isWifi = NetworkUtils.isWifiOrEthernetConnected(context)
        Log.d("DEBUG_IP", "Current IP: '$lastIp', isWifi: $isWifi")

        // B. 决定二维码内容
        // 如果有记录的 IP 且在 WiFi 下，则生成 IP|ID，否则只生成 ID
        val qrContent = if (isWifi && lastIp.isNotEmpty()) "$lastIp|$targetId" else "$targetId"
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
                inventoryViewModel.downloadDataFromPC(ip, targetId)
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
                val result = resultRaw.trim()

                try {
                    // === 情况 A: 完美二维码 (IP|ID) ===
                    if (result.contains(",")) {
                        val parts = result.split(",")
                        val ip = parts[0]
                        val id = parts[1].toLong()

                        if (NetworkUtils.isValidIpAddress(ip)) {
                            // 校验通过，直接下载
                            inventoryViewModel.downloadDataFromPC(ip, id)
                        } else {
                            Toast.makeText(context, "二维码中的 IP 格式无效", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                    // === 情况 B: 只有 ID (或旧版码) ===
                    else {
                        val id = result.toLongOrNull()
                        if (id != null) {
                            // 既然没有 IP，直接弹窗让用户确认/输入
                            showManualIpDialog = id
                        } else {
                            Toast.makeText(context, "无法识别的任务码", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "扫码解析错误", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// --- 组件：单条任务卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    session: InventorySession,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowQRCode: (Long) -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val statusColor = when (session.status) {
        0 -> Color(0xFF4CAF50) // 绿: 进行中
        1 -> Color(0xFFFF9800) // 橙: 已归档
        2 -> Color(0xFFF44336) // 红: 已锁定
        else -> Color.Gray
    }

    val statusText = when (session.status) {
        0 -> "进行中"
        1 -> "已归档"
        2 -> "已锁定"
        else -> "未知"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sdf.format(Date(session.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = {
                // 这里的 onClick 需要回调给上层，告诉它“我要展示 ID 为 xx 的二维码”
                onShowQRCode(session.id)
            }) {
                Icon(Icons.Default.QrCode, contentDescription = "分享任务")
            }
            // 状态标签
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (session.status == 2) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterVertically),
                            tint = statusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作选项: ${session.name}") },
        text = {
            Column {
                // 1. 锁定/解锁选项 (只有归档(1)或锁定(2)状态才显示)
                if (session.status >= 1) {
                    ListItem(
                        headlineContent = {
                            Text(if (session.status == 2) "🔓 解锁任务" else "🔒 锁定任务")
                        },
                        supportingContent = {
                            Text(if (session.status == 2) "解锁后允许删除" else "锁定后禁止删除")
                        },
                        modifier = Modifier.clickable { onToggleLock() }
                    )
                    HorizontalDivider()
                }

                // 2. 删除选项 (只有 进行中(0) 或 已归档(1) 才显示，锁定(2)不显示)
                if (session.status != 2) {
                    ListItem(
                        headlineContent = {
                            Text("删除任务", color = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { onDelete() }
                    )
                } else {
                    // 如果已锁定，显示一个灰色的不可点选项提示
                    ListItem(
                        headlineContent = { Text("删除任务", color = Color.Gray) },
                        supportingContent = { Text("请先解锁后才能删除", color = Color.Gray) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// --- 简单的创建对话框 (如果你还没有的话) ---
@Composable
fun CreateSessionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("${LocalDate.now()} 盘点") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建盘点任务") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("任务名称 (如: xxxx-xx-xx 盘点任务)") }
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📱 扫码加入任务") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isWifiConnected) {
                    // ⚠️ 网络警告
                    Text(
                        "⚠️ 警告：当前未使用 WiFi/局域网，对方可能无法连接。",
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

                // 🔍 调试/辅助信息
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Data: $content", // 显示原始内容方便排查
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )

                if (content.contains("|")) {
                    Text(
                        "✅ 已包含 IP，扫码即连",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "❌ 未包含 IP，扫码后需手动输入",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (missingIpReason != null) {
                    Text(
                        "($missingIpReason)",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请输入服务器 IP") },
        text = {
            Column {
                Text("二维码未包含地址，请输入电脑 IP：", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("例如 192.168.1.xxx") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (NetworkUtils.isValidIpAddress(ip)) onConfirm(ip)
                else {
                    Toast.makeText(context, "IP 格式错误 (例如 192.168.1.5)", Toast.LENGTH_SHORT)
                        .show()
                }
            }) { Text("确定连接") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}