package com.example.inventorymaster.batchscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale

@Composable
fun ReviewCanvasOverlay(
    imageBitmap: ImageBitmap,
    barcodes: List<GlobalBarcode>,
    modifier: Modifier = Modifier
) {
    Image(
        bitmap = imageBitmap,
        contentDescription = "Review Image",
        // Fit 模式保证图片完整显示在屏幕内，不会被裁剪
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                // 1. 先把底图画出来
                drawContent()

                // 2. 计算 ContentScale.Fit 导致的实际缩放与偏移量
                val imageWidth = imageBitmap.width.toFloat()
                val imageHeight = imageBitmap.height.toFloat()
                val canvasWidth = size.width
                val canvasHeight = size.height

                val imageAspectRatio = imageWidth / imageHeight
                val canvasAspectRatio = canvasWidth / canvasHeight

                var drawWidth = canvasWidth
                var drawHeight = canvasHeight
                var leftOffset = 0f
                var topOffset = 0f

                // 核心算法：计算图片的实际渲染区域和留白边距
                if (imageAspectRatio > canvasAspectRatio) {
                    // 图片比屏幕宽：上下有黑边
                    drawHeight = canvasWidth / imageAspectRatio
                    topOffset = (canvasHeight - drawHeight) / 2f
                } else {
                    // 图片比屏幕高：左右有黑边
                    drawWidth = canvasHeight * imageAspectRatio
                    leftOffset = (canvasWidth - drawWidth) / 2f
                }

                val scaleX = drawWidth / imageWidth
                val scaleY = drawHeight / imageHeight

                // 3. 遍历所有条码，将原图坐标转换为屏幕坐标并绘制
                barcodes.forEach { barcode ->
                    val box = barcode.globalBoundingBox

                    val mappedLeft = leftOffset + box.left * scaleX
                    val mappedTop = topOffset + box.top * scaleY
                    val mappedRight = leftOffset + box.right * scaleX
                    val mappedBottom = topOffset + box.bottom * scaleY

                    val rectWidth = mappedRight - mappedLeft
                    val rectHeight = mappedBottom - mappedTop

                    // 根据匹配状态选择颜色：匹配成功(绿)，多出异常(红)
                    val strokeColor = if (barcode.status == ScanStatus.MATCHED) {
                        Color(0xFF00FF00) // 鲜绿色
                    } else {
                        Color(0xFFFF0000) // 鲜红色
                    }

                    // 绘制外边框
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(rectWidth, rectHeight),
                        style = Stroke(width = 6f) // 边框粗细
                    )

                    // 绘制状态图标 (右上角的 ✔ 或 ✘)
                    val iconSize = 40f
                    val iconPadding = 10f
                    val iconX = mappedRight - iconSize - iconPadding
                    val iconY = mappedTop + iconPadding

                    drawStatusIcon(
                        status = barcode.status,
                        color = strokeColor,
                        topLeft = Offset(iconX, iconY),
                        size = iconSize
                    )
                }
            }
    )
}

// 辅助方法：绘制简单的打勾和打叉图案
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStatusIcon(
    status: ScanStatus,
    color: Color,
    topLeft: Offset,
    size: Float
) {
    val path = Path()
    if (status == ScanStatus.MATCHED) {
        // 画一个 ✔
        path.moveTo(topLeft.x, topLeft.y + size * 0.5f)
        path.lineTo(topLeft.x + size * 0.4f, topLeft.y + size)
        path.lineTo(topLeft.x + size, topLeft.y)
    } else if (status == ScanStatus.MISMATCHED) {
        // 画一个 ✘
        path.moveTo(topLeft.x, topLeft.y)
        path.lineTo(topLeft.x + size, topLeft.y + size)
        path.moveTo(topLeft.x + size, topLeft.y)
        path.lineTo(topLeft.x, topLeft.y + size)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    )
}
