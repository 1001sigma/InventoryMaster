package com.example.inventorymaster

import android.graphics.Point
import android.graphics.Rect
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.util.BarcodeStatusAssigner
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.ProductDto
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.data.sync.ProductMergeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMergeHelperTest {

    // 内存 Fake DAO 实现
    private class FakeProductDao : ProductDao {
        val products = mutableMapOf<String, ProductBase>()

        override suspend fun insertProduct(product: ProductBase) {
            products[product.productKey] = product
        }

        override suspend fun insertProducts(newProducts: List<ProductBase>) {
            newProducts.forEach { products[it.productKey] = it }
        }

        override suspend fun getProductByKey(productKey: String): ProductBase? {
            return products[productKey]
        }

        override suspend fun getProductsByMaterialCode(materialCode: String): List<ProductBase> {
            return products.values.filter { it.materialCode == materialCode }.sortedBy { it.di ?: "" }
        }

        override suspend fun getProductsByDi(di: String): List<ProductBase> {
            return products.values.filter { it.di == di }.sortedBy { it.materialCode ?: "" }
        }

        override suspend fun searchProducts(query: String): List<ProductBase> = emptyList()
        override suspend fun getAllProducts(): List<ProductBase> = products.values.toList()
        override suspend fun getProductCount(): Int = products.size

        override suspend fun deleteProductByKey(productKey: String) {
            products.remove(productKey)
        }

        override suspend fun deleteProductsByKey(productKeyList: List<String>) {
            productKeyList.forEach { products.remove(it) }
        }

        override suspend fun updateProduct(product: ProductBase) {
            products[product.productKey] = product
        }
    }

    private class FakeStockRecordDao : StockRecordDao {
        val records = mutableListOf<StockRecord>()
        val keyMigrations = mutableListOf<Pair<String, String>>()

        override suspend fun insertRecord(record: StockRecord): Long {
            records.add(record)
            return records.size.toLong()
        }

        override suspend fun insertRecords(records: List<StockRecord>) {
            this.records.addAll(records)
        }

        override suspend fun updateRecord(record: StockRecord) {}
        override suspend fun deleteRecord(record: StockRecord) {}
        override suspend fun deleteRecordsBySession(sessionId: Long) {}
        override suspend fun getRecordByUuid(uuid: String): StockRecord? = null
        override suspend fun getRecordById(id: Long): StockRecord? = records.find { it.id == id }

        override fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>> = error("Not needed")
        override suspend fun searchRecordsByQuery(sessionId: Long, query: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsByUdi(sessionId: Long, productKey: String, batch: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsBybatchorexpiryDate(sessionId: Long, batch: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsByKey(sessionId: Long, productKey: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsByDiAndBatch(sessionId: Long, di: String, batch: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun getRecordsByKeyOrDiOrMat(sessionId: Long, query: String): List<StockRecordCombined> = error("Not needed")
        override suspend fun countRecordsByKey(productKey: String): Int = records.count { it.productKey == productKey }
        override suspend fun getUnverifiedCount(sessionId: Long): Int = 0
        override fun getExportData(sessionId: Long): List<StockRecordCombined> = error("Not needed")
        override suspend fun findExistingRecord(sessionId: Long, productKey: String, batch: String, location: String): StockRecord? = null
        override suspend fun getDirtyRecords(sessionId: Long): List<StockRecord> = emptyList()
        override suspend fun markAsSynced(ids: List<Long>) {}

        override suspend fun updateProductKeyForRecords(oldKey: String, newKey: String) {
            keyMigrations.add(oldKey to newKey)
            for (i in records.indices) {
                if (records[i].productKey == oldKey) {
                    records[i] = records[i].copy(productKey = newKey)
                }
            }
        }

        override suspend fun deleteRecordsByUuids(uuids: List<String>) {}
        override suspend fun getRecordUuidsBySession(sessionId: Long): List<String> = emptyList()
    }

    private fun createTestBarcode(value: String): GlobalBarcode {
        return GlobalBarcode(
            originalBarcode = null,
            displayValue = value,
            globalBoundingBox = Rect(),
            globalCornerPoints = emptyArray(),
            globalCenterX = 0,
            globalCenterY = 0,
            status = ScanStatus.UNKNOWN
        )
    }

    @Test
    fun testPromoteAndMergePureMaterialCodeProduct() = runBlocking {
        val productDao = FakeProductDao()
        val stockRecordDao = FakeStockRecordDao()

        // 1. 本地已存纯物料编码半成品 "|MAT100" 及名下库存
        val pureMatProduct = ProductBase(
            productKey = "|MAT100",
            di = null,
            materialCode = "MAT100",
            productName = "过渡半成品",
            specification = null,
            model = null,
            manufacturer = "未知",
            registrationCert = null,
            unit = null,
            categoryCode = null
        )
        productDao.insertProduct(pureMatProduct)
        stockRecordDao.insertRecord(
            StockRecord(
                id = 1L,
                sessionId = 1L,
                productKey = "|MAT100",
                batchNumber = "B001",
                expiryDate = 20261231L,
                quantity = 10.0,
                location = "A1",
                actualQuantity = 10.0,
                operator = "test"
            )
        )

        // 2. 存入完整产品 "DI999|MAT100"
        val fullProduct = ProductBase(
            productKey = "DI999|MAT100",
            di = "DI999",
            materialCode = "MAT100",
            productName = "完整正式产品",
            specification = "10支/盒",
            model = null,
            manufacturer = "安图生物",
            registrationCert = "国械注准xxx",
            unit = "盒",
            categoryCode = null
        )
        productDao.insertProduct(fullProduct)

        val keyMapping = mutableMapOf<String, String>()
        val mergedCount = ProductMergeHelper.promoteAndMergeIncompleteProducts(
            productDao, stockRecordDao, fullProduct, keyMapping
        )

        // 验证：
        // a. 成功合并 1 条半成品
        assertEquals(1, mergedCount)
        // b. 旧半成品从产品表中被删除
        assertNull(productDao.getProductByKey("|MAT100"))
        // c. 完整产品依然完好
        assertNotNull(productDao.getProductByKey("DI999|MAT100"))
        // d. 库存记录 productKey 已原子平移到 "DI999|MAT100"
        assertEquals("DI999|MAT100", stockRecordDao.records[0].productKey)
        // e. keyMapping 正确记录了重定向
        assertEquals("DI999|MAT100", keyMapping["|MAT100"])
    }

    @Test
    fun testPromoteAndMergePureDiPlaceholderProduct() = runBlocking {
        val productDao = FakeProductDao()
        val stockRecordDao = FakeStockRecordDao()

        // 1. 扫码生成的纯 DI 占位记录 "DI888|"
        val placeholder = ProductBase(
            productKey = "DI888|",
            di = "DI888",
            materialCode = null,
            productName = "未知产品(扫码)",
            specification = null,
            model = null,
            manufacturer = "未知厂家",
            registrationCert = null,
            unit = null,
            categoryCode = null,
            source = "scan_auto"
        )
        productDao.insertProduct(placeholder)
        stockRecordDao.insertRecord(
            StockRecord(
                id = 1L,
                sessionId = 1L,
                productKey = "DI888|",
                batchNumber = "B002",
                expiryDate = 20261231L,
                quantity = 5.0,
                location = "A2",
                actualQuantity = 5.0,
                operator = "test"
            )
        )

        // 2. 存入建档后的完整产品 "DI888|MAT200"
        val fullProduct = ProductBase(
            productKey = "DI888|MAT200",
            di = "DI888",
            materialCode = "MAT200",
            productName = "正式建档产品",
            specification = "标准型",
            model = null,
            manufacturer = "迈瑞医疗",
            registrationCert = "粤械注准xxx",
            unit = "台",
            categoryCode = null
        )
        productDao.insertProduct(fullProduct)

        val mergedCount = ProductMergeHelper.promoteAndMergeIncompleteProducts(
            productDao, stockRecordDao, fullProduct
        )

        // 验证：占位记录被清理，库存平移
        assertEquals(1, mergedCount)
        assertNull(productDao.getProductByKey("DI888|"))
        assertNotNull(productDao.getProductByKey("DI888|MAT200"))
        assertEquals("DI888|MAT200", stockRecordDao.records[0].productKey)
    }

    @Test
    fun testPlanARuleSameDiDifferentMaterialCodeNeverMerged() = runBlocking {
        val productDao = FakeProductDao()
        val stockRecordDao = FakeStockRecordDao()

        // 1. 本地已有同 DI 但不同物料编码的完整产品 A (例如一分院物料码)
        val productA = ProductBase(
            productKey = "DI777|MAT_HOSP_A",
            di = "DI777",
            materialCode = "MAT_HOSP_A",
            productName = "产品A(一分院)",
            specification = "100ml",
            model = null,
            manufacturer = "正大天晴",
            registrationCert = null,
            unit = "瓶",
            categoryCode = null
        )
        productDao.insertProduct(productA)
        stockRecordDao.insertRecord(
            StockRecord(
                id = 1L,
                sessionId = 1L,
                productKey = "DI777|MAT_HOSP_A",
                batchNumber = "BA01",
                expiryDate = 20261231L,
                quantity = 20.0,
                location = "A3",
                actualQuantity = 20.0,
                operator = "test"
            )
        )

        // 2. 存入同 DI 但另一物料编码的完整产品 B (例如二分院物料码)
        val productB = ProductBase(
            productKey = "DI777|MAT_HOSP_B",
            di = "DI777",
            materialCode = "MAT_HOSP_B",
            productName = "产品B(二分院)",
            specification = "100ml",
            model = null,
            manufacturer = "正大天晴",
            registrationCert = null,
            unit = "瓶",
            categoryCode = null
        )
        productDao.insertProduct(productB)

        val mergedCount = ProductMergeHelper.promoteAndMergeIncompleteProducts(
            productDao, stockRecordDao, productB
        )

        // 验证方案 A 铁律：
        // 绝不自动合并同 DI 但不同物料编码的产品！
        assertEquals(0, mergedCount)
        assertNotNull(productDao.getProductByKey("DI777|MAT_HOSP_A"))
        assertNotNull(productDao.getProductByKey("DI777|MAT_HOSP_B"))
        // productA 的库存绝不平移
        assertEquals("DI777|MAT_HOSP_A", stockRecordDao.records[0].productKey)
        assertTrue(stockRecordDao.keyMigrations.isEmpty())
    }

    @Test
    fun testSaveOrUpdateSyncedProductPromotesIncompleteKey() = runBlocking {
        val productDao = FakeProductDao()
        val stockRecordDao = FakeStockRecordDao()

        // 1. 本地先有半成品 "|MAT300"
        val existingHalf = ProductBase(
            productKey = "|MAT300",
            di = null,
            materialCode = "MAT300",
            productName = "半成品名称",
            specification = "旧规格",
            model = null,
            manufacturer = "厂家",
            registrationCert = null,
            unit = null,
            categoryCode = null
        )
        productDao.insertProduct(existingHalf)
        stockRecordDao.insertRecord(
            StockRecord(
                id = 1L,
                sessionId = 1L,
                productKey = "|MAT300",
                batchNumber = "B300",
                expiryDate = 20261231L,
                quantity = 8.0,
                location = "A4",
                actualQuantity = 8.0,
                operator = "test"
            )
        )

        // 2. 远端下发带有真实 DI 的 DTO
        val remoteDto = ProductDto(
            productKey = "|MAT300", // 远端旧键
            di = "DI300",
            productName = "更新后的产品名",
            materialCode = "MAT300"
        )

        val keyMapping = mutableMapOf<String, String>()
        val (savedEntity, isInserted) = ProductMergeHelper.saveOrUpdateSyncedProduct(
            productDao = productDao,
            stockRecordDao = stockRecordDao,
            pDto = remoteDto,
            keyMapping = keyMapping
        )

        // 验证：
        // a. 保存后的主键晋升为 "DI300|MAT300"
        assertEquals("DI300|MAT300", savedEntity.productKey)
        assertEquals("DI300", savedEntity.di)
        assertEquals("MAT300", savedEntity.materialCode)
        assertTrue(isInserted)

        // b. 旧半成品已被删除，库中只有新晋升的完整记录
        assertNull(productDao.getProductByKey("|MAT300"))
        assertNotNull(productDao.getProductByKey("DI300|MAT300"))

        // c. 旧库存记录已转移到新主键
        assertEquals("DI300|MAT300", stockRecordDao.records[0].productKey)

        // d. keyMapping 记录了远端键与旧键到新键的映射
        assertEquals("DI300|MAT300", keyMapping["|MAT300"])
    }

    @Test
    fun testMultiDiMatchingInBarcodeStatusAssigner() {
        // 模拟单据中一项有 2 个候选 14 位 DI (老包装 DI1 与新包装 DI2)，总配额为 2
        val targetList = mutableListOf(
            "06900000000001,06900000000002|BATCH01",
            "06900000000001,06900000000002|BATCH01"
        )

        // 1. 现场扫入新包装 06900000000002
        val b1 = createTestBarcode("(01)06900000000002(10)BATCH01")
        val res1 = BarcodeStatusAssigner.assignStatus(listOf(b1), targetList)
        assertEquals(ScanStatus.MATCHED, res1[0].status)
        assertEquals(1, targetList.size) // 消耗了 1 个配额

        // 2. 现场扫入老包装 06900000000001
        val b2 = createTestBarcode("(01)06900000000001(10)BATCH01")
        val res2 = BarcodeStatusAssigner.assignStatus(listOf(b2), targetList)
        assertEquals(ScanStatus.MATCHED, res2[0].status)
        assertEquals(0, targetList.size) // 配额消耗完毕

        // 3. 再次扫入（超额）
        val b3 = createTestBarcode("(01)06900000000002(10)BATCH01")
        val res3 = BarcodeStatusAssigner.assignStatus(listOf(b3), targetList)
        assertEquals(ScanStatus.MISMATCHED, res3[0].status)

        // 4. 扫入非单据候选 DI
        val b4 = createTestBarcode("(01)06999999999999(10)BATCH01")
        val res4 = BarcodeStatusAssigner.assignStatus(listOf(b4), targetList)
        assertEquals(ScanStatus.MISMATCHED, res4[0].status)
    }
}
