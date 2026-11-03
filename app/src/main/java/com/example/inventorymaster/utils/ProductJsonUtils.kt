package com.example.inventorymaster.utils

import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.model.ProductImportItem
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.OutputStream

object ProductJsonUtils {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToJson(outputStream: OutputStream, products: List<ProductBase>) {
        val json = gson.toJson(products)
        outputStream.write(json.toByteArray(Charsets.UTF_8))
    }

    fun importFromJson(inputStream: InputStream): List<ProductImportItem> {
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<ProductBase>>() {}.type
        val rawProducts: List<ProductBase> = gson.fromJson(json, type)

        return rawProducts.map { oldProduct ->
            // json里的原主键
            val oldKey = oldProduct.productKey
            // 利用其数据重新计算新主键（防范用户在json中乱改di但没改productKey）
            val newKey = ProductBase.computeProductKey(oldProduct.di, oldProduct.materialCode)

            ProductImportItem(
                oldKey = oldKey,
                newProduct = oldProduct.copy(productKey = newKey)
            )
        }
    }
}
