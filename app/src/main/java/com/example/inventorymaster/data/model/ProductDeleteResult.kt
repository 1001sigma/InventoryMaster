package com.example.inventorymaster.data.model

/**
 * 批量删除产品结果：成功删除的 key + 因被库存记录引用而被拒的 key（含引用数）
 */
data class ProductDeleteResult(
    val deletedKeys: List<String>,
    val blockedKeys: Map<String, Int>
) {
    val hasBlocked: Boolean get() = blockedKeys.isNotEmpty()
}
