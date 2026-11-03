package com.example.inventorymaster.utils.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 水印实时预览白板组件
 * 采用 Glassmorphism 外框 + 纯白 A4 纸拟物化设计
 * @param density 每页水印数量（网格自动均布，与 PdfEngine.addWatermark 导出规则一致）
 */
@Composable
fun WatermarkPreviewBoard(
    text: String,
    opacity: Float,
    angle: Float,
    fontSize: Float,
    colorLong: Long,
    modifier: Modifier = Modifier,
    density: Int = 1
) {
    // 外层容器：使用半透明表面色和极细边框，营造轻盈的毛玻璃质感
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 内层容器：模拟真实的 A4 纸张
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.55f) // 让纸张占据容器 55% 的宽度，留出呼吸感
                .aspectRatio(1f / 1.414f) // 严格的 A4 纸张比例
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(4.dp), spotColor = Color.Black.copy(alpha = 0.1f))
                .background(Color.White, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp)) // 确保水印旋转时不会溢出纸张边界
        ) {
            val currentMaxWidth = maxWidth
            // 1. 模拟单据上的假内容（几条灰色的占位线，让预览更逼真）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(8.dp).background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
            }

            // 2. 核心黑科技：水印渲染层（按密度网格均布，锚点规则与 PdfEngine 导出一致：cols=ceil(√n)）
            if (text.isNotBlank()) {
                // 动态计算缩放比例：因为预览框比实际 A4 纸小很多，我们需要将真实字体大小等比缩小
                val scaledFontSize = remember(fontSize, currentMaxWidth) {
                    val scaleFactor = currentMaxWidth.value / 300f
                    (fontSize * scaleFactor).sp
                }

                val densityFixed = density.coerceIn(1, 16)
                val cols = kotlin.math.ceil(kotlin.math.sqrt(densityFixed.toDouble())).toInt().coerceAtLeast(1)
                val rows = kotlin.math.ceil(densityFixed / cols.toDouble()).toInt().coerceAtLeast(1)

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    repeat(rows) { row ->
                        Row(modifier = Modifier.weight(1f)) {
                            repeat(cols) { col ->
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val tile = row * cols + col
                                    if (tile < densityFixed) {
                                        Text(
                                            text = text,
                                            color = Color(colorLong),
                                            fontSize = scaledFontSize,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                // graphicsLayer 零延迟：GPU 层面直接做矩阵变换，不触发 Compose 重绘
                                                .graphicsLayer {
                                                    rotationZ = angle
                                                    alpha = opacity
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}