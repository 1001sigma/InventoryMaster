package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 工具执行器（docs/AI_Runtime §10 执行生命周期）：
 * 查找 → [ToolValidator] 参数校验 → 超时包裹执行 → 异常归一为 [ToolResult.failure]。
 * 所有失败都带可自纠文案回喂模型，绝不抛出普通异常打断对话。
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val timeoutMs: Long = 10_000
) {
    suspend fun execute(toolCall: AiToolCall): ToolResult {
        val tool = registry.get(toolCall.name)
            ?: return ToolResult.failure(
                toolCall,
                ToolErrorKind.NOT_FOUND,
                "未找到工具 \"${toolCall.name}\"。可用工具：${registry.getAll().joinToString("、") { it.name }}。请从列表中选择后重试。"
            )

        ToolValidator.validate(tool.parameters, toolCall.arguments)?.let { error ->
            return ToolResult.failure(toolCall, error.kind, error.message)
        }

        return try {
            withTimeout(timeoutMs) { tool.execute(toolCall) }
        } catch (e: TimeoutCancellationException) {
            ToolResult.failure(
                toolCall,
                ToolErrorKind.TIMEOUT,
                "工具 ${toolCall.name} 执行超时（${timeoutMs / 1000} 秒）。请缩小查询范围（加过滤条件或减小 limit）后重试。"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.failure(toolCall, ToolErrorKind.INTERNAL_ERROR, "工具 ${toolCall.name} 执行失败：${e.message}")
        }
    }
}
