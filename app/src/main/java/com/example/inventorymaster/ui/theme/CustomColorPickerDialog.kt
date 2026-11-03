package com.example.inventorymaster.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// 精选 16 种高辨识度 Material 基础色彩
private val QuickPaletteColors = listOf(
    Color(0xFFE53935), // 经典红
    Color(0xFFD81B60), // 品红玫瑰
    Color(0xFF8E24AA), // 罗兰紫
    Color(0xFF5E35B1), // 科技深紫
    Color(0xFF3949AB), // 靛蓝
    Color(0xFF1E88E5), // 海洋蓝
    Color(0xFF039BE5), // 天空蓝
    Color(0xFF00ACC1), // 青碧
    Color(0xFF00897B), // 医疗青绿
    Color(0xFF43A047), // 护眼绿
    Color(0xFF7CB342), // 鲜草绿
    Color(0xFFC0CA33), // 青柠
    Color(0xFFFFB300), // 琥珀金
    Color(0xFFFB8C00), // 活力橙
    Color(0xFFF4511E), // 警示深橙
    Color(0xFF546E7A)  // 工业蓝灰
)

/**
 * 仿 ImageToolbox 风格的自定义颜色选择器弹窗。
 * 功能：
 * - 实时 3-Tone 切片徽章预览
 * - 快速基础色网格点选
 * - HEX 颜色代码直接输入与剪贴板一键粘贴
 * - 自动色调协调推导（默认开启）与高级自定义辅助/三级色开关
 */
@Composable
fun CustomColorPickerDialog(
    initialColorTuple: ColorTuple,
    onDismissRequest: () -> Unit,
    onConfirm: (ColorTuple) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    var primaryColor by remember { mutableStateOf(initialColorTuple.primary) }
    var secondaryColor by remember { mutableStateOf(initialColorTuple.secondary) }
    var tertiaryColor by remember { mutableStateOf(initialColorTuple.tertiary) }

    var primaryInput by remember { mutableStateOf(initialColorTuple.primary.toHex()) }
    var primaryError by remember { mutableStateOf<String?>(null) }

    var autoHarmonize by remember { mutableStateOf(initialColorTuple.secondary == null && initialColorTuple.tertiary == null) }
    var showAdvanced by remember { mutableStateOf(!autoHarmonize) }

    // 动态派生当前有效 ColorTuple
    val currentTuple = remember(primaryColor, secondaryColor, tertiaryColor, autoHarmonize) {
        if (autoHarmonize) {
            ColorTuple(primary = primaryColor)
        } else {
            ColorTuple(
                primary = primaryColor,
                secondary = secondaryColor ?: primaryColor.calculateSecondaryColor(),
                tertiary = tertiaryColor ?: primaryColor.calculateTertiaryColor()
            )
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 顶部标题
                Text(
                    text = "自定义配色方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "输入十六进制色值或点击快捷调色盘",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 2. 实时色彩切片徽章大图预览
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ColorTupleBadge(
                            colorTuple = currentTuple,
                            size = 68.dp,
                            borderWidth = 3.dp,
                            borderColor = MaterialTheme.colorScheme.surface,
                            elevation = 4.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = currentTuple.primary.toHex(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. HEX 输入框 + 粘贴按钮
                OutlinedTextField(
                    value = primaryInput,
                    onValueChange = { input ->
                        val clean = input.trim().uppercase()
                        primaryInput = clean
                        val parsed = ColorTuple.parseColorOrNull(clean)
                        if (parsed != null) {
                            primaryColor = parsed
                            primaryError = null
                        } else {
                            primaryError = "格式如 #0D9488"
                        }
                    },
                    label = { Text("主色 HEX 代码") },
                    singleLine = true,
                    isError = primaryError != null,
                    supportingText = primaryError?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (!clipText.isNullOrBlank()) {
                                primaryInput = clipText.uppercase()
                                val parsed = ColorTuple.parseColorOrNull(clipText)
                                if (parsed != null) {
                                    primaryColor = parsed
                                    primaryError = null
                                } else {
                                    primaryError = "剪贴板非有效 HEX 色值"
                                }
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 常用基础色网格 (16色)
                Text(
                    text = "快速选择基色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(QuickPaletteColors) { color ->
                        val isSelected = primaryColor == color
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(0.15f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    primaryColor = color
                                    primaryInput = color.toHex()
                                    primaryError = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 5. 辅助色与点缀色协调开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "智能色相协调",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (autoHarmonize) "自动推导辅助色与高光点缀色" else "独立指定辅助色与点缀色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoHarmonize,
                        onCheckedChange = {
                            autoHarmonize = it
                            showAdvanced = !it
                        }
                    )
                }

                // 高级独立调色区域（仅在关闭自动协调时展开）
                AnimatedVisibility(
                    visible = !autoHarmonize,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "辅助色 (Secondary)：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(secondaryColor ?: primaryColor.calculateSecondaryColor())
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (secondaryColor ?: primaryColor.calculateSecondaryColor()).toHex(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "点缀色 (Tertiary)：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(tertiaryColor ?: primaryColor.calculateTertiaryColor())
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (tertiaryColor ?: primaryColor.calculateTertiaryColor()).toHex(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 6. 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismissRequest) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (primaryError == null) {
                                onConfirm(currentTuple)
                            }
                        },
                        enabled = primaryError == null
                    ) {
                        Text("保存并应用")
                    }
                }
            }
        }
    }
}
