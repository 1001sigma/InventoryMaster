package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.uncheck
import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 盘点记录查验状态修改工具（标记查验 / 撤销查验）。
 * 业务与安全契约：
 * 1. 当撤销查验（is_verified = false）时，若该记录存在实盘数量（actualQuantity），
 *    自动通过 StockRecord.uncheck() 备份转存写入备注（如 "原实盘: 50; 原备注"）后再置空，
 *    确保人工清点数据绝对不丢失；
 * 2. 标记查验（is_verified = true）时，若未显式指定 actual_quantity，默认带出账面数量。
 */
class UpdateRecordVerificationTool(
    private val repository: InventoryRepository
) : AiTool {

    override val name = "update_record_verification"

    override val description = """
        修改指定盘点记录的查验状态（标记为已查验或撤销查验）。
        参数需包含记录ID（record_id）与目标查验状态（is_verified）。
        安全保护：当撤销查验时，原先记录的实际盘点数量会自动备份转存至备注，防止盘点数据丢失。
    """.trimIndent()

    override val parameters: JsonObject = JsonParser.parseString("""
        {
          "type": "object",
          "properties": {
            "record_id": {
              "type": "integer",
              "description": "需要修改查验状态的库存盘点记录ID（recordId）。"
            },
            "is_verified": {
              "type": "boolean",
              "description": "目标查验状态：true=标记为已查验，false=撤销查验（设为未查验）。"
            },
            "actual_quantity": {
              "type": "number",
              "description": "实际盘点数量（可选）。标记为已查验时若不填则默认使用原账面数量。"
            },
            "remarks": {
              "type": "string",
              "description": "附加备注说明（可选）。"
            }
          },
          "required": ["record_id", "is_verified"]
        }
    """).asJsonObject

    override suspend fun execute(toolCall: AiToolCall): ToolResult {
        val args = toolCall.arguments
        val recordId = args.get("record_id")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asLong
            ?: return ToolResult.failure(toolCall, ToolErrorKind.INVALID_ARGUMENTS, "缺少必填参数 record_id")

        val targetVerified = args.get("is_verified")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean
            ?: return ToolResult.failure(toolCall, ToolErrorKind.INVALID_ARGUMENTS, "缺少必填参数 is_verified")

        val actualQty = args.get("actual_quantity")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
            try { it.asDouble } catch (e: Exception) { null }
        }
        val customRemarks = args.get("remarks")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.trim()

        val record = repository.getRecordById(recordId)
            ?: return ToolResult.failure(toolCall, ToolErrorKind.BUSINESS_ERROR, "未找到 ID 为 $recordId 的盘点记录")

        val updatedRecord: StockRecord = if (targetVerified) {
            // 标记查验
            val finalActualQty = actualQty ?: (record.actualQuantity ?: record.quantity)
            val baseRemarks = record.remarks?.replace(Regex("^已查验\\[.*?][^;]*;?\\s*"), "")?.trim()
            val newRemarks = when {
                customRemarks.isNullOrBlank() -> baseRemarks
                baseRemarks.isNullOrBlank() -> customRemarks
                else -> "$baseRemarks; $customRemarks"
            }
            record.copy(
                isVerified = true,
                actualQuantity = finalActualQty,
                remarks = newRemarks?.ifBlank { null },
                syncStatus = 1,
                lastUpdateTime = System.currentTimeMillis()
            )
        } else {
            // 撤销查验：调用 uncheck() 自动将实盘数值格式化转存备注
            val unchecked = record.uncheck()
            if (!customRemarks.isNullOrBlank()) {
                val combinedRemarks = if (unchecked.remarks.isNullOrBlank()) customRemarks else "${unchecked.remarks}; $customRemarks"
                unchecked.copy(remarks = combinedRemarks)
            } else {
                unchecked
            }
        }

        repository.updateRecord(updatedRecord)

        val actionDesc = if (targetVerified) "标记为【已查验】" else "撤销查验（已变更为【未查验】）"
        val msg = buildString {
            append("成功将记录(ID: $recordId, 批号: ${record.batchNumber}) $actionDesc。")
            if (!targetVerified && record.actualQuantity != null) {
                append("（原实盘数量 ${record.actualQuantity} 已自动备份转存至备注：${updatedRecord.remarks}）")
            }
        }

        return ToolResult.success(toolCall, msg)
    }
}
