package com.example.inventorymaster

import com.example.inventorymaster.ui.CalcEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun testLeadingNegative() {
        // 测试以负数开头的表达式
        val result = CalcEngine.evaluate("-5+10")
        assertEquals(5.0, result, 0.001)

        val result2 = CalcEngine.evaluate("-20÷4")
        assertEquals(-5.0, result2, 0.001)
    }

    @Test
    fun testNegativeAfterOperator() {
        // 测试运算符后的负数
        val result = CalcEngine.evaluate("10+-3")
        assertEquals(7.0, result, 0.001)

        val result2 = CalcEngine.evaluate("10×-2")
        assertEquals(-20.0, result2, 0.001)
    }

    @Test
    fun testNegativeResultContinuation() {
        // 测试产生负数后的连续运算（如 2-5=-3, -3+4=1）
        val step1 = CalcEngine.evaluate("2-5")
        assertEquals(-3.0, step1, 0.001)

        val step2 = CalcEngine.evaluate("-3+4")
        assertEquals(1.0, step2, 0.001)
    }

    @Test
    fun testDivideByZero() {
        // 测试除以零返回非有限值 (Double.NaN)
        val result = CalcEngine.evaluate("5÷0")
        assertTrue("除以零必须返回非有限值", !result.isFinite())
    }

    @Test
    fun testTrailingAndEmpty() {
        // 测试末尾带有运算符以及空串的清洗
        assertEquals(10.0, CalcEngine.evaluate("10+"), 0.001)
        assertEquals(0.0, CalcEngine.evaluate(""), 0.001)
        assertEquals(0.0, CalcEngine.evaluate("-"), 0.001)
    }
}