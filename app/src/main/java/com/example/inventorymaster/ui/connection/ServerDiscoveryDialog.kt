package com.example.inventorymaster.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.connection.DiscoveredServer
import com.example.inventorymaster.data.connection.ServerDiscoveryState
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.ui.theme.TouchTargetMin
import com.example.inventorymaster.utils.NetworkUtils

/**
 * 服务端自动发现弹窗（全局）
 *
 * 由 MainActivity 根部挂载，覆盖所有页面。三种形态：
 * 搜索中（Discovering）→ 多结果列表（Results）→ 未找到/手动输入（NotFound）。
 */
@Composable
fun ServerDiscoveryDialog(
    state: ServerDiscoveryState,
    onServerSelected: (DiscoveredServer) -> Unit,
    onManualInput: () -> Unit,
    onManualIpSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is ServerDiscoveryState.Hidden -> Unit

        is ServerDiscoveryState.Discovering -> {
            ModernAlertDialog(
                onDismiss = onDismiss,
                title = "连接服务端",
                icon = Icons.Default.Dns,
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "正在搜索局域网内的服务端…\n请确认手机与电脑连接同一 WiFi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(TouchTargetMin),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        is ServerDiscoveryState.Results -> {
            ModernAlertDialog(
                onDismiss = onDismiss,
                title = "找到多个服务端",
                icon = Icons.Default.Dns,
                content = {
                    Column {
                        state.servers.forEach { server ->
                            ServerRow(server = server, onSelected = { onServerSelected(server) })
                        }
                    }
                },
                confirmButton = {
                    ModernDialogButtonBar(
                        onConfirm = onManualInput,
                        onDismiss = onDismiss,
                        confirmText = "手动输入"
                    )
                }
            )
        }

        is ServerDiscoveryState.NotFound -> {
            // 仅以 isManual 作 key：校验失败更新 message 时保留用户已输入的 IP
            var ipText by remember(state.isManual) { mutableStateOf("") }

            ModernAlertDialog(
                onDismiss = onDismiss,
                title = "未找到服务端",
                icon = Icons.Default.Search,
                content = {
                    Column {
                        Text(
                            text = "没有搜索到服务端。请确认电脑上的服务端已启动、且与手机在同一 WiFi，或直接输入服务端 IP：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ModernDialogTextField(
                            value = ipText,
                            onValueChange = { ipText = it },
                            label = "服务器 IP",
                            placeholder = "例如 192.168.1.100",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            isError = state.message != null && !NetworkUtils.isValidIpAddress(ipText.trim()),
                            supportingText = state.message?.let { message ->
                                { Text(message, color = MaterialTheme.colorScheme.error) }
                            }
                        )
                    }
                },
                confirmButton = {
                    ModernDialogButtonBar(
                        onConfirm = { onManualIpSubmitted(ipText) },
                        onDismiss = onDismiss,
                        confirmText = "保存并连接",
                        confirmEnabled = NetworkUtils.isValidIpAddress(ipText.trim())
                    )
                }
            )
        }
    }
}

@Composable
private fun ServerRow(server: DiscoveredServer, onSelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onSelected)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${server.ip}:${server.httpPort}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
