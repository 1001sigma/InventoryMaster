package com.example.inventorymaster.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.theme.TouchTargetMin

@Composable
fun DownloadConflictDialog(
    dirtyCount: Int,
    onForceDownload: () -> Unit,
    onPushThenDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "本地有未同步数据",
        icon = Icons.Default.Warning,
        modifier = Modifier.fillMaxWidth(0.9f),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "当前任务有 $dirtyCount 条未同步的修改，全量下载将覆盖这些数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "请选择操作方式：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onPushThenDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("先推送再下载", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onForceDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTargetMin)
                ) {
                    Text("强制覆盖下载", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTargetMin)
                ) {
                    Text("取消")
                }
            }
        }
    )
}
