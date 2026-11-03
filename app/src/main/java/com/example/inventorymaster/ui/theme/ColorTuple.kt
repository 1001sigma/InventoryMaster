package com.example.inventorymaster.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * 表达应用色彩方案的核心元组，参考 ImageToolbox 的 ColorTuple 架构。
 * 包含：
 * - [primary]: 主色（界面的核心主基调，如标题栏、重点按钮、强调线条）
 * - [secondary]: 辅助色（若为 null 则自动基于主色计算生成）
 * - [tertiary]: 三级点缀色（若为 null 则自动基于主色对比/互补生成）
 */
data class ColorTuple(
    val primary: Color,
    val secondary: Color? = null,
    val tertiary: Color? = null
) {
    /** 解析出的有效辅助色（若未显式指定，则根据主色色相偏置与饱和度生成） */
    val resolvedSecondary: Color
        get() = secondary ?: primary.calculateSecondaryColor()

    /** 解析出的有效三级色（若未显式指定，则根据主色对比色相生成） */
    val resolvedTertiary: Color
        get() = tertiary ?: primary.calculateTertiaryColor()

    /**
     * 紧凑序列化为字符串：#RRGGBB|#RRGGBB|#RRGGBB
     */
    fun toSerializedString(): String {
        val p = primary.toHex()
        val s = secondary?.toHex() ?: ""
        val t = tertiary?.toHex() ?: ""
        return "$p|$s|$t"
    }

    override fun toString(): String =
        "ColorTuple(primary=${primary.toHex()}, secondary=${secondary?.toHex()}, tertiary=${tertiary?.toHex()})"

    companion object {
        fun fromSerializedString(str: String?): ColorTuple? {
            if (str.isNullOrBlank()) return null
            val parts = str.split("|")
            val p = parseColorOrNull(parts.getOrNull(0)) ?: return null
            val s = parseColorOrNull(parts.getOrNull(1))
            val t = parseColorOrNull(parts.getOrNull(2))
            return ColorTuple(primary = p, secondary = s, tertiary = t)
        }

        fun parseColorOrNull(hex: String?): Color? {
            if (hex.isNullOrBlank()) return null
            val clean = hex.trim()
            return try {
                val parsed = android.graphics.Color.parseColor(if (clean.startsWith("#")) clean else "#$clean")
                Color(parsed)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * 将 Color 转为标准 HEX 字符串，例如 #0D9488 或 #FF0D9488
 */
fun Color.toHex(includeAlpha: Boolean = false): String {
    val argb = this.toArgb()
    return if (includeAlpha) {
        String.format("#%08X", argb)
    } else {
        String.format("#%06X", argb and 0x00FFFFFF)
    }
}

/**
 * 基于主色计算和谐的辅助色 (Secondary)：
 * 色相顺时针偏移 15°，饱和度适度收敛至 75%，保持界面层次
 */
fun Color.calculateSecondaryColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[0] = (hsl[0] + 15f) % 360f
    hsl[1] = (hsl[1] * 0.75f).coerceIn(0.2f, 0.9f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * 基于主色计算生动的三级点缀色 (Tertiary)：
 * 色相偏移 60°，饱和度适中，增加界面点缀与活力
 */
fun Color.calculateTertiaryColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[0] = (hsl[0] + 60f) % 360f
    hsl[1] = (hsl[1] * 0.85f).coerceIn(0.3f, 0.95f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * 预设配色方案封装（名称 + ColorTuple）
 */
data class PresetTheme(
    val id: String,
    val name: String,
    val tuple: ColorTuple,
    val description: String = ""
)

/**
 * 预设调色板默认清单（整合原有 6 色并扩展契合仓储环境的优质配色）
 */
object ColorTupleDefaults {
    val Teal = PresetTheme(
        id = "teal",
        name = "医疗青绿",
        tuple = ColorTuple(Color(0xFF0D9488), Color(0xFF14B8A6), Color(0xFFF97316)),
        description = "专业稳重，医疗设备系统标配"
    )

    val Blue = PresetTheme(
        id = "blue",
        name = "专业深蓝",
        tuple = ColorTuple(Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF10B981)),
        description = "藏青商务，沉稳清晰高对比"
    )

    val CoolBlue = PresetTheme(
        id = "cool_blue",
        name = "极简天蓝",
        tuple = ColorTuple(Color(0xFF0EA5E9), Color(0xFF64748B), Color(0xFFF43F5E)),
        description = "清爽通透，蓝灰质感极简"
    )

    val TechPurple = PresetTheme(
        id = "tech_purple",
        name = "高端科技",
        tuple = ColorTuple(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899)),
        description = "纯正靛紫，浓厚工业科技感"
    )

    val Amber = PresetTheme(
        id = "amber",
        name = "温暖琥珀",
        tuple = ColorTuple(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF0F766E)),
        description = "琥珀金棕，平衡画面温度"
    )

    val Green = PresetTheme(
        id = "green",
        name = "经典医绿",
        tuple = ColorTuple(Color(0xFF059669), Color(0xFF34D399), Color(0xFFFBBF24)),
        description = "标准医疗绿，护眼温和"
    )

    val Slate = PresetTheme(
        id = "slate",
        name = "玄铁冷灰",
        tuple = ColorTuple(Color(0xFF475569), Color(0xFF64748B), Color(0xFF0284C7)),
        description = "冷灰工业质感，长时间作业抗疲劳"
    )

    val Rose = PresetTheme(
        id = "rose",
        name = "晚霞珊瑚",
        tuple = ColorTuple(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFF0EA5E9)),
        description = "活力醒目，高辨识度"
    )

    val presets = listOf(Teal, Blue, CoolBlue, TechPurple, Amber, Green, Slate, Rose)
}
