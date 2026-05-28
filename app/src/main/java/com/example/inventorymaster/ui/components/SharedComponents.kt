package com.example.inventorymaster.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning

// =============================================================================
// StatusBadge — semantic status indicator pill
// =============================================================================

sealed class BadgeSeverity(val bgColor: Color, val fgColor: Color) {
    data object Success : BadgeSeverity(StatusSuccess.copy(alpha = 0.12f), StatusSuccess)
    data object Warning : BadgeSeverity(StatusWarning.copy(alpha = 0.12f), StatusWarning)
    data object Error : BadgeSeverity(StatusError.copy(alpha = 0.12f), StatusError)
    data class Custom(val background: Color, val foreground: Color) : BadgeSeverity(background, foreground)
}

@Composable
fun StatusBadge(
    text: String,
    severity: BadgeSeverity,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(severity.bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = severity.fgColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = severity.fgColor,
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
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(12.dp))
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
