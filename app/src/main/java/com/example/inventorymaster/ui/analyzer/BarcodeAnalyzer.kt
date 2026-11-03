package com.example.inventorymaster.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.inventorymaster.ui.analyzer.multiscan.AggregationDecision
import com.example.inventorymaster.ui.analyzer.multiscan.CameraCoordinateMapper
import com.example.inventorymaster.ui.analyzer.multiscan.DetectedBarcode
import com.example.inventorymaster.ui.analyzer.multiscan.MultiBarcodeAggregator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalGetImage
class BarcodeAnalyzer(
    private val isMultiMode: Boolean,
    private val scope: CoroutineScope,
    private val getPreviewBitmap: () -> Bitmap?,
    private val onScanResult: (String) -> Unit,
    private val onMultiScanResult: (Bitmap, List<DetectedBarcode>) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var targetRegion: Rect? = null

    @Volatile
    var viewWidth: Float = 0f

    @Volatile
    var viewHeight: Float = 0f

    @Volatile
    var imageWidth: Float = 0f

    @Volatile
    var imageHeight: Float = 0f

    // 独立解耦的跨帧多码聚合器（具备手持位移防抖判定与跨帧拼图能力）
    private val aggregator = MultiBarcodeAggregator()

    // 专用 ML Kit 解码器：限定码制，进程/生命周期内单例复用
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // 同步在后台单线程中解码
            val barcodes = Tasks.await(barcodeScanner.process(image))
            val currentRegion = targetRegion

            // 过滤在有效区域内的条码
            val validBarcodes = barcodes.filter { barcode ->
                val box = barcode.boundingBox ?: return@filter false
                val raw = barcode.rawValue
                !raw.isNullOrBlank() && (currentRegion == null || currentRegion.contains(box.centerX(), box.centerY()))
            }

            if (!isMultiMode) {
                // ==================== 非多码模式：保持单码 0 延迟即扫即返 ====================
                if (validBarcodes.isNotEmpty()) {
                    val accepted = validBarcodes.first().rawValue
                    if (!accepted.isNullOrBlank()) {
                        scope.launch(Dispatchers.Main) {
                            onScanResult(accepted)
                        }
                    }
                }
            } else {
                // ==================== 多码选择模式：由 MultiBarcodeAggregator 驱动 ====================
                val vW = viewWidth
                val vH = viewHeight
                val iW = imageWidth
                val iH = imageHeight

                // 借助 CameraCoordinateMapper 进行高精度反算（居中裁剪逆映射）
                val detectedList = validBarcodes.mapNotNull { bc ->
                    CameraCoordinateMapper.mapBarcodeToDetected(
                        barcode = bc,
                        imageWidth = iW,
                        imageHeight = iH,
                        viewWidth = vW,
                        viewHeight = vH
                    )
                }

                when (val decision = aggregator.update(detectedList)) {
                    is AggregationDecision.Idle -> {
                        // 尚在手持移动或收集阶段，静默收集更新
                    }
                    is AggregationDecision.Freeze -> {
                        // 稳定捕获多码，触发定格
                        dispatchMultiMode(decision.barcodes, imageProxy)
                    }
                    is AggregationDecision.SingleScan -> {
                        // 确认镜头内仅有单码并稳定对准，按单码即刻返回
                        scope.launch(Dispatchers.Main) {
                            onScanResult(decision.rawValue)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BarcodeAnalyzer", "条码分析异常", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun dispatchMultiMode(detectedList: List<DetectedBarcode>, imageProxy: ImageProxy) {
        if (detectedList.isEmpty()) return

        val vW = viewWidth
        val vH = viewHeight
        // 若当前未获取到有效视图尺寸，提前在当前线程生成兜底图
        val fallbackBitmap = if (vW <= 0f || vH <= 0f) imageProxy.toBitmap() else null

        scope.launch(Dispatchers.Main) {
            // 优先抓取 PreviewView 视图当前呈现的 1:1 帧（无黑边、无跳帧）；若不可用再用兜底图
            val previewBitmap = getPreviewBitmap()
            val bitmap = previewBitmap ?: fallbackBitmap
            if (previewBitmap != null) {
                fallbackBitmap?.recycle()
            }
            if (bitmap != null) {
                onMultiScanResult(bitmap, detectedList)
            }
        }
    }

    fun resetAggregator() {
        aggregator.reset()
    }

    fun close() {
        try {
            barcodeScanner.close()
        } catch (_: Exception) {}
    }
}