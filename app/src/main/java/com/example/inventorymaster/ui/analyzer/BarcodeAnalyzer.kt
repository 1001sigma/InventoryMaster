package com.example.inventorymaster.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// --- 补回你需要的数据模型 ---
data class DetectedBarcode(
    val rawValue: String,
    val normX: Float, // 0.0 ~ 1.0
    val normY: Float  // 0.0 ~ 1.0
)

@ExperimentalGetImage
class BarcodeAnalyzer(
    private val isMultiMode: Boolean,
    private val scope: CoroutineScope,
    private val onScanResult: (String) -> Unit,
    private val onMultiScanResult: (Bitmap, List<DetectedBarcode>) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var targetRegion: Rect? = null

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_EAN_13
            ).build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty()) return@addOnSuccessListener

                val currentRegion = targetRegion
                // 过滤出在扫描框内的有效条码
                val validBarcodes = barcodes.filter { barcode ->
                    val box = barcode.boundingBox ?: return@filter false
                    barcode.rawValue?.isNotBlank() == true &&
                            (currentRegion == null || currentRegion.contains(box.centerX(), box.centerY()))
                }

                if (validBarcodes.isEmpty()) return@addOnSuccessListener

                if (isMultiMode && validBarcodes.size > 1) {
                    // 触发多码定格
                    handleMultiMode(validBarcodes, imageProxy)
                } else {
                    // 单码直接返回
                    onScanResult(validBarcodes.first().rawValue!!)
                }
            }
            .addOnFailureListener { Log.e("BarcodeAnalyzer", "识别失败", it) }
            .addOnCompleteListener { imageProxy.close() }
    }


    private fun handleMultiMode(barcodes: List<Barcode>, imageProxy: ImageProxy) {
        // CameraX 的 toBitmap 会自动处理好旋转，拿到的就是竖正的图！
        val bitmap = imageProxy.toBitmap() ?: return
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        val detectedList = barcodes.mapNotNull { barcode ->
            val raw = barcode.rawValue ?: return@mapNotNull null
            val box = barcode.boundingBox ?: return@mapNotNull null
            // 因为 bitmap 和 box 都是转正的，直接相除就是归一化坐标，告别矩阵计算！
            DetectedBarcode(raw, box.centerX() / imgW, box.centerY() / imgH)
        }

        scope.launch(Dispatchers.Main) {
            onMultiScanResult(bitmap, detectedList)
        }
    }
}