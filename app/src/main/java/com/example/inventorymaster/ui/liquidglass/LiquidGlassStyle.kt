package com.example.inventorymaster.ui.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃材质参数（四层边缘模型，见《液态玻璃效果实现讨论.md》）。
 * 取值纪律：innerShadowAlpha 必须保持在 0.04~0.12，过高会退化成拟物按钮；
 * borderAlpha 0.45~0.75；highlightAlpha 0.70~0.95；backgroundAlpha 越高越不透。
 */
data class LiquidGlassStyle(
    /** 玻璃主体底色 */
    val containerColor: Color,
    /** 主体填充透明度（0.05~0.20 为晶透玻璃，0.70~0.85 为深色烟熏玻璃） */
    val backgroundAlpha: Float = 0.75f,
    /** 凸面弧度流光强度（0f 关闭；晶透玻璃推荐 0.15~0.25，赋予表面微凸的透镜弧光） */
    val surfaceSheenAlpha: Float = 0f,
    // ── L1 基础边框 ──
    val borderAlpha: Float = 0.65f,
    val borderWidth: Dp = 1.dp,
    // ── L2 内侧方向性高光（左上主 / 右下次） ──
    val highlightAlpha: Float = 0.90f,
    val secondaryHighlightAlpha: Float = 0.55f,
    val highlightWidth: Dp = 1.dp,
    // ── L3 内侧折射暗边/暗槽（拉出物理厚度感，0.06~0.14） ──
    val innerShadowAlpha: Float = 0.08f,
    val innerShadowWidth: Dp = 2.dp,
    // ── L4 外部软阴影 ──
    val outerShadowAlpha: Float = 0.12f,
    val outerShadowElevation: Dp = 16.dp,
) {
    companion object {
        /** 深色烟熏玻璃：默认面板材质，适用于计算器弹窗等深色沉浸面板 */
        val DarkGlass = LiquidGlassStyle(containerColor = Color(0xFF14161A))

        /** 深色加强：边框/高光更亮、阴影更重，用于深色或复杂背景 */
        val DarkStrong = LiquidGlassStyle(
            containerColor = Color(0xFF14161A),
            backgroundAlpha = 0.80f,
            borderAlpha = 0.80f,
            highlightAlpha = 0.95f,
            outerShadowAlpha = 0.20f
        )

        /**
         * 晶透白液态玻璃（高透水晶/透镜质感，参考 Apple VisionOS 与现代化高透亚克力胶囊）：
         * 1. 主体极透（White 10%），由表面微凸流光（surfaceSheen 18%）提供饱满弧面感；
         * 2. 外部锐利高光边（0.85）搭配内侧折射暗缝（0.12），产生真实物理折射率与厚度；
         * 3. 搭配高反差黑色内部控件或深色文字体验最佳。
         */
        val CrystalClear = LiquidGlassStyle(
            containerColor = Color.White,
            backgroundAlpha = 0.10f,
            surfaceSheenAlpha = 0.18f,
            borderAlpha = 0.85f,
            borderWidth = 1.dp,
            highlightAlpha = 0.95f,
            secondaryHighlightAlpha = 0.45f,
            highlightWidth = 1.dp,
            innerShadowAlpha = 0.12f,
            innerShadowWidth = 1.2.dp,
            outerShadowAlpha = 0.12f,
            outerShadowElevation = 12.dp
        )
    }
}
