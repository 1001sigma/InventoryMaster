package com.example.inventorymaster.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.ui.components.ModernAlertDialog

@Composable
fun SyncDialog(
    onDismiss: () -> Unit,
    onUpload: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    var ip by remember { mutableStateOf("192.168.") }
    var selectedTab by remember { mutableStateOf(0) }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "☁️ 数据同步中心",
        icon = Icons.Default.CloudUpload,
        modifier = Modifier.fillMaxWidth(0.9f),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 📍 第一部分：TabRow（优化） - 行 40-56
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "⬆️ 上传",
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "⬇️ 下载",
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 📍 第二部分：IP 输入框（优化） - 行 60-70
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("电脑 IP 地址") },
                    placeholder = { Text("例如 192.168.1.5") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 📍 第三部分：提示卡片（新增！更现代） - 行 74-90
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTab == 0) {
                            Color(0x1A4CAF50)  // 浅绿 (上传)
                        } else {
                            Color(0x1AE53935)  // 浅红 (下载)
                        }
                    )
                ) {
                    Text(
                        text = if (selectedTab == 0) {
                            "✓ 将本地盘点任务数据发送到电脑备份\n" +
                                    "数据安全，支持随时恢复"
                        } else {
                            "⚠️ 将覆盖本地任务数据\n" +
                                    "请确认已备份重要信息"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = if (selectedTab == 0) {
                            Color(0xFF2E7D32)  // 深绿
                        } else {
                            Color(0xFFC62828)  // 深红
                        },
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) onUpload(ip) else onDownload(ip)
                },
                enabled = ip.length > 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (selectedTab == 0) "开始上传" else "开始下载",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        }
    )
}