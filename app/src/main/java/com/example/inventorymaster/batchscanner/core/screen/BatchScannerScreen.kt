package com.example.inventorymaster.batchscanner.core.screen

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.util.BarcodeClassifier
import com.example.inventorymaster.batchscanner.core.util.BarcodeColorCategory
import com.example.inventorymaster.batchscanner.core.util.BarcodeThemeColors
import com.example.inventorymaster.batchscanner.core.util.ClassifiedBarcode
import com.example.inventorymaster.batchscanner.core.util.PendingBatchImageQueue
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.models.ScannerUiState
import com.example.inventorymaster.batchscanner.core.overlay.ModernCropView
import com.example.inventorymaster.batchscanner.core.overlay.ReviewCanvasOverlay
import com.example.inventorymaster.batchscanner.core.viewmodel.BatchScannerViewModel
import com.example.inventorymaster.batchscanner.task.models.PhotoScanResult
import com.example.inventorymaster.batchscanner.ocr.models.ExtractSource
import com.example.inventorymaster.batchscanner.ocr.models.OcrExtractedData
import com.example.inventorymaster.batchscanner.ocr.models.OcrScanState
import com.example.inventorymaster.batchscanner.ocr.OcrCropOverlay
import com.example.inventorymaster.batchscanner.ocr.OcrResultDialog
import com.example.inventorymaster.ui.analyzer.SimpleFloatingScanner
import com.example.inventorymaster.utils.ImageStorageUtils
import com.example.inventorymaster.utils.permission.AppPermission
import com.example.inventorymaster.utils.permission.rememberPermissionAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BatchScannerScreen(
    inputUri: Uri? = null,              // 新增：外部传入的照片 Uri（可选）
    targetList: List<String>? = null,   // 单据清单（可选）
    onComplete: (List<String>, List<PhotoScanResult>, List<GlobalBarcode>) -> Unit, // 模块出口（照片与各自条码按图分组）
    onOcrConfirmed: (OcrExtractedData) -> Unit = {}, // OCR 确认事件回调
    onClose: () -> Unit,                // 退出模块
    viewModel: BatchScannerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scannedBarcodes by viewModel.scannedBarcodes.collectAsState()
    val useOpenCV by viewModel.useOpenCV.collectAsState()
    val saveOriginal by viewModel.saveOriginalToGallery.collectAsState()
    val ocrScanState by viewModel.ocrScanState.collectAsState()
    val ocrCropRects by viewModel.ocrCropRects.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val requestCameraForCapture = rememberPermissionAction(AppPermission.CAMERA) {
        viewModel.resetToCapture()
    }

    // 监听 OCR 确认事件，转发给外层
    LaunchedEffect(Unit) {
        viewModel.ocrConfirmedEvent.collect { data ->
            onOcrConfirmed(data)
        }
    }

    // 多选队列中尚未处理的图片（队列模式下由"下一张"逐张消费）
    var pendingQueueUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // 已保存照片与各自条码按图分组（"核对完成/结束采集"时随 onComplete 回传；PDF 导出按组一图一页）
    val savedPhotoGroups = remember { mutableStateListOf<PhotoScanResult>() }
    val isQueueMode by viewModel.isQueueMode.collectAsState()
    val queueRemaining by viewModel.queueRemaining.collectAsState()
    val currentImageBarcodes by viewModel.currentImageBarcodes.collectAsState()

    // 队列模式：结束采集——保留已扫结果，带全部按图分组照片返回任务页
    fun finishQueue() {
        onComplete(scannedBarcodes.map { it.displayValue }, savedPhotoGroups.toList(), scannedBarcodes)
    }

    // 队列模式：载入下一张待处理图片（进裁剪页）；加载失败的图片自动跳过
    fun loadNextQueueImage() {
        var next = pendingQueueUris.firstOrNull()
        while (next != null) {
            pendingQueueUris = pendingQueueUris.drop(1)
            val bmp = uriToBitmap(context, next)
            if (bmp != null) {
                viewModel.processNextQueueImage(bmp)
                return
            }
            Toast.makeText(context, "一张图片加载失败，已自动跳过", Toast.LENGTH_SHORT).show()
            next = pendingQueueUris.firstOrNull()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // 用户选择了图片，切换到 IO 线程进行解析
            coroutineScope.launch(Dispatchers.IO) {
                val firstBitmap = uriToBitmap(context, uris.first())
                if (firstBitmap != null) {
                    if (uris.size == 1) {
                        // 单张：保持原有裁剪流程
                        viewModel.initScanner(targetList, firstBitmap)
                    } else {
                        // 多张：队列模式——第一张跳过裁剪直接整图解析，其余待处理
                        pendingQueueUris = uris.drop(1)
                        viewModel.startImageQueue(targetList, firstBitmap, uris.size)
                    }
                }
            }
        }
    }

    // 初始化流转逻辑
    LaunchedEffect(inputUri, targetList) {
        if (inputUri != null) {
            // 切换到 IO 线程安全地加载图片
            coroutineScope.launch(Dispatchers.IO) {
                val bitmap = uriToBitmap(context, inputUri)
                if (bitmap == null) {
                    viewModel.initScanner(targetList, null)
                    return@launch
                }
                // 任务详情页"图片"按钮多选进入：消费跨页队列，直接进"下一张"队列模式
                val queued = PendingBatchImageQueue.consume()
                if (queued.size > 1) {
                    pendingQueueUris = queued.drop(1)
                    viewModel.startImageQueue(targetList, bitmap, queued.size)
                } else {
                    // 单张：保持原有裁剪流程
                    viewModel.initScanner(targetList, bitmap)
                }
            }
        } else {
            // 未传入图片，直接开启相机模式
            viewModel.initScanner(targetList, null)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val state=uiState) {
            is ScannerUiState.Capture -> {
                CaptureView(
                    onImageCaptured = { bitmap ->
                        // 裁剪前若开启了存图开关（扫描设置），把原始拍照图存相册
                        if (saveOriginal) {
                            viewModel.saveOriginalToGallery(bitmap)
                        }
                        viewModel.onImageCapturedForCrop(bitmap)
                    },
                    onClose = onClose,
                    // 触发相册选择器（仅限图片）
                    onGalleryClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    savedPhotoCount = savedPhotoGroups.size,
                    accumulatedBarcodeCount = scannedBarcodes.size,
                    onDirectFinish = {
                        onComplete(scannedBarcodes.map { it.displayValue }, savedPhotoGroups.toList(), scannedBarcodes)
                    }
                )
            }

            is ScannerUiState.Cropping -> {
                ModernCropView(
                    bitmap = state.rawBitmap,
                    onCropConfirm = { cropped ->
                        viewModel.processCroppedImage(cropped)
                    },
                    // 队列模式：还有剩余→"跳过此张"；连拍模式：放弃本张；单张：重拍
                    retakeText = when {
                        isQueueMode && queueRemaining > 0 -> "跳过此张"
                        savedPhotoGroups.isNotEmpty() -> "放弃本张"
                        else -> "重拍"
                    },
                    onRetake = {
                        when {
                            isQueueMode && queueRemaining > 0 -> loadNextQueueImage()
                            savedPhotoGroups.isNotEmpty() -> viewModel.discardCurrentImageAndRetake()
                            else -> requestCameraForCapture()
                        }
                    }
                )
            }
            is ScannerUiState.Processing -> {
                ProcessingView()
            }
            is ScannerUiState.Review -> {
                val reviewBitmap = viewModel.currentReviewBitmap
                val isRescanMode by viewModel.isRescanMode.collectAsState()
                val productNameMap by viewModel.productNameMap.collectAsState()
                // 1. 新增：控制悬浮单码相机的显示状态，以及记住刚才点击的坐标
                var showSingleScannerDialog by remember { mutableStateOf(false) }
                val requestSingleScanner = rememberPermissionAction(AppPermission.CAMERA) {
                    showSingleScannerDialog = true
                }
                var rescanTargetCoords by remember { mutableStateOf(Pair(0f, 0f)) }

                // 2. 监听需要拉起单码相机的事件
                LaunchedEffect(Unit) {
                    viewModel.singleScannerEvent.collect { (x, y) ->
                        Toast.makeText(context, "局部模糊，请使用单码对准扫描", Toast.LENGTH_SHORT).show()
                        // 记录坐标，并打开悬浮窗
                        rescanTargetCoords = Pair(x, y)
                        requestSingleScanner()
                    }
                }

                if (reviewBitmap != null) {
                    // 将标记（绿框✔/红框✘ + 黄色虚线OCR选框）绘制到图片上并保存到应用私有目录
                    // 注意：只画"当前这张图"的标记（currentImageBarcodes），累积全集的坐标属于其他图片
                    fun saveMarkedPhoto(): Uri? {
                        val markedBitmap = ImageStorageUtils.drawBarcodesOnBitmap(reviewBitmap, currentImageBarcodes)
                        ImageStorageUtils.drawOcrCropRectsOnBitmap(markedBitmap, ocrCropRects)
                        val savedUri = ImageStorageUtils.saveBitmapToInternal(context, markedBitmap)
                        if (markedBitmap != reviewBitmap) markedBitmap.recycle()
                        // 存图成功即锁定归属：此刻 currentImageBarcodes 就是这张图的码（照片上画的框与之对应）
                        if (savedUri != null) savedPhotoGroups.add(PhotoScanResult(savedUri, currentImageBarcodes.toList()))
                        return savedUri
                    }

                    ReviewView(
                        bitmap = reviewBitmap,
                        barcodes = currentImageBarcodes,
                        targetListSize = targetList?.size ?: 0,
                        isRescanMode = isRescanMode,
                        productNameMap = productNameMap,
                        onToggleRescan = { viewModel.toggleRescanMode() },
                        onImageTap = { x, y -> viewModel.handleImageTapForRescan(x, y) },
                        isQueueMode = isQueueMode,
                        queueRemaining = queueRemaining,
                        savedPhotoCount = savedPhotoGroups.size,
                        onNextImage = {
                            // 队列模式：当前张带标记存入照片轮播，再载入下一张（进裁剪页）
                            saveMarkedPhoto()
                            loadNextQueueImage()
                        },
                        onContinueNextCapture = {
                            // 连拍模式：当前张带标记存入照片轮播，随后切回相机取景拍下一张
                            saveMarkedPhoto()
                            viewModel.prepareNextCapture()
                        },
                        onDiscardCurrent = {
                            // 放弃当前张并重拍，不影响前几张
                            viewModel.discardCurrentImageAndRetake()
                        },
                        onRetake = {
                            if (isQueueMode && queueRemaining > 0) {
                                // 队列模式："结束采集"——保存当前张，携带全部累积结果返回任务页
                                saveMarkedPhoto()
                                finishQueue()
                            } else if (savedPhotoGroups.isNotEmpty()) {
                                viewModel.discardCurrentImageAndRetake()
                            } else {
                                requestCameraForCapture()
                            }
                        },
                        onConfirm = {
                            // 返回全部条码（MATCHED 和 MISMATCHED），由 ViewModel 做单据核对
                            saveMarkedPhoto()
                            onComplete(scannedBarcodes.map { it.displayValue }, savedPhotoGroups.toList(), scannedBarcodes)
                        },
                        ocrScanState = ocrScanState,
                        ocrCropRects = ocrCropRects,
                        onStartOcr = { viewModel.startOcrCrop() },
                        onOcrCropConfirm = { rect ->
                            viewModel.processOcrCrop(
                                android.graphics.Rect(
                                    rect.left.toInt(), rect.top.toInt(),
                                    rect.right.toInt(), rect.bottom.toInt()
                                )
                            )
                        },
                        onCancelOcr = { viewModel.cancelOcr() },
                        onConfirmOcrResult = { data -> viewModel.confirmOcrResult(data) }
                    )
                    // OCR 覆盖层
                    val currentOcrState = ocrScanState
                    when (currentOcrState) {
                        is OcrScanState.Cropping -> {
                            OcrCropOverlay(
                                imageBitmap = reviewBitmap,
                                onConfirm = { rect ->
                                    viewModel.processOcrCrop(
                                        android.graphics.Rect(
                                            rect.left.toInt(), rect.top.toInt(),
                                            rect.right.toInt(), rect.bottom.toInt()
                                        )
                                    )
                                },
                                onCancel = { viewModel.cancelOcr() }
                            )
                        }
                        is OcrScanState.Recognizing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("OCR 识别中...", color = Color.White)
                                }
                            }
                        }
                        is OcrScanState.Extracted -> {
                            OcrResultDialog(
                                data = currentOcrState.data,
                                onConfirm = { data -> viewModel.confirmOcrResult(data) },
                                onDismiss = { viewModel.cancelOcr() }
                            )
                        }
                        is OcrScanState.Error -> {
                            LaunchedEffect(currentOcrState) {
                                Toast.makeText(context, currentOcrState.message, Toast.LENGTH_SHORT).show()
                                viewModel.cancelOcr()
                            }
                        }
                        else -> { /* Idle */ }
                    }
                    if (showSingleScannerDialog) {
                        // 1. 全屏遮罩层：负责监听"点击弹窗以外区域自动退出"
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            // 点击气泡以外的任意空白地方，直接关闭弹窗
                                            showSingleScannerDialog = false
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center // ✨ 锁死居中：让内部的补扫相机永远居中显示
                        ) {
                            // 2. 居中相机卡片：丢弃坐标位移，采用 4:3 的黄金悬浮比例
                            Box(
                                modifier = Modifier
                                    // 设定一个更符合相机比例的尺寸 (宽 260dp，高 195dp 刚好是 4:3)
                                    .size(180.dp, 180.dp)
                                    // 拦截气泡内部的点击，防止误触退出
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { /* 拦截点击，不做任何事 */ })
                                    }
                                    // 优雅的圆角与磨砂白细边框
                                    .background(Color.Black, RoundedCornerShape(24.dp))
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                // 3. 挂载轻量级扫码器
                                SimpleFloatingScanner(
                                    modifier = Modifier.fillMaxSize(),
                                    onScanResult = { scannedCode ->
                                        viewModel.addManualBarcodeAfterSingleScan(
                                            value = scannedCode,
                                            originalX = rescanTargetCoords.first,
                                            originalY = rescanTargetCoords.second
                                        )
                                        showSingleScannerDialog = false
                                        Toast.makeText(context, "补扫成功！", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    viewModel.resetToCapture()
                }
            }
        }
    }
}

