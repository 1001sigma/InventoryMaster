package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository

/**
 * 工具注册表。v3 起为实例（随 Agent 生命周期创建销毁），
 * 不再是全局 object——修复 provider 切换时 clear/re-register 的竞态与旧实例持有问题（BUG-4）。
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, AiTool>()

    fun register(tool: AiTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): AiTool? = tools[name]

    fun getAll(): List<AiTool> = tools.values.toList()

    fun getToolDefinitions(): List<AiToolDefinition> =
        tools.values.map { tool ->
            AiToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        }

    companion object {
        /** 聊天助手默认工具集 */
        fun createDefault(
            aiService: AiService,
            repository: InventoryRepository,
            settingsRepository: SettingsRepository
        ): ToolRegistry = ToolRegistry().apply {
            register(QueryRecordsTool(repository))
            register(ListSessionsTool(repository))
            register(GetProductDetailTool(repository))
            register(ExecuteUiActionTool())
            register(OcrCorrectTool(aiService))
            register(AnalyzeAbnormalTool(repository, settingsRepository))
            register(GenerateSummaryTool(repository, settingsRepository))
        }
    }
}
