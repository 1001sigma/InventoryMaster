package com.example.inventorymaster.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

// 默认颜色
private val DefaultSeedColor = Color(0xFF4CAF50)

@Composable
fun InventoryMasterTheme(
    seedColor: Color = DefaultSeedColor,
    useDynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 1. 动态颜色 (Android 12+)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 2. 自定义算法生成的丰富配色
        else -> {
            generateColorScheme(seedColor, darkTheme)
        }
    }

    // 设置状态栏
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 🎨 核心算法：手动模拟 Material 3 的色板生成逻辑
 */
fun generateColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    // 1. 提取 HSL (色相、饱和度、亮度)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)

    // 2. 准备变量
    val hue = hsl[0]
    val sat = hsl[1]

    // 3. 生成 Tertiary (第三色)：通过旋转色相环 60~90 度得到
    // 这样可以让界面有一个“跳跃”的颜色，不再单调
    val tertiaryHue = (hue + 90) % 360
    val tertiary = Color.hsv(tertiaryHue, sat, if (isDark) 0.8f else 0.6f)

    return if (isDark) {
        // === 暗色模式调色板 ===
        darkColorScheme(
            primary = seed, // 主色
            onPrimary = Color.Black,
            // 容器色：稍微亮一点或暗一点，不要只用透明度
            primaryContainer = shiftLightness(seed, 0.2f), // 深色背景下的容器
            onPrimaryContainer = shiftLightness(seed, 0.9f), // 容器上的字要亮

            secondary = shiftLightness(seed, 0.7f), // 副色稍微变一下亮度
            onSecondary = Color.Black,
            secondaryContainer = shiftLightness(seed, 0.3f),
            onSecondaryContainer = shiftLightness(seed, 0.9f),

            tertiary = tertiary, // 撞色
            onTertiary = Color.Black,
            tertiaryContainer = shiftLightness(tertiary, 0.3f),
            onTertiaryContainer = shiftLightness(tertiary, 0.9f),

            background = Color(0xFF121212),
            surface = Color(0xFF1C1B1F)
        )
    } else {
        // === 亮色模式调色板 (主要优化这里) ===
        darkColorScheme( // ⚠️注意：虽然为了方便我们手动填色，但这里如果是亮色应该返回 lightColorScheme
            // 为了逻辑清晰，下面直接构造 lightColorScheme
        )
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,

            // 关键优化：容器色应该是极淡的颜色 (亮度调高)
            // 之前你用的是 seed.copy(alpha=0.9)，那是深色，根本不是背景
            primaryContainer = shiftLightness(seed, 0.95f), // 极淡的背景
            onPrimaryContainer = shiftLightness(seed, 0.2f), // 深色的字

            secondary = seed, // 副色
            onSecondary = Color.White,
            secondaryContainer = shiftLightness(seed, 0.90f), // 稍微深一点点的淡色
            onSecondaryContainer = shiftLightness(seed, 0.15f),

            tertiary = tertiary, // 引入撞色
            onTertiary = Color.White,
            tertiaryContainer = shiftLightness(tertiary, 0.95f),
            onTertiaryContainer = shiftLightness(tertiary, 0.2f),

            background = Color(0xFFFDFDFD),
            surface = Color(0xFFFDFDFD)
        )
    }
}

/**
 * 辅助函数：调整颜色的亮度 (Lightness)
 * factor: 0.0 (黑) ~ 1.0 (白)
 */
fun shiftLightness(color: Color, lightnessTarget: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = lightnessTarget // 强制设置亮度
    return Color(ColorUtils.HSLToColor(hsl))
}