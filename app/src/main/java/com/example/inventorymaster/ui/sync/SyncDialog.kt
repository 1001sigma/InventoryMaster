package com.example.inventorymaster.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.ui.theme.StatusError
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.TouchTargetMin

@Composable
fun SyncDialog(
    onDismiss: () -> Unit,
    onUpload: (String, String) -> Unit,
    onDownload: (String, String) -> Unit,
    initialIp: String = "192.168.",
    initialToken: String = "",
    uploadLocked: Boolean = false,  // M7（R5）：归档任务只允许下载；待开始任务同样锁定上传
    lockReason: String = "已归档"   // 锁定原因，用于 Tab 文案（"已归档" / "等待开始"）
) {
    var ip by remember { mutableStateOf(initialIp) }
    var token by remember { mutableStateOf(initialToken) }
    var selectedTab by remember { mutableStateOf(if (uploadLocked) 1 else 0) }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "数据同步中心",
        icon = Icons.Default.CloudUpload,
        modifier = Modifier.fillMaxWidth(0.9f),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        enabled = !uploadLocked,  // M7：归档/待开始任务上传置灰
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (uploadLocked) "上传($lockReason)" else "上传",
                                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "下载",
                                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ModernDialogTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "电脑 IP 地址",
                    placeholder = "例如 192.168.1.5",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                ModernDialogTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = "认证 Token",
                    placeholder = "首次连接请输入服务器显示的 Token",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTab == 0) {
                            StatusSuccess.copy(alpha = 0.12f)
                        } else {
                            StatusError.copy(alpha = 0.12f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (selectedTab == 0) StatusSuccess else StatusError,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (selectedTab == 0) {
                                "将本地盘点任务数据发送到电脑备份\n数据安全，支持随时恢复"
                            } else {
                                "将覆盖本地任务数据\n请确认已备份重要信息"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedTab == 0) {
                                StatusSuccess
                            } else {
                                StatusError
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0 && !uploadLocked) onUpload(ip, token) else onDownload(ip, token)
                },
                enabled = ip.length > 8 && token.isNotBlank() && !(selectedTab == 0 && uploadLocked),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTargetMin),
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
                modifier = Modifier.height(TouchTargetMin),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        }
    )
}