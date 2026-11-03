package com.example.inventorymaster

import com.example.inventorymaster.data.entity.ProductBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合键规则单元测试：productKey = "DI|物料编码"
 * - (DI, 物料编码) 完全相同 → 同一产品（去重覆盖）
 * - 任一字段不同 → 不同产品（共存）
 */
class ProductKeyTest {

    @Test
    fun `组合键由 DI 和物料编码拼接`() {
        assertEquals("DI001|MAT01", ProductBase.computeProductKey("DI001", "MAT01"))
    }

    @Test
    fun `只有 DI 时物料编码侧为空`() {
        assertEquals("DI001|", ProductBase.computeProductKey("DI001", null))
        assertEquals("DI001|", ProductBase.computeProductKey("DI001", "  "))
    }

    @Test
    fun `只有物料编码时 DI 侧为空`() {
        assertEquals("|MAT01", ProductBase.computeProductKey(null, "MAT01"))
    }

    @Test
    fun `DI 与物料编码同时为空则抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductBase.computeProductKey(null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductBase.computeProductKey("  ", "")
        }
    }

    @Test
    fun `输入自动 trim`() {
        assertEquals("DI001|MAT01", ProductBase.computeProductKey(" DI001 ", " MAT01 "))
    }

    @Test
    fun `半角竖线被替换为全角防拆分歧义`() {
        val key = ProductBase.computeProductKey("DI|001", "MAT|01")
        assertEquals("DI｜001|MAT｜01", key)
        // 组合键拆分时仍还原出正确的两段
        val (di, mat) = ProductBase.parseProductKey(key)
        assertEquals("DI｜001", di)
        assertEquals("MAT｜01", mat)
    }

    @Test
    fun `同 DI 不同物料编码生成不同键`() {
        val key1 = ProductBase.computeProductKey("DI001", "MAT01")
        val key2 = ProductBase.computeProductKey("DI001", "MAT02")
        assertTrue(key1 != key2)
    }

    @Test
    fun `同物料编码不同 DI 生成不同键`() {
        val key1 = ProductBase.computeProductKey("DI001", "MAT01")
        val key2 = ProductBase.computeProductKey("DI002", "MAT01")
        assertTrue(key1 != key2)
    }

    @Test
    fun `DI 和物料编码全同生成相同键`() {
        assertEquals(
            ProductBase.computeProductKey("DI001", "MAT01"),
            ProductBase.computeProductKey("DI001", "MAT01")
        )
    }

    @Test
    fun `resolveProductKey 与 computeProductKey 一致`() {
        val (key, skip) = ProductBase.resolveProductKey("DI001", "MAT01")
        assertTrue(!skip)
        assertEquals(ProductBase.computeProductKey("DI001", "MAT01"), key)
    }

    @Test
    fun `resolveProductKey 双空时跳过`() {
        val (key, skip) = ProductBase.resolveProductKey(null, "  ")
        assertTrue(skip)
        assertEquals("", key)
    }

    @Test
    fun `parseProductKey 裸输入视为 DI`() {
        val (di, mat) = ProductBase.parseProductKey("DI001")
        assertEquals("DI001", di)
        assertEquals("", mat)
    }

    @Test
    fun `parseProductKey 组合键还原两段`() {
        val (di, mat) = ProductBase.parseProductKey("DI001|MAT01")
        assertEquals("DI001", di)
        assertEquals("MAT01", mat)
    }

    @Test
    fun `parseProductKey 空段还原为空串`() {
        val (di, mat) = ProductBase.parseProductKey("DI001|")
        assertEquals("DI001", di)
        assertEquals("", mat)

        val (di2, mat2) = ProductBase.parseProductKey("|MAT01")
        assertEquals("", di2)
        assertEquals("MAT01", mat2)
    }

    @Test
    fun `hasKeyChanged 检测组合键变更`() {
        assertTrue(ProductBase.hasKeyChanged("DI001|MAT01", "DI001", "MAT02"))
        assertTrue(ProductBase.hasKeyChanged("DI001|MAT01", "DI002", "MAT01"))
        assertTrue(!ProductBase.hasKeyChanged("DI001|MAT01", "DI001", "MAT01"))
    }
}
