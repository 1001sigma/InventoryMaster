package com.example.inventorymaster.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 现代化工业/医疗应用配色预设。
 * 采用更符合极简美学的色彩搭配，提升界面的专业度与通透感。
 */
object ModernColorSchemes {

    // 统一的状态色定义（采用现代扁平化的高级色调，避免外部引用缺失）
    val StatusSuccess = Color(0xFF10B981) // 翡翠绿 - 清晰明确
    val StatusWarning = Color(0xFFF59E0B) // 琥珀黄 - 醒目不刺眼
    val StatusInfo = Color(0xFF3B82F6)    // 亮蓝色 - 传达信息
    val M3Error = Color(0xFFBA1A1A)       // 遵循 Material 3 规范的标准错误色

    val Teal = ThemeColors(
        name = "医疗青绿",
        primary = Color(0xFF0D9488),      // 深青色：专业且稳重
        secondary = Color(0xFF14B8A6),    // 明亮青绿：辅助层次
        tertiary = Color(0xFFF97316),     // 活力橙：用于强对比操作（如 FAB）
        error = M3Error
    )

    val Blue = ThemeColors(
        name = "专业深蓝",
        primary = Color(0xFF1E3A8A),      // 藏青蓝：极其商务、沉稳
        secondary = Color(0xFF3B82F6),    // 亮天蓝：活跃界面氛围
        tertiary = Color(0xFF10B981),     // 翠绿：和谐的点缀
        error = M3Error
    )

    val CoolBlue = ThemeColors(
        name = "极简天蓝",
        primary = Color(0xFF0EA5E9),      // 天空蓝：清爽通透
        secondary = Color(0xFF64748B),    // 蓝灰色：自带高级极简感
        tertiary = Color(0xFFF43F5E),     // 玫瑰红：打破沉闷的提示色
        error = M3Error
    )

    val TechPurple = ThemeColors(
        name = "高端科技",
        primary = Color(0xFF6366F1),      // 纯正的靛紫色：浓厚的科技感
        secondary = Color(0xFF8B5CF6),    // 亮紫：辅助色彩
        tertiary = Color(0xFFEC4899),     // 粉紫：柔和的强调色
        error = M3Error
    )

    val Amber = ThemeColors(
        name = "温暖琥珀",
        primary = Color(0xFFD97706),      // 纯正的琥珀金棕色
        secondary = Color(0xFFB45309),    // 深琥珀：增强厚重感
        tertiary = Color(0xFF0F766E),     // 深青色：通过冷色调来平衡画面温度
        error = M3Error
    )

    val Green = ThemeColors(
        name = "经典医绿",
        primary = Color(0xFF059669),      // 标准医疗绿：护眼温和
        secondary = Color(0xFF34D399),    // 浅绿：适合卡片或次要元素
        tertiary = Color(0xFFFBBF24),     // 柔金：增加界面的精致度
        error = M3Error
    )

    // 兼容旧版本调用的别名
    val Vibrant = Teal
    val Professional = Blue
    val Minimal = CoolBlue
    val Tech = TechPurple
    val Warm = Amber
    val Original = Green

    val allSchemes = listOf(Teal, Blue, CoolBlue, TechPurple, Amber, Green)
}

data class ThemeColors(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color = ModernColorSchemes.M3Error,
    val success: Color = ModernColorSchemes.StatusSuccess,
    val warning: Color = ModernColorSchemes.StatusWarning,
    val info: Color = ModernColorSchemes.StatusInfo
)