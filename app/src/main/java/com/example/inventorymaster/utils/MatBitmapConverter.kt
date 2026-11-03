package com.example.inventorymaster.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Mat ↔ Bitmap 高效转换工具
 * 使用 CvType.CV_8UC4 完美映射 ARGB_8888
 */
object MatBitmapConverter {

    /**
     * Bitmap → Mat
     * 注意：调用方需负责释放返回的 Mat
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Mat → Bitmap
     * 注意：返回的 Bitmap 需要调用方 recycle
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(
            mat.cols(),
            mat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    /**
     * 创建缩略图用于快速定位
     * @param maxWidth 缩略图最大宽度
     */
    fun createThumbnail(bitmap: Bitmap, maxWidth: Int = 1080): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val scale = maxWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }
}
