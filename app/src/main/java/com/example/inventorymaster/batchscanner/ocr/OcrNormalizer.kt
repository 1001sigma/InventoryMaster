package com.example.inventorymaster.batchscanner.ocr

import com.example.inventorymaster.utils.DateUtils

/**
 * OCR 结果标准化器。
 * 统一日期格式为 yyyy-MM-dd，批号去空格/全角转半角/常见 OCR 错误修正。
 * 规则引擎、AI、后续比对模块都使用标准化后的数据。
 */
object OcrNormalizer {

    /** 统一日期格式为 yyyy-MM-dd，失败返回原值 */
    fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val result = DateUtils.formatDateForDisplay(raw, default = "")
        return if (result.isNotBlank()) result else raw
    }

    /** 统一批号格式：去首尾空格、全角转半角、常见 OCR 错误修正 */
    fun normalizeBatch(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var result = raw.trim()
        result = normalizeFullWidth(result)
        result = fixOcrBatchErrors(result)
        return result.ifBlank { null }
    }

    /** 全角 → 半角 */
    fun normalizeFullWidth(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            when {
                // 全角空格 → 半角空格
                code == 0x3000 -> sb.append(' ')
                // 全角 ASCII 范围 ！(0xFF01) ～ ～(0xFF5E) → 半角  ~(0x0021-0x007E)
                code in 0xFF01..0xFF5E -> sb.append((code - 0xFEE0).toChar())
                // 全角数字 ０-９ → 0-9
                code in 0xFF10..0xFF19 -> sb.append((code - 0xFF10 + '0'.code).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 修正 OCR 常见的批号识别错误 */
    private fun fixOcrBatchErrors(text: String): String {
        var result = text
        // 去掉批号标签前缀（如果被误识别进来）
        result = result.replace(Regex("""^(?:批号|LOT|Lot|SN|REF)[：:\s]*""", RegexOption.IGNORE_CASE), "")
        // l/L/| → 1（仅在批号中，纯字母批号不动）
        // 只在包含数字时做替换，避免把纯字母批号改坏
        if (result.any { it.isDigit() }) {
            result = result.replace(Regex("""[lL|](?=\d)"""), "1")
            result = result.replace(Regex("""(?<=\d)[lL|]"""), "1")
        }
        return result
    }
}
