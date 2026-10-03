package com.example.inventorymaster.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownTable
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL

/**
 * 自适应换行表格（替代库默认 MarkdownTable，0.31.0 的默认仍是"每列固定 160dp + maxLines=1"：
 * 5 列 = 800dp 必须横滑，长品名被省略号截断）。
 *
 * 行为对标主流 AI 聊天界面：表格收进可用宽度，列宽按内容测量后比例分配，
 * 单元格文本自动换行；仅保留行/列细分隔线。列数超过 [MAX_WRAP_COLUMNS]（等分后不可读）
 * 时退回库默认实现（固定列宽 + 横向滚动）。
 */
@Composable
fun AutoWrapMarkdownTable(model: MarkdownComponentModel) {
    val content = model.content
    val node = model.node
    val style = model.typography.text

    val header = remember(node) { node.findChildOfType(HEADER) }
    val headerCells = remember(header) { header?.children?.filter { it.type == CELL } }
    val bodyRows = remember(node) { node.children.filter { it.type == ROW } }
    val columnCount = headerCells?.size ?: 0

    if (columnCount == 0 || columnCount > MAX_WRAP_COLUMNS) {
        MarkdownTable(content, node, style = style)
        return
    }

    val colors = LocalMarkdownColors.current
    val annotator = annotatorSettings()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = style.copy(fontWeight = FontWeight.Bold)

    // 行文本（第 0 行 = 表头）；缺格补空串，保证每行列数一致
    val rows: List<List<AnnotatedString>> = remember(node, content) {
        buildList {
            add(headerCells.orEmpty().map { content.buildMarkdownAnnotatedString(it, style, annotator) })
            bodyRows.forEach { row ->
                val cells = row.children.filter { it.type == CELL }
                add((0 until columnCount).map { c ->
                    cells.getOrNull(c)
                        ?.let { content.buildMarkdownAnnotatedString(it, style, annotator) }
                        ?: AnnotatedString("")
                })
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedWidth) {
            MarkdownTable(content, node, style = style)
            return@BoxWithConstraints
        }

        val availablePx = constraints.maxWidth.toFloat()
        val padPx = with(density) { CELL_PADDING.toPx() }
        val minPx = with(density) { MIN_COLUMN_WIDTH.toPx() }

        // 列宽（px）→ Row.weight：先按内容测最大自然宽，再收进/铺满可用宽度
        val weights = remember(rows, style, availablePx) {
            val desired = FloatArray(columnCount) { 2 * padPx }
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, text ->
                    val layout = measurer.measure(
                        text = text,
                        style = if (r == 0) headerStyle else style,
                        softWrap = true,
                        constraints = Constraints(),
                    )
                    desired[c] = maxOf(desired[c], layout.size.width + 2 * padPx)
                }
            }
            distributeColumns(desired, availablePx, minPx)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.tableBackground, RoundedCornerShape(8.dp))
        ) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    row.forEachIndexed { c, text ->
                        if (c > 0) {
                            VerticalDivider(thickness = 0.5.dp, color = colors.dividerColor)
                        }
                        Box(
                            Modifier
                                .weight(weights[c])
                                .padding(CELL_PADDING)
                        ) {
                            Text(
                                text = text,
                                style = if (r == 0) headerStyle else style,
                                color = colors.tableText,
                                softWrap = true,
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.dividerColor)
            }
        }
    }
}

/**
 * 列宽分配：总自然宽不超可用 → 按内容占比放大铺满（表贴满行宽，与网页表格一致）；
 * 超出 → 按比例收缩、每列保底 [minPx]，收缩收敛即返回。
 */
private fun distributeColumns(desired: FloatArray, available: Float, minPx: Float): FloatArray {
    val total = desired.sum()
    if (total <= 0f) return FloatArray(desired.size) { 1f }
    if (total <= available) {
        return FloatArray(desired.size) { desired[it] * available / total }
    }
    val alloc = desired.copyOf()
    repeat(4) {
        val current = alloc.sum()
        if (current <= available) return alloc
        val excess = current - available
        val shrinkable = alloc.filter { it > minPx }.sum()
        if (shrinkable <= 0f) return alloc
        val factor = ((shrinkable - excess) / shrinkable).coerceIn(0f, 1f)
        for (i in alloc.indices) {
            if (alloc[i] > minPx) alloc[i] = maxOf(minPx, alloc[i] * factor)
        }
    }
    return alloc
}

private const val MAX_WRAP_COLUMNS = 8
private val CELL_PADDING = 8.dp
private val MIN_COLUMN_WIDTH = 44.dp
