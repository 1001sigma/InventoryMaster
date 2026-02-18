package com.example.inventorymaster.ui.productManager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
//产品字典同步
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConflictScreen(
    conflicts: List<ProductConflict>,
    onToggle: (String) -> Unit,
    onSelectAll: (ConflictAction) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    // 拦截返回键，等同于取消
    BackHandler { onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("发现 ${conflicts.size} 条信息变更", style = MaterialTheme.typography.titleMedium)
                        Text("请确认是否更新基础信息库", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    TextButton(onClick = onConfirm) {
                        Text("确认合并", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "取消") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // 批量操作区
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onSelectAll(ConflictAction.OVERWRITE) },
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("全部覆盖(新)") }

                OutlinedButton(
                    onClick = { onSelectAll(ConflictAction.IGNORE) },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) { Text("全部忽略(旧)") }
            }

            // 列表区
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conflicts, key = { it.di }) { item ->
                    ConflictCard(item, onToggle)
                }
            }
        }
    }
}

@Composable
fun ConflictCard(item: ProductConflict, onToggle: (String) -> Unit) {
    val isOverwrite = item.resolveAction == ConflictAction.OVERWRITE
    val cardColor = if (isOverwrite) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface

    Card(
        border = if (isOverwrite) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部：DI 和 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.di, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isOverwrite) "覆盖" else "忽略",
                        style = MaterialTheme.typography.labelSmall,
                        color = if(isOverwrite) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isOverwrite,
                        onCheckedChange = { onToggle(item.di) },
                        thumbContent = {
                            if (isOverwrite) Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 差异对比行
            DiffRow("名称", item.oldProduct.productName, item.newProduct.productName)
            DiffRow("规格", item.oldProduct.specification, item.newProduct.specification)
            DiffRow("厂家", item.oldProduct.manufacturer, item.newProduct.manufacturer)
        }
    }
}

@Composable
fun DiffRow(label: String, oldVal: String?, newVal: String?) {
    val o = oldVal?.trim() ?: ""
    val n = newVal?.trim() ?: ""

    // 只有不同才显示
    if (o != n) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 旧值：灰色+删除线
                Text(
                    text = o.ifEmpty { "(空)" },
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )

                Icon(Icons.Default.ArrowForward, null, Modifier.size(14.dp), tint = Color.LightGray)

                // 新值：红色+加粗
                Text(
                    text = n.ifEmpty { "(空)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        }
    }
}