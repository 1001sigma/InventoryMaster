package com.example.inventorymaster.batchscanner.ocr

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.batchscanner.core.overlay.CropDragTarget
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MIN_CROP_WIDTH_DP = 100
private const val MIN_CROP_HEIGHT_DP = 60

/**
 * OCR 裁剪选框 Overlay。
 * 用户可拖拽四角和中心调整选框大小，选框外半透明遮罩，选框内清晰。
 * 裁剪坐标以最终显示 Bitmap 为基准，避免 EXIF 方向导致坐标偏移。
 *
 * @param imageBitmap 待裁剪的图片（已处理 EXIF 旋转后的最终 Bitmap）
 * @param onConfirm 返回原图像素坐标的 Rect
 * @param onCancel 取消裁剪
 */
@Composable
fun OcrCropOverlay(
    imageBitmap: Bitmap,
    onConfirm: (cropRect: Rect) -> Unit,
    onCancel: () -> Unit
) {
    val imgBitmap = remember(imageBitmap) { imageBitmap.asImageBitmap() }

    // 选框比例坐标 (0~1)
    var leftRatio by remember { mutableStateOf(0.15f) }
    var topRatio by remember { mutableStateOf(0.25f) }
    var rightRatio by remember { mutableStateOf(0.85f) }
    var bottomRatio by remember { mutableStateOf(0.75f) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeTarget by remember { mutableStateOf(CropDragTarget.NONE) }

    val hitRadius = with(LocalDensity.current) { 40.dp.toPx() }
    val minCropWidthPx = with(LocalDensity.current) { MIN_CROP_WIDTH_DP.dp.toPx() }
    val minCropHeightPx = with(LocalDensity.current) { MIN_CROP_HEIGHT_DP.dp.toPx() }

    // 图片在画布上的绘制参数
    val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
    val canvasAspect = if (canvasSize.height > 0) canvasSize.width.toFloat() / canvasSize.height.toFloat() else 1f

    val drawWidth: Float
    val drawHeight: Float
    val offsetX: Float
    val offsetY: Float

    if (canvasSize.width > 0 && canvasSize.height > 0) {
        if (canvasAspect > imageAspect) {
            drawHeight = canvasSize.height.toFloat()
            drawWidth = drawHeight * imageAspect
            offsetX = (canvasSize.width - drawWidth) / 2f
            offsetY = 0f
        } else {
            drawWidth = canvasSize.width.toFloat()
            drawHeight = drawWidth / imageAspect
            offsetX = 0f
            offsetY = (canvasSize.height - drawHeight) / 2f
        }
    } else {
        drawWidth = 0f; drawHeight = 0f; offsetX = 0f; offsetY = 0f
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (drawWidth == 0f) return@detectDragGestures
                                val x = startOffset.x
                                val y = startOffset.y

                                val cropL = offsetX + drawWidth * leftRatio
                                val cropT = offsetY + drawHeight * topRatio
                                val cropR = offsetX + drawWidth * rightRatio
                                val cropB = offsetY + drawHeight * bottomRatio

                                fun isHit(px: Float, py: Float) = hypot((x - px).toDouble(), (y - py).toDouble()) <= hitRadius

                                // 四角优先
                                activeTarget = when {
                                    isHit(cropL, cropT) -> CropDragTarget.TOP_LEFT
                                    isHit(cropR, cropT) -> CropDragTarget.TOP_RIGHT
                                    isHit(cropL, cropB) -> CropDragTarget.BOTTOM_LEFT
                                    isHit(cropR, cropB) -> CropDragTarget.BOTTOM_RIGHT
                                    // 中心拖拽
                                    x in cropL..cropR && y in cropT..cropB -> CropDragTarget.CENTER
                                    else -> CropDragTarget.NONE
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (drawWidth == 0f || drawHeight == 0f) return@detectDragGestures
                                val dxRatio = dragAmount.x / drawWidth
                                val dyRatio = dragAmount.y / drawHeight
                                val minWRatio = minCropWidthPx / drawWidth
                                val minHRatio = minCropHeightPx / drawHeight

                                when (activeTarget) {
                                    CropDragTarget.TOP_LEFT -> {
                                        leftRatio = (leftRatio + dxRatio).coerceIn(0f, rightRatio - minWRatio)
                                        topRatio = (topRatio + dyRatio).coerceIn(0f, bottomRatio - minHRatio)
                                    }
                                    CropDragTarget.TOP_RIGHT -> {
                                        rightRatio = (rightRatio + dxRatio).coerceIn(leftRatio + minWRatio, 1f)
                                        topRatio = (topRatio + dyRatio).coerceIn(0f, bottomRatio - minHRatio)
                                    }
                                    CropDragTarget.BOTTOM_LEFT -> {
                                        leftRatio = (leftRatio + dxRatio).coerceIn(0f, rightRatio - minWRatio)
                                        bottomRatio = (bottomRatio + dyRatio).coerceIn(topRatio + minHRatio, 1f)
                                    }
                                    CropDragTarget.BOTTOM_RIGHT -> {
                                        rightRatio = (rightRatio + dxRatio).coerceIn(leftRatio + minWRatio, 1f)
                                        bottomRatio = (bottomRatio + dyRatio).coerceIn(topRatio + minHRatio, 1f)
                                    }
                                    CropDragTarget.CENTER -> {
                                        val wRatio = rightRatio - leftRatio
                                        val hRatio = bottomRatio - topRatio
                                        leftRatio = (leftRatio + dxRatio).coerceIn(0f, 1f - wRatio)
                                        topRatio = (topRatio + dyRatio).coerceIn(0f, 1f - hRatio)
                                        rightRatio = leftRatio + wRatio
                                        bottomRatio = topRatio + hRatio
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {}
                        )
                    }
            ) {
                // 先绘制图片（在独立图层之外，不受 Clear 影响）
                drawImage(
                    image = imgBitmap,
                    dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                    dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt())
                )

                val cropL = offsetX + drawWidth * leftRatio
                val cropT = offsetY + drawHeight * topRatio
                val cropR = offsetX + drawWidth * rightRatio
                val cropB = offsetY + drawHeight * bottomRatio
                val cropW = cropR - cropL
                val cropH = cropB - cropT

                // 在独立图层上绘制遮罩 + 挖孔，BlendMode.Clear 只影响此图层
                drawContext.canvas.saveLayer(
                    androidx.compose.ui.geometry.Rect(0f, 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat()),
                    androidx.compose.ui.graphics.Paint()
                )

                // 半透明遮罩
                drawRect(Color.Black.copy(alpha = 0.5f))

                // 选框内清晰显示（擦除遮罩，仅清除遮罩层，不影响底层图片）
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(cropL, cropT),
                    size = Size(cropW, cropH),
                    blendMode = BlendMode.Clear
                )

                drawContext.canvas.restore()

                // 选框边框
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropL, cropT),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 四角蓝色圆点
                val cornerRadius = 6.dp.toPx()
                val corners = listOf(
                    Offset(cropL, cropT), Offset(cropR, cropT),
                    Offset(cropL, cropB), Offset(cropR, cropB)
                )
                corners.forEach { corner ->
                    drawCircle(Color(0xFF2196F3), cornerRadius, corner)
                }
            }
        }

        // 底部按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Color.White)
            }
            Button(
                onClick = {
                    // 比例坐标 → 原图像素坐标
                    val pixelL = (leftRatio * imageBitmap.width).toInt().coerceIn(0, imageBitmap.width)
                    val pixelT = (topRatio * imageBitmap.height).toInt().coerceIn(0, imageBitmap.height)
                    val pixelR = (rightRatio * imageBitmap.width).toInt().coerceIn(0, imageBitmap.width)
                    val pixelB = (bottomRatio * imageBitmap.height).toInt().coerceIn(0, imageBitmap.height)
                    onConfirm(Rect(pixelL.toFloat(), pixelT.toFloat(), pixelR.toFloat(), pixelB.toFloat()))
                }
            ) {
                Text("确认识别")
            }
        }
    }
}
