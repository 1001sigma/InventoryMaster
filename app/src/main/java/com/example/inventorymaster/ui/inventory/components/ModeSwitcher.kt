package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModeSwitcher(isInventoryMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(if (isInventoryMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onModeChange(true) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "盘库模式",
                color = if (isInventoryMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(if (!isInventoryMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onModeChange(false) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "查询模式",
                color = if (!isInventoryMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
