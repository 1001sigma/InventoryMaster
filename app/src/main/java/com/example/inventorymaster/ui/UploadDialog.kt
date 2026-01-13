package com.example.inventorymaster.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun UploadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 默认给个提示，方便用户不用每次都输完整的
    var ip by remember { mutableStateOf("192.168.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📤 上传数据到电脑") },
        text = {
            Column {
                Text("请输入电脑服务端显示的 IP 地址：")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP 地址 (例如 192.168.1.5)") },
                    singleLine = true,
                    // 键盘优化：只显示数字和小数点
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(ip) },
                enabled = ip.length > 8 // 简单校验
            ) {
                Text("开始上传")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}