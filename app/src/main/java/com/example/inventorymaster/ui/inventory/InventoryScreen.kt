package com.example.inventorymaster.ui.inventory

// --- 请确保包含以下所有 import ---
import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.ui.ScanScreen
import com.example.inventorymaster.ui.productManager.ProductConflictScreen
import com.example.inventorymaster.ui.sync.SyncDialog
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    sessionId: Long,
    onBackClick: () -> Unit,
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
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

    val currentSession = sessionStateUI.sessions.find { it.id == sessionId }
    val sessionStatus = currentSession?.status ?: 0 // 0=进行中
    val isReadOnly = sessionStatus > 0
    var isInventoryMode by remember (sessionStatus) { mutableStateOf(!isReadOnly) }

    // 管理键盘焦点
    val focusManager = LocalFocusManager.current

    var showAddDialog by remember { mutableStateOf(false) }

    // [新增] 控制“操作选项弹窗”显示
    var showOptionDialog by remember { mutableStateOf(false) }
    // [新增] 记录当前选中的是哪条记录 (为了传给编辑框或删除)
    var selectedRecord by remember { mutableStateOf<StockRecordCombined?>(null) }

    // [新增] 控制“编辑弹窗”显示 (其实就是复用 AddRecordDialog)
    var showEditDialog by remember { mutableStateOf(false) }

    var isQuickCheckMode by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. 监听生命周期：进页面自动同步 / 切回来自动同步
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 每次回到页面，自动静默拉取一次最新数据
                inventoryViewModel.refreshData(sessionId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 2. 监听 ViewModel 的同步状态 (用于控制下拉刷新的转圈圈停止)
    val isSyncing by inventoryViewModel.isSyncing.collectAsState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // ✅ 步骤 2: 在回调中，只负责更新状态
        if (isGranted) {
            showScanScreen = true
        } else {
            // （可选但推荐）提示用户权限被拒绝
            Toast.makeText(context, "相机权限被拒绝，无法扫码", Toast.LENGTH_SHORT).show()
            println("相机权限被拒绝")
        }
    }
    val onScanClick: () -> Unit = {
        // 请求权限
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 定义文件选择器
    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // UI 只负责：拿到 Uri -> 丢给 ViewModel
            inventoryViewModel.importExcelFile(context,it,sessionId)
            Toast.makeText(context, "正在后台导入...", Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            inventoryViewModel.exportExcelFile(context, it, sessionId)
            Toast.makeText(context, "正在后台导出...", Toast.LENGTH_SHORT).show()
        }
    }

    // [新增] 冲突弹窗控制
    if (inventoryStateUI.conflictList.isNotEmpty()) {
        ProductConflictScreen(
            conflicts = inventoryStateUI.conflictList,
            onToggle = { inventoryViewModel.toggleConflictAction(it) },
            onSelectAll = { inventoryViewModel.setAllConflictsAction(it) },
            onConfirm = { inventoryViewModel.confirmConflictResolution() },
            onCancel = { inventoryViewModel.cancelImport() }
        )
        // 这一句 return 非常重要，确保背景内容不显示，且 BackHandler 生效
        return
    }
    // [修改] 解析 Excel 的调用
    // 在你的 FilePicker 回调里，不要直接调 repository，而是调 viewModel
    // viewModel.analyzeExcelImport(products, records)

    // 👇 控制弹窗显示的开关
    var showUploadDialog by remember { mutableStateOf(false) }
    // 👇 监听消息：如果 ViewModel 发消息了，就弹 Toast
    LaunchedEffect(inventoryStateUI.userMessage) {
        inventoryStateUI.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            sessionViewModel.clearUserMessage() // 弹完就清空
            showUploadDialog = false     // 关闭弹窗
        }
    }

    var showSyncDialog by remember { mutableStateOf(false) }

    

    Scaffold(
        topBar = {
            Column {
                // 顶部标题栏
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isInventoryMode) "盘库模式" else "🔍 查询模式")
                            // [新增] 状态标签
                            if (sessionStatus == 1) {
                                Text(" (已归档)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            } else if (sessionStatus == 2) {
                                Text(" (🔒已锁定)", style = MaterialTheme.typography.bodySmall, color = Color.Red)
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
                        // 只有在盘库模式下才允许导入，防止乱改历史
                        if (!isReadOnly && isInventoryMode) {
                            IconButton(onClick = { showSyncDialog = true }) {
                                // 这里换个图标，比如 Icons.Default.Sync (如果找不到就用 Cloud)
                                Icon(Icons.Default.CloudUpload, contentDescription = "同步数据")
                            }

                            IconButton(onClick = {
                                // 打开文件选择器，限制只能选 Excel
                                excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                            }) {
                                // 暂时用个“上传/文件”的图标，如果没有可以用 Default.Edit
                                Icon(Icons.Default.FileOpen, contentDescription = "导入Excel")
                            }
                        }

                        IconButton(onClick = {
                            // 启动“另存为”，默认文件名
                            exportLauncher.launch("${LocalDate.now()}_盘点数据_${sessionId}.xlsx")
                        }) {
                            // 用一个向上的箭头或者下载图标
                            Icon(Icons.Default.FileDownload, contentDescription = "导出Excel")
                        }

                        if (!isReadOnly && isInventoryMode) {
                            IconButton(onClick = {
                                // 触发归档检查逻辑 (需要协程或传给 VM)
                                scope.launch {
                                    val unverifiedCount = sessionViewModel.tryFinishSession(sessionId)
                                    if (unverifiedCount == -1) {
                                        // 成功归档 -> UI 会自动监听到 status 变了，刷新界面
                                        // 可以弹个 Toast: "归档成功！"
                                    } else {
                                        // 失败 -> 弹窗提示
                                        Toast.makeText(context, "还有 $unverifiedCount 条未查验", Toast.LENGTH_SHORT).show()
                                        println("还有 $unverifiedCount 条未查验")
                                    }
                                }
                            }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "完成盘点")
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

                // 模式切换开关 (Tab)
                if (!isReadOnly) {
                ModeSwitcher(
                    isInventoryMode = isInventoryMode,
                    onModeChange = { isInventory ->
                        isInventoryMode = isInventory
                        // 切换模式时清空搜索结果，体验更好
                        searchQuery = ""
                    }
                )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3E0)).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text("当前为归档状态，仅供查询", color = Color(0xFFE65100))
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
                isInventoryMode = isInventoryMode,
                onScanClick = onScanClick
            )


            if (inventoryStateUI.isLoading && !isSyncing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (isInventoryMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth() // 占满整行宽度
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp), // 外边距
                        verticalAlignment = Alignment.CenterVertically, // 保证所有东西在垂直方向居中对齐
                        horizontalArrangement = Arrangement.SpaceBetween // ✨关键：两端对齐（中间留空）
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = if (uiState.isBatchProcessorEnabled) "长光模式✨" else "长光模式",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.isBatchProcessorEnabled) MaterialTheme.colorScheme.primary else Color.Gray) // 给用户看的标签
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.isBatchProcessorEnabled, // 绑定状态
                                onCheckedChange = { isChecked ->
                                    // 3. 这里就是“监听”，当用户点击时调用 ViewModel
                                    inventoryViewModel.toggleBatchProcessor(isChecked)
                                }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isQuickCheckMode) "极速模式⚡" else "普通模式",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isQuickCheckMode) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isQuickCheckMode,
                                onCheckedChange = { isQuickCheckMode = it }
                            )
                        }
                    }

                }
                @OptIn(ExperimentalMaterial3Api::class)
                PullToRefreshBox(
                    isRefreshing = isSyncing, // 直接绑定 ViewModel 的状态
                    onRefresh = {
                        // 触发刷新逻辑
                        inventoryViewModel.refreshData(sessionId)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 4. 结果列表区域

                    LazyColumn(modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(items = inventoryStateUI.searchResults, key = { it.record.id }) { combined ->
                            // 判断是否已查验 (备注里包含 "已查验")
                            val record = combined.record
                            val isChecked = record.remarks?.contains("已查验") == true
                            val actionWidth = 84.dp


                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // 卡片间距
                                    .clip(RoundedCornerShape(12.dp)) // 【关键】裁剪内容，保证右边按钮的角也是圆的
                                    .combinedClickable(
                                        onClick = {
                                        /* 普通点击逻辑 onDismiss = { showDetailDialog = false }*/
                                            if (!isQuickCheckMode && !isReadOnly) {
                                                selectedRecord = combined
                                                showOptionDialog = true
                                            }

                                        },
                                        onLongClick = {
                                            // ... 你的长按逻辑 ...
                                            if (!isInventoryMode) {
                                                selectedRecord = combined
                                                showDetailDialog = true
                                            } else if (isQuickCheckMode && !isReadOnly) {
                                                selectedRecord = combined
                                                showOptionDialog = true
                                            }
                                        }
                                    ),
                                colors = CardDefaults.cardColors(

                                    containerColor = MaterialTheme.colorScheme.surfaceContainer// 设置卡片底色
                                ),
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
                                        StockRecordItem(combined = combined)
                                    }

                                    // === 右边：查验按钮 (条件显示) ===
                                    if (isInventoryMode && isQuickCheckMode && !isReadOnly) {
                                        // 只有满足条件时，这个 block 才会渲染
                                        // 渲染时，左边的 weight(1f) 会让出空间；不渲染时，左边占满

                                        if (isChecked) {
                                            // 已查验 -> 绿色标签区域
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight() // 纵向填满
                                                    .background(Color(0xFFE8F5E9)) // 淡淡的绿色背景
                                                    .width(actionWidth),
//                                                    .padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF22C55E).copy(alpha = 0.95f),
                                                        modifier = Modifier.size(55.dp))
                                                    Text(
                                                        text = "已查验",
                                                        color = Color(0xFF37A43D),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            // 未查验 -> 棕色按钮
                                            Surface(
                                                onClick = { inventoryViewModel.quickCheck(combined) },
                                                modifier = Modifier
                                                    .fillMaxHeight() // 纵向填满
                                                    .width(actionWidth),   // 固定宽度
                                                color = Color(0xFF038CF4).copy(alpha = 0.9f), // 棕色背景
                                                contentColor = Color.White
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
//                            HorizontalDivider()
                        }
                    }

                }

            }
            // 外部容器：处理嵌套滚动

            if (showSyncDialog) {
                SyncDialog(
                    onDismiss = { showSyncDialog = false },
                    onUpload = { ip ->
                        // 调用上传
                        inventoryViewModel.uploadDataToServer(ip)
                        // 这里可以不关弹窗，等结果出来再关，或者直接关
                        showSyncDialog = false
                    },
                    onDownload = { ip, sidString ->
                        // 调用下载
                        val sid = sidString.toLongOrNull()
                        if (sid != null) {
                            inventoryViewModel.downloadDataFromPC(ip, sid)
                            showSyncDialog = false
                        } else {
                            // 可以提示 ID 格式不对，或者直接无视
                        }
                    }
                )
            }

            // 👇 [可选] 加一个 Loading 转圈圈
            if (inventoryStateUI.isLoading) {
                CircularProgressIndicator()
            }
        }
    }

    //挂载对话框
    if (showAddDialog) {
        AddRecordDialog(
            onDismiss = { showAddDialog = false },
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
            onDismiss = { showEditDialog = false },
            combined = selectedRecord!!, // [新增参数] 传入旧数据
            onConfirm = { updatedRecord ->
                inventoryViewModel.updateRecord(updatedRecord)
                showEditDialog = false
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
}

// --- 子组件：模式切换器 ---
@Composable
fun ModeSwitcher(isInventoryMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 盘库模式 Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(if (isInventoryMode) MaterialTheme.colorScheme.primary else Color.LightGray)
                .clickable { onModeChange(true) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "盘库模式",
                color = if (isInventoryMode) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
        // 查询模式 Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(if (!isInventoryMode) MaterialTheme.colorScheme.tertiary else Color.LightGray)
                .clickable { onModeChange(false) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "查询模式",
                color = if (!isInventoryMode) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



// --- 子组件：搜索栏 ---
@Composable
fun SearchBarArea(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isInventoryMode: Boolean,
    onScanClick: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        label = { Text(if (isInventoryMode) "扫描或输入 UDI / 批号" else "搜索名称、厂家、库位...") },
        trailingIcon = {
            Row {
                // [新增] 扫码按钮
                IconButton(onClick = onScanClick) { // ✅ 步骤 5: 绑定到 onClick
                    Icon(
                        // 假设你有一个扫码图标，如果没有，可以用其他图标代替
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "扫码"
                    )
                }
            }


        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

// --- 子组件：单条库存记录卡片 ---
@Composable
fun StockRecordItem(combined: StockRecordCombined) {
    val record = combined.record
    val product = combined.product
    val bookQty = record.quantity
    val actQty = record.actualQuantity
    val isVerified = actQty != null
    val isError = isVerified && (actQty != bookQty)

    Column(modifier = Modifier.padding(16.dp)) {

        // === 第一行：UDI 和 数量 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top // 建议加上顶部对齐
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 显示产品名
                Text(
                    text = product?.productName ?: "未录入产品 (${record.di})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                // 显示规格/型号
                if (product != null) {
                    Text(
                        text = "${product.di} ${product.model ?: ""}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // 显示数量
            Column(horizontalAlignment = Alignment.End) {
                if (!isVerified) {
                    // 1. 未查验状态
                    Text(
                        text = "${bookQty.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Blue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "未盘",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                } else if (isError) {
                    // 2. 差异状态 (红色高亮)
                    Text(
                        text = "实: ${actQty.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error, // 红色
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "账: ${bookQty.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Blue,
                        textDecoration = TextDecoration.LineThrough // 删除线
                    )
                    Text(
                        text = "差: ${actQty.toInt()-bookQty.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                    )
                } else {
                    // 3. 正常状态 (绿色/主色)
                    Text(
                        text = "${actQty.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF4CAF50), // 绿色/蓝色
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " 已盘",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } // 👈 Row 在这里结束！

        // === 分割线区域 (必须放在 Column 里，两个 Row 之间) ===
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(8.dp))

        // === 第二行：批号、效期、库位、厂家 ===
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // 左下角信息
            Column {
                Text("批号: ${record.batchNumber}", style = MaterialTheme.typography.bodyMedium)
                // 💡 建议：如果 expiryDate 是 Long 型 (如 20251231)，可能需要简单格式化一下，不然显示一串数字
                Text("效期: ${record.expiryDate}", style = MaterialTheme.typography.bodyMedium)
            }

            // 右下角信息
            Column(horizontalAlignment = Alignment.End) {
                Text("库位: ${record.location}", style = MaterialTheme.typography.bodyMedium)

                val mfr = product?.manufacturer ?: "未知厂家"
                Text(
                    text = if (mfr.length > 10) mfr.take(10) + "..." else mfr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }


}

// --- 子组件：盲盘/新增记录对话框 ---
@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit,
    onConfirm: (StockRecord) -> Unit,
    sessionId: Long,
    existingRecord: StockRecord? = null
) {
    var di by remember { mutableStateOf(existingRecord?.di ?: "") }
    var batch by remember { mutableStateOf(existingRecord?.batchNumber ?: "") }
    var qty by remember { mutableStateOf(existingRecord?.quantity?.toString() ?: "") }
    var location by remember { mutableStateOf(existingRecord?.location ?: "") }
    // 简化起见，效期先让用户手动输，后面我们再加日历选择器
    var expiry by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingRecord == null) "新增记录" else "修改记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = di, onValueChange = { di = it }, label = { Text("产品 DI/条码") })
                OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("批号") })
                OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("效期 (如 20251231)") })
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("库位") })
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                // 简单的校验
                if (di.isNotBlank() && qty.isNotBlank()) {
                    val newRecord = StockRecord(
                        sessionId = sessionId,
                        di = di,
                        batchNumber = batch,
                        expiryDate = expiry.toLongOrNull() ?: 0L, // 简单处理，后面再优化
                        quantity = qty.toDoubleOrNull() ?: 0.0,
                        location = location
                    )
                    onConfirm(newRecord)
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

}

//详情页面
@Composable
fun RecordDetailDialog(combined: StockRecordCombined, onDismiss: () -> Unit) {
    val record = combined.record
    val product = combined.product
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("库存详情") },
        text = {
            Column {
                if (product != null) {
                    Text("物料名称: ${product.productName}")
                }
                Text("UDI/物料编码: ${record.di}")
                Text("批号: ${record.batchNumber}")
                Text("效期: ${record.expiryDate}") // 你可以加个格式化函数
                Text("仓库名称: ${record.location}")
                Text("数量: ${record.quantity}")
                Text("实际数量: ${record.actualQuantity}")
                Text("备注: ${record.remarks}")
                Text("数据来源: ${if(record.sourceType == 1) "Excel导入" else "手动盘点"}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun RecordOptionDialog(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请选择操作") },
        text = { Text("您想对这条记录做什么？") },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text("编辑修改")
            }
        },
        dismissButton = {
            // 只有允许删除时才显示这个按钮
            if (canDelete) {
                TextButton(onClick = onDelete) {
                    Text("🗑️ 删除记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}


//盘库编辑
@Composable
fun AuditRecordDialog(
    combined: StockRecordCombined, // 接收大包裹，才有名字
    onDismiss: () -> Unit,  //取消关闭对话框回调
    onConfirm: (StockRecord) -> Unit //确认保存回调更新记录
) {
    val record = combined.record
    val product = combined.product

    // 状态管理
    // 实盘数量：如果有值就显示，没值显示空字符串
    var actualQtyStr by remember {
        mutableStateOf(record.actualQuantity?.toString() ?: "")
    }
    var remarks by remember { mutableStateOf(record.remarks ?: "") }

    // 错误状态
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("库存查验") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // === 1. 顶部：只读的产品信息 (让用户确认没盘错货) ===
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                    Text(
                        text = product?.productName ?: "未知产品 (${record.di})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "厂家: ${product?.manufacturer ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "规格: ${product?.specification ?: "-"} ${product?.model ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // === 2. 中部：只读的批号/效期/账面数 ===
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("批号", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(record.batchNumber, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("账面数量 (Excel)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        // 账面数量显示在这里，不可修改
                        Text("${record.quantity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()

                // === 3. 底部：核心输入区 (只准改实盘数) ===
                OutlinedTextField(
                    value = actualQtyStr,
                    onValueChange = {
                        actualQtyStr = it
                        isError = false
                    },
                    label = { Text("实盘数量 (Actual)") },
                    placeholder = { Text("请输入你数出来的数量") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    // 贴心的差异提示
                    supportingText = {
                        val inputVal = actualQtyStr.toDoubleOrNull()
                        if (inputVal != null && inputVal != record.quantity) {
                            Text(
                                "⚠️ 差异: ${inputVal - record.quantity}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("备注说明") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newActual = actualQtyStr.toDoubleOrNull()
                    // 简单校验：不能为空且必须是数字(如果是空字符串视为撤销查验，也可以允许)
                    if (actualQtyStr.isNotBlank() && newActual == null) {
                        isError = true
                    } else {
                        val processedRemarks = when {
                            remarks.isBlank() -> {
                                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                "已查验[$timestamp]"
                            }
                            !remarks.contains("已查验") -> {
                                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                "已查验[$timestamp] ${remarks.trim()}"
                            }
                            else -> remarks
                        }
                        // 构建更新对象：只更新 actualQuantity 和 remarks
                        val updated = record.copy(
                            actualQuantity = newActual, // 哪怕是 null 也可以传回去(代表撤销)
                            remarks = processedRemarks.trim()
                        )
                        onConfirm(updated)



                    }
                }
            ) {
                Text("确认保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

