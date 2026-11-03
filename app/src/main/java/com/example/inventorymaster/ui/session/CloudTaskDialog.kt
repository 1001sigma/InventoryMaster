package com.example.inventorymaster.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.StandardCardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.dto.SessionDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudTaskDialog(
    onDismiss: () -> Unit,
    cloudSessions: List<SessionDto>,
    onFetchList: (String, String) -> Unit,
    onJoinTask: (String, String, Long) -> Unit,
    initialIp: String = "192.168.",
    initialToken: String = ""
) {
    var ip by remember { mutableStateOf(initialIp) }
    var token by remember { mutableStateOf(initialToken) }
    var isConnected by remember { mutableStateOf(false) }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "云端任务大厅",
        showContentCard = false,
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    ModernDialogTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = "电脑 IP",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onFetchList(ip, token)
                            isConnected = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "刷新列表")
                    }
                }

                ModernDialogTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = "认证 Token",
                    placeholder = "首次连接请输入服务器显示的 Token",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                if (!isConnected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入 IP 和 Token 并点击刷新按钮\n获取云端任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (cloudSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无任务，请在电脑端上传", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cloudSessions) { task ->
                            TaskItem(task = task, onClick = { onJoinTask(ip, token, task.id) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onDismiss,
                confirmText = "关闭",
                dismissText = "",
                onDismiss = null
            )
        }
    )
}

@Composable
fun TaskItem(task: SessionDto, onClick: () -> Unit) {
    Card(
        shape = StandardCardDefaults.shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${task.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "时间: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(task.date))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.CloudDownload, contentDescription = "加入", tint = MaterialTheme.colorScheme.primary)
        }
    }
}