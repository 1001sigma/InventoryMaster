package com.example.inventorymaster.ui.analyzer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.example.inventorymaster.ui.analyzer.multiscan.DetectedBarcode
import com.example.inventorymaster.ui.analyzer.multiscan.FrozenSelectionOverlay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.UserSettings
import com.example.inventorymaster.ui.theme.DarkBackground
import com.example.inventorymaster.ui.theme.DarkSurfaceVariant
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ocr.OcrResult
import com.example.inventorymaster.utils.QrScanner
import com.example.inventorymaster.utils.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(
    onScanResult: (String) -> Unit, // 扫码/OCR确认后的回调
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // --- 1. 设置读取 ---
    val settingsRepo = remember { SettingsRepository(context) }
    val userSettings by settingsRepo.settingsFlow.collectAsState(initial = UserSettings())
    val isSimpleMode = userSettings.isSimpleMode
    val showOcrMask = userSettings.showOcrMask
    val enableMultiScan = userSettings.enableMultiScan
    val scanFrameRatioW = userSettings.scanFrameRatioW
    val scanFrameRatioH = userSettings.scanFrameRatioH
    val ocrFrameRatioW = userSettings.ocrFrameRatioW
    val ocrFrameRatioH = userSettings.ocrFrameRatioH

    // --- 2. 状态管理 ---
    var scanMode by remember { mutableIntStateOf(0) } // 0 = 扫码, 1 = 识字
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // 拖拽调整扫描框期间暂停帧分析
    var isResizing by remember { mutableStateOf(false) }

    // [新增] 保存 PreviewView 的引用
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // 相机执行器 (用于分析线程)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // OCR ViewModel
    val ocrViewModel = viewModel<OcrViewModel>()
    val ocrResult by ocrViewModel.ocrResult.collectAsState()
    val tokenizedWords by ocrViewModel.tokenizedWords.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRestartTipDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ScanHistoryManager.init(context)
    }

    // 多码模式状态 (定格)
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frozenBarcodes by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }

    // 资源清理
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // 处理定格时的返回键
    BackHandler(enabled = frozenBitmap != null) {
        frozenBitmap = null
        frozenBarcodes = emptyList()
    }

    // --- 3. 分析器组装 (Analyzer Assembly) ---

    // A. 预先构建 ImageAnalysis UseCase (关键：升级至 1080p 高分辨率，显著提高小 DataMatrix 识别率)
    val targetResolution = remember { android.util.Size(1920, 1080) }

    // A. 预先构建 ImageAnalysis
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(targetResolution) // 使用统一分辨率
            .build()
    }

    // B. 实例化重构后的 BarcodeAnalyzer（单例复用，支持多码稳定定格与 1:1 抓帧）
    val barcodeAnalyzer = remember(enableMultiScan) {
        BarcodeAnalyzer(
            isMultiMode = enableMultiScan,
            scope = scope,
            getPreviewBitmap = { previewViewRef?.bitmap },
            onScanResult = { result ->
                ScanHistoryManager.addRecord(context, result, ScanRecordType.BARCODE)
                scope.launch { onScanResult(result) }
            },
            onMultiScanResult = { bitmap, barcodes ->
                frozenBitmap = bitmap
                frozenBarcodes = barcodes
            }
        )
    }
    DisposableEffect(barcodeAnalyzer) {
        onDispose { barcodeAnalyzer.close() }
    }

    // D. 核心调度逻辑：在每帧图像进来时，动态计算精准坐标并分发给对应的分析器
    val density = LocalDensity.current
    var viewWidthPx by remember { mutableFloatStateOf(0f) }
    var viewHeightPx by remember { mutableFloatStateOf(0f) }

    // OCR 快门裁剪用的框像素尺寸（条码框在分析器内逐帧实时读取比例）
    val ocrFrameWPx = if (viewWidthPx > 0f && ocrFrameRatioW > 0f) {
        viewWidthPx * ocrFrameRatioW
    } else {
        with(density) { 300.dp.toPx() }
    }
    val ocrFrameHPx = if (viewHeightPx > 0f && ocrFrameRatioH > 0f) {
        viewHeightPx * ocrFrameRatioH
    } else {
        with(density) { 80.dp.toPx() }
    }

    // 分析器只随模式/定格切换重建。扫描框比例与容器尺寸都在帧内实时读取（委托状态），
    // 拖拽调整扫描框后下一帧自动生效，无需重启分析器
    LaunchedEffect(scanMode, frozenBitmap) {
        if (frozenBitmap != null) {
            imageAnalysis.clearAnalyzer()
        } else {
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // 识字模式或拖拽调整扫描框期间，底层分析器直接罢工休息，省 CPU！
                if (scanMode == 1 || isResizing) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                // === 下面全是扫码模式的逻辑 ===
                val rotation = imageProxy.imageInfo.rotationDegrees
                val isRotated = rotation == 90 || rotation == 270
                val cropRect = imageProxy.cropRect
                val imgW = if (isRotated) cropRect.height() else cropRect.width()
                val imgH = if (isRotated) cropRect.width() else cropRect.height()

                // 扫描框像素尺寸 = 容器尺寸 × 记忆比例（未设置时用默认 260dp）
                val frameWPx = if (viewWidthPx > 0f && userSettings.scanFrameRatioW > 0f) {
                    viewWidthPx * userSettings.scanFrameRatioW
                } else {
                    with(density) { 260.dp.toPx() }
                }
                val frameHPx = if (viewHeightPx > 0f && userSettings.scanFrameRatioH > 0f) {
                    viewHeightPx * userSettings.scanFrameRatioH
                } else {
                    with(density) { 260.dp.toPx() }
                }

                // 条码扫描框的映射逻辑：
                // 开启多码选择模式时放开全画幅识别，避免框外周边多码被提前误杀；单码模式保留框内对准判定
                val mappedRect = if (enableMultiScan) {
                    null
                } else {
                    ScanRegionMapper.getMappedRect(
                        frameWidthPx = frameWPx,
                        frameHeightPx = frameHPx,
                        viewWidthPx = viewWidthPx,
                        viewHeightPx = viewHeightPx,
                        imageWidth = imgW,
                        imageHeight = imgH
                    )
                }

                barcodeAnalyzer.targetRegion = mappedRect
                barcodeAnalyzer.viewWidth = viewWidthPx
                barcodeAnalyzer.viewHeight = viewHeightPx
                barcodeAnalyzer.imageWidth = imgW.toFloat()
                barcodeAnalyzer.imageHeight = imgH.toFloat()
                barcodeAnalyzer.analyze(imageProxy)
            }
        }
    }

    // ==================== 替换区域结束 ====================
    // --- 4. 辅助功能 Launcher (相册 & 词库导入) ---
    val importDictLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ocrViewModel.importDictionary(uri)
        }
    }

    // 收集词典导入一次性事件
    LaunchedEffect(Unit) {
        ocrViewModel.uiEvent.collect { event ->
            when (event) {
                is OcrViewModel.UiEvent.DictImportSuccess -> {
                    showSettingsDialog = false
                    showRestartTipDialog = true
                }
                is OcrViewModel.UiEvent.DictImportError -> {
                    Toast.makeText(context, "导入失败: ${event.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (scanMode == 0) {
                // 条码模式：统一解码门面全链（ML Kit → libdmtx）
                scope.launch {
                    val bitmap = uri.toBitmap(context)
                    val value = bitmap?.let { QrScanner.recognizeQRCode(it) }
                    if (value != null) {
                        ScanHistoryManager.addRecord(context, value, ScanRecordType.BARCODE)
                        onScanResult(value)
                    }
                    bitmap?.recycle()
                }
            } else {
                // OCR 模式：用 OcrViewModel
                ocrViewModel.recognizeFromUri(uri)
            }
        }
    }

    // --- 6. UI 布局 ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                // 实时获取相机容器的绝对物理像素宽高
                viewWidthPx = coordinates.size.width.toFloat()
                viewHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { /* 拦截点击，什么都不做 */ })
            }
    ) {
        // A. 相机预览层
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                // [新增] 存下引用
                previewViewRef = previewView
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .setTargetResolution(targetResolution)
                        .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)


                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis // 绑定我们配置好的 UseCase
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("Camera", "Bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // B. 覆盖层逻辑 (UI 显示)
        if (scanMode == 0) {
            // === 扫码模式 ===
            if (frozenBitmap != null) {
                // 多码定格选择界面
                FrozenSelectionOverlay(
                    bitmap = frozenBitmap!!,
                    barcodes = frozenBarcodes,
                    onBarcodeClick = { code ->
                        frozenBitmap = null // 释放图片
                        ScanHistoryManager.addRecord(context, code, ScanRecordType.BARCODE)
                        onScanResult(code)
                    },
                    onCancel = {
                        frozenBitmap = null // 取消定格，相机自动恢复分析
                        frozenBarcodes = emptyList()
                        barcodeAnalyzer.resetAggregator()
                    }
                )
            } else {
                // 极简纯白可拖拽扫码框（无扫描线，纯白清爽角标）
                ResizableScanOverlay(
                    ratioW = scanFrameRatioW,
                    ratioH = scanFrameRatioH,
                    defaultWidth = 260.dp,
                    defaultHeight = 260.dp,
                    cornerColor = Color.White,
                    handleColor = Color.White,
                    hintText = if (enableMultiScan) "多码选择中 · 对准条码自动收集" else "对准条码 / 二维码，自动识别",
                    onResizeStart = { isResizing = true },
                    onResizeEnd = { w, h ->
                        isResizing = false
                        scope.launch { settingsRepo.updateScanFrameRatio(w, h) }
                    }
                )
            }
        } else {
            // === OCR 模式 ===
            if (showOcrMask) {
                ResizableScanOverlay(
                    ratioW = ocrFrameRatioW,
                    ratioH = ocrFrameRatioH,
                    defaultWidth = 300.dp,
                    defaultHeight = 80.dp,
                    cornerColor = Color.White,
                    handleColor = Color.White,
                    hintText = "对准文字区域，点击拍摄",
                    onResizeStart = { isResizing = true },
                    onResizeEnd = { w, h ->
                        isResizing = false
                        scope.launch { settingsRepo.updateOcrFrameRatio(w, h) }
                    }
                )
            }
        }

        // C. 顶部控制栏 (毛玻璃圆钮 + 状态栏避让)
        if (frozenBitmap == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(
                        onClick = {
                            isTorchOn = !isTorchOn
                            cameraControl?.enableTorch(isTorchOn)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isTorchOn) Color(0xFFF59E0B).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.35f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isTorchOn) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) Color.Black else Color.White
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }
        }

        // D. 底部操作栏 (平滑上下渐变 + 胶囊Tab + 三合一动作栏)
        if (frozenBitmap == null) {
            val bottomGradient = remember {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            }
            val historyList by ScanHistoryManager.historyFlow.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(bottomGradient)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 模式切换胶囊
                ScanModeCapsule(
                    scanMode = scanMode,
                    onModeChange = { scanMode = it }
                )

                // 核心控制区 (相册 | 识别状态/快门 | 识别历史)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. 左侧：相册
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "相册",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "相册",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    // 2. 中间：核心动态区
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(76.dp)
                    ) {
                        if (scanMode == 0) {
                            // 扫码模式：精致微光自动识别指示（彻底告别死板假快门）
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CenterFocusWeak,
                                        contentDescription = "自动识别中",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = "自动识别",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            // 识字模式：可触控的纯白相机快门按键
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .border(3.5.dp, Color.White, CircleShape)
                                        .clickable {
                                            previewViewRef?.bitmap?.let { screenBitmap ->
                                                val frameWPx = ocrFrameWPx
                                                val frameHPx = ocrFrameHPx
                                                val left = ((screenBitmap.width - frameWPx) / 2).toInt().coerceAtLeast(0)
                                                val top = ((screenBitmap.height - frameHPx) / 2).toInt().coerceAtLeast(0)
                                                val width = frameWPx.toInt().coerceAtMost(screenBitmap.width - left)
                                                val height = frameHPx.toInt().coerceAtMost(screenBitmap.height - top)
                                                try {
                                                    val croppedBmp = Bitmap.createBitmap(screenBitmap, left, top, width, height)
                                                    ocrViewModel.recognizeFromBitmap(croppedBmp)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                                Text(
                                    text = "点击拍摄",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // 3. 右侧：识别历史记录 (带数量角标)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            IconButton(
                                onClick = { showHistorySheet = true },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = "识别历史",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (historyList.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (historyList.size > 99) "99+" else historyList.size.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "识别历史",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // E. 弹窗处理
        if (ocrResult != null && ocrResult is OcrResult.Success) {
            WordSelectionSheet(
                rawText = (ocrResult as OcrResult.Success).ocrText,
                tokenizedWords = tokenizedWords,
                onDismiss = { ocrViewModel.clearResult() },
                onConfirm = { text ->
                    ScanHistoryManager.addRecord(context, text, ScanRecordType.OCR)
                    onScanResult(text)
                    ocrViewModel.clearResult()
                }
            )
        }

        if (showHistorySheet) {
            ScanHistorySheet(
                onDismiss = { showHistorySheet = false },
                onSelectRecord = { text ->
                    showHistorySheet = false
                    onScanResult(text)
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                showOcrMask = showOcrMask,
                onToggleOcrMask = { scope.launch { settingsRepo.updateOcrMask(it) } },
                enableMultiScan = enableMultiScan,
                onToggleMultiScan = { scope.launch { settingsRepo.updateMultiScan(it) } },
                onDismiss = { showSettingsDialog = false },
                onImportDict = { importDictLauncher.launch("text/plain") },
                isSimpleMode = isSimpleMode,
                onToggleSimpleMode = { scope.launch { settingsRepo.updateSimpleMode(it) } }
            )
        }

        if (showRestartTipDialog) {
            RestartTipDialog(onRestart = { restartApp(context) })
        }
    }
}

// --- 子组件 ---

@Composable
fun ScanModeCapsule(
    scanMode: Int,
    onModeChange: (Int) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapsuleTabItem(
                text = "扫一扫",
                icon = Icons.Default.QrCodeScanner,
                isSelected = scanMode == 0,
                onClick = { onModeChange(0) }
            )
            CapsuleTabItem(
                text = "识文字",
                icon = Icons.Default.TextFields,
                isSelected = scanMode == 1,
                onClick = { onModeChange(1) }
            )
        }
    }
}

@Composable
fun CapsuleTabItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) Color.White else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}


// 1. 设置弹窗 UI
@Composable
fun SettingsDialog(
    showOcrMask: Boolean,            // [新增] 当前开关状态
    onToggleOcrMask: (Boolean) -> Unit, // [新增] 切换回调
    enableMultiScan: Boolean,   // [新增] 多码模式参数
    onToggleMultiScan:(Boolean) -> Unit,
    onDismiss: () -> Unit,
    onImportDict: () -> Unit,
    isSimpleMode: Boolean,
    onToggleSimpleMode: (Boolean) -> Unit
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "设置",
        showContentCard = false,
        content = {
            Column {
                ListItem(
                    headlineContent = { Text("多码选择模式") },
                    supportingContent = { Text("同屏出现多个码时，手动点击选择") },
                    leadingContent = { Icon(Icons.Default.QrCodeScanner, null) },
                    trailingContent = {
                        Switch(
                            checked = enableMultiScan,
                            onCheckedChange = onToggleMultiScan
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("导入自定义词库") },
                    supportingContent = { Text("上传 user_dict.txt 以优化分词") },
                    leadingContent = { Icon(Icons.Default.UploadFile, null) },
                    modifier = Modifier.clickable { onImportDict() }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("显示 OCR 扫描框") },
                    supportingContent = { Text("识字模式下显示半透明遮罩") },
                    leadingContent = { Icon(Icons.Default.CropFree, null) },
                    trailingContent = {
                        Switch(
                            checked = showOcrMask,
                            onCheckedChange = onToggleOcrMask
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("简单分词模式") },
                    supportingContent = { Text("仅按空格和标点切分，不使用词典") },
                    leadingContent = { Icon(Icons.Default.ContentCut, null) },
                    trailingContent = {
                        Switch(
                            checked = isSimpleMode,
                            onCheckedChange = onToggleSimpleMode
                        )
                    }
                )
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

// 2. 重启提示弹窗 UI
@Composable
fun RestartTipDialog(onRestart: () -> Unit) {
    ModernAlertDialog(
        onDismiss = {},
        title = "需要重启",
        content = {
            Text("自定义词库已导入成功！\n应用需要重启以重新加载词典引擎。")
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onRestart,
                confirmText = "立即重启",
                dismissText = "",
                onDismiss = null
            )
        },
        dismissButton = null
    )
}

// 3. 重启 APP 的逻辑
fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    // 杀掉当前进程，确保彻底重启
    Process.killProcess(Process.myPid())
}
