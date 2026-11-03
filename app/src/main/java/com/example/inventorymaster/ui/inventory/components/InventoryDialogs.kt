package com.example.inventorymaster.ui.inventory.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.ui.InventoryCalcDialog
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.ui.components.ProductInfoCard
import com.example.inventorymaster.ui.theme.TouchTargetMin
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit,
    onConfirm: (StockRecord) -> Unit,
    sessionId: Long,
    existingRecord: StockRecord? = null
) {
    var productKey by remember { mutableStateOf(existingRecord?.productKey ?: "") }
    var batch by remember { mutableStateOf(existingRecord?.batchNumber ?: "") }
    var qty by remember { mutableStateOf(existingRecord?.quantity?.toString() ?: "") }
    var location by remember { mutableStateOf(existingRecord?.location ?: "") }
    var expiry by remember { mutableStateOf("") }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = if (existingRecord == null) "新增记录" else "修改记录",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModernDialogTextField(
                    value = productKey,
                    onValueChange = { productKey = it },
                    label = "产品Key/条码",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = batch,
                    onValueChange = { batch = it },
                    label = "批号",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = expiry,
                    onValueChange = { expiry = it },
                    label = "效期 (如 20251231)",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
                ModernDialogTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "库位",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "数量",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    if (productKey.isNotBlank() && qty.isNotBlank()) {
                        val newRecord = StockRecord(
                            sessionId = sessionId,
                            productKey = productKey,
                            batchNumber = batch,
                            expiryDate = expiry.toLongOrNull() ?: 0L,
                            quantity = qty.toDoubleOrNull() ?: 0.0,
                            location = location,
                            sourceType = 0
                        )
                        onConfirm(newRecord)
                    }
                },
                onDismiss = onDismiss,
                confirmText = "保存"
            )
        },
        dismissButton = null
    )
}

@Composable
fun RecordDetailDialog(combined: StockRecordCombined, onDismiss: () -> Unit) {
    val record = combined.record
    val product = combined.product

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "库存详情",
        content = {
            Column {
                if (product != null) {
                    ProductInfoCard(
                        productName = product.productName,
                        di = product.di,
                        specification = product.specification,
                        model = product.model,
                        registrationCert = product.registrationCert,
                        manufacturer = product.manufacturer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                DetailRow("产品Key", record.productKey)
                DetailRow("物料编码", product?.materialCode)
                DetailRow("批号", record.batchNumber)
                DetailRow("效期", record.expiryDate.toString())
                DetailRow("仓库名称", record.location)
                DetailRow("数量", record.quantity.toString())
                DetailRow("实际数量", record.actualQuantity?.toString())
                DetailRow("备注", record.remarks)
                DetailRow("数据来源", when (record.sourceType) {
                    0 -> "手动盘点"
                    1 -> "Excel导入"
                    2 -> "云端下载"
                    else -> "其它"
                })
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
private fun DetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun RecordOptionDialog(
    onDismiss: () -> Unit,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "请选择操作",
        content = {
            Text("您想对这条记录做什么？")
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // M9：查询模式点击看详情的入口随模式切换移除——详情改从本弹窗进入
                OutlinedButton(
                    onClick = onShowDetail,
                    modifier = Modifier.height(TouchTargetMin),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("查看详情")
                }
                if (canDelete) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.height(TouchTargetMin),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Text("删除记录", color = MaterialTheme.colorScheme.error)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.height(TouchTargetMin),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("编辑修改")
                }
            }
        },
        dismissButton = null
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
    var isVerified by remember {
        mutableStateOf(record.isVerified || record.actualQuantity != null || record.remarks?.contains("已查验") == true)
    }
    var actualQtyStr by remember {
        mutableStateOf(record.actualQuantity?.toString() ?: "")
    }
    var remarks by remember { mutableStateOf(record.remarks ?: "") }
    var isError by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }

    if (showCalc) {
        val defaultInit = actualQtyStr.ifBlank {
            if (record.quantity > 0) {
                java.text.DecimalFormat("0.####", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(record.quantity)
            } else ""
        }
        InventoryCalcDialog(
            initialValue = defaultInit,
            onDismiss = { showCalc = false },
            onConfirm = { resultString ->
                actualQtyStr = resultString
                isError = false
            }
        )
    }

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "库存查验",
        titleTrailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isVerified) "已查验" else "未查验",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isVerified,
                    onCheckedChange = { checked ->
                        isVerified = checked
                        if (checked) {
                            // 由关切换到开：若实盘数量为空，自动带出账面数量
                            if (actualQtyStr.isBlank() && record.quantity > 0) {
                                actualQtyStr = if (record.quantity == record.quantity.toLong().toDouble()) {
                                    record.quantity.toLong().toString()
                                } else {
                                    record.quantity.toString()
                                }
                            }
                        } else {
                            // 由开切换到关：若实际数量非空，将实际数量写入备注后再清除
                            if (actualQtyStr.isNotBlank()) {
                                val backup = "原实盘: ${actualQtyStr.trim()}"
                                remarks = if (remarks.isBlank()) backup else "$backup; $remarks"
                                actualQtyStr = ""
                            }
                        }
                    }
                )
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductInfoCard(
                    productName = product?.productName ?: "未知产品 (${record.productKey})",
                    di = product?.di,
                    specification = product?.specification,
                    model = product?.model,
                    registrationCert = product?.registrationCert,
                    manufacturer = product?.manufacturer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "批号",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(record.batchNumber, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "账面数量",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${record.quantity} ${product?.unit ?: ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider()

                ModernDialogTextField(
                    value = actualQtyStr,
                    onValueChange = {
                        actualQtyStr = it
                        isError = false
                    },
                    label = "实盘数量 (Actual)",
                    placeholder = "请输入你数出来的数量",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    isError = isError,
                    trailingIcon = {
                        IconButton(onClick = { showCalc = true }) {
                            Icon(Icons.Default.Calculate, contentDescription = null)
                        }
                    },
                    supportingText = {
                        val inputVal = actualQtyStr.toDoubleOrNull()
                        if (inputVal != null && inputVal != record.quantity) {
                            Text(
                                "差异: ${inputVal - record.quantity}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                ModernDialogTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = "备注说明",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    val newActual = actualQtyStr.toDoubleOrNull()
                    if (actualQtyStr.isNotBlank() && newActual == null) {
                        isError = true
                    } else {
                        // 清除历史前缀"已查验[...]"（如果有），使备注保持纯净
                        val prefixRegex = Regex("^已查验\\[.*?][^;]*;?\\s*")
                        val cleanRemarks = remarks.replaceFirst(prefixRegex, "").trim()
                        val updated = record.copy(
                            isVerified = isVerified,
                            actualQuantity = newActual,
                            remarks = cleanRemarks.ifBlank { null },
                            syncStatus = 1,
                            lastUpdateTime = System.currentTimeMillis()
                        )
                        onConfirm(updated)
                    }
                },
                onDismiss = onDismiss,
                confirmText = "确认保存"
            )
        }
    )
}
