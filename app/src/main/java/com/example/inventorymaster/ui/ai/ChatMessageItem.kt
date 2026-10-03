package com.example.inventorymaster.ui.ai

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.viewmodel.ChatMessage
import kotlinx.coroutines.delay

/**
 * 消息气泡（v3 二期 UI 简化 + P0 交互增强）：
 * - 无头像（AI 与用户侧都已去除）；
 * - 仅用户消息保留气泡（右侧主色）用以区分；
 * - AI 回复无气泡，Markdown 直接铺满行宽；工具调用为小字提示行；错误为浅红卡片（可重试）。
 * - 等待反馈：三点呼吸动画（docs/AI_UI_OPTIMIZATION.md P0-2）；
 * - AI 最终回复下方极简操作条：复制 / 重新生成（P0-4）——复制仅此入口，无长按复制。
 * - 点击 AI 消息切换思维链展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    showThinking: Boolean = true,
    isProcessing: Boolean = false,
    canRetry: Boolean = false,
    onRegenerate: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val isUser = message.isUser
    val isToolCall = message.isToolCall
    val isError = message.role == "error"
    val clipboardManager = LocalClipboardManager.current
    val copyText = {
        clipboardManager.setText(AnnotatedString(message.content))
    }
    val expandable = showThinking && message.detail != null
    var expanded by remember { mutableStateOf(false) }
    val detail = message.detail

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 4.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (message.isLoading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
        } else {
            // 点击仅用于切换思维链展开：仅在确有详情可展开时挂 clickable，且去涟漪
            // （indication=null）——整条消息可点面积大，按下/长按/滚动起手的矩形波纹很突兀
            val messageInteraction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expandable) Modifier.clickable(
                            interactionSource = messageInteraction,
                            indication = null,
                            onClick = { expanded = !expanded }
                        ) else Modifier
                    )
            ) {
                when {
                    isToolCall -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .alpha(0.6f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .alpha(0.8f)
                                .weight(1f, fill = false)
                        )
                        if (expandable) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (expanded) "收起 ▴" else "详情 ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    isError -> ErrorCard(
                        content = message.content,
                        canRetry = canRetry,
                        onRetry = onRetry
                    )
                    else -> {
                        if (message.isLoading && message.content.isBlank()) {
                            // 首字等待：三点呼吸（TTFT 期间）
                            BreathingDots(Modifier.padding(vertical = 6.dp))
                        } else {
                            MarkdownText(text = message.content)
                            if (message.isLoading) {
                                // 流式生成中：内容后跟呼吸点
                                BreathingDots(Modifier.padding(top = 6.dp))
                            } else {
                                // 最终回复：淡入操作条（复制/重新生成）
                                MessageActionBar(
                                    isProcessing = isProcessing,
                                    onCopy = copyText,
                                    onRegenerate = onRegenerate
                                )
                            }
                        }
                    }
                }
                if (expandable && expanded && detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.85f)
                    )
                }
            }
        }
    }
}

/** 等待指示：三点呼吸（周期 1.2s，相位错开 200ms） */
@Composable
private fun BreathingDots(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "waitingDots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200)
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

/** AI 最终回复下的极简操作条：复制（点击变 ✓ + "已复制"）/ 重新生成 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionBar(
    isProcessing: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            IconButton(
                onClick = {
                    onCopy()
                    copied = true
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (copied) "已复制" else "复制全文",
                    modifier = Modifier.size(15.dp),
                    tint = if (copied) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onRegenerate,
                enabled = !isProcessing,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "重新生成",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (isProcessing) 0.3f else 0.7f)
                )
            }
        }
        if (copied) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                "已复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 错误卡片：浅红边框 + 具体错误文案 + 一键重试（docs/AI_UI_OPTIMIZATION.md P0-3） */
@Composable
private fun ErrorCard(
    content: String,
    canRetry: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (canRetry) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
