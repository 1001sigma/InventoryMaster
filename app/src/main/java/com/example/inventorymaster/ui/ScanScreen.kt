package com.example.inventorymaster.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.inventorymaster.utils.JiebaUtils // 确保导入了 JiebaUtils
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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope() // 用于协程操作

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

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRestartTipDialog by remember { mutableStateOf(false) }

    // --- 核心功能 Launcher ---

    // 1. 词典导入文件选择器
    val importDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    // 调用 JiebaUtils 更新词典
                    JiebaUtils.updateDict(context, uri)
                    // 关闭设置弹窗，显示重启提示弹窗
                    showSettingsDialog = false
                    showRestartTipDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


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
                IconButton(onClick = { showSettingsDialog = true }) {
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

        // 6. 设置弹窗
        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                onImportDict = {
                    // 打开文件选择器，限制为 txt 文本
                    importDictLauncher.launch("text/plain")
                }
            )
        }

        //  7. 重启提示弹窗
        if (showRestartTipDialog) {
            RestartTipDialog(
                onRestart = {
                    restartApp(context)
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
            color = if (isSelected) Color.White  else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
        if (isSelected) {
            Box(modifier = Modifier.padding(top = 4.dp).size(4.dp).background(Color.Yellow, CircleShape))
        }
    }
}

//扫码页面
@Composable
fun ScannerOverlay() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 上下黑色遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)  // 上方遮罩区域高度
                .background(Color.Black.copy(alpha = 1f))  // 半透明黑色遮罩
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)  // 下方遮罩区域高度
                .background(Color.Black.copy(alpha = 1f))  // 半透明黑色遮罩
                .align(Alignment.BottomCenter)
        )

        // 扫描区域边框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 50.dp, vertical = 270.dp) // 扫描框区域
                .border(3.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
        ) {
            // 这里可以加一个上下移动的横线
            HorizontalDivider(
                modifier = Modifier.align(Alignment.Center), // 居中
                thickness = 2.dp,
                color = Color.Green
            )
        }
    }
}

// 1. 设置弹窗 UI
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onImportDict: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("导入自定义词库") },
                    supportingContent = { Text("上传 user_dict.txt 以优化分词") },
                    leadingContent = { Icon(Icons.Default.UploadFile, null) },
                    modifier = Modifier.clickable { onImportDict() }
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
    android.os.Process.killProcess(android.os.Process.myPid())
}


//文字识别的文本框模式
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