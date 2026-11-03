package com.example.inventorymaster.ui.analyzer

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.inventorymaster.batchscanner.task.tracking.CodeTracker
import com.example.inventorymaster.batchscanner.task.tracking.Detection
import com.example.inventorymaster.utils.BarcodeColorHasher
import com.example.inventorymaster.utils.Decoder
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 扫码命中高亮：保持时长 + 淡出时长（总可见 ≈ 450ms，0.5s 以内） */
private const val HIGHLIGHT_HOLD_MS = 250
private const val HIGHLIGHT_FADE_MS = 200

/** 扫码命中的高亮数据：码框（视图像素坐标）+ 命中时刻（作唯一键，同码重扫也能重新触发动画） */
private data class HighlightBox(val rect: Rect, val startMs: Long, val color: Color = Color(0xFF4CAF50))

/**
 * 把 ML Kit 的码框（upright 图像坐标）反算回预览视图坐标：
 * 预览为 FILL_CENTER（等比放大 + 居中裁剪），scale = max(视图/图像)，减去被裁掉的边缘
 */
private fun mapImageRectToView(bb: Rect, imgW: Int, imgH: Int, viewW: Int, viewH: Int): Rect {
    if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0 || bb.isEmpty) return Rect()
    val scale = maxOf(viewW.toFloat() / imgW, viewH.toFloat() / imgH)
    val offsetX = (imgW * scale - viewW) / 2f
    val offsetY = (imgH * scale - viewH) / 2f
    val left = (bb.left * scale - offsetX).roundToInt().coerceIn(0, viewW)
    val top = (bb.top * scale - offsetY).roundToInt().coerceIn(0, viewH)
    val right = (bb.right * scale - offsetX).roundToInt().coerceIn(0, viewW)
    val bottom = (bb.bottom * scale - offsetY).roundToInt().coerceIn(0, viewH)
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else Rect()
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun SimpleFloatingScanner(
    modifier: Modifier = Modifier,      // 让外部控制它的大小、形状（例如圆角）
    onScanResult: (String) -> Unit,     // 扫码成功的回调
    isPaused: () -> Boolean = { false },// 暂停帧分析（持续扫描模式由调用方控制启停）；lambda 形式保证实时读取
    stopAfterFirst: Boolean = true,     // 扫到第一个码后是否自动停止（连续扫描模式传 false）
    scanFrameRatio: Float = 0f,         // 扫码框边长占比（相对视图短边）：0=关闭（全画面识别）；>0 显示白色四角括号，且只识别完整落在框内的码
    tracker: CodeTracker? = null        // 动态多目标跟踪器：非空时启用多目标物理轨迹跟踪与去重
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scannerScope = rememberCoroutineScope()

    // 专用单线程，防止阻塞主线程
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 专属 ML Kit 解码器（单例复用，限定码制，页面销毁时 close）
    val barcodeScanner = remember(tracker != null) {
        val builder = BarcodeScannerOptions.Builder()
        if (tracker != null) {
            builder.setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_CODE_128)
        } else {
            builder.setBarcodeFormats(
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
        }
        BarcodeScanning.getClient(builder.build())
    }
    DisposableEffect(barcodeScanner) {
        onDispose { barcodeScanner.close() }
    }

    // 防止扫码成功后疯狂触发回调
    var isScanned by remember { mutableStateOf(false) }

    // 自身视图尺寸（像素）：扫码框绘制与"框内才识别"的区域映射共用此基准，保证所见即所扫
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 正向（upright）图像尺寸记录，供跟踪命中反算高亮框使用
    var lastUprightW by remember { mutableIntStateOf(0) }
    var lastUprightH by remember { mutableIntStateOf(0) }

    // 相机控制句柄：bind 成功后赋值，供周期辅助对焦使用
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    // 最近一次扫码命中时刻：3s 内扫得动就不打扰自动对焦
    val lastHitAt = remember { mutableLongStateOf(0L) }

    // 扫码命中高亮：绿色 30% 覆盖码位置，保持 250ms 后 200ms 淡出
    var scanHighlight by remember { mutableStateOf<HighlightBox?>(null) }
    val highlightAlpha = remember { Animatable(1f) }
    LaunchedEffect(scanHighlight) {
        if (scanHighlight != null) {
            highlightAlpha.snapTo(1f)
            highlightAlpha.animateTo(
                0f,
                tween(durationMillis = HIGHLIGHT_FADE_MS, delayMillis = HIGHLIGHT_HOLD_MS, easing = LinearEasing)
            )
            scanHighlight = null
        }
    }

    // 监听 Tracker 确认计数：联动命中高亮与单次自动停止
    DisposableEffect(tracker) {
        tracker?.onCountedListener = { track ->
            val halfSize = (track.size / 2f).roundToInt()
            val imgRect = Rect(
                (track.cx - halfSize).roundToInt(),
                (track.cy - halfSize).roundToInt(),
                (track.cx + halfSize).roundToInt(),
                (track.cy + halfSize).roundToInt()
            )
            val w = lastUprightW
            val h = lastUprightH
            val vs = viewSize
            if (w > 0 && h > 0 && vs != IntSize.Zero) {
                val viewRect = mapImageRectToView(imgRect, w, h, vs.width, vs.height)
                if (!viewRect.isEmpty) {
                    val color = BarcodeColorHasher.getColor(track.value)
                    scannerScope.launch(Dispatchers.Main) {
                        scanHighlight = HighlightBox(viewRect, SystemClock.elapsedRealtime(), color)
                    }
                }
            }
            lastHitAt.longValue = SystemClock.elapsedRealtime()
            if (stopAfterFirst) {
                isScanned = true
            }
        }
        onDispose {
            tracker?.onCountedListener = null
        }
    }

    // 周期辅助对焦：近距小码时镜头 AF 容易反复拉风箱、拉焦期间读不出码。仅当扫描
    // 进行中（未暂停/未扫完）且最近 3s 无命中才推一把中心对焦（3s 自动取消）——
    // 正常扫得动就完全不打扰 AF，卡住了才自救。写法抄自 BatchScannerScreen 的点击对焦
    LaunchedEffect(cameraControl) {
        while (true) {
            if (cameraControl != null && !isPaused() && !isScanned &&
                SystemClock.elapsedRealtime() - lastHitAt.longValue >= 3000L
            ) {
                val w = viewSize.width
                val h = viewSize.height
                if (w > 0 && h > 0) {
                    val factory = SurfaceOrientedMeteringPointFactory(w.toFloat(), h.toFloat())
                    val center = factory.createPoint(w / 2f, h / 2f)
                    cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                    )
                }
            }
            delay(3000L)
        }
    }

    // 资源清理
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = modifier.onSizeChanged { viewSize = it }) {
        // 1. 相机预览画面
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
                        // 1080p：小 DataMatrix（医疗 UDI 主力码制）在 720p 下模块太细、
                        // 命中率低是扫码慢的主因；KEEP_ONLY_LATEST 保证高分辨率帧不积压
                        .setTargetResolution(android.util.Size(1920, 1080))
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if ((stopAfterFirst && isScanned) || isPaused()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val ts = System.nanoTime()
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        try {
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                            val barcodes = Tasks.await(barcodeScanner.process(inputImage))

                            // upright（已旋转）图像尺寸：扫码框过滤与命中高亮的坐标换算共用
                            val isRotated = rotation == 90 || rotation == 270
                            val crop = imageProxy.cropRect
                            val uprightW = if (isRotated) crop.height() else crop.width()
                            val uprightH = if (isRotated) crop.width() else crop.height()
                            lastUprightW = uprightW
                            lastUprightH = uprightH

                            // 扫码框模式：只保留完整落在框内的码。
                            val filteredBarcodes = if (scanFrameRatio > 0f && viewSize != IntSize.Zero) {
                                val side = scanFrameRatio * minOf(viewSize.width, viewSize.height)
                                val region = ScanRegionMapper.getMappedRect(
                                    frameWidthPx = side,
                                    frameHeightPx = side,
                                    viewWidthPx = viewSize.width.toFloat(),
                                    viewHeightPx = viewSize.height.toFloat(),
                                    imageWidth = uprightW,
                                    imageHeight = uprightH
                                )
                                // 容忍 2px 边缘取整误差
                                val tol = Rect(
                                    region.left - 2, region.top - 2,
                                    region.right + 2, region.bottom + 2
                                )
                                barcodes.filter { bc ->
                                    val bb = bc.boundingBox
                                    bb != null && tol.contains(bb.left, bb.top) && tol.contains(bb.right, bb.bottom)
                                }
                            } else barcodes

                            if (tracker != null) {
                                val detections = filteredBarcodes.mapNotNull { bc ->
                                    val raw = bc.rawValue ?: return@mapNotNull null
                                    if (raw.isBlank()) return@mapNotNull null
                                    val corners = bc.cornerPoints
                                    if (corners != null && corners.size == 4) {
                                        val cx = (corners[0].x + corners[1].x + corners[2].x + corners[3].x) / 4f
                                        val cy = (corners[0].y + corners[1].y + corners[2].y + corners[3].y) / 4f
                                        val d01 = hypot((corners[1].x - corners[0].x).toDouble(), (corners[1].y - corners[0].y).toDouble()).toFloat()
                                        val d12 = hypot((corners[2].x - corners[1].x).toDouble(), (corners[2].y - corners[1].y).toDouble()).toFloat()
                                        val d23 = hypot((corners[3].x - corners[2].x).toDouble(), (corners[3].y - corners[2].y).toDouble()).toFloat()
                                        val d30 = hypot((corners[0].x - corners[3].x).toDouble(), (corners[0].y - corners[3].y).toDouble()).toFloat()
                                        val size = (d01 + d12 + d23 + d30) / 4f
                                        Detection(raw, cx, cy, size)
                                    } else {
                                        val bb = bc.boundingBox ?: return@mapNotNull null
                                        Detection(raw, bb.centerX().toFloat(), bb.centerY().toFloat(), (bb.width() + bb.height()) / 2f)
                                    }
                                }
                                tracker.update(ts, detections)
                            } else {
                                val accepted = filteredBarcodes.firstOrNull { it.rawValue != null }
                                val value = accepted?.rawValue
                                if (accepted != null && value != null && !isScanned && !isPaused()) {
                                    accepted.boundingBox?.let { bb ->
                                        val viewRect = mapImageRectToView(
                                            bb, uprightW, uprightH, viewSize.width, viewSize.height
                                        )
                                        if (!viewRect.isEmpty) {
                                            val color = BarcodeColorHasher.getColor(value)
                                            scannerScope.launch(Dispatchers.Main) {
                                                scanHighlight = HighlightBox(viewRect, SystemClock.elapsedRealtime(), color)
                                            }
                                        }
                                    }
                                    if (stopAfterFirst) {
                                        isScanned = true
                                    }
                                    lastHitAt.longValue = SystemClock.elapsedRealtime()
                                    scannerScope.launch(Dispatchers.Main) {
                                        onScanResult(value)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            if (t !is CancellationException) {
                                Log.e("SimpleScanner", "帧解码失败", t)
                            }
                        } finally {
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
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("SimpleScanner", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. 扫码框：居中正方形 + 白色四角 L 形括号（scanFrameRatio>0 时显示）
        // 括号区域与解码过滤区域使用同一公式（短边 × scanFrameRatio，居中），所见即所扫
        if (scanFrameRatio > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val side = scanFrameRatio * minOf(size.width, size.height)
                if (side <= 0f) return@Canvas
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                val right = left + side
                val bottom = top + side
                val corner = 22.dp.toPx()
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val color = Color.White.copy(alpha = 0.9f)
                // 左上
                drawLine(color, Offset(left, top), Offset(left + corner, top), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(color, Offset(left, top), Offset(left, top + corner), strokeWidth = stroke.width, cap = stroke.cap)
                // 右上
                drawLine(color, Offset(right, top), Offset(right - corner, top), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(color, Offset(right, top), Offset(right, top + corner), strokeWidth = stroke.width, cap = stroke.cap)
                // 左下
                drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), strokeWidth = stroke.width, cap = stroke.cap)
                // 右下
                drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), strokeWidth = stroke.width, cap = stroke.cap)
                drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), strokeWidth = stroke.width, cap = stroke.cap)
            }
        }

        // 3. 扫码命中高亮：绿色 30% 填充 + 2dp 描边盖住码位置，保持后淡出
        scanHighlight?.let { h ->
            Canvas(modifier = Modifier.matchParentSize()) {
                if (h.rect.isEmpty) return@Canvas
                val a = highlightAlpha.value.coerceIn(0f, 1f)
                if (a <= 0f) return@Canvas
                val topLeft = Offset(h.rect.left.toFloat(), h.rect.top.toFloat())
                val boxSize = Size(h.rect.width().toFloat(), h.rect.height().toFloat())
                val radius = CornerRadius(4.dp.toPx())
                drawRoundRect(
                    color = h.color.copy(alpha = 0.35f * a),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = h.color.copy(alpha = 0.9f * a),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = radius,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}
