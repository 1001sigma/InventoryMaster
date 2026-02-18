package com.example.inventorymaster.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// --- 数据模型 ---
data class DetectedBarcode(
    val rawValue: String,
    val normX: Float, // 0.0 ~ 1.0 (中心点 X)
    val normY: Float  // 0.0 ~ 1.0 (中心点 Y)
)

/**
 * 条码分析器 (核心逻辑)
 * 包含：医疗条码优化、多码模式、中心优先算法
 */
class BarcodeAnalyzer(
    private val isMultiMode: Boolean,       // 开关：是否允许多码定格
    private val scope: CoroutineScope,      // 协程作用域
    private val onScanResult: (String) -> Unit,                  // 单码回调
    private val onMultiScanResult: (Bitmap, List<DetectedBarcode>) -> Unit // 多码回调
) : BaseImageAnalyzer() {

    // 1. 初始化扫描器 (保留之前的优化)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,      // 重点：医疗条码 GS1-128
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_DATA_MATRIX,   // 重点：医疗器械 UDI
                Barcode.FORMAT_CODE_39
            ).build()
    )

    override fun process(image: InputImage, imageProxy: ImageProxy) {
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // A. 基础过滤
                if (barcodes.isEmpty()) {
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val validBarcodes = barcodes.filter { !it.rawValue.isNullOrBlank() }
                if (validBarcodes.isEmpty()) {
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                // B. 分流逻辑
                // 只有在【开关打开】且【数量大于1】时，才进入多码选择
                if (isMultiMode && validBarcodes.size > 1) {
                    handleMultiMode(validBarcodes, imageProxy)
                } else {
                    // === 单码模式 / 只有一个码 ===
                    // 【关键修改】不取 first()，而是取离屏幕中心最近的那个
                    val bestBarcode = getCenterBarcode(validBarcodes, imageProxy.width, imageProxy.height)

                    if (bestBarcode?.rawValue != null) {
                        onScanResult(bestBarcode.rawValue!!)
                    }
                    imageProxy.close()
                }
            }
            .addOnFailureListener {
                Log.e("BarcodeAnalyzer", "Scan failed", it)
                imageProxy.close()
            }
    }

    /**
     * 【新算法】寻找离图片中心最近的条码
     * 原理：计算条码中心点与图片中心点的距离平方，取最小的那个。
     */
    private fun getCenterBarcode(barcodes: List<Barcode>, imgWidth: Int, imgHeight: Int): Barcode? {
        if (barcodes.isEmpty()) return null
        if (barcodes.size == 1) return barcodes[0]

        val cx = imgWidth / 2
        val cy = imgHeight / 2

        var minDistanceSq = Double.MAX_VALUE // 最小距离的平方
        var nearest: Barcode? = null

        for (code in barcodes) {
            val box = code.boundingBox ?: continue
            val bx = box.centerX()
            val by = box.centerY()

            // 计算距离平方 (不需要开根号，比较大小即可)
            // (x2-x1)^2 + (y2-y1)^2
            val distSq = (bx - cx).toDouble() * (bx - cx) + (by - cy).toDouble() * (by - cy)

            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                nearest = code
            }
        }
        return nearest
    }

    // --- 以下保持不变 (多码处理 & 坐标映射) ---

    private fun handleMultiMode(barcodes: List<Barcode>, imageProxy: ImageProxy) {
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = imageProxy.toBitmapWithRotation() ?: return@launch
                val width = imageProxy.width
                val height = imageProxy.height
                val rotation = imageProxy.imageInfo.rotationDegrees

                val detectedList = barcodes.mapNotNull { barcode ->
                    val raw = barcode.rawValue ?: return@mapNotNull null
                    val box = barcode.boundingBox ?: return@mapNotNull null
                    val (nx, ny) = mapBoundingBox(box, width, height, rotation)
                    DetectedBarcode(raw, nx, ny)
                }

                withContext(Dispatchers.Main) {
                    onMultiScanResult(bitmap, detectedList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun mapBoundingBox(
        box: Rect,
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: Int
    ): Pair<Float, Float> {
        val matrix = Matrix()
        when (rotation) {
            90 -> {
                matrix.postRotate(90f)
                matrix.postTranslate(sourceHeight.toFloat(), 0f)
            }
            180 -> {
                matrix.postRotate(180f)
                matrix.postTranslate(sourceWidth.toFloat(), sourceHeight.toFloat())
            }
            270 -> {
                matrix.postRotate(270f)
                matrix.postTranslate(0f, sourceWidth.toFloat())
            }
        }
        val srcRect = RectF(box)
        val dstRect = RectF()
        matrix.mapRect(dstRect, srcRect)

        val (rotatedWidth, rotatedHeight) = if (rotation == 90 || rotation == 270) {
            Pair(sourceHeight.toFloat(), sourceWidth.toFloat())
        } else {
            Pair(sourceWidth.toFloat(), sourceHeight.toFloat())
        }

        val normX = dstRect.centerX() / rotatedWidth
        val normY = dstRect.centerY() / rotatedHeight
        return Pair(normX, normY)
    }
}