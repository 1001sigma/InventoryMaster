package com.example.inventorymaster.utils

object BatchCodeProcessor {

    /**长光批号提取
     * 处理逻辑：
     * 输入：065006200810024022612
     * 规则：提取 第7-10位 (2008) + 第14-17位 (2402)
     * 输出：20082402
     */
    fun process(query: String): String {
        // 1. 安全检查：如果长度不够17位，直接返回原字符串（防止崩溃）
        // 因为要取到第17位，所以长度至少要是17
        val query = query.filter { it.isDigit() }
        if (query.length < 17) {
            return query
        }

        try {
            // 2. 截取第一部分：第7位到第10位
            // 在编程中索引是从0开始的：
            // 第7位 = index 6
            // 第10位 = index 9 (substring的结束位置是不包含的，所以要填 10)
            val part1 = query.substring(6, 10)

            // 3. 截取第二部分：第14位到第17位
            // 第14位 = index 13
            // 第17位 = index 16 (结束位置填 17)
            val part2 = query.substring(13, 17)

            // 4. 合并并去除空格（以防万一）
            return (part1 + part2).trim()

        } catch (e: Exception) {
            // 如果发生任何异常，返回原始字符串，保证程序不崩
            e.printStackTrace()
            return query
        }
    }
}