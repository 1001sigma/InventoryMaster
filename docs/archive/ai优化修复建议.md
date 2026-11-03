1. 为什么“每秒请求次数过多（Rate Limit / 陷入死循环）”？

看你的 InventoryAiAgent.kt 中，工具调用的 while 循环逻辑存在两个严重的逻辑漏洞：

漏洞 A：没有将 AI 的“工具调用请求（Tool Calls）”存入对话历史中。 在 OpenAI/兼容 OpenAI 格式（如 Qwen/DeepSeek）的 API 规范中，如果你要执行工具，你必须先将大模型返回的带有 tool_calls 的 AssistantMessage 塞入上下文，然后再塞入 ToolMessage（工具执行结果）。在你的代码里，你只塞了工具结果 memory.addToolResult，却漏掉了大模型的 ToolCalls 请求！这就导致下一轮循环发请求时，大模型在上下文中只看到了“凭空出现的工具结果”，它会感到困惑，于是再次发起一模一样的工具调用，直接死循环 5 轮，瞬间触发 Rate Limit。

漏洞 B：没有检测“一模一样的工具调用”。 如果模型卡壳了，疯狂重复调用同一个参数的工具，系统没有拦截。

2. 为什么“返回结果出现内部字段（数据污染）”？

漏洞 A：Prompt 中的 ReAct 框架和原生的 Function Calling 混合在一起，导致模型精神分裂。 你在 PromptBuilder.kt 里写了 Thought/Action/Action Input/Observation 的文本规范，但同时又开启了 API 的原生 tools 参数。大模型在用云端高级模型时，会倾向于走原生的 Function Calling，但这会跟你的纯文本 ReAct 规则冲突，导致它在回复 content 时，把推理和内部 JSON 字段一并当成文本打印了出来。

漏洞 B：QueryInventoryDataTool.kt 的 queryExecutor(sql) 结果没有过滤。 数据库返回了什么，你就原封不动喂给了 AI，没有对字段进行人类友好的清洗。

🛠️ 开始修复：请用以下代码替换你原有的文件

1. 修复核心调度逻辑：InventoryAiAgent.kt

我们要解决漏加上下文导致的死循环，以及加入重复调用拦截、更安全的上下文更新。
package com.example.inventorymaster.ai

import android.util.Log
import com.example.inventorymaster.ai.agent.AiAgent
import com.example.inventorymaster.ai.agent.ActionResultExtractor
import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.prompt.PromptBuilder
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiMessage // 确保引入了你的数据模型
import com.example.inventorymaster.ai.tools.ToolExecutor
import com.example.inventorymaster.ai.tools.ToolPermission
import com.example.inventorymaster.ai.tools.ToolPermissionManager
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.repository.InventoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.delay

class InventoryAiAgent(
private val aiService: AiService,
private val repository: InventoryRepository,
private val memory: ConversationMemory = ConversationMemory()
) : AiAgent {

    private val toolExecutor = ToolExecutor()
    private var currentSession: InventorySession? = null

    fun setCurrentSession(session: InventorySession?) {
        currentSession = session
    }

    override suspend fun chat(message: String): Flow<AgentEvent> = channelFlow {
        try {
            memory.addUserMessage(message)
            val allSessions = repository.getAllSessionsOnce()

            val systemPrompt = PromptBuilder.build(
                session = currentSession,
                recordCount = null,
                filterCondition = null,
                allSessions = allSessions
            )

            val toolDefs = ToolRegistry.getToolDefinitions()
            val hasTools = toolDefs.isNotEmpty() && aiService.supportsTools()

            val MAX_TOOL_ROUNDS = 5
            var round = 0
            
            // 记录上一次调用的工具参数，用来防止死循环
            var lastToolCallSignature = ""

            while (round < MAX_TOOL_ROUNDS) {
                val currentMessages = memory.getMessages().toMutableList()
                
                val response = aiService.chat(
                    messages = currentMessages,
                    systemPrompt = systemPrompt,
                    tools = if (hasTools) toolDefs else null
                )

                // 情况 1：大模型没有调用工具，直接给出了最终回答
                if (response.toolCalls.isNullOrEmpty()) {
                    memory.addAssistantMessage(response.content)
                    send(AgentEvent.Response(response.content))
                    break
                }

                // 情况 2：大模型触发了工具调用
                val toolCalls = response.toolCalls
                
                // 【核心修复 1】：必须把大模型这一次返回的 toolCalls 写入对话历史！
                // 否则大模型下一轮不知道自己提过这个请求
                memory.addMessage(AiMessage(
                    role = "assistant", 
                    content = response.content, 
                    toolCalls = toolCalls
                ))

                // 构造防死循环签名（把所有工具名和参数拼起来）
                val currentSignature = toolCalls.joinToString(";") { "${it.name}:${it.arguments}" }
                if (currentSignature == lastToolCallSignature) {
                    Log.w("InventoryAiAgent", "检测到模型陷入工具调用死循环，强行截断")
                    val fallbackMsg = "抱歉，我在查询数据库时卡住了，请换一种更具体的方式向我提问。"
                    memory.addAssistantMessage(fallbackMsg)
                    send(AgentEvent.Response(fallbackMsg))
                    break
                }
                lastToolCallSignature = currentSignature

                // 并发执行工具
                val toolResults = coroutineScope {
                    toolCalls.map { toolCall ->
                        async {
                            send(AgentEvent.ToolCallStart(toolCall.name))
                            val result = toolExecutor.execute(toolCall)
                            send(AgentEvent.ToolCallResult(toolCall.name, result))
                            
                            // 【核心修复 2】：规范写入 ToolMessage 的格式（需要带上 toolCallId）
                            memory.addToolResult(toolCall.id, toolCall.name, result)
                            toolCall.id to result
                        }
                    }
                }.awaitAll()

                // 处理 UI 动作提取
                for (toolCall in toolCalls) {
                    val matchResult = toolResults.find { it.first == toolCall.id }
                    if (matchResult != null) {
                        ActionResultExtractor.extract(toolCall.name, matchResult.second)?.let { action ->
                            send(AgentEvent.ActionExecuted(action))
                        }
                    }
                }

                round++
                // 智能阶梯延迟，缓解 API 频率限制 (500ms)
                delay(500)
            }

            // 如果超过最大轮数仍未结束，强行给一个答复
            if (round >= MAX_TOOL_ROUNDS) {
                val currentMessages = memory.getMessages().toMutableList()
                val finalResponse = aiService.chat(messages = currentMessages, systemPrompt = systemPrompt)
                memory.addAssistantMessage(finalResponse.content)
                send(AgentEvent.Response(finalResponse.content))
            }
        } catch (e: Exception) {
            Log.e("InventoryAiAgent", "Chat failed", e)
            send(AgentEvent.Error("AI 服务出错: ${e.message}"))
        }
    }

    private fun AiService.supportsTools(): Boolean = !isLocal()
}
(注意：请根据你的 ConversationMemory.kt 实现，检查 addToolResult 是不是需要传入 id。标准兼容 OpenAI 的格式通常是需要 tool_call_id 的)

