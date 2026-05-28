package com.example.inventorymaster.batchscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.ui.analyzer.SimpleFloatingScanner
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import com.example.inventorymaster.utils.ImageStorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BatchScannerScreen(
    inputUri: Uri? = null,              // 新增：外部传入的照片 Uri（可选）
    targetList: List<String>? = null,   // 单据清单（可选）
    onComplete: (List<String>, Uri, List<GlobalBarcode>) -> Unit, // 模块出口
    onClose: () -> Unit,                // 退出模块
    viewModel: BatchScannerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scannedBarcodes by viewModel.scannedBarcodes.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // 用户选择了图片，切换到 IO 线程进行解析
            coroutineScope.launch(Dispatchers.IO) {
                val bitmap = uriToBitmap(context, uri)
                if (bitmap != null) {
                    // 从相册选图后，直接进入裁剪阶段
                    viewModel.initScanner(targetList, bitmap)
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
                viewModel.initScanner(targetList, bitmap)
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
                        viewModel.onImageCapturedForCrop(bitmap)
                    },
                    onClose = onClose,
                    // 新增：触发相册选择器（仅限图片）
                    onGalleryClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
            
            is ScannerUiState.Cropping -> {
                ModernCropView(
                    bitmap = state.rawBitmap,
                    onCropConfirm = { cropped ->
                        viewModel.processCroppedImage(cropped)
                    },
                    onRetake = {
                        viewModel.resetToCapture()
                    }
                )
            }
            is ScannerUiState.Processing -> {
                ProcessingView()
            }
            is ScannerUiState.Review -> {
                val reviewBitmap = viewModel.currentReviewBitmap
                val isRescanMode by viewModel.isRescanMode.collectAsState()
                // 1. 新增：控制悬浮单码相机的显示状态，以及记住刚才点击的坐标
                var showSingleScannerDialog by remember { mutableStateOf(false) }
                var rescanTargetCoords by remember { mutableStateOf(Pair(0f, 0f)) }

                // 2. 监听需要拉起单码相机的事件
                LaunchedEffect(Unit) {
                    viewModel.singleScannerEvent.collect { (x, y) ->
                        Toast.makeText(context, "局部模糊，请使用单码对准扫描", Toast.LENGTH_SHORT).show()
                        // 记录坐标，并打开悬浮窗
                        rescanTargetCoords = Pair(x, y)
                        showSingleScannerDialog = true
                    }
                }

                if (reviewBitmap != null) {
                    ReviewView(
                        bitmap = reviewBitmap,
                        barcodes = scannedBarcodes,
                        targetListSize = targetList?.size ?: 0,
                        isRescanMode = isRescanMode,
                        onToggleRescan = { viewModel.toggleRescanMode() },
                        onImageTap = { x, y -> viewModel.handleImageTapForRescan(x, y) },
                        onRetake = { viewModel.resetToCapture() },
                        onConfirm = {
                            // 返回全部条码（MATCHED 和 MISMATCHED），由 ViewModel 做单据核对
                            val validResults = scannedBarcodes.map { it.displayValue }

                            // 将标记（绿框✔/红框✘）永久绘制到图片上再保存
                            val markedBitmap = ImageStorageUtils.drawBarcodesOnBitmap(reviewBitmap, scannedBarcodes)
                            val savedUri = ImageStorageUtils.saveBitmapToInternal(context, markedBitmap)
                            // 回收临时创建的标记位图
                            if (markedBitmap != reviewBitmap) markedBitmap.recycle()
                            if (savedUri != null) {
                                onComplete(validResults, savedUri, scannedBarcodes)
                            }
                        }
                    )
                    if (showSingleScannerDialog) {
                        Dialog(onDismissRequest = { showSingleScannerDialog = false }) {
                            Box(
                                modifier = Modifier
                                    // 给弹窗设定一个合适的大小和圆角
                                    .size(200.dp, 200.dp)
                                    .background(Color.Black, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                // 挂载我们新写的轻量级扫码器
                                SimpleFloatingScanner(
                                    modifier = Modifier.fillMaxSize(),
                                    onScanResult = { scannedCode ->
                                        // 扫码成功！将结果和之前保存的坐标传给 ViewModel 贴上绿框
                                        viewModel.addManualBarcodeAfterSingleScan(
                                            value = scannedCode,
                                            originalX = rescanTargetCoords.first,
                                            originalY = rescanTargetCoords.second
                                        )
                                        // 关闭弹窗
                                        showSingleScannerDialog = false
                                        Toast.makeText(context, "补扫成功！", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 右上角的关闭按钮，允许用户放弃补扫
                                IconButton(
                                    onClick = { showSingleScannerDialog = false },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = Color.White
                                    )
                                }
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
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // 强制分配为 Software 格式，避免 ML Kit 在处理 Hardware Bitmap 时报错
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
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
    onGalleryClick: () -> Unit
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
    // 新增：用于记录父容器（预览区）的尺寸和扫描框的相对位置
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var scanBoxRect by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = Modifier
        .fillMaxSize()
        // 获取整个相机预览区域的尺寸
        .onSizeChanged { previewSize = it }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
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
                        camera.cameraControl.setZoomRatio(1.5f)
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

        // 在中心画一个宽占屏幕 85%，高占比适当的矩形框
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .aspectRatio(9f/16f)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {

            Text(
                text = "请将所有条码置于框内",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
        ) {
            Text("关闭", color = MaterialTheme.colorScheme.onBackground)
        }

        Button(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            val matrix = Matrix().apply {
                                postRotate(image.imageInfo.rotationDegrees.toFloat())
                            }
                            val rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            // 2. 计算裁剪比例并执行裁剪
                                // 兜底：如果 UI 还没初始化完成，送入原图
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

        // 新增：相册入口按钮
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

@Composable
private fun ReviewView(
    bitmap: Bitmap,
    barcodes: List<GlobalBarcode>,
    targetListSize: Int,
    onToggleRescan: () -> Unit, // 新增
    onImageTap: (Float, Float) -> Unit, // 新增
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    isRescanMode: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ReviewCanvasOverlay(
                imageBitmap = bitmap.asImageBitmap(),
                barcodes = barcodes,
                isRescanMode = isRescanMode, // 传入状态
                onImageTap = onImageTap      // 传入回调
            )
            // 顶部补扫模式提示 UI
            if (isRescanMode) {
                Text(
                    text = "请点击图片中漏扫的条码",
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp)
                        .background(StatusWarning, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("放弃重拍")
                    }

                    FilledTonalButton(
                        onClick = onToggleRescan,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRescanMode) StatusWarning else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isRescanMode) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(if (isRescanMode) "取消补扫" else "点选补扫")
                    }

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                    ) {
                        Text("核对完成")
                    }
                }
            }
        }
    }
}