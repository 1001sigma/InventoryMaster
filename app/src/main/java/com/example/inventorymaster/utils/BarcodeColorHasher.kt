package com.example.inventorymaster.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 条码特征颜色哈希工具 (黄金分割 HSL 色散算法)
 *
 * 核心逻辑：
 * 1. 提取条码特征键：标准医疗 UDI 优先提取 DI(14位) + Batch(批号)；非 UDI 则提取原始文本
 * 2. 32 位 FNV-1a 哈希运算，保证相同输入确定性输出相同色值
 * 3. 乘以黄金分割共轭常数 φ ≈ 0.6180339887 进行色相 (Hue) 发散
 *    - 同物料同批次：输入相同，颜色绝对一致（如紫罗兰色）
 *    - 同物料不同批号：微小的批号变动将在 360° 色相环上引发大角度跳跃（通常 >130°），视觉预警混批
 * 4. 固定高饱和度 (82%) 与适中明度 (52%)，呈现高质感、工业级的鲜艳色谱，兼顾深浅背景高对比度
 */
object BarcodeColorHasher {

    private const val GOLDEN_RATIO_CONJUGATE = 0.618033988749895
    private const val SATURATION = 0.82f
    private const val LIGHTNESS = 0.52f

    /**
     * 提取条码特征键：优先提取 DI + 批号
     */
    fun extractFeatureKey(rawBarcode: String?): String {
        if (rawBarcode.isNullOrBlank()) return ""
        val trimmed = rawBarcode.trim()
        val parsed = Gs1Parser.parse(trimmed)
        val di = parsed.di?.trim()
        val batch = parsed.batch?.trim()
        return if (!di.isNullOrEmpty()) {
            "$di#${batch.orEmpty()}"
        } else {
            trimmed
        }
    }

    /**
     * 32 位 FNV-1a 哈希算法
     */
    fun fnv1aHash(key: String): Int {
        var hash = 0x811c9dc5.toInt()
        val bytes = key.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xff)
            hash = hash * 0x01000193
        }
        return hash
    }

    /**
     * 根据条码特征键计算黄金分割发散色相 (0..360)
     */
    fun calculateHue(key: String): Float {
        if (key.isEmpty()) return 120f // 默认绿
        val hash = fnv1aHash(key)
        val unsignedHash = hash.toLong() and 0xFFFFFFFFL
        val frac = (unsignedHash.toDouble() * GOLDEN_RATIO_CONJUGATE) % 1.0
        val positiveFrac = if (frac < 0.0) frac + 1.0 else frac
        return (positiveFrac * 360.0).toFloat()
    }

    /**
     * HSL 转 Compose Color
     * @param h 色相 0..360
     * @param s 饱和度 0..1
     * @param l 明度 0..1
     * @param alpha 透明度 0..1
     */
    fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hPrime = (h % 360f) / 60f
        val x = c * (1f - abs(hPrime % 2f - 1f))
        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha
        )
    }

    /**
     * 获取条码对应的 Compose Color
     */
    fun getColor(rawBarcode: String?): Color {
        if (rawBarcode.isNullOrBlank()) return Color(0xFF4CAF50)
        val key = extractFeatureKey(rawBarcode)
        val hue = calculateHue(key)
        return hslToColor(hue, SATURATION, LIGHTNESS)
    }

    /**
     * 获取条码对应的 16 进制颜色字符串 (如 "#7C20E8")
     */
    fun getHexColor(rawBarcode: String?): String {
        val color = getColor(rawBarcode)
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }
}
