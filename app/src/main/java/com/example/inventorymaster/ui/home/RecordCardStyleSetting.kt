package com.example.inventorymaster.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordUiModel
import com.example.inventorymaster.data.model.RecordCardConfig
import com.example.inventorymaster.data.model.RecordCardField
import com.example.inventorymaster.data.model.RecordCardSlot
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.inventory.components.RecordFieldIcon
import com.example.inventorymaster.ui.inventory.components.StockRecordItem

/**
 * 设置 → 盘存设置 → 记录卡片样式。
 * 直接复用 StockRecordItem 渲染示例卡片，保证预览与真实卡片布局一致；
 * 点击卡片上的信息位弹出字段选择器，即点即换即存。
 */
@Composable
fun RecordCardStyleSetting(
    config: RecordCardConfig,
    onSlotFieldChange: (RecordCardSlot, String) -> Unit,
    onReset: () -> Unit
) {
    var editingSlot by remember { mutableStateOf<RecordCardSlot?>(null) }

    Column {
        Text(
            text = "点击卡片上的字段，即可更换该位置显示的内容。同一字段只能出现在一个位置，替换后原位置自动留空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            StockRecordItem(
                uiModel = demoUiModel,
                cardConfig = config,
                onSlotClick = { editingSlot = it }
            )
        }

        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("恢复默认")
        }
    }

    editingSlot?.let { slot ->
        RecordFieldPickerDialog(
            slot = slot,
            config = config,
            onSelect = { fieldId ->
                onSlotFieldChange(slot, fieldId)
                editingSlot = null
            },
            onDismiss = { editingSlot = null }
        )
    }
}

/** 字段选择器：列出全部可配置字段，当前项打勾，被其他位置占用的项标注占用来源 */
@Composable
private fun RecordFieldPickerDialog(
    slot: RecordCardSlot,
    config: RecordCardConfig,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentField = config.fieldFor(slot)
    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "更换「${slot.title}」显示字段",
        content = {
            Column {
                Text(
                    text = "同一字段仅能显示在一处；选择已被占用的字段后，原位置将自动留空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(RecordCardField.entries) { field ->
                        val occupyingSlot = RecordCardSlot.entries
                            .firstOrNull { it != slot && config.fieldFor(it) == field }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(field.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RecordFieldIcon(field = field, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (field == currentField) FontWeight.Bold else FontWeight.Normal,
                                color = if (field == currentField) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (occupyingSlot != null) {
                                Text(
                                    text = "占用中：${occupyingSlot.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (field == currentField) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "当前字段",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
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

// ---- 预览示例数据（仅用于设置页展示，不入库）----

private const val DEMO_PRODUCT_KEY = "06947173450016|MAT-0086"

private val demoProduct = ProductBase(
    productKey = DEMO_PRODUCT_KEY,
    di = "06947173450016",
    productName = "骨科植入物螺钉（示例）",
    specification = "Ø3.5×40mm",
    model = "AXI-3.5",
    manufacturer = "示例医疗器械有限公司",
    registrationCert = "国械注准20203140123",
    materialCode = "MAT-0086",
    unit = "枚",
    categoryCode = "6846"
)

private val demoRecord = StockRecord(
    sessionId = 0L,
    productKey = DEMO_PRODUCT_KEY,
    batchNumber = "20240315A",
    expiryDate = System.currentTimeMillis() + 2L * 365 * 24 * 3600 * 1000,
    quantity = 10.0,
    location = "A区1号架",
    actualQuantity = null,
    remarks = null,
    sourceType = 0,
    operator = "张三"
)

private val demoUiModel = StockRecordUiModel(
    combined = StockRecordCombined(record = demoRecord, product = demoProduct)
)
