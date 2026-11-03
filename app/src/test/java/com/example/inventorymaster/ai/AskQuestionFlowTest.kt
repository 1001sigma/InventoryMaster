package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.agent.UserQuestionAnswer
import com.example.inventorymaster.ai.memory.ConversationMemory
import com.example.inventorymaster.ai.provider.AiCapabilities
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiResponse
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.provider.AiToolDefinition
import com.example.inventorymaster.ai.provider.StreamChunk
import com.example.inventorymaster.ai.runtime.AiEvent
import com.example.inventorymaster.ai.runtime.AiRuntime
import com.example.inventorymaster.ai.tools.AskQuestionTool
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskQuestionFlowTest {

    private class FakeAiService(
        private val script: List<AiResponse>
    ) : AiService {
        override val capabilities = AiCapabilities(functionCalling = true, streaming = false)
        override val modelId = "fake-model"
        private var cursor = 0
        val receivedMessages = mutableListOf<List<AiMessage>>()

        override suspend fun chat(
            messages: List<AiMessage>,
            systemPrompt: String?,
            tools: List<AiToolDefinition>?
        ): AiResponse {
            receivedMessages.add(messages)
            if (cursor < script.size) {
                return script[cursor++]
            }
            return AiResponse(content = "默认兜底回复")
        }

        override fun streamChat(
            messages: List<AiMessage>,
            systemPrompt: String?,
            tools: List<AiToolDefinition>?
        ): Flow<StreamChunk> = flow {}

        override suspend fun isAvailable(): Boolean = true
    }

    @Test
    fun testAskQuestionToolParse() {
        val args = JsonObject().apply {
            val qArray = JsonArray().apply {
                val q1 = JsonObject().apply {
                    addProperty("question", "界面体验如何？")
                    val opts = JsonArray().apply {
                        add("很好")
                        add("一般")
                    }
                    add("options", opts)
                    addProperty("is_multi_select", false)
                }
                add(q1)
            }
            add("questions", qArray)
        }

        val parsed = AskQuestionTool.parseQuestions(args)
        assertEquals(1, parsed.size)
        assertEquals("界面体验如何？", parsed[0].question)
        assertEquals(listOf("很好", "一般"), parsed[0].options)
        assertFalse(parsed[0].isMultiSelect)
    }

    @Test
    fun testRuntimeAwaitingQuestionAndUserAnswer() = runBlocking {
        val toolCallArgs = JsonObject().apply {
            val qArray = JsonArray().apply {
                val q1 = JsonObject().apply {
                    addProperty("question", "你觉得这个选择卡片界面看起来怎么样？")
                    val opts = JsonArray().apply {
                        add("不错，很好用")
                        add("一般般")
                        add("有点看不懂")
                    }
                    add("options", opts)
                }
                add(q1)
            }
            add("questions", qArray)
        }

        val call = AiToolCall(
            id = "call_ask_1",
            name = "ask_question",
            arguments = toolCallArgs
        )

        val fakeAi = FakeAiService(
            listOf(
                AiResponse(content = "", toolCalls = listOf(call)),
                AiResponse(content = "收到你的反馈，非常高兴你喜欢！")
            )
        )

        val registry = ToolRegistry().apply {
            register(AskQuestionTool())
        }
        val memory = ConversationMemory()
        val runtime = AiRuntime(
            aiService = fakeAi,
            registry = registry,
            memory = memory,
            policies = AiRuntime.Policies(questionTimeoutMs = 10_000)
        )

        var awaitingReceived = false
        var completedAnswer: String? = null

        val job = launch {
            runtime.turn("测试提问流程", "系统提示").collect { event ->
                when (event) {
                    is AiEvent.AwaitingQuestion -> {
                        awaitingReceived = true
                        assertEquals(1, event.questions.size)
                        assertEquals("你觉得这个选择卡片界面看起来怎么样？", event.questions[0].question)
                        // 模拟用户在 UI 点击第一项并提交
                        runtime.respondQuestion(
                            event.requestId,
                            UserQuestionAnswer(
                                isSkipped = false,
                                selectedOptions = mapOf(0 to listOf("不错，很好用"))
                            )
                        )
                    }
                    is AiEvent.Completed -> {
                        completedAnswer = event.answer
                    }
                    else -> {}
                }
            }
        }

        job.join()

        assertTrue("应当接收到 AwaitingQuestion 事件并触发挂起", awaitingReceived)
        assertEquals("收到你的反馈，非常高兴你喜欢！", completedAnswer)

        // 验证 memory 中正确持久化了用户选择结果
        val history = memory.messagesForRequest()
        val toolMsg = history.find { it.role == "tool" && it.toolCallId == "call_ask_1" }
        assertTrue("Memory 中应包含 tool 回传结果", toolMsg != null)
        assertTrue("Tool 结果中应记录用户选择了'不错，很好用'", toolMsg?.content?.contains("不错，很好用") == true)
    }

    @Test
    fun testRuntimeAwaitingQuestionAndUserSkip() = runBlocking {
        val toolCallArgs = JsonObject().apply {
            val qArray = JsonArray().apply {
                val q1 = JsonObject().apply {
                    addProperty("question", "请选择物料类型")
                    val opts = JsonArray().apply {
                        add("试剂")
                        add("耗材")
                    }
                    add("options", opts)
                }
                add(q1)
            }
            add("questions", qArray)
        }

        val call = AiToolCall(
            id = "call_ask_2",
            name = "ask_question",
            arguments = toolCallArgs
        )

        val fakeAi = FakeAiService(
            listOf(
                AiResponse(content = "", toolCalls = listOf(call)),
                AiResponse(content = "已为您按默认全部分类展示。")
            )
        )

        val registry = ToolRegistry().apply {
            register(AskQuestionTool())
        }
        val memory = ConversationMemory()
        val runtime = AiRuntime(
            aiService = fakeAi,
            registry = registry,
            memory = memory
        )

        var awaitingReceived = false
        var completedAnswer: String? = null

        val job = launch {
            runtime.turn("查询", "系统提示").collect { event ->
                when (event) {
                    is AiEvent.AwaitingQuestion -> {
                        awaitingReceived = true
                        // 用户点击跳过
                        runtime.respondQuestion(event.requestId, UserQuestionAnswer(isSkipped = true))
                    }
                    is AiEvent.Completed -> {
                        completedAnswer = event.answer
                    }
                    else -> {}
                }
            }
        }

        job.join()

        assertTrue(awaitingReceived)
        assertEquals("已为您按默认全部分类展示。", completedAnswer)
    }

    @Test
    fun testRuntimeAwaitingQuestionWithCustomTextInput() = runBlocking {
        val toolCallArgs = JsonObject().apply {
            val qArray = JsonArray().apply {
                val q1 = JsonObject().apply {
                    addProperty("question", "需要查询哪些产品？")
                    val opts = JsonArray().apply {
                        add("全部物料")
                    }
                    add("options", opts)
                }
                add(q1)
            }
            add("questions", qArray)
        }

        val call = AiToolCall(
            id = "call_ask_custom",
            name = "ask_question",
            arguments = toolCallArgs
        )

        val fakeAi = FakeAiService(
            listOf(
                AiResponse(content = "", toolCalls = listOf(call)),
                AiResponse(content = "已为您按自定义要求处理。")
            )
        )

        val registry = ToolRegistry().apply {
            register(AskQuestionTool())
        }
        val memory = ConversationMemory()
        val runtime = AiRuntime(
            aiService = fakeAi,
            registry = registry,
            memory = memory
        )

        val job = launch {
            runtime.turn("查询", "系统提示").collect { event ->
                if (event is AiEvent.AwaitingQuestion) {
                    // 模拟用户输入了自定义补充
                    runtime.respondQuestion(
                        event.requestId,
                        UserQuestionAnswer(
                            isSkipped = false,
                            selectedOptions = mapOf(0 to listOf("全部物料", "补充: 重点查一号库位"))
                        )
                    )
                }
            }
        }
        job.join()

        val history = memory.messagesForRequest()
        val toolMsg = history.find { it.role == "tool" && it.toolCallId == "call_ask_custom" }
        assertTrue("Tool 结果中应包含用户的自定义补充", toolMsg?.content?.contains("补充: 重点查一号库位") == true)
    }
}
