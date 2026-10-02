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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.example.inventorymaster.InventoryApplication
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.example.inventorymaster.ui.ai.AiProviderSettingsContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.data.model.MappingTemplate
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import com.example.inventorymaster.ui.theme.TouchTargetMin
import com.example.inventorymaster.utils.NetworkUtils
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel

// 预设颜色列表
val PresetColors = ModernColorSchemes.allSchemes.map { it.primary }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    inventoryViewModel: InventoryViewModel,
    onNavigateToWatermark: () -> Unit,   // 新增：跳转到水印设置
    onNavigateToSignature: () -> Unit    // 新增：跳转到签名库
    ) {
    val uiState by viewModel.uiState.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    var customSectionExpanded by remember { mutableStateOf(false) }
    var darkSectionExpanded by remember { mutableStateOf(false) }
    var scanFrameSectionExpanded by remember { mutableStateOf(false) }
    var inventorySectionExpanded by remember { mutableStateOf(false) }
    var networkSectionExpanded by remember { mutableStateOf(false) }
    var aiSectionExpanded by remember { mutableStateOf(false) }
    var excelMappingSectionExpanded by remember { mutableStateOf(false) }
    var pdfSectionExpanded by remember { mutableStateOf(false) }

    // ===== M2（R1）：操作员绑定 =====
    var showOperatorScanner by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var showBindManualDialog by remember { mutableStateOf(false) }
    var bindCodeInput by remember { mutableStateOf("") }
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showOperatorScanner = true
        else bindError = "相机权限被拒绝，请使用手动输入绑定码"
    }

    if (showOperatorScanner) {
        // 二维码内容即绑定码原文，直接交给绑定接口
        com.example.inventorymaster.ui.analyzer.ScanScreen(
            onScanResult = { raw ->
                showOperatorScanner = false
                viewModel.bindOperator(raw) { err -> bindError = err }
            },
            onClose = { showOperatorScanner = false }
        )
        return
    }

    if (showBindManualDialog) {
        AlertDialog(
            onDismissRequest = { showBindManualDialog = false },
            title = { Text("输入绑定码") },
            text = {
                OutlinedTextField(
                    value = bindCodeInput,
                    onValueChange = { bindCodeInput = it.uppercase() },
                    label = { Text("8 位绑定码") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBindManualDialog = false
                    viewModel.bindOperator(bindCodeInput) { err -> bindError = err }
                }) { Text("绑定") }
            },
            dismissButton = { TextButton(onClick = { showBindManualDialog = false }) { Text("取消") } }
        )
    }

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

        // 4. "盘存设置" 分组
        item {
            ExpandableSection(
                title = "盘存设置",
                icon = Icons.Default.Inventory2,
                expanded = inventorySectionExpanded,
                onExpandChange = { inventorySectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "近效期提醒范围",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "效期在此天数内的产品将显示近效期角标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    val thresholdOptions = listOf(0, 10, 15, 30)
                    val customThreshold = uiState.nearExpiryThresholdDays
                    val isCustom = customThreshold !in thresholdOptions
                    var showCustomDialog by remember { mutableStateOf(false) }

                    // 胶囊分段选择：不提醒 | 10 | 15 | 30 | 自定义
                    val segmentCount = thresholdOptions.size + 1
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        thresholdOptions.forEachIndexed { index, days ->
                            SegmentedButton(
                                selected = customThreshold == days && !isCustom,
                                onClick = { viewModel.setNearExpiryThreshold(days) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = segmentCount),
                                icon = {}
                            ) {
                                Text(if (days == 0) "不提醒" else "$days", maxLines = 1)
                            }
                        }
                        SegmentedButton(
                            selected = isCustom,
                            onClick = { showCustomDialog = true },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = segmentCount - 1, count = segmentCount
                            ),
                            icon = {}
                        ) {
                            Text("自定义", maxLines = 1)
                        }
                    }

                    if (showCustomDialog) {
                        var dialogText by remember {
                            mutableStateOf(if (isCustom) customThreshold.toString() else "")
                        }
                        var dialogInvalid by remember { mutableStateOf(false) }
                        AlertDialog(
                            onDismissRequest = { showCustomDialog = false },
                            title = { Text("自定义近效期提醒范围") },
                            text = {
                                OutlinedTextField(
                                    value = dialogText,
                                    onValueChange = {
                                        dialogText = it
                                        dialogInvalid = false
                                    },
                                    label = { Text("天数（0-365，0 为不提醒）") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    isError = dialogInvalid,
                                    supportingText = if (dialogInvalid) {
                                        { Text("请输入 0-365 的整数") }
                                    } else null,
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val intVal = dialogText.trim().toIntOrNull()
                                    if (intVal != null && intVal in 0..365) {
                                        viewModel.setNearExpiryThreshold(intVal)
                                        showCustomDialog = false
                                    } else {
                                        dialogInvalid = true
                                    }
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomDialog = false }) { Text("取消") }
                            }
                        )
                    }

                    // 记录卡片样式：预览卡片即设置界面，点字段位置换字段
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "记录卡片样式",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "自定义任务详情页记录卡片各信息位显示的字段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    RecordCardStyleSetting(
                        config = uiState.recordCardConfig,
                        onSlotFieldChange = { slot, fieldId ->
                            viewModel.setRecordCardSlotField(slot, fieldId)
                        },
                        onReset = { viewModel.resetRecordCardConfig() }
                    )
                }
            }
        }

        // 5. "扫描设置" 分组（扫描框大小已移至扫码页面内拖拽调整，自动记忆）
        item {
            ExpandableSection(
                title = "扫描设置",
                icon = Icons.Default.CropFree,
                expanded = scanFrameSectionExpanded,
                onExpandChange = { scanFrameSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSwitch(
                        title = "OpenCV 智能检测",
                        subtitle = "关闭后使用暴力分片扫描，速度较慢但更稳定",
                        icon = Icons.Default.AutoAwesome,
                        checked = uiState.useOpenCV,
                        onCheckedChange = { viewModel.setUseOpenCV(it) }
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
                val app = LocalContext.current.applicationContext as InventoryApplication
                ServerIpInput(
                    currentIp = uiState.serverIp,
                    onSave = { viewModel.setServerIp(it) },
                    onSearchServer = { app.connectionManager.startManualDiscovery() }
                )
                SettingsSwitch(
                    title = "实时推送（长连接）",
                    subtitle = "批量扫码实时录入的逐条状态改为服务端即时推送；关闭后回到 2.5 秒轮询",
                    icon = Icons.Default.Dns,
                    checked = uiState.realtimePushEnabled,
                    onCheckedChange = { viewModel.setRealtimePushEnabled(it) }
                )
                SettingsSwitch(
                    title = "盘库实时刷新（长连接）",
                    subtitle = "盘点任务有新数据时自动增量刷新，无需下拉；关闭后回到手动刷新",
                    icon = Icons.Default.Dns,
                    checked = uiState.inventoryPushEnabled,
                    onCheckedChange = { viewModel.setInventoryPushEnabled(it) }
                )

                // M2（R1）：操作员绑定
                val bindContext = LocalContext.current
                Text(
                    if (uiState.operatorName.isBlank()) "当前操作员：未绑定（绑定后该设备的记录自动归属操作员）"
                    else "当前操作员：${uiState.operatorName}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            bindContext, android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) showOperatorScanner = true else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }) { Text("扫码绑定") }
                    OutlinedButton(onClick = { showBindManualDialog = true }) { Text("手动输入绑定码") }
                }
                bindError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 5.5 "AI 助手" 分组
        item {
            ExpandableSection(
                title = "AI 助手",
                icon = Icons.Default.AutoAwesome,
                expanded = aiSectionExpanded,
                onExpandChange = { aiSectionExpanded = it }
            ) {
                AiSettingsContent(
                    viewModel = viewModel
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

        // 👇 "PDF 输出" 分组：与上方各组同为可折叠卡片
        item {
            ExpandableSection(
                title = "PDF 输出",
                icon = Icons.Default.PictureAsPdf,
                expanded = pdfSectionExpanded,
                onExpandChange = { pdfSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 水印设置入口
                    SettingsItem(
                        title = "防伪水印",
                        subtitle = "配置 PDF 导出的水印文本、颜色、密度与透明度",
                        icon = Icons.Default.Security,
                        onClick = onNavigateToWatermark
                    )

                    // 签名库入口
                    SettingsItem(
                        title = "电子签名库",
                        subtitle = "管理经手人手写签名与导出盖章参数",
                        icon = Icons.Default.Create,
                        onClick = onNavigateToSignature
                    )
                }
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
 * 颜色选择圆球
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
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * 服务器 IP 地址输入 — 失焦自动校验并保存
 */
@Composable
fun ServerIpInput(
    currentIp: String,
    onSave: (String) -> Unit,
    onSearchServer: () -> Unit = {}
) {
    var ipText by remember(currentIp) { mutableStateOf(currentIp) }
    var ipInvalid by remember(currentIp) { mutableStateOf(false) }
    var justSaved by remember(currentIp) { mutableStateOf(false) }

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
            text = "用于局域网同步，失焦后自动保存；也可点击下方按钮自动搜索服务端",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AppTextField(
            value = ipText,
            onValueChange = {
                ipText = it
                ipInvalid = false
                justSaved = false
            },
            singleLine = true,
            placeholder = "例如 192.168.1.100",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            isError = ipInvalid,
            supportingText = when {
                ipInvalid -> ({ Text("IP 格式不正确") })
                justSaved -> ({ Text("已保存") })
                else -> null
            },
            onFocusLost = {
                val ip = ipText.trim()
                when {
                    // 清空视为误操作，回退为当前生效值
                    ip.isEmpty() -> ipText = currentIp
                    NetworkUtils.isValidIpAddress(ip) -> {
                        if (ip != currentIp) onSave(ip)
                        justSaved = true
                    }
                    else -> ipInvalid = true
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSearchServer,
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTargetMin),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("搜索服务端")
        }
    }
}

@Composable
fun AiSettingsContent(
    viewModel: SettingsViewModel
) {
    val providers by viewModel.aiProviders.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val activeProvider = providers.find { it.id == activeProviderId }

        // 思维链开关（DeepSeek 风格折叠卡片；关闭=只显示一行工具提示）
        SettingsSwitch(
            title = "显示思考过程",
            subtitle = "聊天中展示 AI 的思考文本与每次工具调用的参数、结果详情（可折叠）",
            icon = Icons.Default.AutoAwesome,
            checked = uiState.aiShowThinking,
            onCheckedChange = { viewModel.setAiShowThinking(it) }
        )

        if (activeProvider != null) {
            Text(
                text = "当前服务商: ${activeProvider.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "模型: ${activeProvider.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "未配置 AI 服务商",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        AiProviderSettingsContent(
            providers = providers,
            activeProviderId = activeProviderId,
            onAdd = { viewModel.addAiProvider(it) },
            onActivate = { viewModel.setActiveProvider(it) },
            onEdit = { viewModel.updateAiProvider(it) },
            onDelete = { viewModel.deleteAiProvider(it) }
        )
    }
}