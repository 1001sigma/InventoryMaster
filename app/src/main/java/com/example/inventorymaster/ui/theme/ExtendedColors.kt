package com.example.inventorymaster.ui.theme

import androidx.compose.ui.graphics.Color

// ========== 📱 现代化预设配色方案 ==========
// 每套配色都包含：主色、副色、强调色、三色（撞色）

object ModernColorSchemes {

    // 方案1：青春活力 (推荐库存管理) ✅
    val Vibrant = ThemeColors(
        name = "青春活力",
        primary = Color(0xFF00BFA5),      // 活力青
        secondary = Color(0xFF00E5FF),    // 天空蓝
        tertiary = Color(0xFFFF6E40),     // 橙红撞色
        error = Color(0xFFE53935)
    )

    // 方案2：专业商务
    val Professional = ThemeColors(
        name = "专业商务",
        primary = Color(0xFF1976D2),      // 深蓝
        secondary = Color(0xFF00897B),    // 深绿
        tertiary = Color(0xFFD32F2F),     // 商务红
        error = Color(0xFFC62828)
    )

    // 方案3：清爽极简
    val Minimal = ThemeColors(
        name = "清爽极简",
        primary = Color(0xFF4A90E2),      // 天蓝
        secondary = Color(0xFF7ED321),    // 鲜绿
        tertiary = Color(0xFFFF3D00),     // 橙色
        error = Color(0xFFD32F2F)
    )

    // 方案4：高端科技
    val Tech = ThemeColors(
        name = "高端科技",
        primary = Color(0xFF00D4FF),      // 氖蓝
        secondary = Color(0xFF7C3AED),    // 紫色
        tertiary = Color(0xFFFFB000),     // 琥珀
        error = Color(0xFFE53935)
    )

    // 方案5：暖色系
    val Warm = ThemeColors(
        name = "暖色系",
        primary = Color(0xFFFF6D00),      // 橙色
        secondary = Color(0xFFFFAB00),    // 琥珀
        tertiary = Color(0xFFDD2C00),     // 深红
        error = Color(0xFFC62828)
    )

    // 方案6：原始绿色（保留原设计）
    val Original = ThemeColors(
        name = "原始绿色",
        primary = Color(0xFF4CAF50),
        secondary = Color(0xFF2196F3),
        tertiary = Color(0xFFFF9800),
        error = Color(0xFFE53935)
    )

    val allSchemes = listOf(Vibrant, Professional, Minimal, Tech, Warm, Original)
}

// 数据类：存储一套完整的颜色方案
data class ThemeColors(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color,
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFFC107),
    val info: Color = Color(0xFF2196F3)
)