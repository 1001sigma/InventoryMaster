package com.example.inventorymaster.batchscanner

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

// 1. 状态机枚举跟着过来
enum class CropDragTarget {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, CENTER
}

// 2. 组件本身（去掉 private 修饰符，让它可以在外部被调用）
@Composable
fun ModernCropView(
    bitmap: Bitmap,
    onCropConfirm: (Bitmap) -> Unit,
    onRetake: () -> Unit
) {
    // ... 这里完全粘贴上一轮我给你的 ModernCropView 内部的所有代码 ...
    // (包括手势检测、Canvas 绘制、底部按钮等)
    var leftRatio by remember { mutableStateOf(0.05f) }
    var topRatio by remember { mutableStateOf(0.05f) }
    var rightRatio by remember { mutableStateOf(0.95f) }
    var bottomRatio by remember { mutableStateOf(0.95f) }

    // 记录画布物理尺寸，用于计算
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 核心状态锁：记录当前手指正在拖拽哪个部位
    var activeTarget by remember { mutableStateOf(CropDragTarget.NONE) }

    // 获取物理像素密度的热区半径 (40dp 约等于手指触摸的舒适区域)
    val hitRadius = with(LocalDensity.current) { 40.dp.toPx() }

    // 预计算图像在画布上的绘制参数
    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
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
                    .onSizeChanged { canvasSize = it } // 获取画布物理尺寸
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (drawWidth == 0f) return@detectDragGestures
                                val x = startOffset.x
                                val y = startOffset.y

                                // 计算当前裁剪框的四个角物理坐标
                                val cropL = offsetX + drawWidth * leftRatio
                                val cropT = offsetY + drawHeight * topRatio
                                val cropR = offsetX + drawWidth * rightRatio
                                val cropB = offsetY + drawHeight * bottomRatio

                                // 辅助函数：计算两点距离
                                fun isHit(px: Float, py: Float) = hypot((x - px).toDouble(), (y - py).toDouble()) <= hitRadius

                                // 阶段 1：判定四个角 (优先级最高，二维操作)
                                activeTarget = when {
                                    isHit(cropL, cropT) -> CropDragTarget.TOP_LEFT
                                    isHit(cropR, cropT) -> CropDragTarget.TOP_RIGHT
                                    isHit(cropL, cropB) -> CropDragTarget.BOTTOM_LEFT
                                    isHit(cropR, cropB) -> CropDragTarget.BOTTOM_RIGHT

                                    // 阶段 2：判定四条边 (一维轨道操作)
                                    // 左右边：X 轴命中，且 Y 轴在框范围内
                                    kotlin.math.abs(x - cropL) <= hitRadius && y in (cropT - hitRadius)..(cropB + hitRadius) -> CropDragTarget.LEFT
                                    kotlin.math.abs(x - cropR) <= hitRadius && y in (cropT - hitRadius)..(cropB + hitRadius) -> CropDragTarget.RIGHT
                                    // 上下边：Y 轴命中，且 X 轴在框范围内
                                    kotlin.math.abs(y - cropT) <= hitRadius && x in (cropL - hitRadius)..(cropR + hitRadius) -> CropDragTarget.TOP
                                    kotlin.math.abs(y - cropB) <= hitRadius && x in (cropL - hitRadius)..(cropR + hitRadius) -> CropDragTarget.BOTTOM

                                    // 阶段 3：判定中心区域 (二维平移操作)
                                    x in cropL..cropR && y in cropT..cropB -> CropDragTarget.CENTER

                                    else -> CropDragTarget.NONE
                                }
                            },
                            onDragEnd = {
                                activeTarget = CropDragTarget.NONE // 手指抬起，释放状态锁
                            },
                            onDragCancel = {
                                activeTarget = CropDragTarget.NONE
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (activeTarget == CropDragTarget.NONE || drawWidth == 0f || drawHeight == 0f) return@detectDragGestures

                            // 将物理位移转换为比例位移
                            val dX = dragAmount.x / drawWidth
                            val dY = dragAmount.y / drawHeight
                            val minSize = 0.05f // 最小裁剪尺寸限制（空气墙），防止反向穿透

                            // 根据当前锁定的状态，执行严格的降维/二维变换
                            when (activeTarget) {
                                // 四角：2D 自由缩放
                                CropDragTarget.TOP_LEFT -> {
                                    leftRatio = (leftRatio + dX).coerceIn(0f, rightRatio - minSize)
                                    topRatio = (topRatio + dY).coerceIn(0f, bottomRatio - minSize)
                                }
                                CropDragTarget.TOP_RIGHT -> {
                                    rightRatio = (rightRatio + dX).coerceIn(leftRatio + minSize, 1f)
                                    topRatio = (topRatio + dY).coerceIn(0f, bottomRatio - minSize)
                                }
                                CropDragTarget.BOTTOM_LEFT -> {
                                    leftRatio = (leftRatio + dX).coerceIn(0f, rightRatio - minSize)
                                    bottomRatio = (bottomRatio + dY).coerceIn(topRatio + minSize, 1f)
                                }
                                CropDragTarget.BOTTOM_RIGHT -> {
                                    rightRatio = (rightRatio + dX).coerceIn(leftRatio + minSize, 1f)
                                    bottomRatio = (bottomRatio + dY).coerceIn(topRatio + minSize, 1f)
                                }

                                // 四边：1D 轨道缩放 (强制屏蔽无关轴的拖拽数据)
                                CropDragTarget.LEFT -> leftRatio = (leftRatio + dX).coerceIn(0f, rightRatio - minSize)
                                CropDragTarget.RIGHT -> rightRatio = (rightRatio + dX).coerceIn(leftRatio + minSize, 1f)
                                CropDragTarget.TOP -> topRatio = (topRatio + dY).coerceIn(0f, bottomRatio - minSize)
                                CropDragTarget.BOTTOM -> bottomRatio = (bottomRatio + dY).coerceIn(topRatio + minSize, 1f)

                                // 中心：2D 整体平移
                                CropDragTarget.CENTER -> {
                                    val safeDx = dX.coerceIn(-leftRatio, 1f - rightRatio)
                                    val safeDy = dY.coerceIn(-topRatio, 1f - bottomRatio)
                                    leftRatio += safeDx
                                    rightRatio += safeDx
                                    topRatio += safeDy
                                    bottomRatio += safeDy
                                }
                                CropDragTarget.NONE -> {}
                            }
                        }
                    }
            ) {
                if (drawWidth == 0f || drawHeight == 0f) return@Canvas

                // 1. 绘制底层图片
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                )

                // 2. 计算裁剪框绝对坐标
                val cropLeft = offsetX + drawWidth * leftRatio
                val cropTop = offsetY + drawHeight * topRatio
                val cropRight = offsetX + drawWidth * rightRatio
                val cropBottom = offsetY + drawHeight * bottomRatio

                // 3. 绘制半透明遮罩层 (防闪烁)
                with(drawContext.canvas.nativeCanvas) {
                    val count = saveLayer(0f, 0f, size.width, size.height, null)
                    drawRect(Color.Black.copy(alpha = 0.6f))
                    drawRect(
                        color = Color.Transparent,
                        topLeft = Offset(cropLeft, cropTop),
                        size = Size(cropRight - cropLeft, cropBottom - cropTop),
                        blendMode = BlendMode.Clear
                    )
                    restoreToCount(count)
                }

                // 4. 绘制四角锚点 (给用户视觉提示)
                val cornerLength = 24.dp.toPx()
                val stroke = Stroke(width = 4.dp.toPx())
                val cornerColor = Color.White

                drawLine(cornerColor, Offset(cropLeft, cropTop), Offset(cropLeft + cornerLength, cropTop), strokeWidth = stroke.width)
                drawLine(cornerColor, Offset(cropLeft, cropTop), Offset(cropLeft, cropTop + cornerLength), strokeWidth = stroke.width)

                drawLine(cornerColor, Offset(cropRight, cropTop), Offset(cropRight - cornerLength, cropTop), strokeWidth = stroke.width)
                drawLine(cornerColor, Offset(cropRight, cropTop), Offset(cropRight, cropTop + cornerLength), strokeWidth = stroke.width)

                drawLine(cornerColor, Offset(cropLeft, cropBottom), Offset(cropLeft + cornerLength, cropBottom), strokeWidth = stroke.width)
                drawLine(cornerColor, Offset(cropLeft, cropBottom), Offset(cropLeft, cropBottom - cornerLength), strokeWidth = stroke.width)

                drawLine(cornerColor, Offset(cropRight, cropBottom), Offset(cropRight - cornerLength, cropBottom), strokeWidth = stroke.width)
                drawLine(cornerColor, Offset(cropRight, cropBottom), Offset(cropRight, cropBottom - cornerLength), strokeWidth = stroke.width)
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onRetake) { Text("重拍") }
                Button(
                    onClick = {
                        val realX = (bitmap.width * leftRatio).toInt()
                        val realY = (bitmap.height * topRatio).toInt()
                        val realW = (bitmap.width * (rightRatio - leftRatio)).toInt()
                        val realH = (bitmap.height * (bottomRatio - topRatio)).toInt()

                        val safeX = maxOf(0, realX)
                        val safeY = maxOf(0, realY)
                        val safeW = minOf(bitmap.width - safeX, realW)
                        val safeH = minOf(bitmap.height - safeY, realH)

                        val finalCropped = Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
                        onCropConfirm(finalCropped)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("确认裁剪")
                }
            }
        }
    }
}