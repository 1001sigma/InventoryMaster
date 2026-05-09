package com.example.inventorymaster.batchscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import com.example.inventorymaster.utils.ImageStorageUtils

@Composable
fun BatchScannerScreen(
    inputUri: Uri? = null,              // 新增：外部传入的照片 Uri（可选）
    targetList: List<String>? = null,   // 单据清单（可选）
    onComplete: (List<String>, Uri) -> Unit, // 模块出口
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
                    // 图片解析成功，直接进入处理流程
                    viewModel.processCapturedImage(bitmap)
                } else {
                    Log.e("BatchScanner", "从相册加载图片失败")
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
                if (bitmap != null) {
                    // 图片加载成功，传给 ViewModel 直接开始解析
                    viewModel.initScanner(targetList, bitmap)
                } else {
                    // 图片加载失败（如权限被拒或文件损坏），降级到相机模式
                    Log.e("BatchScanner", "无法解析传入的 Uri 图片，降级到相机模式")
                    viewModel.initScanner(targetList, null)
                }
            }
        } else {
            // 未传入图片，直接开启相机模式
            viewModel.initScanner(targetList, null)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (uiState) {
            is ScannerUiState.Capture -> {
                CaptureView(
                    onImageCaptured = { bitmap ->
                        viewModel.processCapturedImage(bitmap)
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
            is ScannerUiState.Processing -> {
                ProcessingView()
            }
            is ScannerUiState.Review -> {
                val reviewBitmap = viewModel.currentReviewBitmap
                if (reviewBitmap != null) {
                    ReviewView(
                        bitmap = reviewBitmap,
                        barcodes = scannedBarcodes,
                        targetListSize = targetList?.size ?: 0,
                        onRetake = { viewModel.resetToCapture() },
                        onConfirm = {
                            val validResults = scannedBarcodes
                                .filter { it.status == ScanStatus.MATCHED }
                                .map { it.displayValue }

                            // 关键点：先将标记（绿框✔/红框✘）永久绘制到图片上再保存
                            val markedBitmap = ImageStorageUtils.drawBarcodesOnBitmap(reviewBitmap, scannedBarcodes)
                            val savedUri = ImageStorageUtils.saveBitmapToInternal(context, markedBitmap)
                            // 回收临时创建的标记位图
                            if (markedBitmap != reviewBitmap) markedBitmap.recycle()
                            if (savedUri != null) {
                                onComplete(validResults, savedUri)
                            }
                        }
                    )
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
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
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
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
        ) {
            Text("关闭", color = Color.White)
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
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {}

        // 新增：相册入口按钮
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 64.dp) // 放置在左下角
                .background(Color.Black.copy(alpha = 0.5f), CircleShape) // 加个半透明背景避免看不清
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "选择相册",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ProcessingView() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("高精度神经解析中...", color = Color.White)
        }
    }
}

@Composable
private fun ReviewView(
    bitmap: Bitmap,
    barcodes: List<GlobalBarcode>,
    targetListSize: Int,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ReviewCanvasOverlay(
                imageBitmap = bitmap.asImageBitmap(),
                barcodes = barcodes
            )
        }

        Surface(
            color = Color(0xFF1E1E1E),
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
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("放弃重拍")
                    }

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("核对完成")
                    }
                }
            }
        }
    }
}