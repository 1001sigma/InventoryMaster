package com.example.inventorymaster.utils.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.viewmodel.SettingsViewModel
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

val PresetWatermarks = listOf("盘点专用", "复核通过", "内部资料", "仅供参考", "严禁外传")

val WatermarkColors = listOf(
    0xFFFF0000 to "警示红",
    0xFF4CAF50 to "安全绿",
    0xFF2196F3 to "标准蓝",
    0xFF9E9E9E to "低调灰"
)

/** 密度网格标注：n 枚 → cols=ceil(√n) × rows=ceil(n/cols)（与 PdfEngine 导出规则一致） */
private fun watermarkGridLabel(density: Int): String {
    val n = density.coerceIn(1, 16)
    val cols = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
    val rows = ceil(n / cols.toDouble()).toInt().coerceAtLeast(1)
    return "${cols}×${rows}"
}

/**
 * 防伪水印设置页（全屏页面：自带返回头部，顶栏底栏由 MainScreenNav 隐藏）。
 * 卡片风格与系统设置页一致（24dp 圆角、surface、elevation 2dp）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // 本地实时状态：拖动/输入过程只改这里（预览板直接绑定，所见即所得），松手/失焦才写 DataStore
    var watermarkInput by remember(uiState.watermarkText) { mutableStateOf(uiState.watermarkText) }
    var opacityInput by remember(uiState.watermarkOpacity) { mutableStateOf(uiState.watermarkOpacity) }
    var angleInput by remember(uiState.watermarkAngle) { mutableStateOf(uiState.watermarkAngle) }
    var fontSizeInput by remember(uiState.watermarkFontSize) { mutableStateOf(uiState.watermarkFontSize) }
    var densityInput by remember(uiState.watermarkDensity) { mutableStateOf(uiState.watermarkDensity.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // —— 页内返回头部 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "防伪水印",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // —— 实时预览 ——
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WatermarkPreviewBoard(
                        text = watermarkInput,
                        opacity = opacityInput,
                        angle = angleInput,
                        fontSize = fontSizeInput,
                        colorLong = uiState.watermarkColor,
                        density = densityInput.roundToInt()
                    )
                }
            }

            // —— 文本内容 ——
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "文本内容",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 失焦保存：输入过程不写 DataStore，失焦时才提交；预览板绑定的是上方本地输入状态，边打字边可见
                    AppTextField(
                        value = watermarkInput,
                        onValueChange = { watermarkInput = it },
                        placeholder = "请输入防伪水印文字（留空不叠加）",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onFocusLost = {
                            if (watermarkInput != uiState.watermarkText) {
                                viewModel.setWatermarkText(watermarkInput)
                            }
                        }
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PresetWatermarks.forEach { preset ->
                            val isSelected = uiState.watermarkText == preset
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.setWatermarkText(if (isSelected) "" else preset)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // —— 样式微调 ——
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "样式微调",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 拖动只更新顶部已声明的本地状态（预览实时跟随），松手才写 DataStore
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("不透明度", style = MaterialTheme.typography.bodyMedium)
                            Text("${(opacityInput * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = opacityInput,
                            onValueChange = { opacityInput = it },
                            onValueChangeFinished = { viewModel.setWatermarkOpacity(opacityInput) },
                            valueRange = 0.05f..0.8f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("倾斜角度", style = MaterialTheme.typography.bodyMedium)
                            Text("${angleInput.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = angleInput,
                            onValueChange = { angleInput = it },
                            onValueChangeFinished = { viewModel.setWatermarkAngle(angleInput) },
                            valueRange = 0f..360f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("字体大小", style = MaterialTheme.typography.bodyMedium)
                            Text("${fontSizeInput.roundToInt()} Pt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = fontSizeInput,
                            onValueChange = { fontSizeInput = it },
                            onValueChangeFinished = { viewModel.setWatermarkFontSize(fontSizeInput) },
                            valueRange = 14f..72f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("水印密度", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${densityInput.roundToInt()} 枚（${watermarkGridLabel(densityInput.roundToInt())}）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = densityInput,
                            onValueChange = { densityInput = it },
                            onValueChangeFinished = {
                                viewModel.setWatermarkDensity(densityInput.roundToInt())
                            },
                            valueRange = 1f..16f,
                            steps = 14 // 整数刻度 1..16
                        )
                    }
                }
            }

            // —— 色彩方案 ——
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "色彩方案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WatermarkColors.forEach { (colorValue, name) ->
                            val isSelected = uiState.watermarkColor == colorValue.toLong()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.setWatermarkColor(colorValue.toLong()) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
