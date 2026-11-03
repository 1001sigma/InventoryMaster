package com.example.inventorymaster.data.sync

import android.util.Log
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.dto.mergeInto
import com.example.inventorymaster.data.entity.ProductBase

/**
 * 【方案A 智能晋升合并引擎】
 * 当录入/导入/更新一条具有完整信息 (既有真实 DI 又有物料编码) 的产品时，
 * 自动扫描并接管单侧缺失的过渡半成品记录：
 * 1. 纯物料编码记录：同物料编码但 di 为空的条目 (如 "|MAT")
 * 2. 纯 DI 占位记录：同 DI 但 materialCode 为空的条目 (如 "DI|" 或扫码占位条目)
 * 将其名下的库存记录原子性平移到当前完整产品，并删除过渡条目。
 * （注：同 DI 但物料编码不同的独立实体不在此合并，保持独立共存）。
 */
object ProductMergeHelper {

    suspend fun promoteAndMergeIncompleteProducts(
        productDao: ProductDao,
        stockRecordDao: StockRecordDao,
        newProduct: ProductBase,
        keyMapping: MutableMap<String, String>? = null
    ): Int {
        val di = newProduct.di?.trim()?.ifBlank { null }
        val mat = newProduct.materialCode?.trim()?.ifBlank { null }
        if (di == null || mat == null) return 0

        var mergedCount = 0

        // 1. 扫描并合并纯物料编码的半成品 (例如 "|MAT")
        val pureMatCandidates = productDao.getProductsByMaterialCode(mat)
        for (candidate in pureMatCandidates) {
            if (candidate.productKey != newProduct.productKey && candidate.di.isNullOrBlank()) {
                stockRecordDao.updateProductKeyForRecords(candidate.productKey, newProduct.productKey)
                productDao.deleteProductByKey(candidate.productKey)
                keyMapping?.put(candidate.productKey, newProduct.productKey)
                mergedCount++
                Log.d("DEBUG_SAVE", "已合并无DI半成品产品: ${candidate.productKey} → ${newProduct.productKey}")
            }
        }

        // 2. 扫描并合并纯 DI 的占位半成品 (例如 "DI|" 或扫码生成的未建档记录)
        val pureDiCandidates = productDao.getProductsByDi(di)
        for (candidate in pureDiCandidates) {
            if (candidate.productKey != newProduct.productKey && candidate.materialCode.isNullOrBlank()) {
                stockRecordDao.updateProductKeyForRecords(candidate.productKey, newProduct.productKey)
                productDao.deleteProductByKey(candidate.productKey)
                keyMapping?.put(candidate.productKey, newProduct.productKey)
                mergedCount++
                Log.d("DEBUG_SAVE", "已合并纯DI占位半成品产品: ${candidate.productKey} → ${newProduct.productKey}")
            }
        }

        return mergedCount
    }

    /**
     * 针对同步/下载场景的统一产品保存与晋升逻辑：
     * 1. 组合键精确匹配 → 降级匹配（仅允许匹配单侧缺失的半成品，绝不串碰同 DI 不同物料的独立产品）
     * 2. 若双方或单侧信息补充后成为完整产品，主键必须晋升为标准 "{DI}|{物料编码}" 组合键
     * 3. 级联平移并清理被晋升的旧半成品，记录 keyMapping
     *
     * @return Pair(保存后的产品实体, 是否为新插入)
     */
    suspend fun saveOrUpdateSyncedProduct(
        productDao: ProductDao,
        stockRecordDao: StockRecordDao,
        pDto: com.example.inventorymaster.data.dto.ProductDto,
        keyMapping: MutableMap<String, String>? = null,
        updateSyncTime: Boolean = false
    ): Pair<ProductBase, Boolean> {
        val di = pDto.di?.trim()?.ifBlank { null }
        val mat = pDto.materialCode?.trim()?.ifBlank { null }

        val normalizedKey = if (di != null || mat != null) {
            ProductBase.computeProductKey(di, mat)
        } else {
            pDto.productKey
        }

        var existingProduct = productDao.getProductByKey(normalizedKey)

        if (existingProduct == null) {
            if (di != null && mat != null) {
                // 两者皆有：仅允许匹配单侧缺失的半成品
                existingProduct = productDao.getProductsByMaterialCode(mat)
                    .firstOrNull { it.di.isNullOrBlank() }
                    ?: productDao.getProductsByDi(di)
                        .firstOrNull { it.materialCode.isNullOrBlank() }
            } else if (di != null) {
                existingProduct = productDao.getProductsByDi(di).firstOrNull()
            } else if (mat != null) {
                existingProduct = productDao.getProductsByMaterialCode(mat).firstOrNull()
            }
        }

        // 若当前有完整的 DI 与物料编码，主键晋升为标准组合键，绝不沿用半成品旧键
        val targetKey = if (di != null && mat != null) {
            normalizedKey
        } else {
            existingProduct?.productKey ?: normalizedKey
        }

        var entity = pDto.mergeInto(existingProduct, targetKey)
        if (updateSyncTime) {
            entity = entity.copy(lastSyncTime = System.currentTimeMillis())
        }

        val isInserted: Boolean
        val targetExists = productDao.getProductByKey(targetKey) != null
        if (targetExists) {
            productDao.updateProduct(entity)
            isInserted = false
        } else {
            productDao.insertProduct(entity)
            isInserted = true
        }

        // 级联晋升合并：若 entity 为完整产品，清理其所吸收的单侧缺失半成品并平移库存
        promoteAndMergeIncompleteProducts(productDao, stockRecordDao, entity, keyMapping)

        // 维护 key 重定向映射
        if (keyMapping != null) {
            if (pDto.productKey != targetKey) {
                keyMapping[pDto.productKey] = targetKey
            }
            if (existingProduct != null && existingProduct.productKey != targetKey) {
                keyMapping[existingProduct.productKey] = targetKey
            }
        }

        return Pair(entity, isInserted)
    }
}
