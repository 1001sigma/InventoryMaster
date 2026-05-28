package com.example.inventorymaster.ui.theme

import android.app.Activity
import android.os.Build
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

// 默认使用我们在 ExtendedColors.kt 中定义的某个主题，比如 Teal
private val DefaultThemeColors = ModernColorSchemes.Teal

@Composable
fun InventoryMasterTheme(
    // 💡 优化点 1：将入参从单一的 seedColor 改为我们定义好的 ThemeColors 对象
    themeColors: ThemeColors = DefaultThemeColors,
    useDynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> generateColorScheme(themeColors, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 💡 优化点 2：将状态栏颜色设置为完全透明，配合现代沉浸式/全面屏 UI
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // 这里保留了你原有的 AppTypography，如果报错请确保对应的字体文件存在
        typography = Typography(),
        content = content
    )
}

/**
 * 根据预设的 ThemeColors 和当前深浅模式，生成完整的 Material 3 ColorScheme。
 * 核心主色使用 ThemeColors 中的定义，派生色（如 Container、OnColor）通过亮度偏移自动生成。
 */
fun generateColorScheme(colors: ThemeColors, isDark: Boolean): ColorScheme {
    return if (isDark) {
        // 深色模式：主色调适度提亮以保证在深色背景上的对比度
        darkColorScheme(
            primary = shiftLightness(colors.primary, 0.65f),
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = shiftLightness(colors.primary, 0.25f),
            onPrimaryContainer = shiftLightness(colors.primary, 0.90f),

            secondary = shiftLightness(colors.secondary, 0.65f),
            onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = shiftLightness(colors.secondary, 0.25f),
            onSecondaryContainer = shiftLightness(colors.secondary, 0.90f),

            tertiary = shiftLightness(colors.tertiary, 0.70f),
            onTertiary = Color(0xFF1A1A1A),
            tertiaryContainer = shiftLightness(colors.tertiary, 0.25f),
            onTertiaryContainer = shiftLightness(colors.tertiary, 0.90f),

            error = shiftLightness(colors.error, 0.65f),
            onError = Color(0xFF1A1A1A),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),

            // 使用高级的深空灰而不是纯黑
            background = Color(0xFF121212),
            onBackground = Color(0xFFE3E3E3),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE3E3E3),
            surfaceVariant = Color(0xFF333333),
            onSurfaceVariant = Color(0xFFC4C4C4),
            outline = Color(0xFF8E8E8E),
            outlineVariant = Color(0xFF444444)
        )
    } else {
        // 浅色模式：直接使用定义的颜色，生成极度浅色的容器作为卡片底色
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = shiftLightness(colors.primary, 0.94f),
            onPrimaryContainer = shiftLightness(colors.primary, 0.20f),

            secondary = colors.secondary,
            onSecondary = Color.White,
            secondaryContainer = shiftLightness(colors.secondary, 0.94f),
            onSecondaryContainer = shiftLightness(colors.secondary, 0.20f),

            tertiary = colors.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = shiftLightness(colors.tertiary, 0.94f),
            onTertiaryContainer = shiftLightness(colors.tertiary, 0.20f),

            error = colors.error,
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),

            // 浅色模式表面色优化：背景使用极淡的灰白，Surface（卡片）使用纯白，拉开层次
            background = Color(0xFFF8F9FA),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFF0F1F3),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFD3D3D3)
        )
    }
}

/**
 * 保持色相和饱和度不变，改变颜色的亮度 (Lightness)
 */
fun shiftLightness(color: Color, lightnessTarget: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = lightnessTarget
    return Color(ColorUtils.HSLToColor(hsl))
}