package com.example.inventorymaster.ui.home

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.viewmodel.SettingsViewModel

// 预设颜色列表
val PresetColors = listOf(
    Color(0xFF6EDA74), // 绿色 (默认)
    Color(0xFF2196F3), // 蓝色
    Color(0xFFF44336), // 红色
    Color(0xFFFF9800), // 橙色
    Color(0xFFAB2FC0), // 紫色
    Color(0xFF009688), // 青色
    Color(0xFF795548), // 棕色
    Color(0xFF607D8B)  // 蓝灰
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    // --- 临时状态 (后续需要接到 ViewModel/DataStore) ---
    val uiState by viewModel.uiState.collectAsState()
    var isDynamicColor by remember { mutableStateOf(false) }
    var isAmoledMode by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(PresetColors[0]) }

    // 控制折叠状态
    var customSectionExpanded by remember { mutableStateOf(true) } // "自定义"默认展开
    var nightModeExpanded by remember { mutableStateOf(false) }    // "夜间模式"默认收起

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部提示卡片 (像截图里的"捐赠"位置)
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
                        text = "当前版本: 1.0.0 (Alpha)\nMaterial You 风格设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2. "自定义" 分组 (可折叠)
        item {
            ExpandableSection(
                title = "自定义",
                icon = Icons.Default.Build, // 工具图标
                expanded = customSectionExpanded,
                onExpandChange = { customSectionExpanded = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // A. 颜色选择器
                    Text(
                        text = "配色方案",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // 颜色圆球列表
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(PresetColors) { color ->
                            ColorSwatch(
                                color = color,
                                isSelected = uiState.seedColor == color.toArgb().toLong(),
                                onClick = { viewModel.setSeedColor(color.toArgb().toLong()) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).padding(horizontal = 16.dp))

                    // B. 动态颜色 (仅 Android 12+ 显示)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitch(
                            title = "动态颜色",
                            subtitle = "使用壁纸颜色作为应用主题",
                            icon = Icons.Default.Palette,
                            checked = uiState.useDynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }

                    // C. AMOLED 模式
                    SettingsSwitch(
                        title = "Amoled 模式",
                        subtitle = "在夜间模式下背景设为纯黑",
                        icon = Icons.Default.Brightness2, // 或者 Icons.Default.DarkMode
                        checked = uiState.isAmoledMode,
                        onCheckedChange = {viewModel.setAmoledMode(it)}
                    )

                    // D. 图标形状 (占位)
                    SettingsItem(
                        title = "图标形状",
                        subtitle = "在图标下方添加容器",
                        icon = Icons.Default.CropSquare,
                        onClick = { /* TODO */ }
                    )
                }
            }
        }

        // 3. "夜间模式" 分组 (可折叠)
        item {
            ExpandableSection(
                title = "夜间模式",
                icon = Icons.Default.NightsStay,
                expanded = nightModeExpanded,
                onExpandChange = { nightModeExpanded = it }
            ) {
                Column {
                    SettingsItem(
                        title = "跟随系统",
                        subtitle = "默认开启",
                        icon = Icons.Default.SettingsSystemDaydream,
                        onClick = {viewModel.setThemeMode(0)}
                    )
                    SettingsItem(
                        title = "强制深色",
                        subtitle = "总是使用深色主题",
                        icon = Icons.Default.DarkMode,
                        onClick = {viewModel.setThemeMode(2)}
                    )
                    SettingsItem(
                        title = "强制浅色",
                        subtitle = "总是使用浅色主题",
                        icon = Icons.Default.DarkMode,
                        onClick = {viewModel.setThemeMode(1)}
                    )
                }
            }
        }

        // 底部留白，防止被导航栏遮挡
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
                    tint = Color.Gray
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
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
@Composable
fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp) // 大小
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White, // 选中时显示白色对勾
                modifier = Modifier.size(24.dp)
            )
        }
    }
}