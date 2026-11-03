package com.example.inventorymaster.batchscanner.task.screen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.screen.BatchScannerScreen
import com.example.inventorymaster.batchscanner.core.util.PendingBatchImageQueue
import com.example.inventorymaster.batchscanner.core.viewmodel.BatchScannerViewModel
import com.example.inventorymaster.batchscanner.ocr.models.OcrExtractedData
import com.example.inventorymaster.batchscanner.task.models.DocStatus
import com.example.inventorymaster.batchscanner.task.models.FieldStatus
import com.example.inventorymaster.batchscanner.task.models.InventoryRecord
import com.example.inventorymaster.batchscanner.task.models.RecordSource
import com.example.inventorymaster.batchscanner.task.models.RowStatus
import com.example.inventorymaster.batchscanner.task.models.TargetDocument
import com.example.inventorymaster.batchscanner.task.util.PdfExportUtils
import com.example.inventorymaster.batchscanner.task.viewmodel.InventoryMainViewModel
import com.example.inventorymaster.batchscanner.task.viewmodel.RealtimeEntryState
import com.example.inventorymaster.batchscanner.task.viewmodel.SerialGenUiState
import com.example.inventorymaster.data.network.EntryJobDto
import com.example.inventorymaster.data.network.EntryJobDuplicate
import com.example.inventorymaster.data.network.EntryJobItemResult
import com.example.inventorymaster.batchscanner.videostream.screen.VideoStreamScreen
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.inventorymaster.ui.analyzer.SimpleFloatingScanner
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import com.example.inventorymaster.utils.PdfEngine
import com.example.inventorymaster.utils.pdf.SignatureLibraryPickerDialog
import com.example.inventorymaster.utils.pdf.SignaturePadDialog
import com.example.inventorymaster.utils.pdf.SignatureStore
import com.example.inventorymaster.utils.DateUtils
import com.example.inventorymaster.utils.permission.AppPermission
import com.example.inventorymaster.utils.permission.rememberPermissionAction
import com.example.inventorymaster.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryTaskDetailScreen(
    viewModel: InventoryMainViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToBatchCamera: (inputUri: String?) -> Unit,
    onNavigateToVideoStream: () -> Unit = {},
) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    var showNetworkTaskDialog by remember { mutableStateOf(false) }
    val networkTasks by viewModel.networkTasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val photoList by viewModel.photoList.collectAsState()
    val recordList by viewModel.recordList.collectAsState()
    val currentDocs by viewModel.currentDocuments.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // --- 高级 PDF 配置弹窗状态 ---
    var showPdfAdvancedConfigDialog by remember { mutableStateOf(false) }
    var watermarkInputText by remember { mutableStateOf("") }
    var signatureBitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 是否留存本地：勾选=走系统选位器保存到手机（原流程）；不勾选=跳过本地保存，PDF 仅上传服务端
    var keepLocalPdf by remember { mutableStateOf(true) }

    // --- 👇 新增：手写画布弹窗的显示状态 👇 ---
    var showSignaturePadDialog by remember { mutableStateOf(false) }
    var showSignatureLibraryDialog by remember { mutableStateOf(false) }

    // --- 👇 新增：上传解码信息的弹窗状态 👇 ---
    var showUploadDecodeDialog by remember { mutableStateOf(false) }
    var decodeNameInput by remember { mutableStateOf("") }

    val pickImageRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                // 单张：保持原有流程（进入批量识别裁剪）
                onNavigateToBatchCamera(uris.first().toString())
            } else {
                // 多张：先存跨页队列，带第一张跳转，批量识别页将进入"下一张"队列模式
                PendingBatchImageQueue.put(uris)
                onNavigateToBatchCamera(uris.first().toString())
            }
        }
    }

    // PDF 导出主逻辑（targetUri 为 null 表示不留存本地：生成后仅上传服务端，成功即清理缓存文件）
    suspend fun runPdfExport(targetUri: Uri?) {
        try {
            val tempSourceFile = File(context.cacheDir, "temp_report_source.pdf")
            val tempOutputOutputStream = tempSourceFile.outputStream()

            val docs = viewModel.getDocumentsForExport()
            val photos = viewModel.getPhotoScanResults()
            val records = viewModel.getRecordList()
            val materialDiMap = docs.flatMap { it.items }.zip(records)
                .map { (item, record) -> item.materialCode to record.di }
                .toMap()

            PdfExportUtils.generatePdf(context, tempOutputOutputStream, docs, photos, records, materialDiMap)
            tempOutputOutputStream.close()

            val processedFile = File(context.cacheDir, "temp_report_processed.pdf")

            // 水印层叠加
            val finalWatermarkText = if (watermarkInputText.isNotBlank()) {
                watermarkInputText
            } else {
                settingsState.watermarkText
            }
            if (finalWatermarkText.isNotBlank()) {
                // 🚀 UI 层不再需要做任何位运算，直接把设置参数原样透传给引擎
                        PdfEngine.addWatermark(
                            inputPath = tempSourceFile.absolutePath,
                            outputPath = processedFile.absolutePath,
                            text = finalWatermarkText,
                            fontSize = settingsState.watermarkFontSize,
                            colorLong = settingsState.watermarkColor, // 传入 Long
                            opacity = settingsState.watermarkOpacity, // 传入 Float
                            rotationAngle = settingsState.watermarkAngle,
                            density = settingsState.watermarkDensity
                        )
            } else {
                tempSourceFile.copyTo(processedFile, overwrite = true)
            }

            // 手写签名层无缝叠加（位置/大小/透明度来自 设置 → 电子签名库 的默认盖章参数）
            signatureBitmapState?.let { sigBitmap ->
                val finalFile = File(context.cacheDir, "temp_report_final.pdf")
                PdfEngine.addSignature(
                    inputPath = processedFile.absolutePath,
                    outputPath = finalFile.absolutePath,
                    signatureBitmap = sigBitmap,
                    pageIndex = 0,
                    percentX = settingsState.signatureOffsetX,
                    percentY = settingsState.signatureOffsetY,
                    scaleSize = settingsState.signatureScale,
                    opacity = settingsState.signatureOpacity
                )
                finalFile.copyTo(processedFile, overwrite = true)
            }

            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri)?.use { targetOs ->
                    processedFile.inputStream().copyTo(targetOs)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF 导出完成", Toast.LENGTH_SHORT).show()
                }
            }

            val documents = viewModel.currentDocuments.value
            val documentIdsStr = documents.joinToString(",") { it.documentId }
            if (documentIdsStr.isNotBlank()) {
                if (targetUri != null) {
                    viewModel.uploadPdfResultToNetwork(context, targetUri, documentIdsStr) { resultMsg ->
                        Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.uploadPdfFileToNetwork(processedFile, documentIdsStr) { success, resultMsg ->
                        Toast.makeText(
                            context,
                            if (success) resultMsg else "$resultMsg（PDF 已暂存应用缓存）",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (success) {
                            tempSourceFile.delete()
                            processedFile.delete()
                        }
                    }
                }
            } else if (targetUri == null) {
                tempSourceFile.delete()
                processedFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无关联单据，未上传也未留存本地", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF 渲染挂件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runPdfExport(uri)
            }
        }
    }

    var showExportConfirmDialog by remember { mutableStateOf(false) }

    var showSingleScanner by remember { mutableStateOf(false) }
    val requestBatchCamera = rememberPermissionAction(AppPermission.CAMERA) {
        onNavigateToBatchCamera(null)
    }
    val requestVideoStream = rememberPermissionAction(AppPermission.CAMERA) {
        onNavigateToVideoStream()
    }
    val requestSingleScanner = rememberPermissionAction(AppPermission.CAMERA) {
        showSingleScanner = true
    }
    // 补扫弹窗的内容去重记忆：提升在任务页持有，跨弹窗开关保留（换任务重建屏幕自动归零）；
    // 弹窗内点击去重胶囊切换时由组件内部清空
    val rescanDedupHistory = remember { mutableSetOf<String>() }

    // --- 实时录入（v1.1）：录入模式状态 ---
    val entryState by viewModel.entryState.collectAsState()
    val entryMode = entryState.active
    var showEntrySubmitDialog by remember { mutableStateOf(false) }
    var entryTitleInput by remember { mutableStateOf("") }
    var entryDupHits by remember { mutableStateOf<List<EntryJobDuplicate>>(emptyList()) }
    var showReentryDialog by remember { mutableStateOf(false) }

    // --- 序列号范围生成（v1.3）：serialGenScanMode 让补扫弹窗的扫码路由进生成弹窗；
    //     独立去重集合——起始码通常已扫过录入，不能被页面级补扫去重吞掉 ---
    val serialGenState by viewModel.serialGenState.collectAsState()
    var serialGenScanMode by remember { mutableStateOf(false) }
    val serialGenDedupHistory = remember { mutableSetOf<String>() }
    var pendingSerialGenFromEntry by remember { mutableStateOf<Boolean?>(null) }
    val requestSerialGenScan = rememberPermissionAction(AppPermission.CAMERA) {
        val fromEntry = pendingSerialGenFromEntry
        if (fromEntry != null) {
            viewModel.beginSerialGenScan(fromEntryMode = fromEntry)
        }
        serialGenScanMode = true
        showSingleScanner = true
    }

    // 一次性提示（任务创建/终态/自动开新批次/终止等）→ Toast
    LaunchedEffect(entryState.notice) {
        entryState.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearEntryNotice()
        }
    }

    // 录入模式轮询（兜底）：WS 长连接在线时放宽到 15s，断开立即回 2.5s；离开页面/退出模式自然停止；job 终态自动停
    LaunchedEffect(entryMode, entryState.jobId) {
        while (entryMode && isActive) {
            delay(if (viewModel.entryState.value.wsConnected) 15_000L else 2_500L)
            if (!viewModel.entryState.value.active) break
            viewModel.pollEntryJobOnce()
            if ((viewModel.entryState.value.job?.status ?: 0) >= 2) break
        }
    }

    // 暂离后普通页低频轮询（15s）：刷新顶部角标 + 任务完成时 Toast 提醒；终态自动停
    LaunchedEffect(entryMode, entryState.jobId) {
        while (!entryMode && isActive
            && entryState.jobId != null
            && (entryState.job?.status ?: 0) <= 1
        ) {
            delay(15000)
            viewModel.pollEntryJobOnce()
        }
    }

    if (showExportConfirmDialog) {
        ModernAlertDialog(
            onDismiss = { showExportConfirmDialog = false },
            title = "导出 PDF",
            content = { Text("仍有未完成项（错误项或数量未匹配），是否仍然导出？") },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        showExportConfirmDialog = false
                        showPdfAdvancedConfigDialog = true
                    },
                    onDismiss = { showExportConfirmDialog = false },
                    confirmText = "仍然导出"
                )
            }
        )
    }

    // --- 👇 高级附加参数配置弹窗（包含你描述的纯净样式） 👇 ---
    if (showPdfAdvancedConfigDialog) {
        ModernAlertDialog(
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showPdfAdvancedConfigDialog = false
            },
            title = "PDF 附加图层配置",
            showContentCard = true,
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📄 页面防伪水印", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        AppTextField(
                            value = watermarkInputText,
                            onValueChange = { watermarkInputText = it },
                            placeholder =
                                if (settingsState.watermarkText.isNotBlank())
                                    "留空将使用默认配置: ${settingsState.watermarkText}"
                                else
                                    "请输入防伪水印文字（留空代表无水印）",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("✍ 经手人电子签名", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (signatureBitmapState == null) {
                                Text("未添加任何纸质/电子签章", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text("签名已被挂载缓存", color = StatusSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = { showSignaturePadDialog = true },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(if (signatureBitmapState == null) "绘制签名" else "重绘签名", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { showSignatureLibraryDialog = true },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("签名库", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepLocalPdf = !keepLocalPdf },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("💾 是否留存本地", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "勾选后导出时保存一份到手机；关闭则仅上传服务端，不保存本地",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Checkbox(
                            checked = keepLocalPdf,
                            onCheckedChange = { keepLocalPdf = it }
                        )
                    }
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        showPdfAdvancedConfigDialog = false
                        if (keepLocalPdf) {
                            val defaultFileName = if (currentDocs.isEmpty()) "未录入单据" else currentDocs.joinToString("_") { it.documentName }
                            val fileName = "单据报告_${defaultFileName}.pdf"
                            val defaultDirUri = settingsState.pdfExportDirUri
                            if (defaultDirUri.isNotBlank()) {
                                // 已设置默认储存位置：自动命名直接存入，不再弹系统选位器
                                scope.launch(Dispatchers.IO) {
                                    val target = runCatching {
                                        createPdfTargetUri(context, defaultDirUri, fileName)
                                    }.getOrNull()
                                    if (target != null) {
                                        runPdfExport(target)
                                    } else {
                                        // 授权失效/文件夹不可用（被删除或移动）：回退系统选位器
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "默认保存位置不可用，请手动选择保存位置",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            pdfExportLauncher.launch(fileName)
                                        }
                                    }
                                }
                            } else {
                                pdfExportLauncher.launch(fileName)
                            }
                        } else {
                            scope.launch(Dispatchers.IO) { runPdfExport(null) }
                        }
                    },
                    onDismiss = { showPdfAdvancedConfigDialog = false },
                    confirmText = if (keepLocalPdf) "开始保存 PDF" else "开始导出并上传"
                )
            }
        )
    }

    // --- 手写签名画板（utils/pdf 复用组件；确认后自动存入签名库） ---
    if (showSignaturePadDialog) {
        SignaturePadDialog(
            onDismiss = { showSignaturePadDialog = false },
            onConfirm = { bitmap ->
                signatureBitmapState = bitmap
                showSignaturePadDialog = false
                scope.launch(Dispatchers.IO) {
                    SignatureStore.get(context).saveSignature(bitmap)
                }
            }
        )
    }

    // --- 从电子签名库选取既有签名 ---
    if (showSignatureLibraryDialog) {
        SignatureLibraryPickerDialog(
            onDismiss = { showSignatureLibraryDialog = false },
            onSelect = { bitmap ->
                signatureBitmapState = bitmap
                showSignatureLibraryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (entryMode) "实时录入 · ${entryState.job?.title ?: "提交中..."}"
                        else if (currentDocs.isEmpty()) "批量二维码识别" else currentDocs.joinToString(" + ") { it.documentName },
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (entryMode) {
                        // 实时推送连接状态点（绿=长连接在线，灰=已断开退回轮询）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (entryState.wsConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (entryState.wsConnected) "实时已连接" else "实时断开",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        // 录入模式：带文字的醒目“退出录入”（暂离——任务不取消，可随时回到）
                        TextButton(onClick = { viewModel.exitEntryMode() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("退出录入", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        // 实时录入入口（v1.2：有进行中任务时显示绿色角标；点击弹三选一，不再自动拉回）
                        IconButton(onClick = {
                            viewModel.resumeOrPrepareEntry { resume ->
                                if (resume == null) {
                                    val datePart = dateTagForEntry()
                                    val docPart = if (currentDocs.isEmpty()) "任务"
                                    else currentDocs.joinToString("_") { "${it.documentName}_${it.documentId}" }
                                    entryTitleInput = "${docPart}_实时批次${datePart}-1"
                                    showEntrySubmitDialog = true
                                } else {
                                    showReentryDialog = true
                                }
                            }
                        }) {
                            val liveJob = entryState.job
                            val hasLiveJob = liveJob != null && liveJob.status <= 1
                            BadgedBox(
                                badge = {
                                    if (hasLiveJob) Badge(containerColor = StatusSuccess)
                                }
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = "实时录入")
                            }
                        }
                        IconButton(onClick = {
                            val docStatus = viewModel.checkExportReadiness()
                            when (docStatus) {
                                DocStatus.PASS -> { showPdfAdvancedConfigDialog = true }
                                DocStatus.WARNING -> { showExportConfirmDialog = true }
                                DocStatus.ERROR -> {
                                    Toast.makeText(context, "存在致命错误（如多扫、不在单据中），请处理后再导出", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Text("PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            if (viewModel.rawBarcodeList.value.isEmpty()) {
                                Toast.makeText(context, "暂无解码数据，请先扫描", Toast.LENGTH_SHORT).show()
                            } else if (currentDocs.isEmpty()) {
                                decodeNameInput = ""
                                showUploadDecodeDialog = true
                            } else {
                                val title = currentDocs.joinToString("_") { "${it.documentName}_${it.documentId}" }
                                viewModel.submitDecodeInfoToNetwork(title) { err ->
                                    Toast.makeText(context, err ?: "解码信息上传成功", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "上传解码")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!entryMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ================= 1. 上半区：图片轮播区 =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { if (photoList.isEmpty()) requestBatchCamera() },
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
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "盘点照片 $page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.removePhoto(photoUri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
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

            // ================= 2. 中间区：操作按钮栏 =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.fetchTaskListFromNetwork { errorMsg ->
                            if (errorMsg != null) Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            else showNetworkTaskDialog = true
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("单据", style = MaterialTheme.typography.labelMedium)
                }
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
                Button(
                    onClick = { requestBatchCamera() },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补拍", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { requestSingleScanner() },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补扫", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { requestVideoStream() },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("视频采集", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = {
                        // 先扫码，扫到 (21) 后才弹生成弹窗
                        pendingSerialGenFromEntry = false
                        requestSerialGenScan()
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成序列号", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ================= 3. 下半区：校验结果表格 =================
            Text(
                text = "校验结果明细 (${recordList.size}条)",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val hasTarget = recordList.any { record -> record.targetQty > 0 }

            val wProduct = 120.dp
            val wDi = 60.dp
            val wBatch = 80.dp
            val wProdDate = 90.dp
            val wExpDate = 90.dp
            val wTarget = 60.dp
            val wActual = 70.dp
            val wStatus = 70.dp
            val wAction = 50.dp

            val tableTotalWidth = if (hasTarget) 850.dp else 790.dp
            val horizontalScrollState = rememberScrollState()

            val errorBg = StatusError.copy(alpha = 0.1f)
            val overflowBg = StatusWarning.copy(alpha = 0.1f)
            val matchedBg = StatusSuccess.copy(alpha = 0.1f)
            val bgColor = MaterialTheme.colorScheme.surface
            val modifiedBg = StatusWarning.copy(alpha = 0.25f)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxHeight().horizontalScroll(horizontalScrollState)) {
                    Row(
                        modifier = Modifier
                            .width(tableTotalWidth)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                    val groupedRecords = recordList.groupBy { it.documentName }

                    LazyColumn(modifier = Modifier.width(tableTotalWidth)) {
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
                                val rowColor = if (index % 2 == 0) bgColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                val rowBg = when {
                                    record.actualQty == 0 -> rowColor
                                    record.isManualModified -> modifiedBg
                                    record.rowStatus == RowStatus.ERROR -> errorBg
                                    record.rowStatus == RowStatus.WARNING -> overflowBg
                                    record.rowStatus == RowStatus.PASS -> if (record.targetQty > 0) matchedBg else rowColor
                                    else -> rowColor
                                }

                                Row(
                                    modifier = Modifier
                                        .width(tableTotalWidth)
                                        .background(rowBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableBodyCell(record.productName, Modifier.width(wProduct), color = if (record.rowStatus == RowStatus.ERROR) StatusError else MaterialTheme.colorScheme.onSurface)
                                    VLine()
                                    TableBodyCell(record.di, Modifier.width(wDi), color = getFieldColor(record.diStatus, MaterialTheme.colorScheme.onSurfaceVariant))
                                    VLine()
                                    TableBodyCell(record.batch, Modifier.width(wBatch), color = getFieldColor(record.batchStatus, MaterialTheme.colorScheme.onSurface))
                                    VLine()
                                    TableBodyCell(DateUtils.formatDateForDisplay(record.productionDate), Modifier.width(wProdDate), color = getFieldColor(record.prodDateStatus, MaterialTheme.colorScheme.onSurfaceVariant))
                                    VLine()
                                    TableBodyCell(DateUtils.formatDateForDisplay(record.expiryDate), Modifier.width(wExpDate), color = getFieldColor(record.expDateStatus, MaterialTheme.colorScheme.onSurfaceVariant))

                                    if (hasTarget) {
                                        VLine()
                                        TableBodyCell(record.targetQty.toString(), Modifier.width(wTarget), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    VLine()

                                    EditableQuantityCell(
                                        qty = record.actualQty,
                                        status = record.qtyStatus,
                                        modifier = Modifier.width(wActual),
                                        onQtyChange = { newQty -> viewModel.updateRecordQuantity(record.id, newQty) }
                                    )
                                    VLine()

                                    StatusIndicator(record = record, modifier = Modifier.width(wStatus))
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
        } else {
            // ================= 录入模式：摘要卡 + 补扫 + 逐条三色状态表（v1.1）=================
            RealtimeEntryContent(
                state = entryState,
                onRescan = { requestSingleScanner() },
                onGenerateSerial = {
                    // 先扫码，扫到 (21) 后才弹生成弹窗
                    pendingSerialGenFromEntry = true
                    requestSerialGenScan()
                },
                onAbandon = { viewModel.abandonEntryJob() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }

        if (showNetworkTaskDialog) {
            var selectedTaskIds by remember { mutableStateOf(setOf<String>()) }
            var searchQuery by remember { mutableStateOf("") }

            ModernAlertDialog(
                onDismiss = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showNetworkTaskDialog = false
                },
                title = "待盘点单据 (多选)",
                showContentCard = false,
                content = {
                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else if (networkTasks.isEmpty()) {
                        Text("当前没有未完成的抓取任务")
                    } else {
                        AppTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "搜索公司名称 / 单号",
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "清除")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        val filteredTasks = networkTasks.filter { task ->
                            searchQuery.isBlank() ||
                            task.documentName.contains(searchQuery, ignoreCase = true) ||
                            task.documentId.contains(searchQuery, ignoreCase = true) ||
                            (task.billType?.contains(searchQuery, ignoreCase = true) == true) ||
                            (task.orderNo?.contains(searchQuery, ignoreCase = true) == true)
                        }
                        if (filteredTasks.isEmpty()) {
                            Text("没有匹配的单据", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                                items(filteredTasks.size) { index ->
                                    val task = filteredTasks[index]
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
                                        androidx.compose.material3.Checkbox(checked = isSelected, onCheckedChange = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = task.documentName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            val subInfo = listOf(
                                                "单号: ${task.documentId}",
                                                task.billType.orEmpty(),
                                                task.orderNo.orEmpty(),
                                                "日期: ${task.documentDate}"
                                            ).filter { it.isNotBlank() }.joinToString(" | ")
                                            Text(text = subInfo, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    ModernDialogButtonBar(
                        onConfirm = {
                            viewModel.loadMultipleTaskDetails(selectedTaskIds.toList()) { errorMsg ->
                                if (errorMsg != null) Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                showNetworkTaskDialog = false
                            }
                        },
                        onDismiss = { showNetworkTaskDialog = false },
                        confirmText = "确认导入",
                        confirmEnabled = selectedTaskIds.isNotEmpty()
                    )
                }
            )
        }
    }

    if (showUploadDecodeDialog) {
        ModernAlertDialog(
            onDismiss = { showUploadDecodeDialog = false },
            title = "上传解码信息",
            showContentCard = false,
            content = {
                AppTextField(
                    value = decodeNameInput,
                    onValueChange = { decodeNameInput = it },
                    placeholder = "请输入本次解码的名称",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        if (decodeNameInput.isBlank()) {
                            Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                        } else {
                            showUploadDecodeDialog = false
                            viewModel.submitDecodeInfoToNetwork(decodeNameInput.trim()) { err ->
                                Toast.makeText(context, err ?: "解码信息上传成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = { showUploadDecodeDialog = false },
                    confirmText = "上传"
                )
            }
        )
    }

    // --- 实时录入：提交确认弹窗（批次名 + 条数；提交前自动查重）---
    if (showEntrySubmitDialog) {
        ModernAlertDialog(
            onDismiss = { showEntrySubmitDialog = false },
            title = "实时录入",
            showContentCard = false,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = entryTitleInput,
                        onValueChange = { entryTitleInput = it },
                        placeholder = "批次名称（可修改）",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val rawCount = viewModel.rawBarcodeList.value
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
                    Text(
                        if (rawCount == 0) "将创建空任务（0 条）：开任务后可在录入模式用补扫逐条追加，边扫边录\n需与 PC 服务端同一局域网"
                        else "将提交 $rawCount 条码值（提交前自动规整 + 批内去重）\n需与 PC 服务端同一局域网；PC 脚本面板认领后开始录入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        if (entryTitleInput.isBlank()) {
                            Toast.makeText(context, "请输入批次名称", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.beginEntrySubmit(
                                title = entryTitleInput.trim(),
                                rawCodes = viewModel.rawBarcodeList.value,
                                onNeedConfirm = { hits -> entryDupHits = hits },
                                onResult = { err ->
                                    if (err == null) showEntrySubmitDialog = false
                                    else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    onDismiss = { showEntrySubmitDialog = false },
                    confirmText = "提交并开始"
                )
            }
        )
    }

    // --- 序列号范围生成弹窗（v1.3）：扫码后弹出 → 起止流水（起始可改）→ 预览展开+查重 → 确认生成 ---
    if (serialGenState.visible) {
        SerialGenDialog(
            state = serialGenState,
            onStartChange = viewModel::updateSerialGenStart,
            onEndChange = viewModel::updateSerialGenEnd,
            onScan = {
                pendingSerialGenFromEntry = null
                requestSerialGenScan()
            },
            onPreview = viewModel::previewSerialRange,
            onConfirm = {
                // 成功提示走 entryState.notice（追加/新建/UDI 组三路统一），这里只报错
                viewModel.confirmSerialGenerate { err ->
                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = viewModel::closeSerialGenDialog
        )
    }

    // --- 实时录入：重入三选一弹窗（v1.2：不再自动拉回，由用户选择去向）---
    if (showReentryDialog) {
        val resumeJob = entryState.job
        ModernAlertDialog(
            onDismiss = { showReentryDialog = false },
            title = "实时录入",
            showContentCard = false,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "当前有进行中的任务：\n${resumeJob?.title ?: "-"}\n" +
                            "共 ${resumeJob?.totalCount ?: 0} 条 · 已录 ${(resumeJob?.doneCount ?: 0) + (resumeJob?.failCount ?: 0) + (resumeJob?.skipCount ?: 0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showReentryDialog = false
                            viewModel.reenterEntryMode()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("回到当前任务")
                    }
                    FilledTonalButton(
                        onClick = {
                            showReentryDialog = false
                            val datePart = dateTagForEntry()
                            val docPart = if (currentDocs.isEmpty()) "任务"
                            else currentDocs.joinToString("_") { "${it.documentName}_${it.documentId}" }
                            entryTitleInput = "${docPart}_实时批次${datePart}-1"
                            showEntrySubmitDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("提交新批次")
                    }
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = { showReentryDialog = false },
                    confirmText = "取消",
                    onDismiss = null
                )
            }
        )
    }

    // --- 实时录入：查重确认弹窗（只提醒不硬拦，命中历史由用户决定）---
    if (entryDupHits.isNotEmpty()) {
        ModernAlertDialog(
            onDismiss = { entryDupHits = emptyList() },
            title = "发现历史重复码值 (${entryDupHits.size} 条)",
            showContentCard = false,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "以下码值在历史批次中出现过（可能已录入金蝶），请确认是否仍要提交：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(entryDupHits.size) { index ->
                            val hit = entryDupHits[index]
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    hit.rawValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "批次: ${hit.title ?: "-"} · 任务 #${hit.jobId} · ${if (hit.itemStatus == "success") "已录入过" else "录入中"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        entryDupHits = emptyList()
                        viewModel.performEntrySubmit(
                            title = entryTitleInput.trim(),
                            rawCodes = viewModel.rawBarcodeList.value
                        ) { err ->
                            if (err == null) showEntrySubmitDialog = false
                            else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    },
                    onDismiss = { entryDupHits = emptyList() },
                    confirmText = "仍然提交"
                )
            }
        )
    }

    if (showSingleScanner) {
        // 补扫弹窗：居中模态大窗（顶部两行码值/中部镜头/底部圆形扫码按钮）
        // 单击=单发识别一码；长按1.5s=锁定连续扫描（松手不停，再单击才结束）；
        // 返回键与右上×在组件内统一关闭并弹小结
        FloatingRescanOverlay(
            onCode = { barcodeString ->
                if (serialGenScanMode) {
                    // 序列号生成弹窗的扫码：解析 (21) 定模板与起始流水号，失败提示后可重扫
                    val err = viewModel.onSerialGenScan(barcodeString)
                    serialGenDedupHistory.clear()  // 允许失败后重扫同一个码
                    if (err == null) {
                        serialGenScanMode = false
                        showSingleScanner = false
                    } else {
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                } else if (entryMode) {
                    // 录入模式：新码进实时录入链路（攒 2s 合并 append 进当前 job）
                    viewModel.queueEntryScan(barcodeString)
                } else {
                    viewModel.processScannerResults(
                        bitmaps = emptyList(),
                        rawBarcodes = listOf(barcodeString)
                    )
                }
            },
            onDismiss = {
                serialGenScanMode = false
                showSingleScanner = false
            },
            dedupHistory = if (serialGenScanMode) serialGenDedupHistory else rescanDedupHistory
        )
    }
}

// ===================== 其余表格子组件保持不变 =====================
@Composable
private fun VLine(color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier = Modifier.width(1.dp).height(14.dp).background(color))
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
            onClick = { },
            onLongClick = {
                if (text.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
                }
            }
        )
    )
}

@Composable
private fun EditableQuantityCell(
    qty: Int,
    status: FieldStatus = FieldStatus.NOT_CHECKED,
    modifier: Modifier,
    onQtyChange: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textColor = when(status) {
        FieldStatus.ERROR -> StatusError
        FieldStatus.WARNING -> StatusWarning
        else -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier) {
        BasicTextField(
            value = textValue,
            onValueChange = { newVal ->
                if (newVal.isEmpty() || newVal.all { it.isDigit() }) textValue = newVal
            },
            enabled = isEditing,
            modifier = Modifier
                .height(32.dp)
                .border(1.dp, if (isEditing) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            textStyle = MaterialTheme.typography.labelMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val newQty = textValue.toIntOrNull()
                    if (newQty != null && newQty >= 0) onQtyChange(newQty) else textValue = qty.toString()
                    isEditing = false
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isEditing) innerTextField()
                }
            }
        )
        if (!isEditing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        isEditing = true
                        textValue = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = qty.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StatusIndicator(record: InventoryRecord, modifier: Modifier) {
    val sources = record.sources
    val label = when {
        sources.contains(RecordSource.MANUAL) -> "人工"
        record.errorMessage == "非 UDI 格式" -> "解析失败"
        sources.contains(RecordSource.UDI) && sources.contains(RecordSource.OCR) -> "UDI+OCR"
        sources.contains(RecordSource.UDI) -> "UDI"
        sources.contains(RecordSource.OCR) -> "OCR"
        sources.isEmpty() -> "未查验"
        else -> "未知"
    }
    val color = when (record.qtyStatus) {
        FieldStatus.MATCHED -> StatusSuccess
        FieldStatus.WARNING -> StatusWarning
        FieldStatus.ERROR -> StatusError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteButton(modifier: Modifier, onDelete: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

private fun getFieldColor(status: FieldStatus, defaultColor: Color): Color {
    return when (status) {
        FieldStatus.ERROR -> StatusError
        FieldStatus.WARNING -> StatusWarning
        else -> defaultColor
    }
}

// =========================================================
// 实时录入模式组件（v1.1，契约见方案文档 §7）
// =========================================================

private fun dateTagForEntry(): String =
    java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
        .format(java.util.Date())

private fun entryStatusLabel(job: EntryJobDto?): String = when {
    job == null -> "提交中..."
    job.status == 0 -> "等待脚本认领"
    job.status == 1 && job.stale -> "脚本疑似离线，录入挂起"
    job.status == 1 -> "录入中"
    job.status == 2 -> "明细录入完成"
    else -> "录入已中止"
}

@Composable
private fun entryStatusColor(job: EntryJobDto?): Color = when {
    job == null || job.status == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    job.status == 1 && job.stale -> StatusWarning
    job.status == 1 || job.status == 2 -> StatusSuccess
    else -> StatusError
}

/** 脚本结构化原因码 → 用户可读文案 */
private fun entryReasonText(reason: String?): String = when (reason) {
    "input_missing" -> "找不到录入框"
    "input_unavailable" -> "输入框不可用"
    "empty_udi" -> "空码"
    "page_busy" -> "金蝶页面忙"
    "dialog" -> "金蝶弹窗阻挡"
    "timeout" -> "金蝶落盘超时"
    "unreported" -> "未回报（已收口）"
    null, "" -> "未知"
    else -> reason
}

@Composable
private fun RealtimeEntryContent(
    state: RealtimeEntryState,
    onRescan: () -> Unit,
    onGenerateSerial: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier
) {
    val job = state.job
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        // ---- 摘要卡：状态 + 进度 + 计数 + 脚本在线灯 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(entryStatusColor(job))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    entryStatusLabel(job),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.weight(1f))
                val online = job?.scriptOnline == true
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (online) StatusSuccess else MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (online) "脚本在线" else "脚本离线",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (online) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 进度条：(成功+失败+跳过) / 总数；append 后 total 实时增长
            val total = job?.totalCount ?: 0
            val processed = if (job == null) 0 else job.doneCount + job.failCount + job.skipCount
            val fraction = if (total > 0) (processed.toFloat() / total).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(if (job?.status == 3) StatusError else StatusSuccess)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("成功 ${job?.doneCount ?: 0}", color = StatusSuccess, style = MaterialTheme.typography.labelMedium)
                Text("失败 ${job?.failCount ?: 0}", color = StatusWarning, style = MaterialTheme.typography.labelMedium)
                Text("跳过 ${job?.skipCount ?: 0}", color = StatusWarning, style = MaterialTheme.typography.labelMedium)
                Text("共 $total", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                if (state.pendingScans.isNotEmpty()) {
                    Text(
                        "待追加 ${state.pendingScans.size}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            val hint = when {
                job == null -> "正在创建任务..."
                job.status == 0 -> "等待脚本认领：PC 端脚本面板出现横幅后，点击【开始录入】"
                job.status == 1 && job.stale -> "脚本疑似离线，录入挂起：重开 PC 脚本后可重新认领续录"
                job.status == 1 -> "录入中：逐条写入金蝶，每条完成后即时变色"
                job.status == 2 -> "明细录入完成（单据保存请在金蝶端人工确认）"
                else -> "录入任务已中止"
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- 操作栏：补扫 + 结束任务（终止；用户拍板不弹确认）----
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onRescan,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("补扫", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = onGenerateSerial,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("生成序列号", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onAbandon,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("结束任务", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        val serialRangeInfo = state.job?.serialRange
        if (serialRangeInfo != null) {
            // ---- 范围生成任务：不渲染逐条明细（几百上千条会卡），只显示首条 UDI + 进度 + 当前录入号 ----
            // 混合任务只统计模板前缀命中的条目，扫码条目不干扰
            val srItems = (state.job?.itemResults ?: emptyList())
                .filter { it.rawValue.startsWith(serialRangeInfo.template) }
            val srProcessed = srItems.count { it.status != "pending" }
            val srCurrent = srItems.firstOrNull { it.status == "pending" }
                ?.rawValue?.drop(serialRangeInfo.template.length)
            val srJobStatus = state.job?.status ?: 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "首条 UDI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    serialRangeInfo.template + serialRangeInfo.startSerial,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "流水 ${serialRangeInfo.startSerial}-${serialRangeInfo.endSerial} · $srProcessed/${serialRangeInfo.count}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    srCurrent?.let { "正在录入 $it" }
                        ?: if (srJobStatus == 2) "全部录入完成" else "等待脚本录入…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (srCurrent != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "录入明细 (${job?.itemResults?.size ?: 0}条)",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ---- 逐条三色状态表：等待录入(灰) / 录入完成(绿) / 录入异常(黄) ----
            val listState = rememberLazyListState()
            val results = job?.itemResults ?: emptyList()
            // 自动滚动到最新变更行（最后一个非 pending 条目）
            val lastChangedIndex = results.indexOfLast { it.status != "pending" }
            LaunchedEffect(results.size, lastChangedIndex) {
                if (results.isNotEmpty()) {
                    listState.animateScrollToItem(if (lastChangedIndex >= 0) lastChangedIndex else results.size - 1)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(results, key = { it.seq }) { item ->
                    EntryItemRow(item)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (state.pendingScans.isNotEmpty()) {
                    item(key = "pending-header") {
                        Text(
                            "待追加（${state.pendingScans.size} 条，稍后自动并入当前任务）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    itemsIndexed(state.pendingScans) { _, code ->
                        EntryPendingRow(code)
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryItemRow(item: EntryJobItemResult) {
    val (label, color) = when (item.status) {
        "success" -> "录入完成" to StatusSuccess
        "failed" -> "录入异常" to StatusWarning
        "skipped" -> "已跳过" to StatusWarning
        else -> "等待录入" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rowBg = when (item.status) {
        "success" -> StatusSuccess.copy(alpha = 0.08f)
        "failed", "skipped" -> StatusWarning.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${item.seq + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Column(Modifier.weight(1f)) {
            // 完整扫码内容（原始码串，回传与展示同源）
            Text(
                item.rawValue,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (item.status == "failed" || item.status == "skipped" || item.reason != null) {
                Text(
                    "原因: ${entryReasonText(item.reason)}${if (item.status == "skipped") "（跳过）" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusWarning
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EntryPendingRow(code: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "新增",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Text(
            code,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "待提交",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ===================== 序列号范围生成弹窗（v1.3） =====================
// 扫码成功后弹出：模板只读展示 → 起止流水一行两个输入框（起始扫码填入、可手动修改）
// → 预览（服务端展开+查重）→ 确认生成；修改任一输入后自动退回「预览」
@Composable
private fun SerialGenDialog(
    state: SerialGenUiState,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onScan: () -> Unit,
    onPreview: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "生成序列号",
        showContentCard = false,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重新扫码更换模板")
                }
                Column {
                    Text(
                        "模板串（扫码自动解析，只读）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.template,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 起止流水号一行两框：起始 – 结束
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppTextField(
                        value = state.startSerial,
                        onValueChange = onStartChange,
                        placeholder = "起始流水号",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "–",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = state.endSerial,
                        onValueChange = onEndChange,
                        placeholder = "结束流水号",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.submitting) {
                    Text(
                        "正在提交…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                when {
                    state.previewing -> Text(
                        "正在展开流水号…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.previewError != null -> Text(
                        state.previewError,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError
                    )
                    state.preview != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "共 ${state.preview.count} 条：${state.preview.firstValue} ~ ${state.preview.lastValue}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.preview.duplicates.isNotEmpty()) {
                            Text(
                                "⚠ ${state.preview.duplicates.size} 条与历史录入重复（确认后仍会提交）：",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusWarning
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 110.dp)) {
                                items(state.preview.duplicates.size) { index ->
                                    Text(
                                        state.preview.duplicates[index].rawValue,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusWarning,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    else -> Text(
                        "输入结束流水号后点「预览」，确认数量与重复提醒再生成；起始流水号可手动修改换起点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    if (state.preview == null) onPreview() else onConfirm()
                },
                onDismiss = onDismiss,
                confirmText = if (state.preview == null) "预览" else "确认生成",
                confirmEnabled = !state.previewing && !state.submitting &&
                    state.template.isNotBlank() && state.startSerial.isNotBlank() && state.endSerial.isNotBlank()
            )
        }
    )
}

/**
 * 在用户设置的默认文件夹（SAF tree URI，持久授权）下创建 PDF 文档并返回目标 Uri。
 * 同名文件由系统自动追加 " (1)" 后缀；授权失效或文件夹不可用时返回 null（调用方回退选位器）。
 */
private fun createPdfTargetUri(context: Context, dirUriString: String, fileName: String): Uri? {
    val treeUri = Uri.parse(dirUriString)
    val persisted = context.contentResolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isWritePermission
    }
    if (!persisted) return null
    return DocumentsContract.createDocument(
        context.contentResolver,
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
        "application/pdf",
        fileName
    )
}
