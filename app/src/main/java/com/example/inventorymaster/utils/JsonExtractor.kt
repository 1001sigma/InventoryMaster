package com.example.inventorymaster.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 从 LLM 响应中提取第一个合法 JSON 对象。
 * 处理常见情况：纯 JSON、带前缀文本、```json 代码块包裹等。
 *
 * 实现刻意不使用 Regex——围栏正则在部分机型的 ICU regex 引擎上直接抛
 * PatternSyntaxException（实机案例：Syntax error in regexp pattern near index 31，
 * 同一模式在 JVM 上编译正常），手工扫描无引擎差异且行为可用单测固化。
 */
object JsonExtractor {

    /** 从 text 中提取第一个合法 JsonObject，失败返回 null */
    fun extractJsonObject(text: String): JsonObject? {
        // 1. 尝试直接解析整段文本
        tryParse(text)?.let { return it }

        // 2. ```json ... ``` 代码围栏（兼容 ```json / ``` 前缀、围栏体首行换行）
        val fenceStart = text.indexOf("```")
        if (fenceStart >= 0) {
            var bodyStart = fenceStart + 3
            if (text.length >= bodyStart + 4 && text.regionMatches(bodyStart, "json", 0, 4, ignoreCase = true)) {
                bodyStart += 4
            }
            while (bodyStart < text.length && (text[bodyStart] == '\n' || text[bodyStart] == '\r' || text[bodyStart] == ' ')) {
                bodyStart++
            }
            val fenceEnd = text.indexOf("```", bodyStart)
            if (fenceEnd > bodyStart) {
                tryParse(text.substring(bodyStart, fenceEnd).trim())?.let { return it }
            }
        }

        // 3. 第一个 { 到最后一个 } 之间的内容（覆盖围栏、前后缀文本等剩余情况）
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            tryParse(text.substring(firstBrace, lastBrace + 1))?.let { return it }
        }

        return null
    }

    private fun tryParse(text: String): JsonObject? {
        return try {
            val element = JsonParser.parseString(text.trim())
            if (element.isJsonObject) element.asJsonObject else null
        } catch (_: Exception) {
            null
        }
    }
}
