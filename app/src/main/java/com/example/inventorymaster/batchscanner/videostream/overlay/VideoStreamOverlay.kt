package com.example.inventorymaster.batchscanner.videostream.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.inventorymaster.batchscanner.videostream.models.ActiveTracker
import com.example.inventorymaster.batchscanner.videostream.models.GridCellData
import com.example.inventorymaster.batchscanner.videostream.models.ScanDirection
import com.example.inventorymaster.batchscanner.videostream.models.TrackerState
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning

// === 五色规约 ===
private val ColorCounted = StatusSuccess       // 绿色：已计数
private val ColorTracking = StatusWarning      // 黄色：跟踪中
private val ColorScanZone = Color(0x442196F3)  // 蓝色半透明：扫描区域
private val ColorCovered = Color(0x339E9E9E)   // 灰色半透明：已覆盖 Grid
private val ColorError = Color.Red             // 红色：异常
private val ColorDirection = Color(0x882196F3) // 蓝色：方向箭头

/**
 * 视频流 AI 分析叠加层 v2.0
 *
 * 五色规约：
 * - 绿色：已计数 (COUNTED)
 * - 黄色：跟踪中 (TRACKING)
 * - 蓝色：当前扫描区域
 * - 灰色：已覆盖 Grid 区域
 * - 红色：异常/MISMATCHED
 *
 * 支持：
 * - Grid 覆盖地图（扫地机器人风格）
 * - Tracker 检测框
 * - 扫描方向箭头
 * - 涂色动画
 */
@Composable
fun VideoStreamOverlay(
    trackers: List<ActiveTracker>,
    gridCells: Map<String, GridCellData>,
    gridCols: Int,
    gridRows: Int,
    direction: ScanDirection,
    coveragePercent: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. 绘制 Grid 覆盖地图
        drawGridMap(canvasWidth, canvasHeight, gridCells, gridCols, gridRows)

        // 2. 绘制扫描区域指示
        drawScanZone(canvasWidth, canvasHeight, direction)

        // 3. 绘制 Tracker 检测框
        trackers.forEach { tracker ->
            drawTrackerBox(tracker, canvasWidth, canvasHeight)
        }
    }
}

/**
 * 绘制 Grid 覆盖地图（扫地机器人风格）
 */
private fun DrawScope.drawGridMap(
    canvasWidth: Float,
    canvasHeight: Float,
    gridCells: Map<String, GridCellData>,
    gridCols: Int,
    gridRows: Int
) {
    if (gridCols <= 0 || gridRows <= 0) return

    val cellWidth = canvasWidth / gridCols
    val cellHeight = canvasHeight / gridRows

    for ((key, cell) in gridCells) {
        if (!cell.visited) continue

        val (c, r) = key.split(",").map { it.toInt() }
        val left = c * cellWidth
        val top = r * cellHeight

        // 根据 visitCount 调整灰度：重复扫描的区域颜色略深
        val alpha = if (cell.visitCount > 1) 0.25f else 0.15f
        val color = ColorCovered.copy(alpha = alpha)

        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(cellWidth, cellHeight)
        )
    }
}

/**
 * 绘制扫描区域指示
 */
private fun DrawScope.drawScanZone(
    canvasWidth: Float,
    canvasHeight: Float,
    direction: ScanDirection
) {
    // 扫描区域：屏幕中间 40% 位置，高度 15%
    val zoneTop = canvasHeight * 0.35f
    val zoneHeight = canvasHeight * 0.15f

    // 绘制半透明蓝色扫描区
    drawRect(
        color = ColorScanZone,
        topLeft = Offset(0f, zoneTop),
        size = Size(canvasWidth, zoneHeight)
    )

    // 绘制方向箭头
    val arrowX = canvasWidth / 2f
    val arrowColor = ColorDirection

    when (direction) {
        ScanDirection.DOWN -> {
            // 向下箭头
            val arrowY = zoneTop + zoneHeight / 2f
            drawLine(arrowColor, Offset(arrowX - 30f, arrowY - 20f), Offset(arrowX, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX + 30f, arrowY - 20f), Offset(arrowX, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX, arrowY - 20f), Offset(arrowX, arrowY + 20f), strokeWidth = 4f)
        }
        ScanDirection.UP -> {
            // 向上箭头
            val arrowY = zoneTop + zoneHeight / 2f
            drawLine(arrowColor, Offset(arrowX - 30f, arrowY + 20f), Offset(arrowX, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX + 30f, arrowY + 20f), Offset(arrowX, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX, arrowY + 20f), Offset(arrowX, arrowY - 20f), strokeWidth = 4f)
        }
        ScanDirection.RIGHT -> {
            // 向右箭头（换列）
            val arrowY = zoneTop + zoneHeight / 2f
            drawLine(arrowColor, Offset(arrowX - 30f, arrowY - 20f), Offset(arrowX + 10f, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX - 30f, arrowY + 20f), Offset(arrowX + 10f, arrowY), strokeWidth = 4f)
            drawLine(arrowColor, Offset(arrowX - 30f, arrowY), Offset(arrowX + 30f, arrowY), strokeWidth = 4f)
        }
        ScanDirection.IDLE -> { /* 不绘制箭头 */ }
    }
}

/**
 * 绘制 Tracker 检测框
 */
private fun DrawScope.drawTrackerBox(
    tracker: ActiveTracker,
    canvasWidth: Float,
    canvasHeight: Float
) {
    // 将 Tracker 的 boundingBox 从图像坐标转换为 Canvas 坐标
    // 注意：这里假设 Tracker 的 boundingBox 已经是 View 坐标
    // 实际使用时需要在 TrackerEngine 中做 Matrix 变换
    val box = tracker.boundingBox
    val left = box.left
    val top = box.top
    val right = box.right
    val bottom = box.bottom

    val color = when {
        tracker.state == TrackerState.COUNTED -> ColorCounted
        tracker.state == TrackerState.LOCKED -> ColorCounted.copy(alpha = 0.5f)
        tracker.state == TrackerState.LOST -> Color.Gray
        tracker.confidence >= 80 -> ColorCounted
        else -> ColorTracking
    }

    val isCounted = tracker.isCounted || tracker.confidence >= 80

    if (isCounted) {
        // 已计数：绿色实心填充（半透明）
        drawRect(
            color = ColorCounted.copy(alpha = 0.3f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top)
        )
    }

    // 绘制检测框边框
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 3f)
    )

    // 绘制角落标记（L 形）
    val cornerLen = 16f
    // 左上角
    drawLine(color, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = 5f)
    drawLine(color, Offset(left, top), Offset(left, top + cornerLen), strokeWidth = 5f)
    // 右上角
    drawLine(color, Offset(right, top), Offset(right - cornerLen, top), strokeWidth = 5f)
    drawLine(color, Offset(right, top), Offset(right, top + cornerLen), strokeWidth = 5f)
    // 左下角
    drawLine(color, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth = 5f)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeWidth = 5f)
    // 右下角
    drawLine(color, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth = 5f)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth = 5f)
}
