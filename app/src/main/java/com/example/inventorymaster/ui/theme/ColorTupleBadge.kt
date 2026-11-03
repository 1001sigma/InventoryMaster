package com.example.inventorymaster.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 仿 ImageToolbox 风格的 3-Tone 切片色彩徽章。
 * 构图：
 * - 上半部（1/2 高度）：[primary] 主色
 * - 下半部左侧（1/4 区域）：[resolvedTertiary] 三级点缀色
 * - 下半部右侧（1/4 区域）：[resolvedSecondary] 辅助色
 *
 * 状态：
 * - 支持平滑色彩渐变过度动画
 * - 选中状态下中心浮现高对比圆盘与勾选符号
 * - 支持自定义中心槽位 content（如编辑铅笔、加号图标）
 */
@Composable
fun ColorTupleBadge(
    colorTuple: ColorTuple,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    isSelected: Boolean = false,
    borderWidth: Dp = if (isSelected) 2.5.dp else 1.dp,
    borderColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    elevation: Dp = if (isSelected) 3.dp else 0.dp,
    onClick: (() -> Unit)? = null,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val primaryAnim by animateColorAsState(targetValue = colorTuple.primary, label = "badgePrimary")
    val secondaryAnim by animateColorAsState(targetValue = colorTuple.resolvedSecondary, label = "badgeSecondary")
    val tertiaryAnim by animateColorAsState(targetValue = colorTuple.resolvedTertiary, label = "badgeTertiary")

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .border(borderWidth, borderColor, shape)
            .padding(borderWidth)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // 三色切片背景
        Column(modifier = Modifier.fillMaxSize()) {
            // 上半部：主色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(primaryAnim)
            )
            // 下半部：三级点缀色 (左) + 辅助色 (右)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(tertiaryAnim)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(secondaryAnim)
                )
            }
        }

        // 自定义中心内容或选中勾选状态
        if (content != null) {
            content()
        } else {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                val checkBg = if (colorTuple.primary.luminance() > 0.45f) {
                    Color.Black.copy(alpha = 0.65f)
                } else {
                    Color.White.copy(alpha = 0.85f)
                }
                val checkTint = if (colorTuple.primary.luminance() > 0.45f) {
                    Color.White
                } else {
                    Color(0xFF1E1E1E)
                }

                Box(
                    modifier = Modifier
                        .size(size * 0.48f)
                        .background(checkBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中",
                        tint = checkTint,
                        modifier = Modifier.size(size * 0.32f)
                    )
                }
            }
        }
    }
}
