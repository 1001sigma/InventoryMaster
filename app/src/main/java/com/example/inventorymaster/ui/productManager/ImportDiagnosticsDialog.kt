package com.example.inventorymaster.ui.productManager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.model.DuplicateGroup
import com.example.inventorymaster.data.model.ImportDiagnostics
import com.example.inventorymaster.data.model.SkippedRow
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar

@Composable
fun ImportDiagnosticsDialog(
    diagnostics: ImportDiagnostics,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "导入数据检查",
        showContentCard = false,
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 组合键重复提示
                if (diagnostics.duplicateGroups.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                "以下 ${diagnostics.duplicateGroups.size} 组产品存在重复",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "DI与物料编码完全相同的多行数据将仅保留最后一条，前面的会被覆盖",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(diagnostics.duplicateGroups) { group ->
                        DuplicateGroupCard(group)
                    }
                }

                // 跳过行提示
                if (diagnostics.skippedRows.isNotEmpty()) {
                    item {
                        Column {
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "以下 ${diagnostics.skippedRows.size} 行被跳过未导入",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    items(diagnostics.skippedRows) { row ->
                        SkippedRowCard(row)
                    }
                }

                // 汇总
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    val dupCount = diagnostics.duplicateGroups.sumOf { it.entries.size - 1 }
                    val skipCount = diagnostics.skippedRows.size
                    Text(
                        "共 $dupCount 条将被覆盖，$skipCount 条将被跳过",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                confirmText = "确认导入"
            )
        }
    )
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "组合键: ${group.keyLabel}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            group.entries.forEachIndexed { index, entry ->
                val isLast = index == group.entries.lastIndex
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLast) "保留" else "覆盖",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = "行${entry.rowNumber}: ${entry.productName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = entry.manufacturer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SkippedRowCard(row: SkippedRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "行${row.rowNumber}: ${row.productName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = row.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
