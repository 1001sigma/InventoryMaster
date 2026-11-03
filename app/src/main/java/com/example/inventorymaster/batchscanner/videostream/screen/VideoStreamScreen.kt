package com.example.inventorymaster.batchscanner.videostream.screen

import android.net.Uri
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.videostream.models.RecordingState
import com.example.inventorymaster.batchscanner.videostream.models.VideoStreamUiState
import com.example.inventorymaster.batchscanner.videostream.overlay.VideoStreamOverlay
import com.example.inventorymaster.batchscanner.videostream.util.VideoRecordHelper
import com.example.inventorymaster.batchscanner.videostream.viewmodel.VideoStreamViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Composable
fun VideoStreamScreen(
    targetList: List<String>?,
    onComplete: (List<String>, Uri, List<GlobalBarcode>) -> Unit,
    onClose: () -> Unit,
    viewModel: VideoStreamViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val activeTrackers by viewModel.activeTrackers.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    // --- 帧处理并发控制 ---
    val frameMutex = remember { Mutex() }
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    val minFrameIntervalMs = 66L

    LaunchedEffect(targetList) {
        viewModel.initTargetList(targetList)
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    val videoRecordHelper = remember { VideoRecordHelper(context) }
    var videoCaptureFailed by remember { mutableStateOf(false) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        viewModel.videoRecordHelper = videoRecordHelper
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // 绑定 CameraX
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProviderRef = provider

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            previewViewRef?.let { pv ->
                preview.setSurfaceProvider(pv.surfaceProvider)
            }

            val videoCapture = videoRecordHelper.createVideoCapture()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.unbindAll()

            // 设置 analyzer
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                // FPS 节流
                val now = System.currentTimeMillis()
                if (now - lastAnalysisTime < minFrameIntervalMs) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastAnalysisTime = now

                if (!isAnalyzing) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                // Mutex 串行化
                if (frameMutex.tryLock()) {
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            val detections = viewModel.processFrame(imageProxy)
                            if (detections.isNotEmpty()) {
                                viewModel.onFrameDetected(detections)
                            }
                        } finally {
                            frameMutex.unlock()
                        }
                    }
                } else {
                    imageProxy.close()
                }
            }

            try {
                provider.bindToLifecycle(
                    lifecycleOwner, cameraSelector,
                    preview, imageAnalysis, videoCapture
                )
            } catch (exc: IllegalArgumentException) {
                videoCaptureFailed = true
                viewModel.onRecordingUnavailable()
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner, cameraSelector,
                        preview, imageAnalysis
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "相机绑定失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            imageAnalysis.clearAnalyzer()
            cameraProviderRef?.let { provider ->
                try { provider.unbindAll() } catch (_: Exception) {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 底层：相机预览
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 中层：AI 检测框叠加（新版 Overlay）
        val session = viewModel.getSession()
        VideoStreamOverlay(
            trackers = activeTrackers,
            gridCells = session?.gridCells ?: emptyMap(),
            gridCols = session?.config?.gridCols ?: 8,
            gridRows = session?.config?.gridRows ?: 12,
            direction = session?.currentDirection ?: com.example.inventorymaster.batchscanner.videostream.models.ScanDirection.IDLE,
            coveragePercent = session?.statistics?.coveragePercent ?: 0f,
            modifier = Modifier.fillMaxSize()
        )

        // 顶层：控制按钮

        // 关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }

        // 录像指示器
        if (recordingState.isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
                Text("REC", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        // 实时统计标签
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val stats = session?.statistics
            Text(
                text = "已识别 ${stats?.totalCount ?: 0} 个 | 覆盖 ${String.format("%.0f", stats?.coveragePercent ?: 0f)}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // 底部控制按钮
        when (uiState) {
            is VideoStreamUiState.Idle -> {
                Button(
                    onClick = {
                        viewModel.startStreaming()
                        if (!videoCaptureFailed) {
                            coroutineScope.launch(Dispatchers.IO) {
                                videoRecordHelper.startRecording(
                                    onRecordStarted = {
                                        viewModel.updateRecordingState(
                                            RecordingState(isRecording = true)
                                        )
                                    },
                                    onRecordFinalized = { uri ->
                                        viewModel.updateRecordingState(
                                            RecordingState(isRecording = false, videoUri = uri)
                                        )
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "开始采集",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            is VideoStreamUiState.Streaming -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.pause()
                            if (!videoCaptureFailed) {
                                videoRecordHelper.stopRecording()
                            }
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "暂停",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            is VideoStreamUiState.Paused -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重来")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.resume()
                            if (!videoCaptureFailed) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    videoRecordHelper.startRecording(
                                        onRecordStarted = {
                                            viewModel.updateRecordingState(
                                                RecordingState(isRecording = true)
                                            )
                                        },
                                        onRecordFinalized = { uri ->
                                            viewModel.updateRecordingState(
                                                RecordingState(isRecording = false, videoUri = uri)
                                            )
                                        }
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("继续")
                    }

                    Button(
                        onClick = {
                            val pausedState = uiState as? VideoStreamUiState.Paused
                            val barcodes = pausedState?.detections ?: emptyList()
                            val barcodeStrings = barcodes.map { it.displayValue }
                            val uriToSubmit = recordingState.videoUri ?: Uri.EMPTY
                            onComplete(barcodeStrings, uriToSubmit, barcodes)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("确认提交")
                    }
                }
            }
        }
    }
}
