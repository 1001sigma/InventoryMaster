package com.example.inventorymaster.batchscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale


@Composable
fun ReviewCanvasOverlay(
    imageBitmap: ImageBitmap,
    barcodes: List<GlobalBarcode>,
    isRescanMode: Boolean = false,
    onImageTap: ((imageX: Float, imageY: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 3. 提前获取并记住图标的 Painter
    // 这里使用了 Material Design 的内置图标，你可以换成你自己的 R.drawable.xxx
    val matchedPainter = rememberVectorPainter(image = Icons.Filled.CheckCircle)
    val mismatchedPainter = rememberVectorPainter(image = Icons.Filled.Cancel)

    // 记录用户的缩放比例和拖拽偏移量
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 体验优化：当退出补扫模式时，自动让图片恢复原始大小和居中状态
    LaunchedEffect(isRescanMode) {
        if (!isRescanMode) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Image(
        bitmap = imageBitmap,
        contentDescription = "Review Image",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            // 处理双指缩放和拖拽手势
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 限制缩放比例在 1倍(原图) 到 5倍 之间
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            }
            // 处理点击手势，并加入逆向推导算法
            .pointerInput(isRescanMode) {
                if (!isRescanMode) return@pointerInput

                detectTapGestures { tapOffset ->
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    // 获取屏幕中心点
                    val centerX = canvasWidth / 2f
                    val centerY = canvasHeight / 2f

                    // 核心算法 A：先剥离用户的缩放和拖拽，还原到 1x 状态下的屏幕坐标
                    val unzoomedX = (tapOffset.x - centerX - offset.x) / scale + centerX
                    val unzoomedY = (tapOffset.y - centerY - offset.y) / scale + centerY

                    // 核心算法 B：再剥离 ContentScale.Fit 带来的留白和缩放，映射到原图物理像素
                    val imageWidth = imageBitmap.width.toFloat()
                    val imageHeight = imageBitmap.height.toFloat()
                    val imageAspectRatio = imageWidth / imageHeight
                    val canvasAspectRatio = canvasWidth / canvasHeight

                    var drawWidth = canvasWidth
                    var drawHeight = canvasHeight
                    var leftOffset = 0f
                    var topOffset = 0f

                    if (imageAspectRatio > canvasAspectRatio) {
                        drawHeight = canvasWidth / imageAspectRatio
                        topOffset = (canvasHeight - drawHeight) / 2f
                    } else {
                        drawWidth = canvasHeight * imageAspectRatio
                        leftOffset = (canvasWidth - drawWidth) / 2f
                    }

                    val fitScaleX = drawWidth / imageWidth
                    val fitScaleY = drawHeight / imageHeight

                    // 使用刚才计算出的 unzoomed 坐标进行最终映射
                    val mappedX = (unzoomedX - leftOffset) / fitScaleX
                    val mappedY = (unzoomedY - topOffset) / fitScaleY

                    // 确保点击在图片的有效范围内
                    if (mappedX in 0f..imageWidth && mappedY in 0f..imageHeight) {
                        onImageTap?.invoke(mappedX, mappedY)
                    }
                }
            }
            // 将缩放和位移应用到整个渲染层
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .drawWithContent {
                // 底图绘制
                drawContent()

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

                if (imageAspectRatio > canvasAspectRatio) {
                    drawHeight = canvasWidth / imageAspectRatio
                    topOffset = (canvasHeight - drawHeight) / 2f
                } else {
                    drawWidth = canvasHeight * imageAspectRatio
                    leftOffset = (canvasWidth - drawWidth) / 2f
                }

                val scaleX = drawWidth / imageWidth
                val scaleY = drawHeight / imageHeight

                // 遍历条码绘制状态
                barcodes.forEach { barcode ->
                    val box = barcode.globalBoundingBox
                    val mappedLeft = leftOffset + box.left * scaleX
                    val mappedTop = topOffset + box.top * scaleY
                    val mappedRight = leftOffset + box.right * scaleX
                    val mappedBottom = topOffset + box.bottom * scaleY

                    val rectWidth = mappedRight - mappedLeft
                    val rectHeight = mappedBottom - mappedTop

                    val themeColor = if (barcode.status == ScanStatus.MATCHED) {
                        Color(0xFF00FF00) // 绿
                    } else {
                        Color(0xFFFF0000) // 红
                    }

                    // 考虑到放大后边框可能会显得特别粗
                    val dynamicStrokeWidth = 6f / scale

                    // 1. 绘制框
                    drawRect(
                        color = themeColor,
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(rectWidth, rectHeight),
                        style = Stroke(width = dynamicStrokeWidth)
                    )

                    val iconSideLength = minOf(rectWidth, rectHeight) * 0.9f
                    // 2. 绘制图标 (替换了原来的 drawStatusIcon)
                    val iconSize = Size(iconSideLength, iconSideLength)
                    // 将图标放在框的右上角内部
                    val centerX = mappedLeft + rectWidth / 2f
                    val centerY = mappedTop + rectHeight / 2f
                    val iconX = centerX - iconSize.width / 2f
                    val iconY = centerY - iconSize.height / 2f
                    // 根据状态选择 Painter
                    val painter = if (barcode.status == ScanStatus.MATCHED) {
                        matchedPainter
                    } else {
                        mismatchedPainter
                    }

                    // 核心修改：在 DrawScope 内部绘制 Painter
                    // 需要使用 translate 将画笔移动到指定位置
                    translate(left = iconX, top = iconY) {
                        with(painter) {
                            draw(
                                size = iconSize,
                                colorFilter = ColorFilter.tint(themeColor)
                            )
                        }
                    }
                }

                // 补扫模式的全屏黄色遮罩提示
                if (isRescanMode) {
                    drawRect(
                        color = Color.Yellow.copy(alpha = 0.1f),
                        size = size
                    )
                }
            }
    )
}
