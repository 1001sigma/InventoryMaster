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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.utils.JiebaUtils
import com.google.mlkit.vision.text.Text

// 定义数据模型
data class SmartTerm(
    val id: String,
    val text: String,
    val isOriginal: Boolean = false // 标记是否为原始行（未分词）
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordSelectionSheet(
    rawText: Text, // MLKit 的原始结果
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isSimpleMode: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // 状态管理
    var isLoading by remember { mutableStateOf(true) }
    var termList by remember { mutableStateOf<List<SmartTerm>>(emptyList()) }
    val selectedItems = remember { mutableStateListOf<SmartTerm>() }

    // 布局缓存 (用于滑动选中)
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // 🔥 核心逻辑：异步分词
    LaunchedEffect(rawText) {
        isLoading = true
        // 1. 提取所有原始文本，拼接成一大段
        val fullText = rawText.textBlocks.joinToString(" ") { it.text }
            .replace("\n", " ")

        // 2. 调用 Jieba 进行智能分词
        val cutWords = JiebaUtils.cut(fullText,useSimpleMode = isSimpleMode)

        // 3. 构建展示列表
        // 策略：可以把“原始行”也放进去供备选，或者只放分词结果
        // 这里演示只放分词结果，为了方便选择
        val list = cutWords.mapIndexed { index, word ->
            SmartTerm("term_$index", word)
        }

        termList = list
        isLoading = false
    }

    // 预览文本：将选中的词拼接起来
    val previewText = selectedItems.joinToString("") { it.text }

    // 处理拖拽选中 (逻辑复用你之前的优秀实现)
    fun handleDrag(touchYInVisibleArea: Float, touchX: Float) {
        val absoluteY = touchYInVisibleArea + scrollState.value
        val touchPoint = Offset(touchX, absoluteY)
        itemBounds.forEach { (index, rect) ->
            if (rect.contains(touchPoint)) {
                val item = termList.getOrNull(index) ?: return@forEach
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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f) //稍微高一点
                .padding(horizontal = 16.dp)
        ) {
            // --- 顶部栏 (标题 + 确认) ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("智能分词选择", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                FilledIconButton(
                    onClick = { onConfirm(previewText) },
                    enabled = previewText.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.Check, null) }
            }

            // --- 预览框 ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (previewText.isEmpty()) {
                    Text("点击或滑动选择词汇...", color = MaterialTheme.colorScheme.outline)
                } else {
                    Text(text = previewText, fontSize = 16.sp, maxLines = 2, lineHeight = 20.sp)
                }

                if (previewText.isNotEmpty()) {
                    IconButton(onClick = { selectedItems.clear() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, "Clear", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 内容区域 ---
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text("正在智能拆解...", Modifier.padding(top = 48.dp), fontSize = 12.sp, color = Color.Gray)
                }
            } else if (termList.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("未能识别出有效文本", color = Color.Gray)
                }
            } else {
                // 滑动监听容器
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                    Column(Modifier.verticalScroll(scrollState)) {
                        // 提示用户
                        Text("识别结果 (智能分词)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            termList.forEachIndexed { index, item ->
                                val isSelected = selectedItems.contains(item)
                                SmartItemBox(
                                    text = item.text,
                                    isSelected = isSelected,
                                    onClick = { if (isSelected) selectedItems.remove(item) else selectedItems.add(item) },
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
private fun SmartItemBox(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates -> onPositioned(coordinates.boundsInParent()) }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp) // 增大一点点击区域
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}