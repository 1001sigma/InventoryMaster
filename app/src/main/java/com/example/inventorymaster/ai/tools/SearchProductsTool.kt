package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 产品库唯一查询入口（合并原 get_product_detail，docs/AI_UI_OPTIMIZATION.md 讨论定案）：
 * 按 名称/厂家/DI/物料编码 模糊查找产品档案，精确 productKey/DI 也走这里。
 *
 * 三级降级匹配：组合键精确（productKey）→ DI 精确（同 DI 多物料编码场景）→ 四字段 LIKE 模糊
 * （复用 ProductDao.searchProducts：productName/manufacturer/di/materialCode）。
 *
 * 输出含 注册证号/型号/单位 等静态全字段 + total 计数（"多少种物料是XX厂的"直接读 total）；
 * 行内携带 产品编码（productKey），模型可链式发起 navigate_to_product。
 * 边界：产品库不含数量/批号/有效期——库存事实一律 query_records。
 */
class SearchProductsTool(
    private val repository: InventoryRepository
) : AiTool {

    override val name = "search_products"

    override val description =
        "产品库查询的唯一入口：按产品名称（支持不完整词）、厂家、DI、物料编码查找产品档案" +
            "（DI/注册证号/规格/型号/厂家/单位/分类等静态信息），也可统计匹配的产品种数（读 total）。" +
            "已知精确 productKey 或 DI 时同样用本工具。注意：产品库不含库存数量、批号、有效期——" +
            "涉及库存、效期、批号请改用 query_records。"

    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("keyword", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "检索词：产品名称片段/厂家名/DI/物料编码，模糊匹配。例：\"二聚体\"、\"西门子\"。")
            })
            add("limit", JsonObject().apply {
                addProperty("type", "integer")
                addProperty("description", "返回条数上限，默认 20，最大 50。")
            })
        })
        add("required", JsonArray().apply { add("keyword") })
    }

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val keyword = toolCall.arguments.get("keyword")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?: return ToolResult.failure(
                toolCall, ToolErrorKind.INVALID_ARGUMENTS,
                "search_products 需要提供 keyword（检索词，支持名称片段/厂家/DI/物料编码）。"
            )
        if (keyword.isBlank()) {
            return ToolResult.failure(toolCall, ToolErrorKind.INVALID_ARGUMENTS, "keyword 不能为空。")
        }
        val limit = toolCall.arguments.get("limit")?.takeIf { it.isJsonPrimitive }?.let {
            try { it.asInt.coerceIn(1, MAX_LIMIT) } catch (_: Exception) { DEFAULT_LIMIT }
        } ?: DEFAULT_LIMIT

        // 三级降级：组合键精确 → DI 精确 → 四字段 LIKE 模糊
        val matches: List<ProductBase> = repository.getProductByKey(keyword)?.let { listOf(it) }
            ?: repository.getProductsByDi(keyword).ifEmpty { repository.searchProducts(keyword) }

        if (matches.isEmpty()) {
            return ToolResult.success(
                toolCall,
                "产品库中未找到匹配\"$keyword\"的产品。\n" +
                    "自查建议：换更短的关键词或名称片段重试（如\"二聚体\"→\"二聚\"）；" +
                    "若要查的是库存数量/批号/效期，请改用 query_records。"
            )
        }

        val total = matches.size
        val returned = matches.take(limit)
        val json = JsonObject().apply {
            addProperty("total", total)
            addProperty("returned", returned.size)
            addProperty("truncated", total > returned.size)
            add("products", JsonArray().apply {
                returned.forEach { p ->
                    add(JsonObject().apply {
                        addProperty("产品编码", p.productKey)
                        addProperty("产品名称", p.productName)
                        addProperty("DI", p.di ?: "")
                        addProperty("物料编码", p.materialCode ?: "")
                        addProperty("规格", p.specification ?: "")
                        addProperty("型号", p.model ?: "")
                        addProperty("厂家", p.manufacturer ?: "")
                        addProperty("单位", p.unit ?: "")
                        addProperty("注册证号", p.registrationCert ?: "")
                        addProperty("分类编码", p.categoryCode ?: "")
                        addProperty("来源", p.source ?: "")
                    })
                }
            })
        }
        val content = buildString {
            append("产品档案查询成功，找到 $total 个匹配产品")
            if (total > returned.size) append("（仅返回前 ${returned.size} 个，可缩小关键词或加大 limit）")
            append("。明细如下（请向用户简明汇报产品档案）：\n")
            append(json.toString())
        }
        return ToolResult.success(toolCall, content)
    }

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 50
    }
}
