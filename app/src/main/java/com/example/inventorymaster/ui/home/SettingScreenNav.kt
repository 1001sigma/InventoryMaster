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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.inventorymaster.ui.theme.StatusSuccess
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.data.model.MappingTemplate
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.ui.theme.ColorPaletteBottomSheet
import com.example.inventorymaster.ui.theme.ColorTuple
import com.example.inventorymaster.ui.theme.ColorTupleBadge
import com.example.inventorymaster.ui.theme.ColorTupleDefaults
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import com.example.inventorymaster.ui.theme.TouchTargetMin
import com.example.inventorymaster.ui.theme.toHex
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
    onNavigateToSignature: () -> Unit,   // 新增：跳转到签名库
    onNavigateToAiSettings: () -> Unit   // 跳转到 AI 助手设置页
    ) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
    var showColorPaletteSheet by remember { mutableStateOf(false) }
    var scanFrameSectionExpanded by remember { mutableStateOf(false) }
    var inventorySectionExpanded by remember { mutableStateOf(false) }
    var networkSectionExpanded by remember { mutableStateOf(false) }
    var excelMappingSectionExpanded by remember { mutableStateOf(false) }
    var pdfSectionExpanded by remember { mutableStateOf(false) }

    // ===== PDF 本地储存位置（SAF 文件夹持久授权）=====
    var showPdfDirDialog by remember { mutableStateOf(false) }
    val pdfDirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // 取得跨重启的持久读写授权（用户在系统选择器里勾选的文件夹）
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setPdfExportDirUri(uri.toString())
        }
    }

    // ===== 操作者昵称设置 =====
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var editNicknameInput by remember { mutableStateOf("") }

    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text("修改操作者昵称") },
            text = {
                Column {
                    Text(
                        "任务历史与扫码记录均会以此名称标识操作者：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNicknameInput,
                        onValueChange = { if (it.length <= 16) editNicknameInput = it },
                        label = { Text("昵称") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editNicknameInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.setOperatorName(trimmed)
                            showEditNicknameDialog = false
                        }
                    },
                    enabled = editNicknameInput.trim().isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) { Text("取消") }
            }
        )
    }

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

        // 2. "外观与配色" 分组 (可折叠)
        item {
            val activeTuple = uiState.getActiveColorTuple()
            val activePreset = ColorTupleDefaults.presets.find { it.tuple.primary == activeTuple.primary }
            val activeTupleName = activePreset?.name ?: "自定义 (${activeTuple.primary.toHex()})"
            val isDynamic = uiState.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            ExpandableSection(
                title = "外观与配色",
                icon = Icons.Default.Palette,
                expanded = customSectionExpanded,
                onExpandChange = { customSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 配色方案入口行 (仿 ImageToolbox 风格，内嵌 3-Tone 切片徽章)
                    SettingsItem(
                        title = "配色方案",
                        subtitle = if (isDynamic) "系统壁纸动态取色生效中（停用后可自定义）" else "当前方案：$activeTupleName",
                        icon = Icons.Default.Palette,
                        enabled = !isDynamic,
                        onClick = {
                            if (isDynamic) {
                                android.widget.Toast.makeText(context, "动态颜色开启中，请先关闭动态颜色以选用配色方案", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showColorPaletteSheet = true
                            }
                        },
                        endContent = {
                            ColorTupleBadge(
                                colorTuple = activeTuple,
                                size = 42.dp,
                                isSelected = true
                            )
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
                        SettingsSwitch(
                            title = "动态颜色",
                            subtitle = "根据系统壁纸调色板自动提取主题色（Android 12+）",
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
                        subtitle = "深色模式下使用纯黑背景以节省电量（适配手持机 OLED 屏）",
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

                    // 胶囊分段选择：斜杠铃铛(不提醒) | 10 | 15 | 30 | 自定义
                    // 手工分权重布局：数字段收窄，把宽度让给「自定义」避免文字截断
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(40.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ThresholdSegmentCell(
                                weight = 0.85f,
                                selected = customThreshold == 0 && !isCustom,
                                onClick = { viewModel.setNearExpiryThreshold(0) },
                                showDivider = false
                            ) { tint ->
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = "不提醒",
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            thresholdOptions.filter { it > 0 }.forEach { days ->
                                ThresholdSegmentCell(
                                    weight = 0.85f,
                                    selected = customThreshold == days && !isCustom,
                                    onClick = { viewModel.setNearExpiryThreshold(days) },
                                    showDivider = true
                                ) { tint ->
                                    Text(
                                        "$days",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = tint,
                                        maxLines = 1
                                    )
                                }
                            }
                            ThresholdSegmentCell(
                                weight = 1.25f,
                                selected = isCustom,
                                onClick = { showCustomDialog = true },
                                showDivider = true
                            ) { tint ->
                                // 选中自定义时直接显示自定义天数，便于确认当前生效值
                                Text(
                                    if (isCustom) "$customThreshold" else "自定义",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tint,
                                    maxLines = 1
                                )
                            }
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

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitch(
                        title = "快捷计算器保留上次结果",
                        subtitle = "再次打开时自动带入上次计算结果，关闭则每次均为空白状态",
                        icon = Icons.Default.Calculate,
                        checked = uiState.calcRetainLastResult,
                        onCheckedChange = { viewModel.setCalcRetainLastResult(it) }
                    )
                    SettingsSwitch(
                        title = "长光模式（智能识别）",
                        subtitle = "自动识别长光华医 21 位试剂条码并提取批号搜索，无需手动切换",
                        icon = Icons.Default.AutoAwesome,
                        checked = uiState.enableHybiomeMode,
                        onCheckedChange = { viewModel.setHybiomeMode(it) }
                    )
                    SettingsSwitch(
                        title = "已查验物料自动移至末尾",
                        subtitle = "开启后已查验物料自动排至列表末尾，便于聚焦未盘物料；关闭则严格保持原始录入顺序",
                        icon = Icons.Default.LowPriority,
                        checked = uiState.sinkVerifiedItems,
                        onCheckedChange = { viewModel.setSinkVerifiedItems(it) }
                    )
                    SettingsSwitch(
                        title = "空搜索不显示数据（测试）",
                        subtitle = "开启后，搜索框为空时不自动加载全量物料，仅在扫码或输入关键词后显示匹配项",
                        icon = Icons.Default.SearchOff,
                        checked = uiState.emptySearchShowsNothing,
                        onCheckedChange = { viewModel.setEmptySearchShowsNothing(it) }
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
                    SettingsSwitch(
                        title = "拍照原图存相册",
                        subtitle = "开启后批量扫描拍照时自动将原始照片存入系统相册；默认关闭",
                        icon = Icons.Default.PhotoLibrary,
                        checked = uiState.saveOriginalPhotoToGallery,
                        onCheckedChange = { viewModel.setSaveOriginalPhotoToGallery(it) }
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
                Spacer(modifier = Modifier.height(12.dp))
                ServerTokenInput(
                    currentToken = uiState.serverToken,
                    onSave = { viewModel.setServerToken(it) },
                    onVerify = { token, callback -> viewModel.verifyAndSaveToken(token, callback) }
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

                // 操作者昵称显示与修改入口
                SettingsItem(
                    title = "操作者昵称",
                    subtitle = if (uiState.operatorName.isBlank()) "点击设置昵称" else "当前昵称：${uiState.operatorName}",
                    icon = Icons.Default.Create,
                    onClick = {
                        editNicknameInput = uiState.operatorName
                        showEditNicknameDialog = true
                    }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "产品库同步",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "从服务器拉取全量/增量物料主档（含金蝶同步数据）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Button(
                        onClick = { inventoryViewModel.syncProductLibrary() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即同步")
                    }
                }
            }
        }

        // 5.5 "AI 助手" 入口（独立设置页，点击跳转）
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsItem(
                    title = "AI 助手",
                    subtitle = "AI 服务商、本地模型与思考过程显示设置",
                    icon = Icons.Default.AutoAwesome,
                    onClick = onNavigateToAiSettings,
                    showChevron = true,
                    // 与其他分组头图标同色（ExpandableSection 头图标为主题色）
                    iconTint = MaterialTheme.colorScheme.primary
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

                    // 本地储存位置：SAF 文件夹持久授权，导出时自动保存不再弹选位器
                    SettingsItem(
                        title = "本地储存位置",
                        subtitle = if (uiState.pdfExportDirUri.isBlank())
                            "未设置：每次导出时弹系统选位器选择保存位置"
                        else
                            "导出自动保存到：${pdfDirDisplay(uiState.pdfExportDirUri)}",
                        icon = Icons.Default.Folder,
                        onClick = {
                            if (uiState.pdfExportDirUri.isBlank()) pdfDirPicker.launch(null)
                            else showPdfDirDialog = true
                        }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // PDF 本地储存位置管理弹窗（已设置时点按该项弹出）
    if (showPdfDirDialog) {
        AlertDialog(
            onDismissRequest = { showPdfDirDialog = false },
            title = { Text("本地储存位置") },
            text = {
                Text(
                    "当前保存到：${pdfDirDisplay(uiState.pdfExportDirUri)}\n\n" +
                        "导出 PDF 时将自动命名保存到该文件夹，不再弹出系统选位器。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPdfDirDialog = false
                    pdfDirPicker.launch(null)
                }) { Text("重新选择") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPdfDirDialog = false
                    // 释放持久授权并清除设置，恢复"每次询问"
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(uiState.pdfExportDirUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    viewModel.setPdfExportDirUri("")
                }) { Text("清除") }
            }
        )
    }

    // ===== 配色方案管理抽屉 (仿 ImageToolbox) =====
    ColorPaletteBottomSheet(
        visible = showColorPaletteSheet,
        currentColorTuple = uiState.getActiveColorTuple(),
        customTuples = uiState.getCustomColorTuples(),
        onDismiss = { showColorPaletteSheet = false },
        onSelectColorTuple = { viewModel.setColorTuple(it) },
        onAddCustomColorTuple = { viewModel.addCustomColorTuple(it) },
        onDeleteCustomColorTuple = { viewModel.removeCustomColorTuple(it) }
    )
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
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) } // 点击整行也能切换
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
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
    onClick: () -> Unit,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    endContent: (@Composable () -> Unit)? = null
) {
    val rowAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = true, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(rowAlpha)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (endContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.alpha(rowAlpha)) {
                endContent()
            }
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }
}

/**
 * 近效期分段选择单元：无自身圆角（外层 Surface 统一裁剪），可选 1dp 左分隔线；
 * weight 控制各段宽度占比（数字段窄、自定义段宽）
 */
@Composable
private fun RowScope.ThresholdSegmentCell(
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
    content: @Composable (Color) -> Unit
) {
    if (showDivider) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content(
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** SAF tree URI → 人话路径："primary:Documents/盘存PDF" → "Documents/盘存PDF" */
private fun pdfDirDisplay(uriString: String): String {
    if (uriString.isBlank()) return ""
    return try {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        docId.substringAfter(':', docId).ifBlank { docId }
    } catch (_: Exception) {
        uriString
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
fun ServerTokenInput(
    currentToken: String,
    onSave: (String) -> Unit,
    onVerify: (String, (Boolean, String) -> Unit) -> Unit
) {
    var tokenText by remember(currentToken) { mutableStateOf(currentToken) }
    var verifying by remember { mutableStateOf(false) }
    var verifyResultMsg by remember { mutableStateOf<String?>(null) }
    var verifySuccess by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "服务端认证 Token",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "与服务端通信的安全密钥（可在服务端启动控制台查看）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AppTextField(
            value = tokenText,
            onValueChange = {
                tokenText = it
                verifyResultMsg = null
            },
            singleLine = true,
            placeholder = "请输入 Token",
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "隐藏 Token" else "显示 Token"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            isError = verifyResultMsg != null && !verifySuccess,
            supportingText = verifyResultMsg?.let { msg ->
                {
                    Text(
                        text = msg,
                        color = if (verifySuccess) StatusSuccess else MaterialTheme.colorScheme.error
                    )
                }
            },
            onFocusLost = {
                val token = tokenText.trim()
                if (token != currentToken) {
                    onSave(token)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val token = tokenText.trim()
                onSave(token)
                verifying = true
                verifyResultMsg = null
                onVerify(token) { success, msg ->
                    verifying = false
                    verifySuccess = success
                    verifyResultMsg = msg
                }
            },
            enabled = !verifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTargetMin),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (verifying) "正在验证 Token..." else "保存并测试连接")
        }
    }
}