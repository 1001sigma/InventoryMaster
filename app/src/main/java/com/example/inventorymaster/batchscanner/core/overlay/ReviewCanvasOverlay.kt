package com.example.inventorymaster.batchscanner.core.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.util.BarcodeClassifier
import com.example.inventorymaster.batchscanner.core.util.BarcodeColorCategory
import com.example.inventorymaster.batchscanner.core.util.BarcodeThemeColors
import com.example.inventorymaster.batchscanner.core.util.ClassifiedBarcode


@Composable
fun ReviewCanvasOverlay(
    imageBitmap: ImageBitmap,
    barcodes: List<GlobalBarcode>,
    classifiedBarcodes: List<ClassifiedBarcode> = emptyList(),
    highlightedBarcode: GlobalBarcode? = null,
    highlightKey: Any? = null,
    ocrCropRects: List<android.graphics.Rect> = emptyList(),
    isRescanMode: Boolean = false,
    onImageTap: ((imageX: Float, imageY: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 3. 提前获取并记住图标的 Painter
    val matchedPainter = rememberVectorPainter(image = Icons.Filled.CheckCircle)
    val mismatchedPainter = rememberVectorPainter(image = Icons.Filled.Cancel)

    // 分类映射
    val actualClassified = remember(barcodes, classifiedBarcodes) {
        if (classifiedBarcodes.isNotEmpty()) classifiedBarcodes
        else BarcodeClassifier.classify(barcodes)
    }
    val categoryMap = remember(actualClassified) {
        actualClassified.associateBy { it.barcode }
    }

    // 高亮脉冲闪烁动画
    val flashAnim = remember { Animatable(0f) }
    LaunchedEffect(highlightKey) {
        if (highlightedBarcode != null && highlightKey != null && highlightKey != 0L) {
            flashAnim.snapTo(0f)
            flashAnim.animateTo(1f, animationSpec = tween(160, easing = FastOutSlowInEasing))
            flashAnim.animateTo(0.25f, animationSpec = tween(120, easing = LinearEasing))
            flashAnim.animateTo(1f, animationSpec = tween(160, easing = FastOutSlowInEasing))
            flashAnim.animateTo(0f, animationSpec = tween(240, easing = FastOutSlowInEasing))
        }
    }

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

                    val classified = categoryMap[barcode]
                    val category = classified?.category ?: BarcodeColorCategory.MAJORITY_STANDARD

                    val (strokeColor, fillColor) = when (category) {
                        BarcodeColorCategory.MAJORITY_STANDARD -> Pair(
                            BarcodeThemeColors.BlueBorder,
                            BarcodeThemeColors.BlueFill
                        )
                        BarcodeColorCategory.MAJORITY_DIFF_BATCH -> Pair(
                            BarcodeThemeColors.RedBorder,
                            BarcodeThemeColors.RedFill
                        )
                        BarcodeColorCategory.OTHER_MATERIAL -> Pair(
                            BarcodeThemeColors.YellowBorder,
                            BarcodeThemeColors.YellowFill
                        )
                    }

                    // 1. 绘制半透明底色填充
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(rectWidth, rectHeight)
                    )

                    // 考虑到放大后边框可能会显得特别粗
                    val dynamicStrokeWidth = 6f / scale

                    // 2. 绘制常规分类外框
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(rectWidth, rectHeight),
                        style = Stroke(width = dynamicStrokeWidth)
                    )

                    // 3. 高亮脉冲闪烁动画效果
                    val isHighlighted = barcode == highlightedBarcode && flashAnim.value > 0f
                    if (isHighlighted) {
                        val animProgress = flashAnim.value
                        // 强光闪烁层
                        drawRect(
                            color = Color.White.copy(alpha = 0.55f * animProgress),
                            topLeft = Offset(mappedLeft, mappedTop),
                            size = Size(rectWidth, rectHeight)
                        )
                        // 动态发光外光晕
                        val pulseExpand = 12f * animProgress / scale
                        val pulseWidth = (dynamicStrokeWidth + 8f * animProgress)
                        drawRect(
                            color = strokeColor.copy(alpha = 0.9f * animProgress),
                            topLeft = Offset(mappedLeft - pulseExpand, mappedTop - pulseExpand),
                            size = Size(rectWidth + pulseExpand * 2f, rectHeight + pulseExpand * 2f),
                            style = Stroke(width = pulseWidth)
                        )
                        // 中心聚焦光圈
                        val centerPoint = Offset(mappedLeft + rectWidth / 2f, mappedTop + rectHeight / 2f)
                        val focusRadius = (minOf(rectWidth, rectHeight) * 0.28f).coerceAtLeast(8f)
                        drawCircle(
                            color = strokeColor.copy(alpha = 0.85f * animProgress),
                            radius = focusRadius,
                            center = centerPoint,
                            style = Stroke(width = 4f / scale)
                        )
                    }

                    // 4. 右上角绘制单据匹配状态图标 (✔ / ✘)
                    if (barcode.status == ScanStatus.MATCHED || barcode.status == ScanStatus.MISMATCHED) {
                        val iconSideLength = (minOf(rectWidth, rectHeight) * 0.38f)
                            .coerceAtLeast(16f / scale)
                            .coerceAtMost(36f / scale)
                        val iconSize = Size(iconSideLength, iconSideLength)
                        val iconMargin = 2f / scale
                        val iconX = mappedRight - iconSideLength - iconMargin
                        val iconY = mappedTop + iconMargin

                        val statusColor = if (barcode.status == ScanStatus.MATCHED) {
                            Color(0xFF4CAF50) // 绿 ✔
                        } else {
                            Color(0xFFF44336) // 红 ✘
                        }
                        val painter = if (barcode.status == ScanStatus.MATCHED) {
                            matchedPainter
                        } else {
                            mismatchedPainter
                        }

                        translate(left = iconX, top = iconY) {
                            with(painter) {
                                draw(
                                    size = iconSize,
                                    colorFilter = ColorFilter.tint(statusColor)
                                )
                            }
                        }
                    }
                }

                // 绘制 OCR 选框（黄色虚线）
                if (ocrCropRects.isNotEmpty()) {
                    val ocrColor = Color(0xFFFFEB3B) // 黄色
                    val dynamicStrokeWidth = 5f / scale
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(20f / scale, 10f / scale), 0f
                    )

                    ocrCropRects.forEach { rect ->
                        val mappedLeft = leftOffset + rect.left * scaleX
                        val mappedTop = topOffset + rect.top * scaleY
                        val mappedRight = leftOffset + rect.right * scaleX
                        val mappedBottom = topOffset + rect.bottom * scaleY
                        val rectWidth = mappedRight - mappedLeft
                        val rectHeight = mappedBottom - mappedTop

                        drawRect(
                            color = ocrColor.copy(alpha = 0.15f),
                            topLeft = Offset(mappedLeft, mappedTop),
                            size = Size(rectWidth, rectHeight)
                        )
                        drawRect(
                            color = ocrColor,
                            topLeft = Offset(mappedLeft, mappedTop),
                            size = Size(rectWidth, rectHeight),
                            style = Stroke(width = dynamicStrokeWidth, pathEffect = pathEffect)
                        )
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
