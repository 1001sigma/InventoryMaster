package com.example.inventorymaster

import com.example.inventorymaster.utils.BatchCodeProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchCodeProcessorTest {

    // 用例 1: 测试最完美的标准输入
    @Test
    fun process_valid_input_returns_batch() {
        // 1. 准备数据
        val input = "065006200810024022612"

        // 2. 执行你的工具
        val result = BatchCodeProcessor.process(input)

        // 3. 验证结果 (断言)
        // 意思是：我期望 result 必须等于 "12345"，不然就报错
        assertEquals("20082402", result)
    }

    // 用例 2: 测试带空格的情况
    @Test
    fun process_input_with_spaces_trim_correctly() {
        val input = "  06500620 08100 24022612  "
        val result = BatchCodeProcessor.process(input)
        assertEquals("20082402", result)
    }

    // 用例 3: 测试无法处理的乱码
    @Test
    fun process_invalid_input_returns_null() {
        val input = "我是乱写的"
        val result = BatchCodeProcessor.process(input)

        // 期望它处理不了时返回 null，而不是随便返回一个东西
        assertEquals(input,result)
    }

    // 用例 4: 测试空字符串
    @Test
    fun process_empty_input_returns_null() {
        val input = ""
        val result = BatchCodeProcessor.process(input)
        assertNull(result)
    }

    // 用例 5: 测试智能识别条码特征
    @Test
    fun isHybiomeBarcode_detection() {
        org.junit.Assert.assertTrue(BatchCodeProcessor.isHybiomeBarcode("065006200810024022612"))
        org.junit.Assert.assertTrue(BatchCodeProcessor.isHybiomeBarcode("  06500620 08100 24022612  "))
        org.junit.Assert.assertFalse(BatchCodeProcessor.isHybiomeBarcode("(01)06912345678901(17)251231(10)BATCH001"))
        org.junit.Assert.assertFalse(BatchCodeProcessor.isHybiomeBarcode("注射器"))
        org.junit.Assert.assertFalse(BatchCodeProcessor.isHybiomeBarcode("20082402"))
        org.junit.Assert.assertFalse(BatchCodeProcessor.isHybiomeBarcode(""))
    }

    // 用例 6: 测试批号安全提取
    @Test
    fun extractBatch_test() {
        assertEquals("20082402", BatchCodeProcessor.extractBatch("065006200810024022612"))
        assertEquals("20082402", BatchCodeProcessor.extractBatch("  06500620 08100 24022612  "))
        assertNull(BatchCodeProcessor.extractBatch("注射器"))
        assertNull(BatchCodeProcessor.extractBatch("20082402"))
        assertNull(BatchCodeProcessor.extractBatch(null))
    }
}