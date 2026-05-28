package com.example.inventorymaster.utils

import com.example.inventorymaster.data.entity.ProductBase
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

    fun importFromJson(inputStream: InputStream): List<ProductBase> {
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<ProductBase>>() {}.type
        return gson.fromJson(json, type)
    }
}
