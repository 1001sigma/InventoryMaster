package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.utils.ExcelUtils

private data class FieldDef(
    val fieldType: ExcelUtils.FieldType,
    val label: String
)

private val MAPPABLE_FIELDS = listOf(
    FieldDef(ExcelUtils.FieldType.DI, "条码/DI"),
    FieldDef(ExcelUtils.FieldType.MAT_CODE, "物料编码"),
    FieldDef(ExcelUtils.FieldType.NAME, "名称"),
    FieldDef(ExcelUtils.FieldType.SPEC, "规格"),
    FieldDef(ExcelUtils.FieldType.MODEL, "型号"),
    FieldDef(ExcelUtils.FieldType.MFR, "厂家"),
    FieldDef(ExcelUtils.FieldType.REG_CERT, "注册证"),
    FieldDef(ExcelUtils.FieldType.UNIT, "单位"),
    FieldDef(ExcelUtils.FieldType.BATCH, "批号"),
    FieldDef(ExcelUtils.FieldType.EXPIRY, "效期"),
    FieldDef(ExcelUtils.FieldType.QTY, "数量"),
    FieldDef(ExcelUtils.FieldType.LOC, "库位"),
    FieldDef(ExcelUtils.FieldType.ACTUAL_QTY, "实际数量"),
    FieldDef(ExcelUtils.FieldType.MEMO, "备注")
)

@Composable
fun MappingConfigDialog(
    headers: List<String>,
    initialMapping: Map<Int, ExcelUtils.FieldType> = emptyMap(),
    initialTemplateName: String = "",
    existingTemplateId: String? = null,
    onConfirm: (mapping: Map<Int, ExcelUtils.FieldType>, saveAsTemplateName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 关键词默认值：header[N] 的推荐是 FieldType.X → 预选 (N, X)
    val keywordDefaults = remember(headers) {
        val map = mutableMapOf<Int, ExcelUtils.FieldType>()
        headers.forEachIndexed { idx, header ->
            val ft = ExcelUtils.suggestFieldType(header)
            if (ft != ExcelUtils.FieldType.UNKNOWN) {
                map[idx] = ft
            }
        }
        map
    }

    // 反向映射：FieldType → columnIndex (-1 = 不映射)
    // 如果传入了模板映射则用它初始化，否则用关键词默认值填充
    val selections = remember {
        val map = mutableStateMapOf<ExcelUtils.FieldType, Int>()
        if (initialMapping.isNotEmpty()) {
            for ((colIdx, ft) in initialMapping) {
                map[ft] = colIdx
            }
        } else {
            for ((colIdx, ft) in keywordDefaults) {
                map[ft] = colIdx
            }
        }
        map
    }

    var templateName by remember { mutableStateOf(initialTemplateName) }
    var saveTemplate by remember { mutableStateOf(initialTemplateName.isNotBlank()) }
    var expandedField by remember { mutableStateOf<ExcelUtils.FieldType?>(null) }

    // 为每个 header 计算关键词推荐（供下拉菜单显示推荐标记用）
    val headerSuggestions = remember(headers) {
        headers.map { ExcelUtils.suggestFieldType(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("列映射配置") },
        text = {
            Column {
                // 模板名称输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("模板名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { saveTemplate = !saveTemplate }) {
                        Text(
                            if (saveTemplate) "已勾选保存" else "保存模板",
                            color = if (saveTemplate) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 字段映射列表
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(MAPPABLE_FIELDS) { fieldDef ->
                        val fieldType = fieldDef.fieldType
                        val selectedIdx = selections[fieldType]
                            ?: keywordDefaults.entries.firstOrNull { it.value == fieldType }?.key
                            ?: -2  // -2 = 未选择，回退关键词兜底

                        val displayText = when {
                            selectedIdx == -2 -> "(自动识别)"
                            selectedIdx == -1 -> "(不映射)"
                            selectedIdx in headers.indices -> headers[selectedIdx]
                            else -> "(自动识别)"
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { expandedField = fieldType },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fieldDef.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        selectedIdx == -2 -> MaterialTheme.colorScheme.onSurfaceVariant
                                        selectedIdx == -1 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = expandedField == fieldType,
                                onDismissRequest = { expandedField = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("(不映射)", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        selections[fieldType] = -1
                                        expandedField = null
                                    }
                                )
                                headers.forEachIndexed { idx, header ->
                                    val suggestion = headerSuggestions[idx]
                                    val isRecommended = suggestion == fieldType
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isRecommended) "★ $header" else header,
                                                fontWeight = if (isRecommended) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selections[fieldType] = idx
                                            expandedField = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalMapping = mutableMapOf<Int, ExcelUtils.FieldType>()
                for ((ft, idx) in selections) {
                    if (idx >= 0) finalMapping[idx] = ft
                }
                val name = if (saveTemplate && templateName.isNotBlank()) templateName.trim() else null
                onConfirm(finalMapping, name)
            }) {
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
