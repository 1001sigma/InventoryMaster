package com.example.inventorymaster.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

// 统一圆角档位：8 小组件(芯片/输入) / 12 列表项与小卡 / 16 标准卡片 / 24 大卡片 / 28 玻璃面板
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InventoryMasterTheme(
    colorTuple: ColorTuple = ColorTupleDefaults.Teal.tuple,
    useDynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoledMode: Boolean = false,
    // 兼容历史调用参
    themeColors: ThemeColors? = null,
    content: @Composable () -> Unit
) {
    val effectiveTuple = themeColors?.asColorTuple() ?: colorTuple

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

            // 当开启深色且启用 AMOLED 纯黑时，对系统动态深色方案实施纯黑化覆写
            if (darkTheme && isAmoledMode) {
                baseScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF121212),
                    outlineVariant = Color(0xFF2E2E2E)
                )
            } else {
                baseScheme
            }
        }
        else -> generateColorScheme(
            tuple = effectiveTuple,
            isDark = darkTheme,
            isAmoled = isAmoledMode
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}

/**
 * 根据 ColorTuple、深浅模式及 AMOLED 状态，生成完整的 Material 3 ColorScheme。
 * 核心色采用 ColorTuple 定义，派生色（如 Container、OnColor）通过亮度偏移自动生成。
 */
fun generateColorScheme(
    tuple: ColorTuple,
    isDark: Boolean,
    isAmoled: Boolean = false
): ColorScheme {
    val primary = tuple.primary
    val secondary = tuple.resolvedSecondary
    val tertiary = tuple.resolvedTertiary
    val error = ModernColorSchemes.M3Error

    return if (isDark) {
        // 深色模式：主色调适度提亮以保证在深色背景上的对比度
        val isPureBlack = isAmoled
        darkColorScheme(
            primary = shiftLightness(primary, 0.65f),
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = shiftLightness(primary, 0.25f),
            onPrimaryContainer = shiftLightness(primary, 0.90f),

            secondary = shiftLightness(secondary, 0.65f),
            onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = shiftLightness(secondary, 0.25f),
            onSecondaryContainer = shiftLightness(secondary, 0.90f),

            tertiary = shiftLightness(tertiary, 0.70f),
            onTertiary = Color(0xFF1A1A1A),
            tertiaryContainer = shiftLightness(tertiary, 0.25f),
            onTertiaryContainer = shiftLightness(tertiary, 0.90f),

            error = shiftLightness(error, 0.65f),
            onError = Color(0xFF1A1A1A),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),

            // AMOLED 模式下全面纯黑化，普通深色模式保留 0D0D0D/141414 层次
            background = if (isPureBlack) Color.Black else Color(0xFF0D0D0D),
            onBackground = Color(0xFFE3E3E3),
            surface = if (isPureBlack) Color.Black else Color(0xFF141414),
            onSurface = Color(0xFFE3E3E3),
            surfaceVariant = if (isPureBlack) Color(0xFF121212) else Color(0xFF1E1E1E),
            onSurfaceVariant = Color(0xFFC4C4C4),
            outline = if (isPureBlack) Color(0xFF666666) else Color(0xFF8E8E8E),
            outlineVariant = if (isPureBlack) Color(0xFF2E2E2E) else Color(0xFF444444)
        )
    } else {
        // 浅色模式：直接使用定义的颜色，生成极度浅色的容器作为卡片底色
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = shiftLightness(primary, 0.94f),
            onPrimaryContainer = shiftLightness(primary, 0.20f),

            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = shiftLightness(secondary, 0.94f),
            onSecondaryContainer = shiftLightness(secondary, 0.20f),

            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = shiftLightness(tertiary, 0.94f),
            onTertiaryContainer = shiftLightness(tertiary, 0.20f),

            error = error,
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
 * 兼容旧版本 ThemeColors 参数的重载
 */
fun generateColorScheme(colors: ThemeColors, isDark: Boolean): ColorScheme =
    generateColorScheme(tuple = colors.asColorTuple(), isDark = isDark, isAmoled = false)

/**
 * 保持色相和饱和度不变，改变颜色的亮度 (Lightness)
 */
fun shiftLightness(color: Color, lightnessTarget: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = lightnessTarget
    return Color(ColorUtils.HSLToColor(hsl))
}