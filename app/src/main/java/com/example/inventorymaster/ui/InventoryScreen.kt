package com.example.inventorymaster.ui

// --- 请确保包含以下所有 import ---

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.utils.ExcelUtils
import com.example.inventorymaster.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.CloudUpload // 需要用到这个图标
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.inventorymaster.utils.QRCodeUtil


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    sessionId: Long,
    onBackClick: () -> Unit,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)
) {
    // 1. 监听数据状态
    val uiState by viewModel.uiState.collectAsState()
    // 2. 本地 UI 状态
    var searchQuery by remember { mutableStateOf("") }
    // 默认为盘库模式 (true=盘库, false=查询)
    LaunchedEffect(uiState.searchQuery) {
        if (searchQuery != uiState.searchQuery) {
            searchQuery = uiState.searchQuery
        }
    }
    var showScanScreen by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }

    val currentSession = uiState.sessions.find { it.id == sessionId }
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
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }



    // 定义文件选择器
    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 用户选中了文件，开始解析并导入
            scope.launch {
                // 1. 调用刚才写的 ExcelUtils 解析
                // 注意：这里是在主线程调用的，如果文件很大可能会卡。
                // 更好的做法是把 parseExcel 放到 IO 线程，或者在 ViewModel 里做。
                // 为了代码简单，我们暂时放在这里，后面我教你移到 ViewModel。
                try {
                    val result = ExcelUtils.parseExcel(context, it, sessionId)

                    // 2. 拿到数据，传给 ViewModel 保存
                    viewModel.analyzeExcelImport(result.products, result.records)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    // 1. 找 ViewModel 要数据
                    val data = viewModel.getExportDataForExcel(sessionId)

                    // 2. 打开文件流并写入
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        ExcelUtils.exportExcel(outputStream, data)
                    }

                    // 3. 提示成功 (这里简单打印，你可以弹个 Toast)
                    println("导出成功！路径: $it")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // [新增] 冲突弹窗控制
    if (uiState.conflictList.isNotEmpty()) {
        ProductConflictScreen(
            conflicts = uiState.conflictList,
            onToggle = { viewModel.toggleConflictAction(it) },
            onSelectAll = { viewModel.setAllConflictsAction(it) },
            onConfirm = { viewModel.confirmConflictResolution() },
            onCancel = { viewModel.cancelImport() }
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
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearUserMessage() // 弹完就清空
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
                        // 👇 [新增] 上传按钮
                        IconButton(onClick = { showSyncDialog = true }) {
                            // 这里换个图标，比如 Icons.Default.Sync (如果找不到就用 Cloud)
                            Icon(Icons.Default.CloudUpload, contentDescription = "同步数据")
                        }

                        // 只有在盘库模式下才允许导入，防止乱改历史
                        if (!isReadOnly && isInventoryMode) {
                            IconButton(onClick = {
                                // 打开文件选择器，限制只能选 Excel
                                excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                            }) {
                                // 暂时用个“上传/文件”的图标，如果没有可以用 Default.Edit
                                Icon(Icons.Default.Edit, contentDescription = "导入Excel")
                            }
                        }

                        IconButton(onClick = {
                            // 启动“另存为”，默认文件名
                            exportLauncher.launch("盘点数据_${sessionId}.xlsx")
                        }) {
                            // 用一个向上的箭头或者下载图标
                            Icon(Icons.Default.ArrowUpward, contentDescription = "导出Excel")
                        }

                        if (!isReadOnly && isInventoryMode) {
                            IconButton(onClick = {
                                // 触发归档检查逻辑 (需要协程或传给 VM)
                                scope.launch {
                                    val unverifiedCount = viewModel.tryFinishSession(sessionId)
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
                    viewModel.performSearch(searchQuery, isInventoryMode)
                    focusManager.clearFocus() // 收起键盘
                },
                isInventoryMode = isInventoryMode,
                onScanClick = onScanClick
            )

            // 4. 结果列表区域
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (isInventoryMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End // 靠右显示
                    ) {
                        Text(
                            text = if (isQuickCheckMode) "⚡ 极速模式" else "普通模式",
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

                LazyColumn(modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(items = uiState.searchResults, key = { it.record.id }) { combined ->
                        // 判断是否已查验 (备注里包含 "已查验")
                        val record = combined.record
                        val isChecked = record.remarks?.contains("已查验") == true

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},       // 普通点击：暂时没定义，可以留空
                                    onLongClick = {
                                        // 逻辑分流：
                                        if (!isInventoryMode) {
                                            // 1. 查询模式 -> 显示详情
                                            selectedRecord = combined
                                            showDetailDialog = true
                                        } else {
                                            // 2. 盘库模式
                                            if (!isQuickCheckMode && !isReadOnly) {
                                                // 极速模式下 -> 禁用长按 (防误触)
                                            } else {
                                                // 普通模式 -> 弹出编辑菜单
                                                selectedRecord = combined
                                                showOptionDialog = true
                                            }
                                        }
                                    }
                                ),
                            color = Color.Transparent
                        ) {
                            // 在这里放你的 Item UI，我们用 Row 来包装一下，以便把按钮放右边
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左边：原有的 Item 内容 (占大部分宽度)
                                Box(modifier = Modifier.weight(1f)) {
                                    StockRecordItem(combined = combined)
                                }

                                // 右边：查验按钮 (仅在 盘库模式 + 极速模式 下显示)
                                if (isInventoryMode && isQuickCheckMode && !isReadOnly) {
                                    if (isChecked) {
                                        // 已查验 -> 显示绿色标签
                                        Text(
                                            text = "✅ 已查验",
                                            color = Color(0xFF4CAF50), // 绿色
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    } else {
                                        // 未查验 -> 显示按钮
                                        Button(
                                            onClick = { viewModel.quickCheck(combined) },
                                            modifier = Modifier.padding(start = 8.dp),
                                            // 稍微调小一点按钮
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("查验")
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            // 👇 [新增] 如果开关打开，就显示弹窗
//            if (showUploadDialog) {
//                UploadDialog(
//                    onDismiss = { showUploadDialog = false },
//                    onConfirm = { ip ->
//                        // 调用 ViewModel 开始上传
//                        viewModel.uploadDataToServer(ip)
//                        // 注意：这里不要马上把 showUploadDialog = false
//                        // 最好等 loading 结束或者成功后再关 (上面的 LaunchedEffect 会处理)
//                    }
//                )
//            }

            if (showSyncDialog) {
                SyncDialog(
                    onDismiss = { showSyncDialog = false },
                    onUpload = { ip ->
                        // 调用上传
                        viewModel.uploadDataToServer(ip)
                        // 这里可以不关弹窗，等结果出来再关，或者直接关
                        showSyncDialog = false
                    },
                    onDownload = { ip, sidString ->
                        // 调用下载
                        val sid = sidString.toLongOrNull()
                        if (sid != null) {
                            viewModel.downloadDataFromPC(ip, sid)
                            showSyncDialog = false
                        } else {
                            // 可以提示 ID 格式不对，或者直接无视
                        }
                    }
                )
            }

            // 👇 [可选] 加一个 Loading 转圈圈
            if (uiState.isLoading) {
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
                viewModel.addRecord(record)
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
                selectedRecord?.let { viewModel.deleteRecord(it.record) }
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
                viewModel.updateRecord(updatedRecord)
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
                viewModel.performSearch(result, isInventoryMode)
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
            .padding(16.dp),
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

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                            color = Color.Blue
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
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Gray,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough // 删除线
                        )
                    } else {
                        // 3. 正常状态 (绿色/主色)
                        Text(
                            text = "x${actQty.toInt()}",
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

@Composable
fun AuditRecordDialog(
    combined: StockRecordCombined, // 接收大包裹，才有名字
    onDismiss: () -> Unit,
    onConfirm: (StockRecord) -> Unit
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
                        // 构建更新对象：只更新 actualQuantity 和 remarks
                        val updated = record.copy(
                            actualQuantity = newActual, // 哪怕是 null 也可以传回去(代表撤销)
                            remarks = remarks
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

