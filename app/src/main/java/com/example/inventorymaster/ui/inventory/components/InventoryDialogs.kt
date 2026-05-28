package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.ui.InventoryCalcDialog
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit,
    onConfirm: (StockRecord) -> Unit,
    sessionId: Long,
    existingRecord: StockRecord? = null
) {
    var di by remember { mutableStateOf(existingRecord?.di ?: "") }
    var batch by remember { mutableStateOf(existingRecord?.batchNumber ?: "") }
    var qty by remember { mutableStateOf(existingRecord?.quantity?.toString() ?: "") }
    var location by remember { mutableStateOf(existingRecord?.location ?: "") }
    var expiry by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingRecord == null) "新增记录" else "修改记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = di, onValueChange = { di = it }, label = { Text("产品 DI/条码") })
                OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("批号") })
                OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("效期 (如 20251231)") })
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("库位") })
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (di.isNotBlank() && qty.isNotBlank()) {
                    val newRecord = StockRecord(
                        sessionId = sessionId,
                        di = di,
                        batchNumber = batch,
                        expiryDate = expiry.toLongOrNull() ?: 0L,
                        quantity = qty.toDoubleOrNull() ?: 0.0,
                        location = location
                    )
                    onConfirm(newRecord)
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun RecordDetailDialog(combined: StockRecordCombined, onDismiss: () -> Unit) {
    val record = combined.record
    val product = combined.product
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("库存详情") },
        text = {
            Column {
                if (product != null) {
                    Text("物料名称: ${product.productName}")
                }
                Text("UDI: ${record.di}")
                if (product != null) {
                    Text("物料编码: ${product.materialCode}")
                }
                Text("批号: ${record.batchNumber}")
                Text("效期: ${record.expiryDate}")
                Text("仓库名称: ${record.location}")
                Text("数量: ${record.quantity}")
                Text("实际数量: ${record.actualQuantity}")
                Text("备注: ${record.remarks}")
                Text("数据来源: ${when(record.sourceType) {
                        0 -> "手动盘点"
                        1 -> "Excel导入"
                        2 -> "云端下载"
                        else -> "其它"
                }}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun RecordOptionDialog(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请选择操作") },
        text = { Text("您想对这条记录做什么？") },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text("编辑修改")
            }
        },
        dismissButton = {
            if (canDelete) {
                TextButton(onClick = onDelete) {
                    Text("删除记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun AuditRecordDialog(
    combined: StockRecordCombined,
    onDismiss: () -> Unit,
    onConfirm: (StockRecord) -> Unit,
) {
    val record = combined.record
    val product = combined.product
    var actualQtyStr by remember {
        mutableStateOf(record.actualQuantity?.toString() ?: "")
    }
    var remarks by remember { mutableStateOf(record.remarks ?: "") }
    var isError by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }

    if (showCalc) {
        InventoryCalcDialog(
            onDismiss = { showCalc = false },
            onConfirm = { resultString ->
                actualQtyStr = resultString
                isError = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("库存查验") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                    Text(
                        text = product?.productName ?: "未知产品 (${record.di})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UDI/物料编码: ${product?.di ?: ""} ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "规格: ${product?.specification ?: "-"} ${product?.model ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "注册证号: ${product?.registrationCert ?: ""} ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "厂家: ${product?.manufacturer ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("批号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.batchNumber, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("账面数量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${record.quantity} ${product?.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = actualQtyStr,
                    onValueChange = {
                        actualQtyStr = it
                        isError = false
                    },
                    label = { Text("实盘数量 (Actual)") },
                    placeholder = { Text("请输入你数出来的数量") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showCalc = true }) {
                            Icon(Icons.Default.Calculate, contentDescription = null)
                        }
                    },
                    supportingText = {
                        val inputVal = actualQtyStr.toDoubleOrNull()
                        if (inputVal != null && inputVal != record.quantity) {
                            Text(
                                "⚠️ 差异: ${inputVal - record.quantity}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("备注说明") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(record.lastUpdateTime)
                    val newActual = actualQtyStr.toDoubleOrNull()
                    val prefixRegex = Regex("^已查验\\[.*?][^;]*;?\\s*")
                    if (actualQtyStr.isNotBlank() && newActual == null) {
                        isError = true
                    } else {
                        val processedRemarks = when {
                            actualQtyStr.isBlank() -> remarks
                            actualQtyStr.isNotBlank() -> {
                                val pureRemarks = remarks.replaceFirst(prefixRegex, "").trim()
                                "已查验[$timestamp]${record.operator}; $pureRemarks"
                            }
                            else -> remarks
                        }
                        val updated = record.copy(
                            actualQuantity = newActual,
                            remarks = processedRemarks.trim()
                        )
                        onConfirm(updated)
                    }
                }
            ) {
                Text("确认保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
