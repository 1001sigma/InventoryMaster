package com.example.inventorymaster.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SyncDialog(
    onDismiss: () -> Unit,
    onUpload: (String) -> Unit, // 上传回调 (IP)
    onDownload: (String, String) -> Unit // 下载回调 (IP, SessionID)
) {
    var ip by remember { mutableStateOf("192.168.") }
    var sessionIdInput by remember { mutableStateOf("") }

    // 0 = 上传模式, 1 = 下载模式
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("☁️ 数据同步中心") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. 顶部选项卡
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("上传") },
                        icon = { Icon(Icons.Default.CloudUpload, null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("下载") },
                        icon = { Icon(Icons.Default.CloudDownload, null) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 公共部分：IP 输入框
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("电脑 IP 地址") },
                    placeholder = { Text("例如 192.168.1.5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. 根据选项卡显示不同内容
                if (selectedTab == 0) {
                    // --- 上传模式 ---
                    Text(
                        "将当前盘点任务的数据发送到电脑备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // --- 下载模式 ---
                    OutlinedTextField(
                        value = sessionIdInput,
                        onValueChange = { sessionIdInput = it },
                        label = { Text("目标任务 ID (SessionID)") },
                        placeholder = { Text("电脑网页显示的 ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "⚠️ 注意：将覆盖本地相同 Session 的数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onUpload(ip)
                    } else {
                        onDownload(ip, sessionIdInput)
                    }
                },
                // 简单的非空校验
                enabled = ip.length > 8 && (selectedTab == 0 || sessionIdInput.isNotEmpty())
            ) {
                Text(if (selectedTab == 0) "开始上传" else "开始下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}