package com.example.inventorymaster.ui.home

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.data.model.MappingTemplate
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel

// 预设颜色列表
val PresetColors = ModernColorSchemes.allSchemes.map { it.primary }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, inventoryViewModel: InventoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var customSectionExpanded by remember { mutableStateOf(false) }
    var darkSectionExpanded by remember { mutableStateOf(false) }
    var scanFrameSectionExpanded by remember { mutableStateOf(false) }
    var networkSectionExpanded by remember { mutableStateOf(false) }
    var excelMappingSectionExpanded by remember { mutableStateOf(false) }

    // Map preset colors to their scheme names for labels
    val schemeNames = ModernColorSchemes.allSchemes.map { it.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部提示卡片
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "InventoryMaster v1.0.0\n医疗器械仓库管理系统",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2. "自定义" 分组 (可折叠)
        item {
            ExpandableSection(
                title = "外观自定义",
                icon = Icons.Default.Palette,
                expanded = customSectionExpanded,
                onExpandChange = { customSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "配色方案",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(PresetColors) { color ->
                            val schemeIdx = PresetColors.indexOf(color)
                            val schemeName = if (schemeIdx < schemeNames.size) schemeNames[schemeIdx] else ""
                            ColorSwatchWithLabel(
                                color = color,
                                label = schemeName,
                                isSelected = uiState.seedColor == color.toArgb().toLong(),
                                onClick = { viewModel.setSeedColor(color.toArgb().toLong()) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).padding(horizontal = 16.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitch(
                            title = "动态颜色",
                            subtitle = "使用壁纸颜色作为应用主题",
                            icon = Icons.Default.SettingsSystemDaydream,
                            checked = uiState.useDynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }
        }

        // 3. "夜间模式" 分组 (可折叠)
        item {
            ExpandableSection(
                title = "主题模式",
                icon = Icons.Default.DarkMode,
                expanded = darkSectionExpanded,
                onExpandChange = { darkSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val themeMode = uiState.themeMode

                    // 跟随系统
                    SettingsRadioItem(
                        title = "跟随系统",
                        subtitle = "根据系统设置自动切换浅色/深色",
                        icon = Icons.Default.SettingsSystemDaydream,
                        selected = themeMode == 0,
                        onClick = { viewModel.setThemeMode(0) }
                    )
                    // 强制浅色
                    SettingsRadioItem(
                        title = "浅色模式",
                        subtitle = "始终使用浅色主题",
                        icon = Icons.Default.Brightness2,
                        selected = themeMode == 1,
                        onClick = { viewModel.setThemeMode(1) }
                    )
                    // 强制深色
                    SettingsRadioItem(
                        title = "深色模式",
                        subtitle = "始终使用深色主题（适合仓库灯光环境）",
                        icon = Icons.Default.NightsStay,
                        selected = themeMode == 2,
                        onClick = { viewModel.setThemeMode(2) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 16.dp))

                    SettingsSwitch(
                        title = "AMOLED 纯黑",
                        subtitle = "深色模式下使用纯黑背景以节省电量",
                        icon = Icons.Default.DarkMode,
                        checked = uiState.isAmoledMode,
                        onCheckedChange = { viewModel.setAmoledMode(it) }
                    )
                }
            }
        }

        // 4. "扫描框设置" 分组
        item {
            ExpandableSection(
                title = "扫描框设置",
                icon = Icons.Default.CropFree,
                expanded = scanFrameSectionExpanded,
                onExpandChange = { scanFrameSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // --- 扫码框 ---
                    Text(
                        text = "扫码框",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    FrameSizeControl(
                        label = "宽度",
                        value = uiState.barcodeFrameWidth,
                        onValueChange = { viewModel.setBarcodeFrameWidth(it) },
                        min = 50f, max = 500f
                    )
                    FrameSizeControl(
                        label = "高度",
                        value = uiState.barcodeFrameHeight,
                        onValueChange = { viewModel.setBarcodeFrameHeight(it) },
                        min = 50f, max = 500f
                    )
                    FramePreview(
                        frameW = uiState.barcodeFrameWidth,
                        frameH = uiState.barcodeFrameHeight,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 16.dp))

                    // --- OCR 框 ---
                    Text(
                        text = "OCR 识别框",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    FrameSizeControl(
                        label = "宽度",
                        value = uiState.ocrFrameWidth,
                        onValueChange = { viewModel.setOcrFrameWidth(it) },
                        min = 50f, max = 500f
                    )
                    FrameSizeControl(
                        label = "高度",
                        value = uiState.ocrFrameHeight,
                        onValueChange = { viewModel.setOcrFrameHeight(it) },
                        min = 50f, max = 500f
                    )
                    FramePreview(
                        frameW = uiState.ocrFrameWidth,
                        frameH = uiState.ocrFrameHeight,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // 5. "网络设置" 分组
        item {
            ExpandableSection(
                title = "网络设置",
                icon = Icons.Default.Dns,
                expanded = networkSectionExpanded,
                onExpandChange = { networkSectionExpanded = it }
            ) {
                ServerIpInput(
                    currentIp = uiState.serverIp,
                    onSave = { viewModel.setServerIp(it) }
                )
            }
        }

        // 6. "Excel 映射管理" 分组
        item {
            ExpandableSection(
                title = "Excel 映射管理",
                icon = Icons.Default.FileOpen,
                expanded = excelMappingSectionExpanded,
                onExpandChange = {
                    excelMappingSectionExpanded = it
                    if (it) inventoryViewModel.loadMappingTemplates()
                }
            ) {
                ExcelMappingContent(
                    templates = inventoryViewModel.getCachedTemplates(),
                    onDelete = { inventoryViewModel.deleteMappingTemplate(it) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// --- 核心组件封装 ---

/**
 * 可折叠的分组卡片
 */
@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    // 旋转动画
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "ArrowRotation"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface, // 使用表面色，看起来像白色/深灰色卡片
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 标题栏 (可点击)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // 箭头图标
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotationState),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 内容区域 (展开/收起动画)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * 开关选项行
 */
@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) } // 点击整行也能切换
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 单选选项行 (用于多选一场景)
 */
@Composable
fun SettingsRadioItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 普通点击选项行
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

/**
 * 颜色选择圆球
 */
/**
 * 带标签的颜色选择球
 */
@Composable
fun ColorSwatchWithLabel(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 4.dp else 2.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isContrastEnough(color)) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}

/**
 * 扫描框尺寸控制：滑块 + 数字输入
 */
@Composable
fun FrameSizeControl(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Float,
    max: Float
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val intVal = v.toInt()
                onValueChange(intVal)
                textValue = intVal.toString()
            },
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                val intVal = input.toIntOrNull()
                if (intVal != null && intVal in min.toInt()..max.toInt()) {
                    onValueChange(intVal)
                }
            },
            modifier = Modifier.width(100.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text("dp")
    }
}

/**
 * 扫描框比例预览
 * 在一个固定高度区域内，按比例绘制矩形框
 */
@Composable
fun FramePreview(
    frameW: Int,
    frameH: Int,
    modifier: Modifier = Modifier
) {
    val previewColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val cornerColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val previewWidth = size.width
        val previewHeight = size.height
        val maxFrameW = previewWidth * 0.8f
        val maxFrameH = previewHeight * 0.8f

        // 按比例缩放框到预览区域内
        val scale = minOf(maxFrameW / frameW, maxFrameH / frameH)
        val drawW = frameW * scale
        val drawH = frameH * scale

        val left = (previewWidth - drawW) / 2f
        val top = (previewHeight - drawH) / 2f

        // 半透明填充
        drawRect(
            color = previewColor,
            topLeft = Offset(left, top),
            size = Size(drawW, drawH)
        )

        // 四角
        val cornerLen = 8f
        val strokeW = 2f
        for ((cx, cy) in listOf(
            left to top,
            left + drawW to top,
            left + drawW to top + drawH,
            left to top + drawH
        )) {
            val dirX = if (cx == left) 1f else -1f
            val dirY = if (cy == top) 1f else -1f
            drawLine(cornerColor, Offset(cx, cy), Offset(cx + dirX * cornerLen, cy), strokeW)
            drawLine(cornerColor, Offset(cx, cy), Offset(cx, cy + dirY * cornerLen), strokeW)
        }
    }
}

// 辅助函数：判断文字是否需要白色
fun isContrastEnough(color: Color): Boolean {
    val luminance = (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114)
    return luminance > 0.5
}

@Composable
private fun ExcelMappingContent(
    templates: List<MappingTemplate>,
    onDelete: (String) -> Unit
) {
    if (templates.isEmpty()) {
        Text(
            text = "暂无保存的映射模板。\n在导入 Excel 时保存映射即可在此管理。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    } else {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            templates.forEach { template ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${template.mappings.size} 个字段映射",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onDelete(template.id) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/**
 * 服务器 IP 地址输入与保存
 */
@Composable
fun ServerIpInput(
    currentIp: String,
    onSave: (String) -> Unit
) {
    var ipText by remember(currentIp) { mutableStateOf(currentIp) }
    var saved by remember(currentIp) { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "服务器 IP 地址",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "用于局域网同步，修改后点击保存生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = ipText,
                onValueChange = {
                    ipText = it
                    saved = false
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("例如 192.168.1.100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.width(12.dp))

            androidx.compose.material3.TextButton(
                onClick = {
                    onSave(ipText.trim())
                    saved = true
                }
            ) {
                Text(
                    text = if (saved) "已保存" else "保存",
                    color = if (saved) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}