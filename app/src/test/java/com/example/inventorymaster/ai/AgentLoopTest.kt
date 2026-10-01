package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.agent.AgentEvent
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiCapabilities
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiResponse
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiServiceException
import com.example.inventorymaster.ai.provider.AiErrorKind
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.ai.provider.StreamChunk
import com.example.inventorymaster.ai.tools.AiTool
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent 循环行为测试（docs/AI_V3_REDESIGN.md §5 AgentLoopTest）。
 * 用脚本化 FakeAiService 驱动 InventoryAiAgent，全部纯 JVM，不依赖 Android。
 */
class AgentLoopTest {

    // ── 测试替身 ──────────────────────────────────────────────

    /** 按脚本依次返回响应；脚本耗尽则抛错（防止测试静默走过头） */
    private class FakeAiService(
        override val capabilities: AiCapabilities = AiCapabilities(functionCalling = true, streaming = false),
        private val script: List<AiResponse> = emptyList(),
        private val alwaysThrow: AiServiceException? = null,
        private val hangForever: Boolean = false
    ) : AiService {

        /** 每次 chat 的 (messages, tools) 快照，供断言请求形态 */
        val calls = mutableListOf<Pair<List<AiMessage>, List<AiToolDefinition>?>>()

        private var cursor = 0

        override suspend fun chat(
            messages: List<AiMessage>,
            systemPrompt: String?,
            tools: List<AiToolDefinition>?
        ): AiResponse {
            calls += messages to tools
            alwaysThrow?.let { throw it }
            if (hangForever) CompletableDeferred<AiResponse>().await()
            return script.getOrNull(cursor++)
                ?: error("FakeAiService 脚本耗尽（第 ${calls.size} 次调用）")
        }

        override fun streamChat(
            messages: List<AiMessage>,
            systemPrompt: String?,
            tools: List<AiToolDefinition>?
        ): Flow<StreamChunk> = error("AgentLoopTest 不覆盖流式路径")

        override suspend fun isAvailable(): Boolean = true
    }

    private class FakeTool(
        override val name: String,
        private val result: String = "{\"状态\":\"ok\"}"
    ) : AiTool {
        override val description = "测试工具 $name"
        override val parameters = JsonObject()

        var executeCount = 0
            private set
        var lastArgs: JsonObject? = null
            private set

        override suspend fun execute(arguments: JsonObject): String {
            executeCount++
            lastArgs = arguments
            return result
        }
    }

    private fun call(id: String, name: String, args: String = "{}"): AiToolCall =
        AiToolCall(id = id, name = name, arguments = JsonParser.parseString(args).asJsonObject)

    private fun newAgent(service: AiService, vararg tools: AiTool): Pair<InventoryAiAgent, ConversationMemory> {
        val memory = ConversationMemory()
        val agent = InventoryAiAgent(service, ToolRegistry().apply { tools.forEach { register(it) } }, memory)
        return agent to memory
    }

