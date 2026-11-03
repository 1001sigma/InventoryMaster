package com.example.inventorymaster.batchscanner.ocr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.inventorymaster.batchscanner.ocr.models.ExtractSource
import com.example.inventorymaster.batchscanner.ocr.models.OcrExtractedData
import com.example.inventorymaster.batchscanner.ocr.models.OcrStatus
import com.example.inventorymaster.ui.components.AppTextField

/**
 * OCR 结果确认弹窗。
 * 显示提取的批号、生产日期、有效期，用户可编辑后确认。
 *
 * @param data OCR 提取的结构化数据
 * @param onConfirm 用户确认，返回编辑后的数据
 * @param onDismiss 关闭弹窗
 */
@Composable
fun OcrResultDialog(
    data: OcrExtractedData,
    onConfirm: (OcrExtractedData) -> Unit,
    onDismiss: () -> Unit
) {
    var batch by remember(data.batch) { mutableStateOf(data.batch) }
    var prodDate by remember(data.prodDate) { mutableStateOf(data.prodDate ?: "") }
    var expDate by remember(data.expDate) { mutableStateOf(data.expDate ?: "") }
    var showRawText by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Text(
                    text = "OCR 识别结果",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 来源标签
                Text(
                    text = "来源：${if (data.source == ExtractSource.AI) "AI 识别" else "规则引擎"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 批号
                AppTextField(
                    value = batch,
                    onValueChange = { batch = it },
                    label = "批号",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 生产日期
                AppTextField(
                    value = prodDate,
                    onValueChange = { prodDate = it },
                    label = "生产日期",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "yyyy-MM-dd",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 有效期
                AppTextField(
                    value = expDate,
                    onValueChange = { expDate = it },
                    label = "有效期",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "yyyy-MM-dd",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 原始文本折叠
                TextButton(
                    onClick = { showRawText = !showRawText },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(if (showRawText) "收起原始文本" else "查看原始文本")
                }

                if (showRawText) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = data.rawText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                data.copy(
                                    batch = batch,
                                    prodDate = prodDate.ifBlank { null },
                                    expDate = expDate.ifBlank { null },
                                    status = OcrStatus.USED
                                )
                            )
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}
