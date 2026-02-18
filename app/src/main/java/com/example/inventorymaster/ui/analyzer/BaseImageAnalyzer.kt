package com.example.inventorymaster.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

/**
 * 基础图像分析器
 * 作用：封装 CameraX 的 ImageAnalysis.Analyzer 接口，处理通用的图像转换逻辑。
 * 子类只需要关心拿到 InputImage 后的业务逻辑，不需要关心底层转换。
 */
abstract class BaseImageAnalyzer : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        // 1. 安全检查：如果相机传来的图是空的，直接关闭并返回，防止崩溃
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            // 2. 统一转换：将 CameraX 的 Image 转换为 ML Kit 需要的 InputImage
            // rotationDegrees 是矫正方向的关键
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            // 3. 将处理权交给子类 (例如 BarcodeAnalyzer 或 TextAnalyzer)
            // 注意：我们将 imageProxy 也传过去，因为子类可能需要异步处理（比如多码定格），
            // 需要子类自己决定什么时候调用 imageProxy.close()
            process(inputImage, imageProxy)

        } catch (e: Exception) {
            // 4. 兜底容错：如果处理过程发生任何异常，确保关闭流，防止相机卡死
            e.printStackTrace()
            imageProxy.close()
        }
    }

    /**
     * 抽象方法：子类必须实现这个方法来处理具体的识别逻辑
     * @param image ML Kit 准备好的输入图像
     * @param imageProxy 原始图像代理 (处理完必须手动调用 .close())
     */
    abstract fun process(image: InputImage, imageProxy: ImageProxy)

    // --- 通用工具方法：供子类使用 ---

    /**
     * 辅助方法：将 ImageProxy 转为 Bitmap (用于多码定格时的截图)
     * 包含旋转矫正逻辑
     */
    protected fun ImageProxy.toBitmapWithRotation(): Bitmap? {
        val bitmap = this.toBitmap() ?: return null
        val rotation = this.imageInfo.rotationDegrees.toFloat()

        return if (rotation != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotation)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
}