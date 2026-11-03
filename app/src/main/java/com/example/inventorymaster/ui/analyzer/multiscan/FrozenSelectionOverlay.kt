package com.example.inventorymaster.ui.analyzer.multiscan

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * 微信级 1:1 无缝原地定格多码选择覆盖层 (FrozenSelectionOverlay)
 *
 * 核心特性：
 * 1. 1:1 原地无缝定格：渲染由 PreviewView.bitmap 截取的瞬间画面，彻底消除黑边与视口跳变；
 * 2. 微信绿波纹锚点：在每个识别到的条码位置渲染 #07C160 呼吸扩散水波纹与 56dp 触控热区；
 * 3. 柔和暗化与秒级解冻：全屏 35% 暗化遮罩，轻触空白区域立即取消定格并恢复相机实时预览；
 * 4. 独立解耦：纯 Compose UI 组件，可被任何扫码页面（单码扫码、批量补扫、盘点等）无缝复用。
 *
 * @param bitmap 定格瞬间捕获的 1:1 底图
 * @param barcodes 定格瞬间各条码的归一化视图像素坐标
 * @param onBarcodeClick 用户选中某个条码时的回调
 * @param onCancel 用户轻触空白处取消定格时的回调
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FrozenSelectionOverlay(
    bitmap: Bitmap,
    barcodes: List<DetectedBarcode>,
    onBarcodeClick: (String) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // 微信同款呼吸水波纹脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "wechat_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // --- A. 1:1 无缝原地底图（来自 PreviewView.bitmap，无黑边无跳帧）---
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Frozen Frame",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // --- B. 35% 柔和暗化遮罩（点击任意空白处立即取消并解冻恢复）---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCancel() }
        )

        // --- C. 顶部微提示条 ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "轻触绿色锚点选择条码，点击空白处取消",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // --- D. 微信绿呼吸水波纹锚点按钮 ---
        barcodes.forEach { code ->
            val finalX = code.normX * screenWidth
            val finalY = code.normY * screenHeight

            val xDp = with(density) { finalX.toDp() }
            val yDp = with(density) { finalY.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = xDp - 28.dp, y = yDp - 28.dp)
                    .size(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBarcodeClick(code.rawValue)
                    },
                contentAlignment = Alignment.Center
            ) {
                // 外层扩散波纹
                Box(
                    modifier = Modifier
                        .size(42.dp * pulseScale)
                        .border(2.dp, Color(0xFF07C160).copy(alpha = pulseAlpha), CircleShape)
                )

                // 核心实心按钮（微信绿）
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(2.5.dp)
                        .background(Color(0xFF07C160), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "选择此码",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