/**
 * 辅助方法：将外部 Uri 转换为 Bitmap，兼容各个 Android 版本
 */
private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // 强制分配为 Software 格式，避免 ML Kit 在处理 Hardware Bitmap 时报错
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@Composable
private fun CaptureView(
    onImageCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onGalleryClick: () -> Unit,
    savedPhotoCount: Int = 0,
    accumulatedBarcodeCount: Int = 0,
    onDirectFinish: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. 底层：相机预览（FIT_CENTER 真实还原视角，不裁切放大）
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                        cameraControl = camera.cameraControl
                        cameraControl?.setZoomRatio(1.0f)
                    } catch (exc: Exception) {
                        Log.e("BatchScanner", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val factory = SurfaceOrientedMeteringPointFactory(
                            size.width.toFloat(), size.height.toFloat()
                        )
                        val point = factory.createPoint(tapOffset.x, tapOffset.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        cameraControl?.startFocusAndMetering(action)
                    }
                )
            }
        )

        // 2. 连拍模式顶部信息条
        if (savedPhotoCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "已拍摄 ${savedPhotoCount} 张 · 累积识别 ${accumulatedBarcodeCount} 码",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // 3. 顶层：控制按钮操作层
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
        ) {
            Text("关闭", color = Color.White)
        }

        // 右上角：连拍已有数据时的快捷完成提交入口
        if (savedPhotoCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(16.dp))
                    .clickable { onDirectFinish() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "完成提交",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            val matrix = android.graphics.Matrix().apply {
                                postRotate(image.imageInfo.rotationDegrees.toFloat())
                            }
                            val rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            onImageCaptured(rotatedBitmap)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("BatchScanner", "Photo capture failed", exception)
                        }
                    }
                )
            },
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {}

        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 64.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "选择相册",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}



