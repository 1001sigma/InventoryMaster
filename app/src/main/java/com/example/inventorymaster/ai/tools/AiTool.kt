package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.gson.JsonObject

interface AiTool {
    val name: String
    val description: String
    val parameters: JsonObject

    /**
     * 执行工具，返回结构化 [ToolResult]（v3 二期起，不再返回裸字符串）。
     * 参数校验已在 [ToolExecutor] 前置完成；工具内部只做业务校验与执行。
     */
    suspend fun execute(toolCall: AiToolCall): ToolResult
}
