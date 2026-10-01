package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ToolExecutor(
    private val registry: ToolRegistry,
    private val timeoutMs: Long = 10_000
) {
    suspend fun execute(toolCall: AiToolCall): String {
        val tool = registry.get(toolCall.name)
            ?: return "Error: Unknown tool ${toolCall.name}"

        return try {
            withTimeout(timeoutMs) { tool.execute(toolCall.arguments) }
        } catch (e: TimeoutCancellationException) {
            "工具 ${toolCall.name} 执行超时（${timeoutMs / 1000} 秒）。请缩小查询范围（加过滤条件或减小 limit）后重试。"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "工具 ${toolCall.name} 执行失败: ${e.message}"
        }
    }
}
