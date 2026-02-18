
package com.example.inventorymaster

import com.example.inventorymaster.utils.BatchCodeProcessor
import com.example.inventorymaster.utils.Gs1Parser
import org.junit.Test
import org.junit.Assert.*

class Gs1ParserTest {

    // 测试用例 1: 带括号的人眼可读格式 (最常见的手动输入或某些二维码)
    @Test
    fun testBracketFormat() {
        // 模拟数据: DI=06912345678901, 效期=251231(2025年12月31日), 批号=BATCH001
        val input = "(01)06912345678901(17)251231(10)BATCH001"

        val result = Gs1Parser.parse(input)

        println("测试括号格式: $result") // 打印结果方便看

        assertTrue("应该是 UDI", result.isUdi)
        assertEquals("DI 匹配", "06912345678901", result.di)
        assertEquals("批号 匹配", "BATCH001", result.batch)
        assertEquals("效期 匹配", 20251231L, result.expiry)
    }

    // 测试用例 2: 机器原始格式 (带 FNC1/GS 分隔符)
    @Test
    fun testRawFormat() {
        // 模拟 <GS> 字符 (ASCII 29)
        val gs = 0x1D.toChar()
        // 格式: 01...10...<GS>17...
        // 注意：批号是变长的，后面必须跟分隔符，或者位于字符串末尾
        val input = "01069357716063321124072817260727102501010420"

        val result = Gs1Parser.parse(input)

        println("测试机器格式: $result")

        assertTrue("应该是 UDI", result.isUdi)
        assertEquals("DI 匹配", "06935771606332", result.di)
        assertEquals("批号 匹配", "2501010420", result.batch) // 确保没把后面的 17 吃进去
        assertEquals("效期 匹配", 20260727L, result.expiry)
    }

    // 测试用例 3: 只有 DI 的情况
    @Test
    fun testOnlyDi() {
        val input = "(01)12345678901234"
        val result = Gs1Parser.parse(input)

        assertTrue(result.isUdi)
        assertEquals("12345678901234", result.di)
        assertNull(result.batch)
    }

    // 测试用例 4: 普通条码 (非 UDI)
    @Test
    fun testNormalBarcode() {
        val input = "690123456789" // 普通商品条码
        val result = Gs1Parser.parse(input)

        assertFalse("不应该是 UDI", result.isUdi)
        assertEquals("原始值保留", input, result.rawValue)
    }


}


