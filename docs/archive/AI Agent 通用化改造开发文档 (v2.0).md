AI Agent 通用化改造开发文档 (v2.0)

架构目标

打破工具壁垒：用单一的 QueryInventoryDataTool 替代所有基于硬编码的盘点统计工具。

赋予 AI 思考能力：引入 ReAct (Thought-Action-Observation) 框架，让 AI 具备多步推理与决策能力。

修复循环阻断：解决多轮工具调用时上下文及可用工具列表丢失的问题。

核心改造步骤

第一阶段：底层数据查询的动态化支撑

由于 AI 会组合出各种意想不到的聚合查询（如 COUNT、SUM），传统的 Room @Dao 配合特定实体类的方法会因为字段映射失败而崩溃。因此，我们需要在 Repository 层直接操作底层数据库，将 Cursor 转换为 AI 友好的 JSON 字符串。

1. 在 InventoryRepository 中新增动态查询方法
   import androidx.sqlite.db.SimpleSQLiteQuery
   import com.google.gson.JsonArray
   import com.google.gson.JsonObject

class InventoryRepository(private val database: AppDatabase) { // 替换为你实际的 Database 类名

    /**
     * 执行由 AI 生成的动态 SQL，并将 Cursor 转换为 JSON 数组字符串返回
     */
    fun executeDynamicSqlForAi(sqlString: String): String {
        return try {
            val cursor = database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sqlString))
            val jsonArray = JsonArray()

            cursor.use { c ->
                val columnNames = c.columnNames
                while (c.moveToNext()) {
                    val rowObject = JsonObject()
                    for (i in columnNames.indices) {
                        val columnName = columnNames[i]
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> rowObject.addProperty(columnName, c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> rowObject.addProperty(columnName, c.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> rowObject.addProperty(columnName, c.getString(i))
                            android.database.Cursor.FIELD_TYPE_NULL -> rowObject.addProperty(columnName, null as String?)
                        }
                    }
                    jsonArray.add(rowObject)
                }
            }
            jsonArray.toString()
        } catch (e: Exception) {
            "SQL Execution Error: ${e.message}"
        }
    }
}
第二阶段：编写通用数据提取工具 (Gson 版本)

使用 Gson 实现 AiTool 接口，定义标准的 JSON 入参契约，约束大模型只能查询核心盘存字段。

创建 QueryInventoryDataTool.kt
package com.example.inventorymaster.ai.tools

import com.google.gson.JsonObject
import com.google.gson.JsonParser

class QueryInventoryDataTool(
private val queryExecutor: (String) -> String
) : AiTool {

    override val name = "QueryInventoryDataTool"
    
    override val description = """
        通用库存数据查询引擎。用于查询 stock_records 表。
        支持的字段包括作为主键唯一标识的 documentId，以及 productKey, batchNumber, quantity, location, productionDate, expiryDate 等。
        你必须根据用户的需求组合 SQL 参数。返回结果为 JSON。
    """.trimIndent()

    // 使用 Gson 解析 JSON 字符串作为 parameters 契约
    override val parameters: JsonObject = JsonParser.parseString("""
        {
          "type": "object",
          "properties": {
            "select": { "type": "string", "description": "要聚合或查询的字段，例如 'documentId, COUNT(*) as count'" },
            "where": { "type": "string", "description": "基础过滤条件，例如 'quantity > 0'" },
            "groupBy": { "type": "string", "description": "分组字段，例如 'productKey, location'" },
            "having": { "type": "string", "description": "分组过滤，例如 'COUNT(documentId) > 1'" },
            "orderBy": { "type": "string", "description": "排序逻辑，例如 'expiryDate ASC'" }
          },
          "required": ["select"]
        }
    """).asJsonObject

    override suspend fun execute(arguments: JsonObject): String {
        return try {
            val select = if (arguments.has("select")) arguments.get("select").asString else "*"
            val whereArg = if (arguments.has("where")) arguments.get("where").asString else ""
            val groupByArg = if (arguments.has("groupBy")) arguments.get("groupBy").asString else ""
            val havingArg = if (arguments.has("having")) arguments.get("having").asString else ""
            val orderByArg = if (arguments.has("orderBy")) arguments.get("orderBy").asString else ""

            val where = if (whereArg.isNotBlank()) "WHERE $whereArg" else ""
            val groupBy = if (groupByArg.isNotBlank()) "GROUP BY $groupByArg" else ""
            val having = if (havingArg.isNotBlank()) "HAVING $havingArg" else ""
            val orderBy = if (orderByArg.isNotBlank()) "ORDER BY $orderByArg" else ""

            val sql = "SELECT $select FROM stock_records $where $groupBy $having $orderBy LIMIT 100"
            
            // 执行查询
            queryExecutor(sql)
            
        } catch (e: Exception) {
            "Error parsing arguments or executing query: ${e.message}"
        }
    }
}
第三阶段：改造大脑中枢与上下文循环

1. 升级 PromptBuilder.kt (注入 ReAct 思维)

替换原来的 # 工具使用策略 模块，强制推行 Thought-Action 循环。
// ...前面的代码保持不变
appendLine("# 思考与执行框架 (ReAct)")
appendLine("遇到需要查数据的问题时，必须遵循以下步骤循环：")
appendLine("Thought: [分析当前任务，思考需要调用什么工具组合什么参数]")
appendLine("Action: [工具名称，例如 QueryInventoryDataTool]")
appendLine("Action Input: [工具所需的 JSON 参数]")
appendLine("Observation: [等待系统返回执行结果]")
appendLine("Thought: [分析返回的数据，决定是继续查询还是可以直接回答]")
appendLine("Final Answer: [用 Markdown 格式输出最终结论给用户]")
appendLine()
appendLine("# 核心工具库")
appendLine("- QueryInventoryDataTool: 唯一的数据查询网关，你可以用它完成找重复、看效期、统计数量等所有查询动作。")
appendLine("- get_product_detail: 用于精确获取某一个产品的字典表详细信息。")
appendLine("- execute_ui_action: 引导用户跳转界面。")
2. 修复 InventoryAiAgent.kt 中的工具断层

确保在 MAX_TOOL_ROUNDS 循环中，AI 始终持有工具可用权。
// 找到 InventoryAiAgent.kt 的大约第 52 行
// 【修改前】
val response = aiService.chat(
messages = currentMessages,
systemPrompt = systemPrompt,
tools = if (hasTools && round == 0) toolDefs else null
)

// 【修改后：去掉 round == 0 的限制】
val response = aiService.chat(
messages = currentMessages,
systemPrompt = systemPrompt,
tools = if (hasTools) toolDefs else null
)
第四阶段：注册与清理

在初始化阶段（例如你的 Application 或依赖注入模块），将旧的查询工具从 ToolRegistry 中移除，并注册新的通用引擎：
ToolRegistry.clear()
ToolRegistry.register(QueryInventoryDataTool { sql ->
inventoryRepository.executeDynamicSqlForAi(sql)
})
// 保留必要的单体工具
ToolRegistry.register(GetProductDetailTool(repository))
ToolRegistry.register(ExecuteUiActionTool())