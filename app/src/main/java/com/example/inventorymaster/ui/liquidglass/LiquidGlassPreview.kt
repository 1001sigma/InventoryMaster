package com.example.inventorymaster.ui.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 调参画廊：同一组形状在浅色（晶透白玻璃）/深色（烟熏玻璃）背景下的质感对比
@Preview(name = "浅色背景 - 晶透玻璃", showBackground = true, backgroundColor = 0xFFF2F4F7, widthDp = 360)
@Composable
private fun LiquidGlassLightPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CrystalLiquidGlassShowcase()
    }
}

@Preview(name = "深色背景 - 烟熏面板", showBackground = true, backgroundColor = 0xFF101214, widthDp = 360)
@Composable
private fun LiquidGlassDarkPreview() {
    GlassGallery(style = LiquidGlassStyle.DarkStrong)
}

/**
 * 晶透液态玻璃样例展示（1:1 还原现代化高透凸透镜/水晶亚克力质感）：
 * 左侧圆形「+」浮钮，右侧带有纯黑高反差圆形按键的「Get started >」胶囊按钮。
 */
@Composable
fun CrystalLiquidGlassShowcase() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 左侧：圆形「+」液态玻璃悬浮按钮
        LiquidGlass(
            shape = CircleShape,
            style = LiquidGlassStyle.CrystalClear,
            modifier = Modifier.size(68.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color(0xFF1E2024),
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.Center)
            )
        }

        // 2. 右侧：带黑色高反差动作圆钮的「Get started >」胶囊按钮
        val interactionSource = remember { MutableInteractionSource() }
        LiquidGlass(
            shape = RoundedCornerShape(percent = 50),
            style = LiquidGlassStyle.CrystalClear,
            interactionSource = interactionSource,
            modifier = Modifier
                .height(68.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Get started",
                    color = Color(0xFF141618),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )

                // 内部纯黑高反差动作圆钮（通过黑白明度对比衬托外层玻璃晶透感）
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            spotColor = Color.Black.copy(alpha = 0.30f)
                        )
                        .clip(CircleShape)
                        .background(Color(0xFF0E1013)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Proceed",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassGallery(style: LiquidGlassStyle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidGlass(
            shape = RoundedCornerShape(28.dp),
            style = style,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "12.5",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 24.dp, vertical = 40.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlass(
                shape = RoundedCornerShape(24.dp),
                style = style,
                modifier = Modifier.width(140.dp).height(48.dp)
            ) {
                Text("胶囊按钮", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            LiquidGlass(
                shape = CircleShape,
                style = style,
                modifier = Modifier.size(72.dp)
            ) {
                Text("+", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
