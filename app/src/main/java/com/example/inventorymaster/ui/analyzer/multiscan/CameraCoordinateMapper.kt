package com.example.inventorymaster.ui.analyzer.multiscan

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 相机画面与 PreviewView 视图之间的坐标映射工具
 *
 * 专门处理 CameraX FILL_CENTER 居中裁剪模式下的坐标变换，
 * 将 ML Kit 检测到的条码（在旋转后的正向图像空间）像素坐标精准映射为 PreviewView 视图像素归一化坐标 (0.0 ~ 1.0)。
 */
object CameraCoordinateMapper {

    /**
     * 将 ML Kit 条码的中心点反算为 PreviewView 视图归一化坐标 (0.0 ~ 1.0)
     *
     * @param barcode ML Kit 识别结果
     * @param imageWidth 旋转后的图像正向宽度 (如 1080)
     * @param imageHeight 旋转后的图像正向高度 (如 1920)
     * @param viewWidth PreviewView 容器宽度 (如 1080)
     * @param viewHeight PreviewView 容器高度 (如 2400)
     * @return 映射后的 DetectedBarcode，若参数无效或 rawValue 为空则返回 null
     */
    fun mapBarcodeToDetected(
        barcode: Barcode,
        imageWidth: Float,
        imageHeight: Float,
        viewWidth: Float,
        viewHeight: Float
    ): DetectedBarcode? {
        val raw = barcode.rawValue ?: return null
        if (raw.isBlank()) return null

        val (imgCx, imgCy) = getBarcodeCenter(barcode) ?: return null

        val (normX, normY) = mapImagePointToViewNorm(
            imageX = imgCx,
            imageY = imgCy,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )

        return DetectedBarcode(
            rawValue = raw,
            normX = normX,
            normY = normY
        )
    }

    /**
     * 提取条码中心点（优先使用四角点平均值，保证四边形/倾斜 DataMatrix 绝对居中；兜底用 boundingBox）
     */
    fun getBarcodeCenter(barcode: Barcode): Pair<Float, Float>? {
        val corners = barcode.cornerPoints
        return if (corners != null && corners.size == 4) {
            val cx = (corners[0].x + corners[1].x + corners[2].x + corners[3].x) / 4f
            val cy = (corners[0].y + corners[1].y + corners[2].y + corners[3].y) / 4f
            cx to cy
        } else {
            val box = barcode.boundingBox ?: return null
            box.centerX().toFloat() to box.centerY().toFloat()
        }
    }

    /**
     * 计算点在 PreviewView (FILL_CENTER) 中的归一化坐标
     *
     * @param imageX 图像像素空间 X
     * @param imageY 图像像素空间 Y
     * @param imageWidth 图像正向宽
     * @param imageHeight 图像正向高
     * @param viewWidth 视图实际宽
     * @param viewHeight 视图实际高
     * @return 视图像素空间归一化坐标 Pair(normX, normY)
     */
    fun mapImagePointToViewNorm(
        imageX: Float,
        imageY: Float,
        imageWidth: Float,
        imageHeight: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Pair<Float, Float> {
        if (viewWidth <= 0f || viewHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
            val fallbackX = if (imageWidth > 0f) (imageX / imageWidth).coerceIn(0f, 1f) else 0.5f
            val fallbackY = if (imageHeight > 0f) (imageY / imageHeight).coerceIn(0f, 1f) else 0.5f
            return fallbackX to fallbackY
        }

        val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        val viewX = imageX * scale + offsetX
        val viewY = imageY * scale + offsetY

        val normX = (viewX / viewWidth).coerceIn(0f, 1f)
        val normY = (viewY / viewHeight).coerceIn(0f, 1f)

        return normX to normY
    }
}
