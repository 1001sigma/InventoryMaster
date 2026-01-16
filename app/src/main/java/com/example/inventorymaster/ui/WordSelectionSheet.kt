package com.example.inventorymaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.text.Text

enum class SplitMode { LINE, WORD, CHAR }

data class OcrItem(
    val id: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordSelectionSheet(
    rawText: Text,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var splitMode by remember { mutableStateOf(SplitMode.LINE) }
    val selectedItems = remember { mutableStateListOf<OcrItem>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    // 滚动状态 (这是解决偏移的关键！)
    val scrollState = rememberScrollState()

    // 数据解析
    val parsedItems = remember(rawText, splitMode) {
        val list = mutableListOf<OcrItem>()
        for (block in rawText.textBlocks) {
            for (line in block.lines) {
                when (splitMode) {
                    SplitMode.LINE -> list.add(OcrItem("${line.hashCode()}", line.text))
                    SplitMode.WORD -> line.elements.forEach { list.add(OcrItem("${it.hashCode()}", it.text)) }
                    SplitMode.CHAR -> line.text.forEachIndexed { i, c ->
                        if (!c.isWhitespace()) list.add(OcrItem("${line.hashCode()}_$i", c.toString()))
                    }
                }
            }
        }
        selectedItems.clear()
        list
    }

    val previewText = selectedItems.joinToString(if (splitMode == SplitMode.CHAR) "" else " ") { it.text }

    // 🔥 核心优化 1: 缓存 Item 的位置 (相对父容器)
    // Map<索引, 矩形区域>
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // 🔥 核心优化 2: 纯数学计算碰撞，不依赖 Window 坐标
    fun handleDrag(touchYInVisibleArea: Float, touchX: Float) {
        // 🌟 魔法公式：真实 Y = 可视区 Y + 滚动条已滚动的距离
        // 这彻底解决了“滑第一行选中第四行”的 Bug
        val absoluteY = touchYInVisibleArea + scrollState.value
        val touchPoint = Offset(touchX, absoluteY)

        // 遍历所有缓存的 Rect 进行碰撞检测
        itemBounds.forEach { (index, rect) ->
            if (rect.contains(touchPoint)) {
                val item = parsedItems.getOrNull(index) ?: return@forEach
                if (!selectedItems.contains(item)) {
                    selectedItems.add(item)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = 16.dp)
        ) {
            // ... (顶部控制栏代码保持不变) ...
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                    listOf(SplitMode.LINE to "行", SplitMode.WORD to "词", SplitMode.CHAR to "字").forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = splitMode == mode,
                            onClick = {
                                splitMode = mode
                                itemBounds.clear() // 切换模式必须清空缓存
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            icon = {}
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }

                FilledIconButton(
                    onClick = { onConfirm(previewText) },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp)) }
            }

            // ... (预览输入框代码保持不变) ...
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (previewText.isEmpty()) {
                    Text("点击或长按滑动选词...", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                } else {
                    Text(text = previewText, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, lineHeight = 20.sp)
                }
                if (previewText.isNotEmpty()) {
                    IconButton(onClick = { selectedItems.clear() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, "Clear", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ================= 选词区域 (修复版) =================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 👇 手势监听放在 Box 上，这里的坐标是相对于 Box 左上角的 (可视区域)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleDrag(offset.y, offset.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                handleDrag(change.position.y, change.position.x)
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState) // 绑定 scrollState
                ) {
                    // 👇 增加 key 以强制刷新 FlowRow，避免模式切换时布局缓存导致的 bug
                    key(splitMode) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsedItems.forEachIndexed { index, item ->
                                // 提取状态，减少重组范围
                                val isSelected = selectedItems.contains(item)

                                ItemBox(
                                    text = item.text,
                                    isSelected = isSelected,
                                    onClick = { if (isSelected) selectedItems.remove(item) else selectedItems.add(item) },
                                    // 👇 这里的坐标是相对于 FlowRow (也就是 Column 内部) 的真实布局坐标
                                    onPositioned = { rect -> itemBounds[index] = rect }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemBox(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            // 🔥 核心优化 3: 使用 boundsInParent() 获取相对于父容器(FlowRow)的坐标
            // 因为 FlowRow 在 ScrollColumn 里面，所以这个坐标就是内容的绝对布局坐标
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates.boundsInParent())
            }
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // 使用 BasicText 或者简单的 Text 减少开销
        Text(
            text = text,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}