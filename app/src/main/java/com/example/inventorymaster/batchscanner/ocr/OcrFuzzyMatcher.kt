package com.example.inventorymaster.batchscanner.ocr

/**
 * OCR 批号模糊匹配工具。
 * 通过可配置的字符等价映射表，处理 OCR 常见识别错误。
 * 默认映射：0↔O、I↔1↔l、S↔5、B↔8、Z↔2、G↔6、Q↔O、D↔O
 */
object OcrFuzzyMatcher {

    /** 默认字符等价映射表（OCR 常见混淆字符） */
    val defaultCharMap: Map<Char, Char> = mapOf(
        'O' to '0', 'o' to '0',
        'I' to '1', 'i' to '1', 'l' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'Z' to '2', 'z' to '2',
        'G' to '6',
        'Q' to '0', 'q' to '0',
        'D' to '0'
    )

    /**
     * 模糊匹配两个字符串。
     * 长度必须相同，逐字符通过 charMap 判定等价性。
     */
    fun fuzzyMatch(a: String, b: String, charMap: Map<Char, Char> = defaultCharMap): Boolean {
        if (a.length != b.length) return false
        return a.zip(b).all { (ca, cb) -> charEquivalent(ca, cb, charMap) }
    }

    private fun charEquivalent(a: Char, b: Char, charMap: Map<Char, Char>): Boolean {
        if (a == b) return true
        val na = charMap[a] ?: a
        val nb = charMap[b] ?: b
        return na == nb
    }
}
