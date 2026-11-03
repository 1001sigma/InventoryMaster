package com.example.inventorymaster.utils

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 日期格式化工具类
 * 用于将各种格式的日期转换为统一的显示格式 (yyyy-MM-dd)
 */
object DateUtils {

    private val FORMATTER_8 = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val FORMATTER_6 = DateTimeFormatter.ofPattern("yyMMdd")

    /**
     * 判断一个Long值是否是Unix时间戳（毫秒）
     * Unix时间戳通常是13位数字，范围大致在 946684800000L (2000-01-01) ~ 4102444800000L (2100-01-01)
     */
    private fun isUnixTimestamp(value: Long): Boolean {
        return value > 946684800000L && value < 4102444800000L
    }

    /**
     * 将Long类型的日期转换为 yyyy-MM-dd 字符串
     * 支持两种格式：
     * 1. Unix时间戳（毫秒）：自动识别并转换
     * 2. yyyyMMdd 或 yyMMdd 格式的数字
     */
    fun formatDateForDisplay(dateLong: Long): String {
        // 先判断是否是Unix时间戳
        if (isUnixTimestamp(dateLong)) {
            return try {
                val date = java.time.Instant.ofEpochMilli(dateLong)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                dateLong.toString()
            }
        }

        // 按 yyyyMMdd 或 yyMMdd 格式解析
        val dateStr = dateLong.toString()
        return formatDateFromDigits(dateStr) ?: dateStr
    }

    /**
     * 将Long类型的效期/生产日期解析为 LocalDate（业务判定用：近效期徽标、AI 异常分析等）。
     * 与 formatDateForDisplay 用同一套形态判定，保证"显示正常则判定也正常"：
     * 1. Unix时间戳（毫秒）：服务端 Excel 导入的历史数据（13位，2000~2100年区间）
     * 2. yyyyMMdd / yyMMdd 数字
     * GS1 AI(17) 允许"日=00"表示日未知，按当月最后一天处理。
     * 解析失败返回 null，调用方按正常效期处理。
     */
    fun toLocalDate(dateLong: Long?): LocalDate? {
        if (dateLong == null || dateLong <= 0L) return null
        if (isUnixTimestamp(dateLong)) {
            return try {
                Instant.ofEpochMilli(dateLong).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
                null
            }
        }
        val digits = dateLong.toString()
        if (digits.length != 8 && digits.length != 6) return null
        return try {
            val year = if (digits.length == 8) digits.substring(0, 4).toInt()
            else 2000 + digits.substring(0, 2).toInt()
            val month = digits.substring(digits.length - 4, digits.length - 2).toInt()
            val day = digits.substring(digits.length - 2).toInt()
            if (month < 1 || month > 12) return null
            if (day == 0) {
                YearMonth.of(year, month).atEndOfMonth()
            } else {
                LocalDate.of(year, month, day)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将日期字符串（多种格式）格式化为 yyyy-MM-dd 格式
     * 支持的格式：
     * - yyyy-MM-dd, yyyy/MM/dd, yyyy.MM.dd
     * - yyyyMMdd, yyMMdd
     * - yyyy/M/d, yyyy/M/dd, yyyy/MM/d (无前导零)
     * - 纯数字 6-8 位
     */
    fun formatDateForDisplay(dateStr: String?, default: String = "-"): String {
        if (dateStr.isNullOrBlank()) return default

        // 尝试用 LocalDate.parse 直接解析标准格式
        val trimmed = dateStr.trim()

        // 尝试多种标准格式
        val parsers = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yy-MM-dd"),
            DateTimeFormatter.ofPattern("yy/MM/dd"),
            DateTimeFormatter.ofPattern("yy-M-d"),
            DateTimeFormatter.ofPattern("yy/M/d")
        )

        for (fmt in parsers) {
            try {
                val date = LocalDate.parse(trimmed, fmt)
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {
                // 继续尝试下一个格式
            }
        }

        // 移除分隔符后尝试按纯数字解析
        val digits = trimmed.replace(Regex("[^0-9]"), "")
        return formatDateFromDigits(digits) ?: default
    }

    /**
     * 将日期字符串转换为Long格式（用于存储，yyyyMMdd格式）
     */
    fun parseDateToLong(dateStr: String): Long? {
        if (dateStr.isBlank()) return null

        val formatted = formatDateForDisplay(dateStr, returnNull = true) ?: return null
        return try {
            val date = LocalDate.parse(formatted, DateTimeFormatter.ISO_LOCAL_DATE)
            date.format(FORMATTER_8).toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 内部方法：从纯数字字符串解析日期
     */
    private fun formatDateFromDigits(digits: String): String? {
        if (digits.isBlank()) return null

        return when (digits.length) {
            8 -> { // yyyyMMdd
                try {
                    val date = LocalDate.parse(digits, FORMATTER_8)
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            6 -> { // yyMMdd
                try {
                    val date = LocalDate.parse(digits, FORMATTER_6)
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            7 -> { // 可能是 yyyyMdd 或 yyMMdd (缺少一位)
                // 尝试 yyyyMdd: 如 202749 -> 2027-04-09
                val y4 = digits.substring(0, 4)
                val md = digits.substring(4)
                try {
                    val date = LocalDate.parse("${y4}0$md", FORMATTER_8)
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    // 尝试 yyMMdd 缺少一位的情况：26113 -> 2026-01-13
                    val y2 = digits.substring(0, 2)
                    val mdd = digits.substring(2)
                    try {
                        val date = LocalDate.parse("${y2}0$mdd", FORMATTER_6)
                        date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (e2: Exception) {
                        null
                    }
                }
            }
            5 -> { // 可能是 yyMdd
                val y2 = digits.substring(0, 2)
                val md = digits.substring(2)
                try {
                    val date = LocalDate.parse("${y2}0$md", FORMATTER_6)
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 内部方法：parseDateToLong 专用，返回null而非default
     */
    private fun formatDateForDisplay(dateStr: String, returnNull: Boolean): String? {
        if (dateStr.isBlank()) return null

        val trimmed = dateStr.trim()
        val parsers = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yy-MM-dd"),
            DateTimeFormatter.ofPattern("yy/MM/dd"),
            DateTimeFormatter.ofPattern("yy-M-d"),
            DateTimeFormatter.ofPattern("yy/M/d")
        )

        for (fmt in parsers) {
            try {
                val date = LocalDate.parse(trimmed, fmt)
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {
                // 继续
            }
        }

        val digits = trimmed.replace(Regex("[^0-9]"), "")
        return formatDateFromDigits(digits)
    }

    /**
     * 将任意格式的日期字符串解析为 LocalDate。
     * 支持：yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd、yyMMdd、yyyy.M.d 等。
     * 失败返回 null。
     */
    fun parseToLocalDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val formatted = formatDateForDisplay(dateStr, returnNull = true) ?: return null
        return try {
            LocalDate.parse(formatted, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }
}