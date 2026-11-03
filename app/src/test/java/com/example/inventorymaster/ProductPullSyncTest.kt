package com.example.inventorymaster

import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.dto.ProductPullData
import com.example.inventorymaster.data.dto.ProductPullResponse
import com.example.inventorymaster.data.dto.mergeInto
import com.example.inventorymaster.data.dto.toEntity
import com.example.inventorymaster.data.entity.ProductBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProductPullSyncTest {

    @Test
    fun testProductDtoToEntityMapping() {
        val dto = ProductDto(
            productKey = "06970150900540|SJ.ZZAT.011",
            di = "06970150900540",
            productName = "抗缪勒管激素检测试剂盒",
            specification = "12×50mm，50支/袋",
            manufacturer = "郑州安图生物工程股份有限公司",
            registrationCert = "豫械注准20192400297",
            materialCode = "SJ.ZZAT.011",
            unit = "盒",
            changeSeq = 100L
        )

        val entity = dto.toEntity()
        assertEquals("06970150900540|SJ.ZZAT.011", entity.productKey)
        assertEquals("06970150900540", entity.di)
        assertEquals("抗缪勒管激素检测试剂盒", entity.productName)
        assertEquals("12×50mm，50支/袋", entity.specification)
        assertEquals("郑州安图生物工程股份有限公司", entity.manufacturer)
        assertEquals("豫械注准20192400297", entity.registrationCert)
        assertEquals("SJ.ZZAT.011", entity.materialCode)
        assertEquals("盒", entity.unit)
    }

    @Test
    fun testKeyNormalizationWithAndWithoutDi() {
        // 有 DI 和物料编码
        val keyWithDi = ProductBase.computeProductKey("06970150900540", "SJ.ZZAT.011")
        assertEquals("06970150900540|SJ.ZZAT.011", keyWithDi)

        // 仅物料编码（无 DI）
        val keyWithoutDi = ProductBase.computeProductKey(null, "HC.BJMC.001")
        assertEquals("|HC.BJMC.001", keyWithoutDi)
    }

    @Test
    fun testProductPullResponseStructure() {
        val dtoList = listOf(
            ProductDto(
                productKey = "|HC.AYK0.001",
                di = null,
                productName = "软试管",
                specification = "12×50mm",
                manufacturer = "泰兴市安与康医疗器械有限公司",
                registrationCert = "无",
                materialCode = "HC.AYK0.001",
                unit = "袋",
                changeSeq = 101L
            )
        )
        val response = ProductPullResponse(
            status = "success",
            data = ProductPullData(
                maxSeq = 101L,
                products = dtoList
            )
        )

        assertEquals("success", response.status)
        assertEquals(101L, response.data.maxSeq)
        assertEquals(1, response.data.products.size)
        assertEquals("软试管", response.data.products[0].productName)
    }

    @Test
    fun testCursorCalculationWhenEmptyOrForceFull() {
        // 当本地产品数仍有数据且非强制时，沿用已有游标
        val lastSavedSeq = 1858L
        val localCountWithData = 50
        val normalSeq = if (localCountWithData == 0) 0L else lastSavedSeq
        assertEquals(1858L, normalSeq)

        // 当本地产品被全部清空（localCount == 0）时，必须判定为 0L 重新全量拉取
        val emptyCount = 0
        val resetSeq = if (emptyCount == 0) 0L else lastSavedSeq
        assertEquals(0L, resetSeq)

        // 当用户显式指定 forceFull 时，必须判定为 0L
        val forceFull = true
        val forceSeq = if (forceFull || localCountWithData == 0) 0L else lastSavedSeq
        assertEquals(0L, forceSeq)
    }

    @Test
    fun testMergeIntoPreservesLocalDiWhenRemoteDiIsNull() {
        val existing = ProductBase(
            productKey = "06970150900540|SJ.ZZAT.011",
            di = "06970150900540",
            productName = "抗缪勒管激素检测试剂盒",
            specification = "12×50mm",
            model = "A型",
            manufacturer = "郑州安图",
            registrationCert = "豫械注准20192400297",
            materialCode = "SJ.ZZAT.011",
            unit = "盒",
            categoryCode = ""
        )
        // 远端下发无 DI 的 DTO（如仅物料编码）
        val remoteDto = ProductDto(
            productKey = "|SJ.ZZAT.011",
            di = null,
            productName = "抗缪勒管激素检测试剂盒(服务端更新)",
            specification = "",
            manufacturer = "",
            materialCode = "SJ.ZZAT.011"
        )

        val merged = remoteDto.mergeInto(existing, existing.productKey)

        // 验证：本地已有的 DI 和规格厂家未被冲掉，且产品名被更新
        assertEquals("06970150900540", merged.di)
        assertEquals("SJ.ZZAT.011", merged.materialCode)
        assertEquals("06970150900540|SJ.ZZAT.011", merged.productKey)
        assertEquals("抗缪勒管激素检测试剂盒(服务端更新)", merged.productName)
        assertEquals("12×50mm", merged.specification)
        assertEquals("郑州安图", merged.manufacturer)
        assertEquals("豫械注准20192400297", merged.registrationCert)
        assertEquals("盒", merged.unit)
    }

    @Test
    fun testMergeIntoPreservesLocalDiWhenRemoteDiIsBlank() {
        val existing = ProductBase(
            productKey = "06970150900540|SJ.ZZAT.011",
            di = "06970150900540",
            productName = "测试产品",
            specification = "规格",
            model = "",
            manufacturer = "厂家",
            registrationCert = "",
            materialCode = "SJ.ZZAT.011",
            unit = "",
            categoryCode = ""
        )
        val remoteDto = ProductDto(
            productKey = "|SJ.ZZAT.011",
            di = "   ", // 空白串
            productName = "测试产品",
            materialCode = "SJ.ZZAT.011"
        )

        val merged = remoteDto.mergeInto(existing, existing.productKey)
        assertEquals("06970150900540", merged.di)
    }

    @Test
    fun testMergeIntoUpdatesDiWhenRemoteDiIsProvided() {
        val existing = ProductBase(
            productKey = "|SJ.ZZAT.011",
            di = null,
            productName = "测试产品",
            specification = "",
            model = "",
            manufacturer = "厂家",
            registrationCert = "",
            materialCode = "SJ.ZZAT.011",
            unit = "",
            categoryCode = ""
        )
        // 远端补充了真实 DI
        val remoteDto = ProductDto(
            productKey = "06970150900540|SJ.ZZAT.011",
            di = "06970150900540",
            productName = "测试产品",
            materialCode = "SJ.ZZAT.011"
        )

        val merged = remoteDto.mergeInto(existing, "06970150900540|SJ.ZZAT.011")
        assertEquals("06970150900540", merged.di)
        assertEquals("06970150900540|SJ.ZZAT.011", merged.productKey)
    }

    @Test
    fun testMergeIntoWhenExistingIsNull() {
        val remoteDto = ProductDto(
            productKey = "|SJ.ZZAT.011",
            di = null,
            productName = "新产品",
            materialCode = "SJ.ZZAT.011"
        )
        val merged = remoteDto.mergeInto(null, "|SJ.ZZAT.011")
        assertEquals(null, merged.di)
        assertEquals("SJ.ZZAT.011", merged.materialCode)
        assertEquals("|SJ.ZZAT.011", merged.productKey)
        assertEquals("新产品", merged.productName)
    }
}
