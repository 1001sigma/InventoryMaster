package com.example.inventorymaster.utils.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 一条已完成的笔迹：保留原始采样点（用于导出重描）与渲染用平滑路径 */
private class InkStroke(val points: List<Offset>, val path: Path)

private val PenColors = listOf(
    "墨黑" to Color.Black,
    "签发蓝" to Color(0xFF1A237E),
    "印泥红" to Color(0xFFB71C1C)
)

/**
 * 手写签名画板弹窗（可复用组件）。
 * 确认后输出「按笔迹裁边、最长边 ≤1024px、透明底」的 Bitmap，可直接用于 PDF 盖章与签名库存档。
 */
@OptIn(ExperimentalComposeUiApi::class) // historical 采样点 API
@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    val completedStrokes = remember { mutableStateListOf<InkStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    // 手势事件不触发重组，用版本号驱动 Canvas 重绘（同 ImageToolbox 的 redraw 计数）
    var strokeVersion by remember { mutableIntStateOf(0) }
    val lastMid = remember { mutableStateOf(Offset.Zero) }

    var penColor by remember { mutableStateOf(PenColors.first().second) }
    var penWidthDp by remember { mutableStateOf(10f) }
    val strokePx = with(density) { penWidthDp.dp.toPx() }

    var canvasSizePx by remember { mutableStateOf(Offset.Zero) }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "绘制签名",
        showContentCard = false,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "请在下方空白区域书写您的正楷姓名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            // 原始手势循环（对齐 ImageToolbox SignatureDialog）：
                            // 不走 detectDragGestures——它要先滑过 touch slop 才起笔，起笔段会丢失/跳变，产生"断"感
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                // 按下瞬间起笔
                                currentPoints.clear()
                                currentPoints.add(down.position)
                                lastMid.value = down.position
                                val path = Path()
                                path.moveTo(down.position.x, down.position.y)
                                currentPath = path
                                strokeVersion++

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    // 快速书写时多个采样点被合并进同一事件，historical 补齐真实轨迹（精度）
                                    val samples = change.historical.map { it.position } + change.position
                                    for (point in samples) {
                                        val prev = currentPoints.lastOrNull() ?: continue
                                        if (abs(point.x - prev.x) < 0.5f && abs(point.y - prev.y) < 0.5f) continue
                                        // 中点法增量 cubicTo：只追加不重建，路径连续无折线断裂感
                                        val mid = Offset((prev.x + point.x) / 2f, (prev.y + point.y) / 2f)
                                        val lm = lastMid.value
                                        path.cubicTo(lm.x, lm.y, prev.x, prev.y, mid.x, mid.y)
                                        lastMid.value = mid
                                        currentPoints.add(point)
                                    }
                                    strokeVersion++
                                    change.consume()
                                    if (!change.pressed) break
                                }

                                // 收笔定稿：补一段到真实终点；单点也出墨（圆头笔帽呈现圆点）
                                if (currentPoints.size == 1) {
                                    val p = currentPoints.first()
                                    path.lineTo(p.x + 0.1f, p.y)
                                } else if (currentPoints.isNotEmpty()) {
                                    val endPoint = currentPoints.last()
                                    val lm = lastMid.value
                                    path.cubicTo(
                                        lm.x, lm.y,
                                        (lm.x + endPoint.x) / 2f, (lm.y + endPoint.y) / 2f,
                                        endPoint.x, endPoint.y
                                    )
                                }
                                if (currentPoints.isNotEmpty()) {
                                    completedStrokes.add(InkStroke(currentPoints.toList(), path))
                                }
                                currentPoints.clear()
                                currentPath = null
                                strokeVersion++
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        canvasSizePx = Offset(size.width, size.height)
                        strokeVersion // 订阅笔画版本，手势路径更新即重绘
                        val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        completedStrokes.forEach { stroke ->
                            drawPath(stroke.path, penColor, style = style)
                        }
                        currentPath?.let { drawPath(it, penColor, style = style) }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (completedStrokes.isNotEmpty()) completedStrokes.removeAt(completedStrokes.lastIndex)
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销上一笔", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                completedStrokes.clear()
                                currentPoints.clear()
                                currentPath = null
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "清空重写", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 笔参数：颜色预设 + 线宽
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PenColors.forEach { (_, color) ->
                        Box(
                            modifier = Modifier
                                .size(if (penColor == color) 30.dp else 24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (penColor == color) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { penColor = color }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("笔迹粗细", style = MaterialTheme.typography.bodyMedium)
                    Text("${penWidthDp.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = penWidthDp,
                    onValueChange = { penWidthDp = it },
                    valueRange = 4f..24f
                )
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    val bitmap = renderSignatureBitmap(
                        strokes = completedStrokes.toList(),
                        canvasSize = canvasSizePx,
                        strokePx = strokePx,
                        colorArgb = penColor.toArgb()
                    )
                    if (bitmap == null) {
                        Toast.makeText(context, "请先绘制您的笔迹", Toast.LENGTH_SHORT).show()
                        return@ModernDialogButtonBar
                    }
                    onConfirm(bitmap)
                },
                onDismiss = onDismiss,
                confirmText = "确认"
            )
        }
    )
}

