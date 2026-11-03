package com.example.inventorymaster.utils

object BatchCodeProcessor {
    /**
     * 判断输入是否符合长光华医试剂条码特征：
     * 1. 不包含 GS1 括号标识符 (01) 或 GS 分隔符
     * 2. 不包含汉字等非数字字符（允许包含空格）
     * 3. 提取出的数字长度在 17..23 位之间（典型为 21 位，如 065006200810024022612）
     */
    fun isHybiomeBarcode(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        if (query.contains("(") || query.contains(")") || query.contains("\u001D")) {
            return false
        }
        val nonDigits = query.filter { !it.isDigit() && !it.isWhitespace() }
        if (nonDigits.isNotEmpty()) return false

        val digits = query.filter { it.isDigit() }
        return digits.length in 17..23
    }

    /**
     * 长光批号提取
     * 输入：065006200810024022612
     * 规则：提取 第7-10位 (2008) + 第14-17位 (2402)
     * 输出：20082402
     */
    fun extractBatch(query: String?): String? {
        if (query.isNullOrBlank()) return null
        val digits = query.filter { it.isDigit() }
        if (digits.length < 17) return null

        return try {
            val part1 = digits.substring(6, 10)
            val part2 = digits.substring(13, 17)
            (part1 + part2).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 保持向后兼容的方法
     * 输入：065006200810024022612 -> 输出：20082402
     * 输入为空 -> 返回 null
     * 无法提取（字符不足或乱码） -> 返回原始字符串
     */
    fun process(query: String): String? {
        if (query.isEmpty()) return null
        val digits = query.filter { it.isDigit() }
        if (digits.length < 17) {
            return query
        }

        return try {
            val part1 = digits.substring(6, 10)
            val part2 = digits.substring(13, 17)
            (part1 + part2).trim()
        } catch (e: Exception) {
            e.printStackTrace()
            query
        }
    }
}