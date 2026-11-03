package com.example.inventorymaster.batchscanner.videostream.util

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.example.inventorymaster.batchscanner.videostream.models.FrameDetection
import com.example.inventorymaster.utils.Decoder
import com.google.mlkit.vision.common.InputImage

/**
 * 帧处理接口：方便后续替换为 ONNX/YOLO 等自定义引擎
 */
interface FrameProcessor {
    /**
     * 处理单帧。内部会在 finally 中 close imageProxy，调用方无需手动关闭。
     */
    suspend fun processFrame(imageProxy: ImageProxy): List<FrameDetection>
    fun close()
}

/**
 * MLKit 帧处理器实现
 * 委托全局统一解码门面 [Decoder]（仅 ML Kit，实时帧不跑 libdmtx 兜底）
 */
class MLKitFrameProcessor : FrameProcessor {
    override suspend fun processFrame(imageProxy: ImageProxy): List<FrameDetection> {
        val mediaImage = imageProxy.image ?: return emptyList()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        return try {
            Decoder.decodeFrameAwait(inputImage).map { barcode ->
                FrameDetection(
                    displayValue = barcode.rawValue ?: "",
                    boundingBox = barcode.boundingBox ?: Rect(0, 0, 0, 0),
                    confidence = 1.0f,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            imageProxy.close()
        }
    }

    override fun close() {
        // 解码器由全局 Decoder 单例持有（进程级），此处保留空实现兼容接口
    }
}