/**
 * 将笔迹矢量离屏渲染为透明底 PNG 素材：
 * 按墨迹包围盒裁边并等比缩放到最长边 ≤1024px，避免盖章时带入大片空白。
 */
private fun renderSignatureBitmap(
    strokes: List<InkStroke>,
    canvasSize: Offset,
    strokePx: Float,
    colorArgb: Int
): Bitmap? {
    if (strokes.isEmpty() || canvasSize == Offset.Zero) return null

    var bounds: Rect? = null
    strokes.forEach { stroke ->
        stroke.points.forEach { point ->
            val r = Rect(point.x, point.y, point.x, point.y)
            bounds = if (bounds == null) r else Rect(
                min(bounds!!.left, r.left), min(bounds!!.top, r.top),
                max(bounds!!.right, r.right), max(bounds!!.bottom, r.bottom)
            )
        }
    }
    val ink = bounds ?: return null
    val inkW = (ink.right - ink.left).coerceAtLeast(1f)
    val inkH = (ink.bottom - ink.top).coerceAtLeast(1f)

    val scale = 1024f / max(inkW, inkH)
    // 内边距随缩放比例走，封顶 256px：避免极小笔迹时把画布撑爆
    val pad = (strokePx * scale * 1.5f).coerceIn(8f, 256f)

    val outW = (inkW * scale + pad * 2).roundToInt().coerceAtMost(2048)
    val outH = (inkH * scale + pad * 2).roundToInt().coerceAtMost(2048)

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = colorArgb
        style = Paint.Style.STROKE
        strokeWidth = strokePx * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    strokes.forEach { stroke ->
        val path = android.graphics.Path()
        val pts = stroke.points
        if (pts.isEmpty()) return@forEach
        path.moveTo(
            (pts.first().x - ink.left) * scale + pad,
            (pts.first().y - ink.top) * scale + pad
        )
        if (pts.size == 1) {
            path.lineTo(
                (pts.first().x - ink.left) * scale + pad + 0.1f,
                (pts.first().y - ink.top) * scale + pad
            )
        }
        var prev = pts.first()
        var lastMid = pts.first()
        for (i in 1 until pts.size) {
            val point = pts[i]
            val mid = Offset((prev.x + point.x) / 2f, (prev.y + point.y) / 2f)
            path.cubicTo(
                (lastMid.x - ink.left) * scale + pad, (lastMid.y - ink.top) * scale + pad,
                (prev.x - ink.left) * scale + pad, (prev.y - ink.top) * scale + pad,
                (mid.x - ink.left) * scale + pad, (mid.y - ink.top) * scale + pad
            )
            lastMid = mid
            prev = point
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

/**
 * 从电子签名库选取签名的弹窗（导出流程 / 设置页共用入口）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignatureLibraryPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val signatures by SignatureStore.get(context).signatures.collectAsState()

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "选择电子签名",
        showContentCard = true,
        content = {
            if (signatures.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("签名库为空", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "请先在 设置 → 电子签名库 中绘制保存，或在导出时绘制签名（会自动入库）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(signatures) { file ->
                        var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(file.absolutePath) {
                            bitmap = withContext(Dispatchers.IO) {
                                SignatureStore.get(context).decodeThumbnail(file, 300)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { bitmap?.let(onSelect) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = (bitmap as Bitmap).asImageBitmap(),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onDismiss,
                onDismiss = onDismiss,
                confirmText = "关闭"
            )
        }
    )
}
