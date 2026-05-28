package com.example.inventorymaster.batchscanner

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryTaskDetailScreen(
    viewModel: InventoryMainViewModel,
    onNavigateToBatchCamera: (inputUri: String?) -> Unit,
    onNavigateToSingleScanner: () -> Unit
) {
    // 在已有的状态变量（如 currentDoc）下方添加：
    var showImportMenu by remember { mutableStateOf(false) }
    var showNetworkTaskDialog by remember { mutableStateOf(false) }
    val networkTasks by viewModel.networkTasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()


    val photoList by viewModel.photoList.collectAsState()
    val recordList by viewModel.recordList.collectAsState()
    // --- 👇 新增：监听单据状态 👇 ---
    // 替换成复数状态
    val currentDocs by viewModel.currentDocuments.collectAsState()
    val context = LocalContext.current

    // 创建 PickVisualMediaRequest 实例
    val pickImageRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    // 相册选择器：选择图片后导航到 BatchScannerScreen 进行处理
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // 将选中的图片 Uri 传给 BatchScannerScreen 进行条码解析
            onNavigateToBatchCamera(uri.toString())
        }
    }

    // --- 👇 新增：JSON 文件选择器 👇 ---
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importTargetDocumentFromJson(uri)
        }
    }

    // PDF 导出文件创建器
    val scope = rememberCoroutineScope()
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val docs = viewModel.getDocumentsForExport()
                    val photos = viewModel.getPhotoScanResults()
                    val records = viewModel.getRecordList()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        PdfExportUtils.generatePdf(context, outputStream, docs, photos, records)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF 已导出", Toast.LENGTH_SHORT).show()
                    }
                    // --- 调用网络上传归档 ---
                    val documents = viewModel.currentDocuments.value // 获取多单据列表
                    val documentIdsStr = documents.joinToString(",") { it.documentId }
                    if (documentIdsStr.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "正在回传服务器归档...", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.uploadPdfResultToNetwork(context, uri, documentIdsStr) { resultMsg ->
                            Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF 导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 导出前校验弹窗
    var showExportConfirmDialog by remember { mutableStateOf(false) }


    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = { Text("导出 PDF") },
            text = { Text("仍有未完成项（错误项或数量未匹配），是否仍然导出？") },
            confirmButton = {
                Button(onClick = {
                    showExportConfirmDialog = false
                    pdfExportLauncher.launch("单据报告_${currentDocs.firstOrNull()?.documentName ?: "无单据"}.pdf")
                }) { Text("仍然导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentDocs.isEmpty()) "批量二维码识别" else currentDocs.joinToString(" + ") { it.documentName },
                        maxLines = 1, // 必须限制为单行
                        modifier = Modifier.basicMarquee() // ✨ 添加跑马灯效果
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        // 导入单据
                        documentLauncher.launch(arrayOf("application/json"))
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导入单据")
                    }
                    IconButton(onClick = {
                        // 导出 PDF：先校验，有问题弹窗
                        val readiness = viewModel.checkExportReadiness()
                        if (readiness == ExportReadiness.CLEAN) {
                            val defaultFileName = if (currentDocs.isEmpty()) "未录入单据" else currentDocs.joinToString("_") { it.documentName }
                            pdfExportLauncher.launch("单据报告_${defaultFileName}.pdf")
                        } else {
                            showExportConfirmDialog = true
                        }
                    }) {
                        Text("PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background) // 浅灰背景，让卡片更凸显
        ) {
            // ================= 1. 上半区：图片轮播区 =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        // 如果没有图片，点击图片区域也可以触发拍照
                        if (photoList.isEmpty()) onNavigateToBatchCamera(null)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoList.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击此处或下方按钮进行批量采集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { photoList.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val photoUri = photoList[page]
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 1. 底图：展示照片
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "盘点照片 $page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            // 2. 右上角：删除按钮
                            IconButton(
                                onClick = {
                                    // 调用 ViewModel 执行删除逻辑
                                    viewModel.removePhoto(photoUri)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    // 给图标加一个半透明背景，确保在浅色图片下也能看清
                                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除照片",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    // 简单的页码指示器
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photoList.size}",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ================= 2. 中间区：操作按钮栏（现代化紧凑风格） =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 导入单据
                Box {
                    FilledTonalButton(
                        onClick = { showImportMenu = true },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📄 单据", style = MaterialTheme.typography.labelMedium)
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("局域网获取 (Python)") },
                            onClick = {
                                showImportMenu = false
                                viewModel.fetchTaskListFromNetwork { errorMsg ->
                                    if (errorMsg != null) Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                    else showNetworkTaskDialog = true
                                }
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("本地 JSON 文件") },
                            onClick = {
                                showImportMenu = false
                                documentLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
                // 导入图片
                FilledTonalButton(
                    onClick = { galleryLauncher.launch(pickImageRequest) },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("图片", style = MaterialTheme.typography.labelMedium)
                }
                // 批量补拍
                Button(
                    onClick = { onNavigateToBatchCamera(null) },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补拍", style = MaterialTheme.typography.labelMedium)
                }
                // 单码补扫
                FilledTonalButton(
                    onClick = onNavigateToSingleScanner,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补扫", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ================= 3. 下半区：校验结果表格（现代化风格） =================
            // ================= 3. 下半区：校验结果表格（现代化风格） =================
            Text(
                text = "校验结果明细 (${recordList.size}条)",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 判断是否有导入单据（是否显示"目标"列）
            val hasTarget = recordList.any { record -> record.targetQty > 0 }

            //统一定义所有列的宽度，确保表头和表体完全对齐
            val wProduct = 120.dp
            val wDi = 60.dp
            val wBatch = 80.dp
            val wProdDate = 90.dp // 新增生产日期列宽
            val wExpDate = 90.dp  // 新增失效日期列宽
            val wTarget = 60.dp
            val wActual = 70.dp
            val wStatus = 50.dp
            val wAction = 50.dp

            // 2. 总宽度必须大于普通手机屏幕（约400dp），才能触发横向滑动
            val tableTotalWidth = if (hasTarget) 830.dp else 770.dp
            val horizontalScrollState = rememberScrollState()

            // 优化：在 LazyColumn 外部提前定义好这些衍生颜色，避免滑动时疯狂创建对象
            val errorBg = StatusError.copy(alpha = 0.1f)
            val overflowBg = StatusWarning.copy(alpha = 0.1f)
            val missingBgTarget = StatusSuccess.copy(alpha = 0.1f)
            val matchedBg = StatusSuccess.copy(alpha = 0.1f)
            val missingBgNoTarget = StatusWarning.copy(alpha = 0.2f)
            val modifiedBg = StatusWarning.copy(alpha = 0.25f)


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState) // 横向滑动
                ) {
                    // -------- 表头（固定） --------
                    Row(
                        modifier = Modifier
                            .width(tableTotalWidth)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 使用统一定义的宽度变量
                        TableHeaderCell("产品名称", Modifier.width(wProduct))
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("DI", Modifier.width(wDi))
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("批号", Modifier.width(wBatch))
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("生产日期", Modifier.width(wProdDate))
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("失效日期", Modifier.width(wExpDate))
                        if (hasTarget) {
                            VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            TableHeaderCell("目标", Modifier.width(wTarget), TextAlign.Center)
                        }
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("实际", Modifier.width(wActual), TextAlign.Center)
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("状态", Modifier.width(wStatus), TextAlign.Center)
                        VLine(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        TableHeaderCell("", Modifier.width(wAction))
                    }

                    // -------- 表体（LazyColumn 虚拟化列表） --------
                    val groupedRecords = recordList.groupBy { it.documentName }

                    LazyColumn(
                        modifier = Modifier.width(tableTotalWidth)
                    ) {
                        groupedRecords.forEach { (docName, recordsInDoc) ->

                            if (currentDocs.isNotEmpty()) {
                                item {
                                    val docId = recordsInDoc.firstOrNull()?.documentId?.takeIf { it.isNotBlank() } ?: "-"
                                    Box(
                                        modifier = Modifier
                                            .width(tableTotalWidth)
                                            .padding(vertical = 4.dp)
                                            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$docName $docId",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            itemsIndexed(recordsInDoc, key = { _, record -> record.id }) { index, record ->
                                val bgColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                val rowBg = when {
                                    record.isError -> errorBg
                                    record.status == RecordStatus.OVERFLOW -> overflowBg
                                    record.targetQty > 0 && record.status == RecordStatus.MISSING -> missingBgTarget
                                    record.status == RecordStatus.MATCHED -> matchedBg
                                    record.status == RecordStatus.MISSING -> missingBgNoTarget
                                    record.isManualModified -> modifiedBg
                                    else -> bgColor
                                }

                                Row(
                                    modifier = Modifier
                                        .width(tableTotalWidth)
                                        .background(rowBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 同样使用统一定义的宽度变量
                                    TableBodyCell(
                                        text = record.productName,
                                        modifier = Modifier.width(wProduct),
                                        color = if (record.isError) StatusError else MaterialTheme.colorScheme.onSurface
                                    )
                                    VLine()
                                    TableBodyCell(text = record.di, modifier = Modifier.width(wDi), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    VLine()
                                    TableBodyCell(text = record.batch, modifier = Modifier.width(wBatch))
                                    VLine()
                                    TableBodyCell(text = record.productionDate ?: "-", modifier = Modifier.width(wProdDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    VLine()
                                    TableBodyCell(text = record.expiryDate ?: "-", modifier = Modifier.width(wExpDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (hasTarget) {
                                        VLine()
                                        TableBodyCell(
                                            text = record.targetQty.toString(),
                                            modifier = Modifier.width(wTarget),
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    VLine()
                                    EditableQuantityCell(
                                        qty = record.actualQty,
                                        modifier = Modifier.width(wActual),
                                        onQtyChange = { newQty -> viewModel.updateRecordQuantity(record.id, newQty) }
                                    )
                                    VLine()
                                    StatusIndicator(status = record.status, modifier = Modifier.width(wStatus))
                                    VLine()
                                    DeleteButton(modifier = Modifier.width(wAction), onDelete = { viewModel.removeRecord(record.id) })
                                }

                                HorizontalDivider(
                                    modifier = Modifier.width(tableTotalWidth),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNetworkTaskDialog) {
            // 记住当前勾选的 ID 集合
            var selectedTaskIds by remember { mutableStateOf(setOf<String>()) }

            AlertDialog(
                onDismissRequest = { showNetworkTaskDialog = false },
                title = { Text("待盘点单据 (多选)") },
                text = {
                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else if (networkTasks.isEmpty()) {
                        Text("当前没有未完成的抓取任务")
                    } else {
                        LazyColumn {
                            items(networkTasks.size) { index ->
                                val task = networkTasks[index]
                                val isSelected = selectedTaskIds.contains(task.documentId)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTaskIds = if (isSelected) selectedTaskIds - task.documentId else selectedTaskIds + task.documentId
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null // 由 Row 的 clickable 托管
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = task.documentName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(text = "单号: ${task.documentId} | 日期: ${task.documentDate}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = selectedTaskIds.isNotEmpty(),
                        onClick = {
                            // 开始多选拉取！
                            viewModel.loadMultipleTaskDetails(selectedTaskIds.toList()) { errorMsg ->
                                if (errorMsg != null) Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                showNetworkTaskDialog = false
                            }
                        }
                    ) { Text("确认导入") }
                },
                dismissButton = {
                    TextButton(onClick = { showNetworkTaskDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

// ===================== 表格子组件（全部使用 Modifier.weight 自适应） =====================

/**
 * 垂直分隔线（用 Box 绘制，避免 Material3 Divider 的方向兼容问题）
 */
@Composable
private fun VLine(color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(color)
    )
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableBodyCell(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.combinedClickable(
            onClick = { /* 普通点击不做处理，但必须声明 onClick 才能捕获长按 */ },
            onLongClick = {
                if (text.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(text))
                    // 给用户一个 Toast 交互反馈
                    Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
                }
            }
        )
    )
}

@Composable
private fun EditableQuantityCell(
    qty: Int,
    modifier: Modifier,
    onQtyChange: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    if (isEditing) {
        BasicTextField(
            value = textValue,
            onValueChange = { newVal ->
                if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                    textValue = newVal
                }
            },
            modifier = modifier
                .height(32.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            textStyle = MaterialTheme.typography.labelMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val newQty = textValue.toIntOrNull()
                    if (newQty != null && newQty >= 0) {
                        onQtyChange(newQty)
                    } else {
                        textValue = qty.toString()
                    }
                    isEditing = false
                }
            ),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    innerTextField()
                }
            }
        )
    } else {
        Box(
            modifier = modifier.clickable {
                isEditing = true
                textValue = ""
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = qty.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: RecordStatus, modifier: Modifier) {
    val (color, label) = when (status) {
        RecordStatus.NORMAL -> StatusSuccess to "✔"
        RecordStatus.MATCHED -> StatusSuccess to "✔"
        RecordStatus.MISSING -> StatusWarning to "⚠"
        RecordStatus.OVERFLOW -> StatusWarning to "⚠"
        RecordStatus.ERROR -> StatusError to "✘"
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun DeleteButton(modifier: Modifier, onDelete: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
