package com.example.inventorymaster.ui.analyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 扫描框最小边长（dp） */
private const val MIN_FRAME_DP = 60

/**
 * 极简纯白可拖拽调整大小的扫描框覆盖层
 *
 * - 中心对称矩形：柔和半透明遮罩 + 纯白高对比四角标记，无多余激光扫描线
 * - 右下角拖拽手柄：向外拖放大、向内拖缩小；拖拽开始回调 [onResizeStart]（调用方暂停解码），
 *   松手回调 [onResizeEnd] 归一化比例（调用方持久化并恢复解码）
 * - [ratioW]/[ratioH] 为相对容器的比例，0 表示未设置，用 defaultWidth/defaultHeight
 * - [hintText] 取景框下方居中提示引导文案
 */
@Composable
fun ResizableScanOverlay(
    ratioW: Float,
    ratioH: Float,
    modifier: Modifier = Modifier,
    defaultWidth: Dp = 260.dp,
    defaultHeight: Dp = 260.dp,
    cornerColor: Color = Color.White,
    handleColor: Color = Color.White,
    showMask: Boolean = true,
    maskColor: Color = Color.Black.copy(alpha = 0.55f),
    hintText: String? = null,
    onResizeStart: () -> Unit = {},
    onResizeEnd: (widthRatio: Float, heightRatio: Float) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        if (containerW < 1f || containerH < 1f) return@BoxWithConstraints

        val minSide = with(density) { MIN_FRAME_DP.dp.toPx() }
        val maxW = containerW * 0.92f
        val maxH = containerH * 0.92f

        // 尺寸只在容器变化（旋转等）时重新初始化；比例回流由下方 LaunchedEffect 同步
        var frameW by remember(containerW) {
            mutableFloatStateOf(
                (if (ratioW > 0f) containerW * ratioW else with(density) { defaultWidth.toPx() })
                    .coerceIn(minSide, maxW)
            )
        }
        var frameH by remember(containerH) {
            mutableFloatStateOf(
                (if (ratioH > 0f) containerH * ratioH else with(density) { defaultHeight.toPx() })
                    .coerceIn(minSide, maxH)
            )
        }
        var dragging by remember { mutableStateOf(false) }

        // 外部比例变化（持久化写回、设置变更）时同步；拖拽进行中不打断，避免尺寸来回跳
        LaunchedEffect(ratioW, ratioH) {
            if (!dragging) {
                if (ratioW > 0f) frameW = (containerW * ratioW).coerceIn(minSide, maxW)
                if (ratioH > 0f) frameH = (containerH * ratioH).coerceIn(minSide, maxH)
            }
        }

        val handleTouchSizeDp = 44.dp
        val handleTouchSizePx = with(density) { handleTouchSizeDp.toPx() }

        // 遮罩 + 四角（无扫描线，清爽白标）。frameW/frameH 在绘制阶段读取，拖拽只触发重绘不触发重组
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = frameW
            val h = frameH
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            val right = left + w
            val bottom = top + h

            if (showMask) {
                drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, h))
                drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(size.width - right, h))
            }

            val cornerLength = 24.dp.toPx()
            val cornerWidth = 3.5.dp.toPx()
            val path = Path().apply {
                // 左上角
                moveTo(left, top + cornerLength); lineTo(left, top); lineTo(left + cornerLength, top)
                // 右上角
                moveTo(right - cornerLength, top); lineTo(right, top); lineTo(right, top + cornerLength)
                // 右下角
                moveTo(right, bottom - cornerLength); lineTo(right, bottom); lineTo(right - cornerLength, bottom)
                // 左下角
                moveTo(left + cornerLength, bottom); lineTo(left, bottom); lineTo(left, bottom - cornerLength)
            }
            drawPath(
                path = path,
                color = cornerColor,
                style = Stroke(width = cornerWidth, cap = StrokeCap.Round)
            )
        }

        // 提示文案徽章 (紧贴取景框正下方)
        if (!hintText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val bottom = (containerH + frameH) / 2f
                        IntOffset(0, (bottom + with(density) { 20.dp.toPx() }).roundToInt())
                    }
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hintText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 右下角拖拽手柄（中心对齐右下角顶点，向外拖放大、向内拖缩小）。
        // offset lambda 在布局阶段读取状态，拖拽中只重新定位不重组
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ((containerW + frameW) / 2f - handleTouchSizePx / 2f).roundToInt(),
                        ((containerH + frameH) / 2f - handleTouchSizePx / 2f).roundToInt()
                    )
                }
                .size(handleTouchSizeDp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            onResizeStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            frameW = (frameW + dragAmount.x * 2f).coerceIn(minSide, maxW)
                            frameH = (frameH + dragAmount.y * 2f).coerceIn(minSide, maxH)
                        },
                        onDragEnd = {
                            dragging = false
                            onResizeEnd(frameW / containerW, frameH / containerH)
                        },
                        onDragCancel = {
                            dragging = false
                            onResizeEnd(frameW / containerW, frameH / containerH)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 清爽白质感微缩放角标
            Canvas(modifier = Modifier.size(20.dp)) {
                // 外圈阴影效果白圆
                drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = size.minDimension / 2f)
                drawCircle(color = handleColor, radius = size.minDimension / 2f - 1.5.dp.toPx())
                // 中心小微点提示
                drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = 2.dp.toPx())
            }
        }
    }
}
