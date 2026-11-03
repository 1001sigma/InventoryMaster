package com.example.inventorymaster.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.inventorymaster.ui.theme.DarkSurfaceVariant
import com.example.inventorymaster.ui.theme.DarkSurfaceVariant2
import com.example.inventorymaster.ui.theme.TouchTargetMin


// ─── 重构后的透明度 Tokens (毛玻璃核心) ───
private object DialogTokens {
    // 浅色模式：白色基底，高透明度
    val LightBg = Color(0xFFFFFFFF).copy(alpha = 0.65f)
    val LightContentCard = Color(0xFFFFFFFF).copy(alpha = 0.4f)
    val LightBorder = Color(0xFFFFFFFF).copy(alpha = 0.8f)

    // 深色模式：暗色基底，适中透明度（色值取自主题表面层级，避免重复硬编码）
    val DarkBg = DarkSurfaceVariant.copy(alpha = 0.70f)
    val DarkContentCard = DarkSurfaceVariant2.copy(alpha = 0.4f)
    val DarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
}

@Composable
private fun dialogBackground(): Color {
    return if (isSystemInDarkTheme()) DialogTokens.DarkBg else DialogTokens.LightBg
}

@Composable
private fun contentCardBackground(): Color {
    return if (isSystemInDarkTheme()) DialogTokens.DarkContentCard else DialogTokens.LightContentCard
}



/**
 * 现代化弹窗容器 — 项目弹窗统一标准
 *
 * - 纯白背景（浅色）/ 暗色背景（深色），无 MD3 tonal elevation 偏移
 * - 12dp elevation 强阴影悬浮感
 * - 可选的浅灰内容卡片包裹
 * - 标准化标题栏（图标 + 标题 + X 关闭按钮）
 * - 底部按钮槽（确认 + 取消）
 */
@Composable
fun ModernAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
    showContentCard: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val dialogBg = if (isDark) DialogTokens.DarkBg else DialogTokens.LightBg
    val borderColor = if (isDark) DialogTokens.DarkBorder else DialogTokens.LightBorder
    val contentBg = if (isDark) DialogTokens.DarkContentCard else DialogTokens.LightContentCard

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // ===== 获取 Window Provider 开启底层模糊 =====
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            // 剥离默认的不透明背景色
            window.setBackgroundDrawableResource(android.R.color.transparent)
            // 降低 Dim 遮罩，让模糊效果更明显
            window.setDimAmount(0.1f)

            // 启用系统级窗口模糊 (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 40 // 模糊半径，可根据需要调整
                }
            }
        }

        Card(
            modifier = modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            // 移除厚重阴影，改用细腻的半透明边框勾勒玻璃边缘
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // ===== 标题栏 =====
                HeaderSection(title, icon, onDismiss, titleTrailing)

                Spacer(modifier = Modifier.height(16.dp))

                // ===== 内容区 =====
                if (showContentCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = contentBg,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        content()
                    }
                } else {
                    content()
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ===== 按钮栏 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    title: String,
    icon: ImageVector?,
    onDismiss: () -> Unit,
    titleTrailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 12.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge, // 稍微缩减标题字号，更符合现代感
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (titleTrailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                titleTrailing()
            }
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 可复用子组件
// ═══════════════════════════════════════════════════════════

/**
 * 现代化对话框输入框 — 委托统一的 [AppTextField]，保留对话框玻璃拟态的半透明底色
 */
@Composable
fun ModernDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    val fieldBg = if (isSystemInDarkTheme()) DialogTokens.DarkBorder else DialogTokens.LightBorder

    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        isError = isError,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        containerColor = fieldBg,
        modifier = modifier
    )
}

/**
 * 产品信息卡片 — 纯展示，无阴影，浅灰背景，层次分明
 */
@Composable
fun ProductInfoCard(
    productName: String,
    di: String?,
    specification: String?,
    model: String?,
    registrationCert: String?,
    manufacturer: String?,
    modifier: Modifier = Modifier
) {
    val cardBg = contentCardBackground()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = productName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            val secondaryStyle = MaterialTheme.typography.bodySmall
            val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

            if (!di.isNullOrBlank()) {
                Text("DI: $di", style = secondaryStyle, color = secondaryColor)
            }
            if (!specification.isNullOrBlank() || !model.isNullOrBlank()) {
                val spec = listOfNotNull(
                    specification?.takeIf { it.isNotBlank() },
                    model?.takeIf { it.isNotBlank() }
                ).joinToString(" / ")
                Text("规格: $spec", style = secondaryStyle, color = secondaryColor)
            }
            if (!registrationCert.isNullOrBlank()) {
                Text("注册证号: $registrationCert", style = secondaryStyle, color = secondaryColor)
            }
            if (!manufacturer.isNullOrBlank()) {
                Text("厂家: $manufacturer", style = secondaryStyle, color = secondaryColor)
            }
        }
    }
}

/**
 * 标准化对话框底部按钮组
 *
 * 取消按钮: OutlinedButton（次要操作）
 * 确认按钮: filled Button，primary teal 背景（主要操作）
 */
@Composable
fun ModernDialogButtonBar(
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    confirmText: String = "确认",
    dismissText: String = "取消",
    confirmEnabled: Boolean = true,
    confirmColor: Color = MaterialTheme.colorScheme.primary
) {
    if (onDismiss != null) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.height(TouchTargetMin),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(dismissText, fontWeight = FontWeight.Medium)
        }
    }

    Button(
        onClick = onConfirm,
        enabled = confirmEnabled,
        modifier = Modifier.height(TouchTargetMin),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = confirmColor
        )
    ) {
        Text(confirmText, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 现代化下拉菜单 — 消除 MD3 Tonal Elevation 淡紫色偏移
 *
 * 显式覆写 containerColor，浅色纯白 / 深色暗色背景
 */
@Composable
fun ModernDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val menuBg = dialogBackground()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = menuBg
    ) {
        content()
    }
}



