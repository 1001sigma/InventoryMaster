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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.UserSettings
import com.example.inventorymaster.ui.theme.DarkBackground
import com.example.inventorymaster.ui.theme.DarkSurfaceVariant
import com.example.inventorymaster.ui.theme.ScannerCorner
import com.example.inventorymaster.ui.theme.ScannerLine
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.utils.JiebaUtils
import com.example.inventorymaster.utils.RecognitionUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
    val barcodeFrameW = userSettings.barcodeFrameWidth
    val barcodeFrameH = userSettings.barcodeFrameHeight
    val ocrFrameW = userSettings.ocrFrameWidth
    val ocrFrameH = userSettings.ocrFrameHeight

    // --- 2. 状态管理 ---
    var scanMode by remember { mutableIntStateOf(0) } // 0 = 扫码, 1 = 识字
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // [新增] 保存 PreviewView 的引用
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // 相机执行器 (用于分析线程)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 结果状态
    var rawOcrResult by remember { mutableStateOf<Text?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRestartTipDialog by remember { mutableStateOf(false) }

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

    // A. 预先构建 ImageAnalysis UseCase (关键：配置高分辨率)
    val targetResolution = remember { android.util.Size(1280, 720) }

    // A. 预先构建 ImageAnalysis
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(targetResolution) // 使用统一分辨率
            .build()
    }

    // ==================== 替换区域开始 ====================

    // B. 实例化重构后的 BarcodeAnalyzer
    // 在 ScanScreen.kt 里面找到这段并替换：
    val barcodeAnalyzer = remember(enableMultiScan) {
        BarcodeAnalyzer(
            isMultiMode = enableMultiScan,
            scope = scope,
            onScanResult = { result ->
                scope.launch { onScanResult(result) }
            },
            onMultiScanResult = { bitmap, barcodes ->
                frozenBitmap = bitmap
                frozenBarcodes = barcodes
            }
        )
    }

    // D. 核心调度逻辑：在每帧图像进来时，动态计算精准坐标并分发给对应的分析器
    val density = LocalDensity.current
    var viewWidthPx by remember { mutableFloatStateOf(0f) }
    var viewHeightPx by remember { mutableFloatStateOf(0f) }

    val barcodeFrameWPx = with(density) { barcodeFrameW.dp.toPx() }
    val barcodeFrameHPx = with(density) { barcodeFrameH.dp.toPx() }
    val ocrFrameWPx = with(density) { ocrFrameW.dp.toPx() }
    val ocrFrameHPx = with(density) { ocrFrameH.dp.toPx() }

    LaunchedEffect(scanMode,
        frozenBitmap,
        viewWidthPx,
        viewHeightPx,
        barcodeFrameWPx, // 加上这个
        barcodeFrameHPx
        ) {
        if (frozenBitmap != null) {
            imageAnalysis.clearAnalyzer()
        } else {
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // 如果当前是识字模式，底层分析器直接罢工休息，省 CPU！
                if (scanMode == 1) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                // === 下面全是扫码模式的逻辑 ===
                val rotation = imageProxy.imageInfo.rotationDegrees
                val isRotated = rotation == 90 || rotation == 270
                val cropRect = imageProxy.cropRect
                val imgW = if (isRotated) cropRect.height() else cropRect.width()
                val imgH = if (isRotated) cropRect.width() else cropRect.height()

                // 条码扫描框的映射逻辑（这部分保留，因为条码依然需要底层扫描）
                val mappedRect = ScanRegionMapper.getMappedRect( // ⚠️ 注意：如果你把 ScanRegionMapper 删了，这里可以不传 mappedRect，直接扫全图，或者自己单独留一个简易的 BarcodeMapper
                    frameWidthPx = barcodeFrameWPx,
                    frameHeightPx = barcodeFrameHPx,
                    viewWidthPx = viewWidthPx,
                    viewHeightPx = viewHeightPx,
                    imageWidth = imgW,
                    imageHeight = imgH
                )

                barcodeAnalyzer.targetRegion = mappedRect
                barcodeAnalyzer.analyze(imageProxy)
            }
        }
    }

    // ==================== 替换区域结束 ====================
    // --- 4. 辅助功能 Launcher (相册 & 词库导入) ---
    val importDictLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    JiebaUtils.updateDict(context, uri)
                    showSettingsDialog = false
                    showRestartTipDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val inputImage = RecognitionUtils.imageFromUri(context, uri)
            if (inputImage != null) {
                if (scanMode == 0) {
                    RecognitionUtils.recognizeQRCode(inputImage, { onScanResult(it) }, {})
                } else {
                    RecognitionUtils.recognizeText(inputImage, { rawOcrResult = it }, {})
                }
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
                        onScanResult(code)
                    },
                    onCancel = {
                        frozenBitmap = null // 取消定格，相机自动恢复分析
                        frozenBarcodes = emptyList()
                    }
                )
            } else {
                // 正常扫描框
                ScanOverlay(
                    windowWidth = barcodeFrameW.dp,
                    windowHeight = barcodeFrameH.dp,
                    isAnimating = true,
                    showMask = true
                )
            }
        } else {
            // === OCR 模式 ===
            if (showOcrMask) {
                ScanOverlay(
                    windowWidth = ocrFrameW.dp,
                    windowHeight = ocrFrameH.dp,
                    isAnimating = false,
                    showMask = true,
                    cornerColor = Color.White
                )
            }
        }

        // C. 顶部控制栏
        if (frozenBitmap == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
                Row {
                    IconButton(onClick = {
                        isTorchOn = !isTorchOn
                        cameraControl?.enableTorch(isTorchOn)
                    }) {
                        Icon(if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }

        // D. 底部操作栏
        if (frozenBitmap == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(DarkBackground.copy(alpha = 0.6f))
                    .padding(bottom = 30.dp, top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 模式切换
                Row(modifier = Modifier.padding(bottom = 20.dp)) {
                    ModeButton("扫一扫", scanMode == 0) { scanMode = 0 }
                    Spacer(modifier = Modifier.width(32.dp))
                    ModeButton("识文字", scanMode == 1) { scanMode = 1 }
                }

                // 核心控制区
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 相册
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.size(48.dp).background(DarkSurfaceVariant, CircleShape)
                        ) { Icon(Icons.Default.Image, "Gallery", tint = MaterialTheme.colorScheme.onSurface) }
                        Text("相册", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }

                    // 快门键
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(72.dp).border(4.dp, MaterialTheme.colorScheme.onBackground, CircleShape))
                        if (scanMode == 0) {
                            Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                                    .clickable { // 【替换逻辑】使用方案 2：PreviewView 直接截图法
                                        previewViewRef?.bitmap?.let { screenBitmap ->
                                            // 1. 获取当前设置里的扫描框真实像素
                                            val frameWPx = with(density) { ocrFrameW.dp.toPx() }
                                            val frameHPx = with(density) { ocrFrameH.dp.toPx() }

                                            // 2. 因为你的 UI 遮罩是绝对居中的，数学逻辑极其简单：
                                            val left = ((screenBitmap.width - frameWPx) / 2).toInt().coerceAtLeast(0)
                                            val top = ((screenBitmap.height - frameHPx) / 2).toInt().coerceAtLeast(0)
                                            val width = frameWPx.toInt().coerceAtMost(screenBitmap.width - left)
                                            val height = frameHPx.toInt().coerceAtMost(screenBitmap.height - top)

                                            try {
                                                // 3. 暴力切图：屏幕上看到什么，就切什么
                                                val croppedBmp = Bitmap.createBitmap(screenBitmap, left, top, width, height)
                                                val inputImage = InputImage.fromBitmap(croppedBmp, 0)

                                                // 4. 直接喂给 OCR 工具类
                                                RecognitionUtils.recognizeText(
                                                    image = inputImage,
                                                    onSuccess = { result ->
                                                        if (result.text.isNotBlank()) {
                                                            rawOcrResult = result
                                                        } else {
                                                            Toast.makeText(context, "未识别到文字", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onFailure = { e ->
                                                        Toast.makeText(context, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(48.dp)) // 占位保持平衡
                }
            }
        }

        // E. 弹窗处理
        if (rawOcrResult != null) {
            WordSelectionSheet(
                rawText = rawOcrResult!!,
                onDismiss = { rawOcrResult = null },
                onConfirm = { text ->
                    onScanResult(text)
                    rawOcrResult = null
                },
                isSimpleMode = isSimpleMode
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
fun ModeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onBackground  else Color.Gray.copy(alpha = 0.5f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
        if (isSelected) {
            Box(modifier = Modifier.padding(top = 4.dp).size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                // [新增] 多码选择开关
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
                    leadingContent = { Icon(Icons.Default.CropFree, null) }, // 需要导入 Icons.Default.CropFree 或其他
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
                    leadingContent = { Icon(Icons.Default.ContentCut, null) }, // 如果没有 ContentCut 图标，可以用 Edit 之类的
                    trailingContent = {
                        Switch(
                            checked = isSimpleMode,
                            onCheckedChange = onToggleSimpleMode // 绑定回调
                        )
                    }
                )
            }
        },

        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// 2. 重启提示弹窗 UI
@Composable
fun RestartTipDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // 禁止点击外部关闭，强制重启
        title = { Text("需要重启") },
        text = { Text("自定义词库已导入成功！\n应用需要重启以重新加载词典引擎。") },
        confirmButton = {
            Button(onClick = onRestart) {
                Text("立即重启")
            }
        },
        dismissButton = null // 不允许取消
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

//扫码页面
@Composable
fun ScanOverlay(
    windowWidth: Dp = 260.dp,  // 扫描框宽度
    windowHeight: Dp = 260.dp, // 扫描框高度
    isAnimating: Boolean = true,
    showMask: Boolean = true,
    maskColor: Color = Color.Black.copy(alpha = 0.6f),
    cornerColor: Color = ScannerCorner,
    lineColor: Color = ScannerLine
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val windowWPx = windowWidth.toPx()
        val windowHPx = windowHeight.toPx()

        // 扫描框居中
        val left = (canvasWidth - windowWPx) / 2
        val top = (canvasHeight - windowHPx) / 2
        val right = left + windowWPx
        val bottom = top + windowHPx

        // 定义扫描框区域 Rect

        // === A. 绘制半透明遮罩 (Mask) ===
        // 原理：在扫描框的 上、下、左、右 画四个矩形
        if (showMask) {
            drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(canvasWidth, top))
            drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(canvasWidth, canvasHeight - bottom))
            drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, windowHPx))
            drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(canvasWidth - right, windowHPx))
        }

        // === B. 绘制四个角 (Corners) ===
        val cornerLength = 20.dp.toPx() // 角的长度
        val cornerWidth = 4.dp.toPx()   // 角的粗细

        val path = Path().apply {
            // 左上角
            moveTo(left, top + cornerLength); lineTo(left, top); lineTo(left + cornerLength, top)
            // 右上角
            moveTo(right - cornerLength, top); lineTo(right, top); lineTo(right, top + cornerLength)
            // 右下角
            moveTo(right, bottom - cornerLength); lineTo(right, bottom); lineTo(right - cornerLength, bottom)
            // 左下角
            moveTo(left + cornerLength, bottom); lineTo(left, bottom); lineTo(left, bottom - cornerLength)
        }

        drawPath(
            path = path,
            color = cornerColor,
            style = Stroke(width = cornerWidth, cap = StrokeCap.Round) // 圆头线条
        )

        // === C. 绘制扫描线 (Scanner Line) ===
        // === C. 【重点修改】绘制带拖尾效果的扫描线 ===
        if (isAnimating) {
            // 定义拖尾的高度 (例如 60dp)
            val trailHeightPx = 35.dp.toPx()

            // 计算主扫描线当前的 Y 坐标
            val lineY = top + (windowHPx * progress)

            clipRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom
            ) {
                val trailBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        lineColor.copy(alpha = 0.7f)
                    ),
                    startY = lineY - trailHeightPx,
                    endY = lineY
                )

                drawRect(
                    brush = trailBrush,
                    topLeft = Offset(left, lineY - trailHeightPx),
                    size = Size(windowWPx, trailHeightPx)
                )

                drawLine(
                    color = lineColor,
                    start = Offset(left, lineY),
                    end = Offset(right, lineY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Square
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FrozenSelectionOverlay(
    bitmap: Bitmap,
    barcodes: List<DetectedBarcode>,
    onBarcodeClick: (String) -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        // [修复 1] 这里不再用 toPx()，而是直接从 constraints 拿像素值 (Int 转 Float)
        // 这样就解决了 "Unresolved reference 'toPx'" 和类型推断错误
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // 2. 获取图片的宽高
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()

        // 3. 计算缩放比例 (Fit 模式：取宽和高中较小的那个比例)
        val scale = minOf(screenWidth / imgWidth, screenHeight / imgHeight)

        // 4. 计算图片显示的大小
        val displayW = imgWidth * scale
        val displayH = imgHeight * scale

        // 5. 计算黑边偏移量
        val offsetX = (screenWidth - displayW) / 2
        val offsetY = (screenHeight - displayH) / 2

        // --- A. 绘制底图 ---
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Frozen Frame",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // --- B. 绘制半透明遮罩 ---
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        // --- C. 绘制按钮 ---
        barcodes.forEach { code ->
            // 还原坐标公式：偏移量 + (归一化坐标 * 显示宽高)
            val finalX = offsetX + (code.normX * displayW)
            val finalY = offsetY + (code.normY * displayH)

            // 像素转 Dp
            val xDp = with(density) { finalX.toDp() }
            val yDp = with(density) { finalY.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = xDp - 24.dp, y = yDp - 24.dp) // 修正中心点
                    .size(48.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(StatusSuccess, CircleShape)
                    .clickable { onBarcodeClick(code.rawValue) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // --- D. 底部取消按钮 ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
        ) {
            Button(onClick = onCancel) {
                Text("取消选择")
            }
        }
    }
}






