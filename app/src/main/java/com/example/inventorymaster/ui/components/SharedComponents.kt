package com.example.inventorymaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.theme.SpaceXs
import com.example.inventorymaster.ui.theme.SpaceXxs
import com.example.inventorymaster.ui.theme.SpaceMd
import com.example.inventorymaster.ui.theme.SpaceSm
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning
import com.example.inventorymaster.ui.theme.shiftLightness

/**
 * 返回在给定背景色上可读的纯色前景（深字或白字）。
 * 阈值 0.35：低亮度背景配白字，高亮度背景配深字，
 * 保证实色语义徽章（近效期/已过期等）在明暗两种模式下都保持可读。
 */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.35f) Color(0xFF1A1A1A) else Color.White

/**
 * 语义色的模式自适应前景色：浅色模式加深、深色模式提亮。
 * 用于把语义色作为文字放在其 12%~15% 透明度着色底上时保持 ≥4.5:1 对比度。
 */
@Composable
fun semanticFg(base: Color): Color =
    shiftLightness(base, if (isSystemInDarkTheme()) 0.72f else 0.32f)

// =============================================================================
// StatusBadge — semantic status indicator pill
// =============================================================================

sealed class BadgeSeverity {
    data object Success : BadgeSeverity()
    data object Warning : BadgeSeverity()
    data object Error : BadgeSeverity()

    /** 自定义配色：直接指定背景/前景，对比度由调用方保证 */
    data class Custom(val background: Color, val foreground: Color) : BadgeSeverity()
}

@Composable
fun StatusBadge(
    text: String,
    severity: BadgeSeverity,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val bgColor: Color
    val fgColor: Color
    when (severity) {
        is BadgeSeverity.Custom -> {
            bgColor = severity.background
            fgColor = severity.foreground
        }
        else -> {
            val base = when (severity) {
                BadgeSeverity.Success -> StatusSuccess
                BadgeSeverity.Warning -> StatusWarning
                else -> StatusError
            }
            // 复用模式自适应前景色，保证文字在 12% 着色底上满足对比度
            bgColor = base.copy(alpha = 0.12f)
            fgColor = semanticFg(base)
        }
    }
    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = SpaceXs, vertical = SpaceXxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(SpaceXxs))
        }
        Text(
            text = text,
            color = fgColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// =============================================================================
// EmptyStateView — consistent empty state display
// =============================================================================

@Composable
fun EmptyStateView(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(SpaceMd))
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(SpaceSm))
                action()
            }
        }
    }
}

// =============================================================================
// SectionHeader — consistent section title
// =============================================================================

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .padding(horizontal = SpaceMd, vertical = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

// =============================================================================
// StandardCardDefaults — shared card styling constants
// =============================================================================

object StandardCardDefaults {
    val shape = RoundedCornerShape(12.dp)
    val elevation @Composable get() = 1.dp
    val contentPadding = 16.dp
    val listItemPadding = 16.dp
}
