package com.example.inventorymaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.dto.SessionDto
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudTaskDialog(
    onDismiss: () -> Unit,
    cloudSessions: List<SessionDto>, // 传入 ViewModel 里的列表
    onFetchList: (String) -> Unit,   // 点击“刷新/连接”的回调
    onJoinTask: (String, Long) -> Unit // 点击“加入”的回调 (IP, ID)
) {
    var ip by remember { mutableStateOf("192.168.") }
    // 标记是否已经点过连接，用来切换界面显示
    var isConnected by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("☁️ 云端任务大厅") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)) {

                // 1. 顶部 IP 输入栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("电脑 IP") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onFetchList(ip)
                            isConnected = true
                        },
                        modifier = Modifier.size(48.dp) // 增大一点触控面积
                    ) {
                        Icon(Icons.Default.Refresh, "刷新列表")
                    }
                }

                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // 2. 任务列表区域
                if (!isConnected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入 IP 并点击刷新按钮\n获取云端任务", color = Color.Gray)
                    }
                } else if (cloudSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无任务，请在电脑端上传", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cloudSessions) { task ->
                            TaskItem(task = task, onClick = { onJoinTask(ip, task.id) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun TaskItem(task: SessionDto, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    color = Color.Gray
                )
                Text(
                    text = "时间: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(task.date))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.CloudDownload, contentDescription = "加入", tint = MaterialTheme.colorScheme.primary)
        }
    }
}