@Composable
private fun ProcessingView() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("图像解析中...", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private data class CardColorStyle(
    val bg: Color,
    val border: Color,
    val tagColor: Color,
    val tagText: String
)

@Composable
private fun ReviewView(
    bitmap: Bitmap,
    barcodes: List<GlobalBarcode>,
    targetListSize: Int,
    onToggleRescan: () -> Unit,
    onImageTap: (Float, Float) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    isRescanMode: Boolean,
    isQueueMode: Boolean,
    queueRemaining: Int,
    onNextImage: () -> Unit,
    ocrScanState: OcrScanState,
    ocrCropRects: List<android.graphics.Rect>,
    onStartOcr: () -> Unit,
    onOcrCropConfirm: (androidx.compose.ui.geometry.Rect) -> Unit,
    onCancelOcr: () -> Unit,
    onConfirmOcrResult: (OcrExtractedData) -> Unit,
    savedPhotoCount: Int = 0,
    onContinueNextCapture: () -> Unit = {},
    onDiscardCurrent: () -> Unit = {},
    productNameMap: Map<String, String> = emptyMap()
) {
    // 0: 操作控制, 1: 扫码数据
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var highlightedBarcode by remember { mutableStateOf<GlobalBarcode?>(null) }
    var highlightKey by remember { mutableLongStateOf(0L) }

    val classifiedBarcodes = remember(barcodes) {
        BarcodeClassifier.classify(barcodes)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ReviewCanvasOverlay(
                imageBitmap = bitmap.asImageBitmap(),
                barcodes = barcodes,
                classifiedBarcodes = classifiedBarcodes,
                highlightedBarcode = highlightedBarcode,
                highlightKey = highlightKey,
                ocrCropRects = ocrCropRects,
                isRescanMode = isRescanMode,
                onImageTap = onImageTap
            )
            // 顶部补扫模式提示 UI
            if (isRescanMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, Color(0xFFFFB703).copy(alpha = 0.5f), CircleShape)
                        .blur(6.dp)
                ) {}

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp)
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                        .border(1.dp, Color(0xFFFFB703).copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(6.dp)
                                .background(Color(0xFFFFB703), CircleShape)
                        )
                        Text(
                            text = "请点击图片中漏扫的条码",
                            color = Color(0xFFFFB703),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部胶囊切换器：[ 操作控制 | 扫码数据 (N) ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (selectedTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "操作控制",
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (selectedTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "扫码数据 (${barcodes.size})",
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // ================= 模式 0：操作控制面板 =================
                    val matchedCount = barcodes.count { it.status == ScanStatus.MATCHED }
                    val mismatchedCount = barcodes.count { it.status == ScanStatus.MISMATCHED }

                    Text(
                        text = if (targetListSize > 0) {
                            "单据目标: $targetListSize | 成功匹配: $matchedCount | 异常多出: $mismatchedCount"
                        } else {
                            "识别总数: $matchedCount"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 主操作按钮
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 第一行：辅助工具行（三个 4 字符按钮并排，单行防换行）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isQueueMode && queueRemaining > 0) {
                                        onRetake()
                                    } else if (savedPhotoCount > 0) {
                                        onDiscardCurrent()
                                    } else {
                                        onRetake()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = when {
                                        isQueueMode && queueRemaining > 0 -> "结束采集"
                                        savedPhotoCount > 0 -> "放弃本张"
                                        else -> "放弃重拍"
                                    },
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            FilledTonalButton(
                                onClick = onToggleRescan,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isRescanMode) Color(0xFFFFB703) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isRescanMode) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = if (isRescanMode) "取消补扫" else "点选补扫",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            FilledTonalButton(
                                onClick = onStartOcr,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = "文字识别",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // 第二行：主流程推进操作行（高优先级大按键，高度 48dp）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isQueueMode && queueRemaining > 0) {
                                Button(
                                    onClick = onNextImage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E88E5)
                                    )
                                ) {
                                    Text(
                                        text = "下一张 (剩${queueRemaining}张)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onContinueNextCapture,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E88E5),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "拍下一张",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }

                                Button(
                                    onClick = onConfirm,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text(
                                        text = if (savedPhotoCount > 0) "核对完成 (${savedPhotoCount + 1}张)" else "核对完成",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ================= 模式 1：扫码数据列表 =================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 顶部统计分类小标签
                        item {
                            val standardCount = classifiedBarcodes.count { it.category == BarcodeColorCategory.MAJORITY_STANDARD }
                            val diffBatchCount = classifiedBarcodes.count { it.category == BarcodeColorCategory.MAJORITY_DIFF_BATCH }
                            val otherMatCount = classifiedBarcodes.count { it.category == BarcodeColorCategory.OTHER_MATERIAL }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "点击条目定位：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .background(BarcodeThemeColors.BlueCardBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, BarcodeThemeColors.BlueCardBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("主物料 $standardCount", style = MaterialTheme.typography.labelSmall, color = BarcodeThemeColors.BlueText)
                                }
                                if (diffBatchCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(BarcodeThemeColors.RedCardBg, RoundedCornerShape(4.dp))
                                            .border(1.dp, BarcodeThemeColors.RedCardBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("混批 $diffBatchCount", style = MaterialTheme.typography.labelSmall, color = BarcodeThemeColors.RedText)
                                    }
                                }
                                if (otherMatCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(BarcodeThemeColors.YellowCardBg, RoundedCornerShape(4.dp))
                                            .border(1.dp, BarcodeThemeColors.YellowCardBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("混料 $otherMatCount", style = MaterialTheme.typography.labelSmall, color = BarcodeThemeColors.YellowText)
                                    }
                                }
                            }
                        }

                        // 条目列表
                        items(classifiedBarcodes) { item ->
                            val style = when (item.category) {
                                BarcodeColorCategory.MAJORITY_STANDARD -> CardColorStyle(
                                    BarcodeThemeColors.BlueCardBg,
                                    BarcodeThemeColors.BlueCardBorder,
                                    BarcodeThemeColors.BlueText,
                                    "主要物料"
                                )
                                BarcodeColorCategory.MAJORITY_DIFF_BATCH -> CardColorStyle(
                                    BarcodeThemeColors.RedCardBg,
                                    BarcodeThemeColors.RedCardBorder,
                                    BarcodeThemeColors.RedText,
                                    "同品混批"
                                )
                                BarcodeColorCategory.OTHER_MATERIAL -> CardColorStyle(
                                    BarcodeThemeColors.YellowCardBg,
                                    BarcodeThemeColors.YellowCardBorder,
                                    BarcodeThemeColors.YellowText,
                                    "不同物料"
                                )
                            }

                            val productName = item.di?.let { productNameMap[it] }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        highlightedBarcode = item.barcode
                                        highlightKey = System.currentTimeMillis()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = style.bg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, style.border)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 序号
                                        Text(
                                            text = "#${item.index}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = style.tagColor,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )

                                        // 类别 Tag
                                        Box(
                                            modifier = Modifier
                                                .background(style.border.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = style.tagText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = style.tagColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 商品名称 / DI
                                        Text(
                                            text = productName ?: (item.di?.let { "DI: $it" } ?: item.barcode.displayValue),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f),
                                            color = Color(0xFF212121)
                                        )

                                        // 单据核对状态
                                        if (targetListSize > 0) {
                                            if (item.barcode.status == ScanStatus.MATCHED) {
                                                Text(
                                                    text = "✔ 已匹配",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2E7D32),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else if (item.barcode.status == ScanStatus.MISMATCHED) {
                                                Text(
                                                    text = "✘ 异常多出",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFC62828),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 次行：批号与码值
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (!item.batch.isNullOrBlank()) "批号: ${item.batch}" else "无批号",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF424242),
                                            fontWeight = FontWeight.Medium
                                        )

                                        if (item.di != null) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "DI: ${item.di}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF757575),
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Text(
                                            text = "点击闪烁 🔍",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = style.tagColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // 用户明确要求的：数据滑动到最下面显示 [拍下一张] 和 [核对完成]
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isQueueMode && queueRemaining > 0) {
                                    Button(
                                        onClick = onNextImage,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1E88E5)
                                        )
                                    ) {
                                        Text(
                                            text = "下一张 (剩${queueRemaining}张)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onContinueNextCapture,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1E88E5),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "拍下一张",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    Button(
                                        onClick = onConfirm,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                                    ) {
                                        Text(
                                            text = if (savedPhotoCount > 0) "核对完成 (${savedPhotoCount + 1}张)" else "核对完成",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
