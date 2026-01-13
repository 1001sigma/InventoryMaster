package com.example.inventorymaster.ui

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.inventorymaster.utils.RecognitionUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(
    onScanResult: (String) -> Unit, // 扫码/OCR确认后的回调
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // --- 状态管理 ---
    // 模式: 0 = 扫码 (QR), 1 = 识字 (OCR)
    var scanMode by remember { mutableIntStateOf(0) }
    // 闪光灯状态
    var isTorchOn by remember { mutableStateOf(false) }
    // 相机控制器 (用于开关闪光灯)
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // OCR 流程控制
    var isCaptureRequested = remember { AtomicBoolean(false) } // 是否按下了快门
    var rawOcrResult by remember { mutableStateOf<Text?>(null) } // OCR 结果文字

    // 相册选择器
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputImage = RecognitionUtils.imageFromUri(context, uri)
            if (inputImage != null) {
                if (scanMode == 0) {
                    // 扫码模式：查二维码
                    RecognitionUtils.recognizeQRCode(inputImage, { res -> onScanResult(res) }, {})
                } else {
                    // 识字模式：查文字
                    RecognitionUtils.recognizeText(inputImage, { res -> rawOcrResult = res }, {})
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. 相机预览层
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // A. 预览 UseCase
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // B. 图像分析 UseCase
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                            // *** 核心逻辑分支 ***
                            if (scanMode == 0) {
                                // === 模式：扫码 (实时检测) ===
                                RecognitionUtils.recognizeQRCode(
                                    image,
                                    onSuccess = { res ->
                                        // 扫到了！在主线程回调
                                        previewView.post { onScanResult(res) }
                                    },
                                    onFailure = { /* 忽略错误 */ },
                                    onComplete = { imageProxy.close() }
                                )
//                                imageProxy.close() // 继续下一帧

                            } else {
                                // === 模式：识字 (按快门才检测) ===
                                if (isCaptureRequested.getAndSet(false)) {
                                    // 用户按了快门，处理这一帧
                                    previewView.post { Toast.makeText(context, "正在识别...", Toast.LENGTH_SHORT).show() }
                                    RecognitionUtils.recognizeText(
                                        image,
                                        onSuccess = { res ->
                                            if (res.text.isNotBlank()) {
                                                previewView.post { rawOcrResult = res }
                                            } else {
                                                previewView.post {
                                                    Toast.makeText(context, "未识别到文字，请对准重试", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onFailure = {
                                            previewView.post {
                                                Toast.makeText(context, "识别出错: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onComplete = { imageProxy.close() }
                                    )
                                } else {
                                    // 没按快门，直接丢弃这一帧，节省 CPU
                                    imageProxy.close()
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl // 拿到控制器
                    } catch (e: Exception) {
                        Log.e("Camera", "Bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. 扫码模式下的激光扫描线动画 (仅视觉效果)
        if (scanMode == 0) {
            ScannerOverlay()
        }

        // 3. 顶部控制栏 (关闭、闪光灯、设置)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 关闭按钮
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Row {
                // 闪光灯开关
                IconButton(onClick = {
                    isTorchOn = !isTorchOn
                    cameraControl?.enableTorch(isTorchOn)
                }) {
                    Icon(
                        if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = Color.White
                    )
                }

                // 设置按钮 (占位)
                IconButton(onClick = { Toast.makeText(context, "设置暂未开放", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        // 4. 底部操作栏 (模式切换、快门、相册)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f)) // 半透明底
                .padding(bottom = 30.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 模式切换器
            Row(modifier = Modifier.padding(bottom = 20.dp)) {
                ModeButton(text = "扫一扫", isSelected = scanMode == 0) { scanMode = 0 }
                Spacer(modifier = Modifier.width(32.dp))
                ModeButton(text = "识文字", isSelected = scanMode == 1) { scanMode = 1 }
            }

            // 核心控制区
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：相册入口
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(48.dp).background(Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Image, "Gallery", tint = Color.White)
                    }
                    Text("相册", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                // 中间：快门键 / 暂停键
                Box(contentAlignment = Alignment.Center) {
                    // 外圈
                    Box(modifier = Modifier.size(72.dp).border(4.dp, Color.White, CircleShape))
                    // 内芯
                    if (scanMode == 0) {
                        // 扫码模式：显示一个小点，表示正在录制中
                        Box(modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape))
                    } else {
                        // OCR模式：显示实心快门键，可点击
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White, CircleShape)
                                .clickable { isCaptureRequested.set(true) } // 👈 触发 OCR
                        )
                    }
                }

                // 右侧：占位 (为了布局平衡，或者放历史记录)
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // 5. OCR 结果弹窗
        if (rawOcrResult != null) {
            WordSelectionSheet(
                rawText = rawOcrResult!!,
                onDismiss = { rawOcrResult = null }, // 关闭弹窗，继续预览
                onConfirm = { selectedText ->
                    onScanResult(selectedText) // 返回最终拼接好的文字
                    rawOcrResult = null
                }
            )
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
            color = if (isSelected) Color.Yellow else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
        if (isSelected) {
            Box(modifier = Modifier.padding(top = 4.dp).size(4.dp).background(Color.Yellow, CircleShape))
        }
    }
}

@Composable
fun ScannerOverlay() {
    // 画一个模拟的扫描线动画 (简单版)
    // 实际项目中可以用 Lottie 或者更复杂的 Canvas 动画
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 150.dp) // 定义扫描区域
            .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        // 这里可以加一个上下移动的横线
        HorizontalDivider(
            modifier = Modifier.align(Alignment.Center), // 暂时居中
            thickness = 2.dp,
            color = Color.Green
        )
    }
}

@Composable
fun OCRResultDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var editedText by remember { mutableStateOf(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别结果") },
        text = {
            Column {
                Text("请确认或编辑识别到的文字：", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(editedText) }) {
                Text("填入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}