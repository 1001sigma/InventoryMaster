package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject

class GetProductDetailTool(
    private val repository: InventoryRepository
) : AiTool {
    override val name = "get_product_detail"
    override val description = "获取某个产品的完整详细信息"
    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("product_key", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "产品标识（productKey，通常是DI码）")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("product_key") })
    }

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        // 组合键精确查找；未命中时按 DI 兜底（AI 通常拿裸 DI，可能对应多条不同物料编码的产品）
        val candidates = listOfNotNull(repository.getProductByKey(toolCall.arguments.get("product_key")?.asString ?: ""))
            .ifEmpty { repository.getProductsByDi(toolCall.arguments.get("product_key")?.asString ?: "") }
        if (candidates.isEmpty()) {
            return ToolResult.failure(
                toolCall,
                ToolErrorKind.NOT_FOUND,
                "未找到产品: ${toolCall.arguments.get("product_key")?.asString ?: ""}。请确认产品编码或 DI 是否正确，可先用 query_records 按名称关键词查找。"
            )
        }

        val content = candidates.joinToString("\n\n") { product ->
            buildString {
                appendLine("产品详细信息:")
                appendLine("- 产品名称: ${product.productName}")
                appendLine("- DI码: ${product.di ?: "无"}")
                appendLine("- 物料编码: ${product.materialCode ?: "无"}")
                appendLine("- 厂家: ${product.manufacturer ?: "未知"}")
                appendLine("- 规格: ${product.specification ?: "无"}")
                appendLine("- 型号: ${product.model ?: "无"}")
                appendLine("- 注册证号: ${product.registrationCert ?: "无"}")
                appendLine("- 单位: ${product.unit ?: "无"}")
                appendLine("- 分类编码: ${product.categoryCode ?: "无"}")
                appendLine("- 来源: ${product.source ?: "未知"}")
            }
        }
        return ToolResult.success(toolCall, content)
    }
}
