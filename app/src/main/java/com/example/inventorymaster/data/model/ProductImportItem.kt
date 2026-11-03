package com.example.inventorymaster.data.model

import com.example.inventorymaster.data.entity.ProductBase

/**
 * 产品导入包裹
 * @param oldKey 原系统主键 (从 Excel 第一列或原生 JSON 获取)
 * @param newProduct 经过最新计算的，即将写入数据库的商品对象
 */
data class ProductImportItem(
    val oldKey: String,
    val newProduct: ProductBase
)