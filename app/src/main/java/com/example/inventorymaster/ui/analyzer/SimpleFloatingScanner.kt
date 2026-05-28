package com.example.inventorymaster.ui.analyzer

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.utils.RecognitionUtils
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun SimpleFloatingScanner(
    modifier: Modifier = Modifier,      // 让外部控制它的大小、形状（例如圆角）
    onScanResult: (String) -> Unit      // 扫码成功的回调
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 专用单线程，防止阻塞主线程
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 防止扫码成功后疯狂触发回调
    var isScanned by remember { mutableStateOf(false) }

    // 资源清理
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = modifier) {
        // 1. 相机预览画面
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // A. 预览 UseCase
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // B. 图像分析 UseCase
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(1280, 720)) // 保证医疗长条码的识别率
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isScanned) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            // 必须传入 rotationDegrees，否则 ML Kit 无法正确识别方向
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            // 完美复用你之前的 Utils 工具类
                            RecognitionUtils.recognizeQRCode(
                                image = inputImage,
                                onSuccess = { result ->
                                    isScanned = true
                                    onScanResult(result)
                                },
                                onFailure = { /* 静默失败，继续下一帧 */ },
                                onComplete = { imageProxy.close() } // 必须 close，否则卡死
                            )
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("SimpleScanner", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. 简单的中心瞄准框（不需要复杂的动画，仅用于视觉引导）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp) // 瞄准框大小
                .border(2.dp, Color.Green.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        )
    }
}