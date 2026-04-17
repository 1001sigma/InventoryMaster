package com.example.inventorymaster

import com.example.inventorymaster.utils.CalcEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcTest {
    @Test
    fun testStandardPriority() {
        // 测试先乘除后加减
        val result = CalcEngine.evaluate("10+5×2")
        assertEquals(20.0, result, 0.001)
    }

    @Test
    fun testDivision() {
        // 测试除法产生小数
        val result = CalcEngine.evaluate("5÷2")
        assertEquals(2.5, result, 0.001)
    }

    @Test
    fun testContinuous() {
        // 测试连续运算
        val result = CalcEngine.evaluate("100-20÷4") // 100 - 5 = 95
        assertEquals(95.0, result, 0.001)
    }
}