    private suspend fun collect(agent: InventoryAiAgent, message: String): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>()
        agent.chat(message).collect { events += it }
        return events
    }

    /** 边收集边答复 CONFIRM 请求 */
    private suspend fun collectWithConfirm(
        agent: InventoryAiAgent,
        message: String,
        approved: Boolean
    ): List<AgentEvent> = runBlocking {
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            agent.chat(message).collect { e ->
                events += e
                if (e is AgentEvent.ConfirmRequired) agent.respondConfirm(e.requestId, approved)
            }
        }
        job.join()
        events
    }

    /** 校验消息序列中 assistant(tool_calls) 与 tool 结果完整配对（BUG-1 不回归） */
    private fun assertPairingValid(messages: List<AiMessage>) {
        val openIds = mutableSetOf<String>()
        for (m in messages) {
            m.toolCalls?.forEach { openIds.add(it.id) }
            if (m.role == "tool") {
                assertTrue("tool 消息(${m.toolCallId}) 无对应 tool_calls", m.toolCallId != null && openIds.contains(m.toolCallId))
                openIds.remove(m.toolCallId)
            }
        }
        assertTrue("序列末尾仍有未配对的 tool_calls: $openIds", openIds.isEmpty())
    }

    // ── 用例 ─────────────────────────────────────────────────

    @Test
    fun `直接回答_无工具调用`() = runBlocking {
        val service = FakeAiService(script = listOf(AiResponse("你好，我是库存助手")))
        val (agent, memory) = newAgent(service, FakeTool("query_records"))

        val events = collect(agent, "你好")

        assertEquals(1, service.calls.size)
        assertEquals(1, events.size)
        val response = events[0] as AgentEvent.Response
        assertEquals("你好，我是库存助手", response.content)
        val exported = memory.messagesForRequest()
        assertEquals(listOf("user", "assistant"), exported.map { it.role })
        assertPairingValid(exported)
    }

    @Test
    fun `单轮工具调用后总结_配对完整`() = runBlocking {
        val tool = FakeTool("query_records", result = "{\"状态\":\"ok\",\"合计\":12}")
        val service = FakeAiService(
            script = listOf(
                AiResponse(content = "", toolCalls = listOf(call("c1", "query_records"))),
                AiResponse("当前库存合计 12 件")
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collect(agent, "现在库存多少")

        assertEquals(1, tool.executeCount)
        assertEquals(2, service.calls.size)
        assertTrue(events.any { it is AgentEvent.ToolCallStart })
        assertEquals("当前库存合计 12 件", (events.last() as AgentEvent.Response).content)
        val exported = memory.messagesForRequest()
        assertEquals(listOf("user", "assistant", "tool", "assistant"), exported.map { it.role })
        assertTrue(exported[2].content.orEmpty().contains("状态"))
        assertPairingValid(exported)
    }

    @Test
    fun `完全相同签名的重复调用被截断`() = runBlocking {
        val tool = FakeTool("query_records")
        val service = FakeAiService(
            script = listOf(
                AiResponse(content = "", toolCalls = listOf(call("c1", "query_records", "{\"a\":1}"))),
                AiResponse(content = "", toolCalls = listOf(call("c2", "query_records", "{\"a\":1}")))
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collect(agent, "查询")

        assertEquals(1, tool.executeCount)
        assertTrue(events.last() is AgentEvent.Response)
        assertTrue((events.last() as AgentEvent.Response).content.contains("陷入循环"))
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `同一工具连续三轮未决被截断`() = runBlocking {
        val tool = FakeTool("query_records")
        val service = FakeAiService(
            script = listOf(
                AiResponse(content = "", toolCalls = listOf(call("c1", "query_records", "{\"a\":1}"))),
                AiResponse(content = "", toolCalls = listOf(call("c2", "query_records", "{\"a\":2}"))),
                AiResponse(content = "", toolCalls = listOf(call("c3", "query_records", "{\"a\":3}")))
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collect(agent, "查询")

        // 第三轮在执行前被截断，只真正执行了两次
        assertEquals(2, tool.executeCount)
        assertTrue((events.last() as AgentEvent.Response).content.contains("连续执行多次"))
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `CONFIRM批准后执行`() = runBlocking {
        val tool = FakeTool("execute_ui_action", result = "已跳转")
        val service = FakeAiService(
            script = listOf(
                AiResponse(content = "", toolCalls = listOf(call("c1", "execute_ui_action"))),
                AiResponse("已为你打开记录详情")
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collectWithConfirm(agent, "打开第一条记录", approved = true)

        assertTrue(events.any { it is AgentEvent.ConfirmRequired })
        assertEquals(1, tool.executeCount)
        assertTrue(events.filterIsInstance<AgentEvent.ToolCallResult>().any { it.toolName == "execute_ui_action" })
        assertEquals("已为你打开记录详情", (events.last() as AgentEvent.Response).content)
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `CONFIRM拒绝则不执行`() = runBlocking {
        val tool = FakeTool("execute_ui_action")
        val service = FakeAiService(
            script = listOf(
                AiResponse(content = "", toolCalls = listOf(call("c1", "execute_ui_action"))),
                AiResponse("好的，已取消")
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collectWithConfirm(agent, "打开第一条记录", approved = false)

        assertTrue(events.any { it is AgentEvent.ConfirmRequired })
        assertEquals(0, tool.executeCount)
        val toolMsg = memory.messagesForRequest().first { it.role == "tool" }
        assertTrue(toolMsg.content.orEmpty().contains("拒绝"))
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `服务异常转为Error事件且轮次补齐`() = runBlocking {
        val service = FakeAiService(alwaysThrow = AiServiceException(AiErrorKind.UNREACHABLE, "网络连接失败，请检查网络后重试"))
        val (agent, memory) = newAgent(service, FakeTool("query_records"))

        val events = collect(agent, "查库存")

        assertEquals(1, events.size)
        val error = events[0] as AgentEvent.Error
        assertEquals(AiErrorKind.UNREACHABLE, error.kind)
        val exported = memory.messagesForRequest()
        assertEquals("assistant", exported.last().role)
        assertTrue(exported.last().content.orEmpty().contains("服务暂时不可用"))
        assertPairingValid(exported)
    }

    @Test
    fun `用户取消后补齐轮次保持记忆合法`() = runBlocking {
        val service = FakeAiService(hangForever = true)
        val (agent, memory) = newAgent(service, FakeTool("query_records"))

        val job = launch { agent.chat("查库存").collect {} }
        delay(100)
        job.cancelAndJoin()

        val exported = memory.messagesForRequest()
        assertEquals("assistant", exported.last().role)
        assertEquals("（本次回答已停止）", exported.last().content)
        assertPairingValid(exported)
    }

    @Test
    fun `轮次耗尽后做无工具总结`() = runBlocking {
        val tools = Array(5) { FakeTool("tool_$it") }
        val script = tools.mapIndexed { i, t ->
            AiResponse(content = "", toolCalls = listOf(call("c$i", t.name, "{\"step\":$i}")))
        } + AiResponse("总结完毕")
        val service = FakeAiService(script = script)
        val (agent, memory) = newAgent(service, *tools)

        val events = collect(agent, "全面盘点")

        assertEquals(6, service.calls.size)
        // 总结调用不带工具定义
        assertNull(service.calls[5].second)
        tools.forEach { assertEquals(1, it.executeCount) }
        assertEquals("总结完毕", (events.last() as AgentEvent.Response).content)
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `JSON协议模式下走JsonToolCallStrategy`() = runBlocking {
        val tool = FakeTool("query_records")
        val service = FakeAiService(
            capabilities = AiCapabilities(functionCalling = false, streaming = false),
            script = listOf(
                AiResponse("{\"tool\":\"query_records\",\"arguments\":{\"limit\":5}}"),
                AiResponse("共 5 条记录")
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collect(agent, "查5条")

        assertEquals(1, tool.executeCount)
        assertEquals(5, tool.lastArgs?.get("limit")?.asInt)
        assertEquals("共 5 条记录", (events.last() as AgentEvent.Response).content)
        assertPairingValid(memory.messagesForRequest())
    }

    @Test
    fun `JSON模式畸形输出纠正重试一次后按普通回答处理`() = runBlocking {
        val tool = FakeTool("query_records")
        val service = FakeAiService(
            capabilities = AiCapabilities(functionCalling = false, streaming = false),
            script = listOf(
                AiResponse("{\"tool\": \"query_records\" \"arguments\": {\"limit\": 5}}"),
                AiResponse("直接回答：共 5 条")
            )
        )
        val (agent, memory) = newAgent(service, tool)

        val events = collect(agent, "查5条")

        assertEquals(0, tool.executeCount)
        assertEquals(2, service.calls.size)
        assertEquals("直接回答：共 5 条", (events.last() as AgentEvent.Response).content)
        // 纠正提示写入了记忆
        assertTrue(memory.messagesForRequest().any { it.role == "user" && it.content.orEmpty().contains("无法解析为工具调用 JSON") })
        assertPairingValid(memory.messagesForRequest())
    }
}
