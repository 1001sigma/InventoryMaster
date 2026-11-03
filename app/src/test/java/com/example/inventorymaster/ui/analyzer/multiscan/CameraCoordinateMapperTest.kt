package com.example.inventorymaster.ui.analyzer.multiscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCoordinateMapperTest {

    @Test
    fun testCenterMappingWithFillCenter() {
        // 模拟全面屏手机 20:9 (1080 x 2400) 与 1080p 相机流 (1080 x 1920)
        val viewWidth = 1080f
        val viewHeight = 2400f
        val imgWidth = 1080f
        val imgHeight = 1920f

        // 图像中心点 (540, 960) 映射后应当严格落在视图像素中心 (0.5, 0.5)
        val (normX, normY) = CameraCoordinateMapper.mapImagePointToViewNorm(
            imageX = 540f,
            imageY = 960f,
            imageWidth = imgWidth,
            imageHeight = imgHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )

        assertEquals("中心点 X 必须精准归一化为 0.5", 0.5f, normX, 0.0001f)
        assertEquals("中心点 Y 必须精准归一化为 0.5", 0.5f, normY, 0.0001f)
    }

    @Test
    fun testEdgeCroppingCalculation() {
        // view: 1080 x 2400. img: 1080 x 1920.
        // scale = max(1080/1080, 2400/1920) = 1.25.
        // scaledWidth = 1350, scaledHeight = 2400.
        // offsetX = (1080 - 1350) / 2 = -135.
        // 可见区域在图像水平像素上的左边界为 135 / 1.25 = 108.
        val viewWidth = 1080f
        val viewHeight = 2400f
        val imgWidth = 1080f
        val imgHeight = 1920f

        val (normLeft, _) = CameraCoordinateMapper.mapImagePointToViewNorm(
            imageX = 108f,
            imageY = 960f,
            imageWidth = imgWidth,
            imageHeight = imgHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )

        assertEquals("左裁剪边界像素在视图中应刚好为 0.0", 0.0f, normLeft, 0.0001f)

        // 右裁剪边界 1080 - 108 = 972
        val (normRight, _) = CameraCoordinateMapper.mapImagePointToViewNorm(
            imageX = 972f,
            imageY = 960f,
            imageWidth = imgWidth,
            imageHeight = imgHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )

        assertEquals("右裁剪边界像素在视图中应刚好为 1.0", 1.0f, normRight, 0.0001f)
    }

    @Test
    fun testClampingOutOfBounds() {
        val (normX, normY) = CameraCoordinateMapper.mapImagePointToViewNorm(
            imageX = 50f, // 已被裁剪到屏幕外左侧
            imageY = 960f,
            imageWidth = 1080f,
            imageHeight = 1920f,
            viewWidth = 1080f,
            viewHeight = 2400f
        )

        assertTrue("超出左边界的点应限制在 [0.0, 1.0] 内", normX >= 0f)
        assertEquals(0f, normX, 0.0001f)
    }
}