2. 修复提示词污染：PromptBuilder.kt

我们要精简和剥离纯文本的 ReAct 格式。因为你开启了原生的 tools 接口，模型会通过 JSON 调用，不需要再让它输出文本形式的 Thought/Action/Observation，否则两种格式冲突，大模型就会把内部字段当成正文打印出来。
package com.example.inventorymaster.ai.prompt

import com.example.inventorymaster.data.entity.InventorySession

object PromptBuilder {
fun build(
session: InventorySession?,
recordCount: Int?,
filterCondition: String?,
allSessions: List<InventorySession> = emptyList()
): String {
return buildString {
appendLine("# 角色")
appendLine("你是医疗器械仓库库存管理助手，专门处理库存查询、产品管理、盘存分析等任务。")
appendLine("你只回答与医疗器械库存管理相关的问题。对于无关问题，请礼貌地引导用户回到库存管理话题。")
appendLine()
appendLine("# 回答与语言规范")
appendLine("1. 严禁在最终回答（Final Answer）中输出任何数据库内部的字段名（例如下划线字段 'productKey'、'sessionId'、'expiryDate' ）。")
appendLine("2. 你必须将这些内部字段转换为清晰、专业的人类语言展示给用户。例如：将 'productKey' 转换为 '产品编码'，'expiryDate' 转换为 '有效期'，'quantity' 转换为 '数量'。")
appendLine("3. 如果数据库返回的日期是 13 位数字时间戳，必须在你的大脑中将其换算为可读的 'YYYY-MM-DD' 日期格式再展现给用户。")
appendLine("4. 使用简洁专业的 Markdown 格式：")
appendLine("   - 关键数据或结论用**粗体**")
appendLine("   - 列表、多项数据务必使用 **Markdown 表格** 或 **列表 (- 或 1.)** 清晰列出。")
appendLine()
appendLine("# 仓库实时上下文")
session?.let {
appendLine("- 当前正在执行的盘存任务 ID：`${it.id}`")
appendLine("- 当前盘存任务名称：**${it.name}**")
appendLine("- 任务开启日期：${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it.date))}")
}
if (allSessions.isNotEmpty()) {
appendLine("\n- 系统中可用的盘存任务列表（当用户没有指定当前任务，但提到了其他盘存任务时使用）：")
val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
for (s in allSessions) {
appendLine("  · 任务ID: `${s.id}`, 名称: ${s.name}, 创建日期: ${formatter.format(java.util.Date(s.date))}")
}
}
appendLine()
appendLine("# 核心工具调用规范")
appendLine("1. 当用户查询的需求无法直接从上面的上下文中得出时，你必须使用下方的原生工具进行查询。")
appendLine("2. ⚠️【极其重要】：查询数据时，必须遵循以下规则：")
appendLine("   - 当前时间是 2026-07-09。")
appendLine("   - 数据库中的 `expiryDate`（有效期）和 `productionDate`（生产日期）是以 **13位毫秒级时间戳 Long 型** 存储的。")
appendLine("   - 当用户要求过滤日期（如‘过期’、‘近效期’）时，你必须在心中把日期转换成对应的 13 位时间戳数字，并写在 where 条件里。严禁在 where 子句中写 '2024-12-31' 这样的字符串！")
appendLine("   - 示例：若要查询已过期的器械，应转化为：`expiryDate < 1783612800000` (即 2026-07-09 的时间戳)。")
appendLine("3. 严禁向用户解释你使用了什么工具，执行完工具后，直接用人类语言输出你整理好的 Markdown 报表。")
}
}
}
// 在 QueryInventoryDataTool.kt 的 execute 函数的 return try 块里：
val resultRaw = queryExecutor(sql)
if (resultRaw.isBlank() || resultRaw == "[]") {
return "Observation: 数据库查询成功，但未找到任何匹配的库存记录。请如实告知用户未查到相关数据。"
}
return "Observation: 数据库返回的真实记录如下（请转换为优雅的中文 Markdown 列表或表格回复给用户，不要泄露字段名）：\n$resultRaw"

