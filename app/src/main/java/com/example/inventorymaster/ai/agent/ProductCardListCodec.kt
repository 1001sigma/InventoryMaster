package com.example.inventorymaster.ai.agent

import com.google.gson.JsonParser

/**
 * 产品卡片 ↔ JSON 编解码（手写往返，不走 Gson 反射——Kotlin data class 默认值 + 反射实例化有坑）。
 *
 * 用途：卡片随最终回复消息持久化（chat_messages.cardActionJson，DB v10），
 * 退出重进后经 ChatViewModel.displayMessages 反序列化回显。
 * 铁律：该 JSON 只进数据库/界面回显，绝不进模型提示词（LocalLlmRuntime 的映射层不读它）。
 */
object ProductCardListCodec {

    private const val TYPE_PRODUCT_LIST = "productList"

    fun toJson(card: UiAction.ShowProductCardList): String {
        val root = com.google.gson.JsonObject().apply {
            addProperty("type", TYPE_PRODUCT_LIST)
            addProperty("title", card.title)
            add("products", com.google.gson.JsonArray().apply {
                card.products.forEach { p ->
                    add(com.google.gson.JsonObject().apply {
                        addProperty("productKey", p.productKey)
                        addProperty("productName", p.productName)
                        addProperty("batchNumber", p.batchNumber)
                        addProperty("expiryDateStr", p.expiryDateStr)
                        addProperty("quantity", p.quantity)
                        addProperty("sessionId", p.sessionId)
                        addProperty("recordId", p.recordId)
                        addProperty("sessionName", p.sessionName)
                        addProperty("location", p.location)
                        addProperty("manufacturer", p.manufacturer)
                        addProperty("specification", p.specification)
                        addProperty("unit", p.unit)
                        addProperty("isVerified", p.isVerified)
                        addProperty("productionDateStr", p.productionDateStr)
                        addProperty("remarks", p.remarks)
                        addProperty("materialCode", p.materialCode)
                        addProperty("di", p.di)
                    })
                }
            })
        }
        return root.toString()
    }

    /** 坏 JSON / 类型不符 / 缺 products 数组返回 null（旧数据、脏数据不崩 UI） */
    fun fromJson(json: String): UiAction.ShowProductCardList? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("type")?.asString != TYPE_PRODUCT_LIST) return@runCatching null
        val productsArr = root.getAsJsonArray("products") ?: return@runCatching null
        val products = productsArr.map { p ->
            val o = p.asJsonObject
            ExpiredProductCardData(
                productKey = o.get("productKey")?.asString ?: "",
                productName = o.get("productName")?.asString ?: "",
                batchNumber = o.get("batchNumber")?.asString ?: "",
                expiryDateStr = o.get("expiryDateStr")?.asString ?: "",
                quantity = o.get("quantity")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                sessionId = o.get("sessionId")?.asString ?: "",
                recordId = o.get("recordId")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                sessionName = o.get("sessionName")?.takeIf { !it.isJsonNull }?.asString ?: "",
                location = o.get("location")?.takeIf { !it.isJsonNull }?.asString ?: "",
                manufacturer = o.get("manufacturer")?.takeIf { !it.isJsonNull }?.asString ?: "",
                specification = o.get("specification")?.takeIf { !it.isJsonNull }?.asString ?: "",
                unit = o.get("unit")?.takeIf { !it.isJsonNull }?.asString ?: "件",
                isVerified = o.get("isVerified")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                productionDateStr = o.get("productionDateStr")?.takeIf { !it.isJsonNull }?.asString ?: "",
                remarks = o.get("remarks")?.takeIf { !it.isJsonNull }?.asString ?: "",
                materialCode = o.get("materialCode")?.takeIf { !it.isJsonNull }?.asString ?: "",
                di = o.get("di")?.takeIf { !it.isJsonNull }?.asString ?: ""
            )
        }
        UiAction.ShowProductCardList(
            title = root.get("title")?.asString ?: "",
            products = products
        )
    }.getOrNull()
}
