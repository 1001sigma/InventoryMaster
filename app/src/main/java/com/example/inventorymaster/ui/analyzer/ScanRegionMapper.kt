package com.example.inventorymaster.ui.analyzer

import android.graphics.Rect

/**
 * 极简版坐标映射器：专门用于将 UI 扫描框坐标映射到底层相机图像坐标
 * (目前仅用于实时条码扫描 BarcodeAnalyzer)
 */
object ScanRegionMapper {

    fun getMappedRect(
        frameWidthPx: Float,
        frameHeightPx: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
        imageWidth: Int,
        imageHeight: Int
    ): Rect {
        // 防止除数为 0 或未初始化的情况，退回全图
        if (viewWidthPx <= 0f || viewHeightPx <= 0f || imageWidth <= 0 || imageHeight <= 0) {
            return Rect(0, 0, imageWidth, imageHeight)
        }

        // 1. CameraX 默认使用 FILL_CENTER，计算真实的缩放比例
        val scale = maxOf(viewWidthPx / imageWidth, viewHeightPx / imageHeight)

        // 2. 图像被拉伸放大后的实际尺寸
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // 3. 计算因为 FILL_CENTER 居中裁剪，导致屏幕边缘被裁掉了多少物理像素
        val offsetX = (scaledImageWidth - viewWidthPx) / 2f
        val offsetY = (scaledImageHeight - viewHeightPx) / 2f

        // 4. UI 扫描框在屏幕上的绝对位置 (居中)
        val screenLeft = (viewWidthPx - frameWidthPx) / 2f
        val screenTop = (viewHeightPx - frameHeightPx) / 2f
        val screenRight = screenLeft + frameWidthPx
        val screenBottom = screenTop + frameHeightPx

        // 5. 核心还原：屏幕坐标 + 偏移量，再除以缩放比例，还原回相机的真实物理坐标
        val imageLeft = ((screenLeft + offsetX) / scale).toInt()
        val imageTop = ((screenTop + offsetY) / scale).toInt()
        val imageRight = ((screenRight + offsetX) / scale).toInt()
        val imageBottom = ((screenBottom + offsetY) / scale).toInt()

        // 限制边界，防止溢出导致的崩溃
        return Rect(
            imageLeft.coerceIn(0, imageWidth),
            imageTop.coerceIn(0, imageHeight),
            imageRight.coerceIn(0, imageWidth),
            imageBottom.coerceIn(0, imageHeight)
        )
    }
}