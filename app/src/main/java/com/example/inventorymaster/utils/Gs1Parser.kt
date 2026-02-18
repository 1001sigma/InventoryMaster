package com.example.inventorymaster.utils

/**
 * GS1-128 解析器 (修正增强版)
 */
object Gs1Parser {

    /** 内部统一分隔符 */
    private const val SEP = '\u001D' // 使用 ASCII 29 (GS) 作为内部标准分隔符

    /** AI 规则定义 */
    data class AIRule(
        val desc: String,
        val fixedLength: Boolean, // 是否定长
        val length: Int? = null,  // 定长时的数据长度 (不含AI)
        val maxLength: Int? = null // 变长时的最大长度
    )

    /** 常用规则配置 */
    private val aiRules: Map<String, AIRule> = mapOf(
        // === 标识类 ===
        "00" to AIRule("SSCC", true, 18),
        "01" to AIRule("GTIN/DI", true, 14),
        "02" to AIRule("Content GTIN", true, 14),
        // === 变长属性 ===
        "10" to AIRule("Batch/Lot", false, maxLength = 20),
        "21" to AIRule("Serial", false, maxLength = 20),
        "22" to AIRule("CPV", false, maxLength = 20),
        // === 日期类 (定长) ===
        "11" to AIRule("Prod. Date", true, 6),
        "13" to AIRule("Pack Date", true, 6),
        "15" to AIRule("Best Before", true, 6),
        "17" to AIRule("Expiry Date", true, 6),
        // === 数量/其他 ===
        "30" to AIRule("Count", false, maxLength = 8),
        "37" to AIRule("Count", false, maxLength = 8),
        "91" to AIRule("Internal", false, maxLength = 30)

    )

    /** 解析结果封装 */
    data class ParseResult(
        val isUdi: Boolean,
        val rawValue: String,
        val items: Map<String, String> // 简化为 Map<AI, Value>，如果AI重复取最后一个
    ) {
        // 快捷获取常用字段的辅助方法
        val di: String? get() = items["01"]
        val batch: String? get() = items["10"]
        val expiry: Long? get() = items["17"]?.let { parseDate(it) }

        // 将 YYMMDD 转为 Long (20YYMMDD)
        private fun parseDate(yymmdd: String): Long? {
            return try {
                if (yymmdd.length == 6) "20$yymmdd".toLong() else null
            } catch (e: Exception) { null }
        }
    }

    /** 核心解析方法 */
    fun parse(input: String): ParseResult {
        if (input.isBlank()) return ParseResult(false, input, emptyMap())

        // 1. 预处理：解决括号和分隔符问题
        val processedStr = preprocess(input)

        // 判断：如果不含 GS1 特征（既不是01开头，也没有分隔符），直接视为非 UDI
        // 注意：这里为了宽容度，只要能解析出任何一个 Key 都算成功

        val resultItems = linkedMapOf<String, String>()
        var currentStr = processedStr

        try {
            while (currentStr.isNotEmpty()) {
                // 如果遇到分隔符开头，直接跳过
                if (currentStr[0] == SEP) {
                    currentStr = currentStr.drop(1)
                    continue
                }

                // 2. 匹配 AI
                val ai = findAI(currentStr) ?: break // 找不到 AI 了，停止解析（视为剩余部分是垃圾数据）
                val rule = aiRules[ai]!!

                // 3. 提取数据
                var dataPart = currentStr.drop(ai.length) // 去掉 AI 头

                // 解释：如果输入是 (01)123...，预处理变成了 01<SEP>123...
                // 去掉 01 后，dataPart 变成了 <SEP>123...
                // 我们必须检测并把这个紧跟在 AI 后面的分隔符删掉，否则它会占用数据的第1位！
                if (dataPart.isNotEmpty() && dataPart[0] == SEP) {
                    dataPart = dataPart.drop(1)
                }

                val (value, remaining) = if (rule.fixedLength) {
                    extractFixed(dataPart, rule.length ?: 0)
                } else {
                    extractVariable(dataPart, rule.maxLength ?: 99)
                }

                resultItems[ai] = value
                currentStr = remaining
            }
        } catch (e: Exception) {
            // 解析过程中出错（比如长度不够），保留已解析的部分
            e.printStackTrace()
        }

        // 判定标准：至少解析出了 01 (DI)，才认为是有效的 UDI 码
        val isValidUdi = resultItems.containsKey("01")

        return ParseResult(isValidUdi, input, resultItems)
    }

