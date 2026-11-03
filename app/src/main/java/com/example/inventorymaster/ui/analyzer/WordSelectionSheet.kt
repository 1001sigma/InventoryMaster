package com.example.inventorymaster.ui.analyzer

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.example.inventorymaster.ocr.OcrText

data class SmartTerm(
    val id: String,
    val text: String,
    val isOriginal: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordSelectionSheet(
    rawText: OcrText,
    tokenizedWords: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val termList = tokenizedWords.mapIndexed { index, word ->
        SmartTerm("term_$index", word)
    }
    val selectedItems = remember { mutableStateListOf<SmartTerm>() }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    val previewText = selectedItems.joinToString("") { it.text }

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
                .fillMaxHeight(0.7f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("智能分词选择", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                FilledIconButton(
                    onClick = { onConfirm(previewText) },
                    enabled = previewText.isNotEmpty(),
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.Default.Check, null) }
            }

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

            if (termList.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("未能识别出有效文本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
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
                        Text("识别结果 (智能分词)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))

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
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}