    /**
     * 关键修正：预处理
     * 将人类可读的括号格式 (10)ABC 转换为机器码 10ABC<GS>
     */
    private fun preprocess(raw: String): String {
        var s = raw.trim()
        // === 清洗协议头 (Symbology Identifier) ===
        // 很多专业扫码引擎会返回 ISO/IEC 15424 标准前缀
        // ]C1 = GS1-128 
        // ]Q1 = GS1 QR Code
        // ]d2 = GS1 DataMatrix
        // ]e0 = GS1 DataBar
        // 这些前缀通常占3个字符，代表"这是GS1格式的数据"，需要去掉才能解析
        if (s.startsWith("]C1") ||
            s.startsWith("]Q1") ||
            s.startsWith("]d2") ||
            s.startsWith("]e0") ||
            s.startsWith("]L1")) {
            s = s.substring(3)
        }
        // 1. 替换常见的 FNC1 字符为统一的 SEP
        s = s.replace(0x1D.toChar(), SEP) // ASCII 29 Group Separator
            .replace(0xE8.toChar(), SEP) // 有些扫码枪会映射为这个

        // 2. 处理括号格式：(10)ABC(17)YYMMDD
        // 逻辑：如果包含括号，我们假设它是人类可读格式
        if (s.contains("(") && s.contains(")")) {
            // 将 ')' 替换为 SEP，将 '(' 去掉
            // 例子: (10)ABC(17)123 -> 10)ABC17)123 -> 10<SEP>ABC17<SEP>123
            // 注意：定长字段后面多一个 SEP 没关系，解析时会忽略
            s = s.replace(")", SEP.toString()).replace("(", "")
        }

        return s
    }

    /** 寻找最长匹配的 AI */
    private fun findAI(s: String): String? {
        // 尝试截取前 4, 3, 2 位
        val maxLen = minOf(4, s.length)
        for (i in maxLen downTo 2) {
            val prefix = s.substring(0, i)
            if (aiRules.containsKey(prefix)) {
                return prefix
            }
        }
        return null
    }

    /** 提取定长数据 */
    private fun extractFixed(s: String, len: Int): Pair<String, String> {
        if (s.length < len) throw IllegalArgumentException("Data too short")
        val value = s.substring(0, len)
        // 哪怕定长字段后面跟了 SEP，也只切 len 长度，剩下的留给下一轮处理
        return value to s.substring(len)
    }

    /** 提取变长数据 */
    private fun extractVariable(s: String, maxLen: Int): Pair<String, String> {
        // 找分隔符
        val sepIndex = s.indexOf(SEP)

        // 确定截取长度
        var cutLen = if (sepIndex != -1) {
            sepIndex // 有分隔符，读到分隔符之前
        } else {
            s.length // 无分隔符，读到底
        }

        // 超过最大长度限制进行截断（保护机制）
        if (cutLen > maxLen) {
            cutLen = maxLen
        }

        val value = s.substring(0, cutLen)

        // 计算剩余字符串
        // 如果是因为分隔符截断的，remaining 要跳过那个分隔符 (+1)
        val remainingStart = if (sepIndex != -1 && sepIndex == cutLen) cutLen + 1 else cutLen
        val remaining = if (remainingStart < s.length) s.substring(remainingStart) else ""

        return value to remaining
    }
}