package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.local.LocalLlmRuntime
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Role
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LocalLlmRuntime 消息映射（纯 Kotlin 部分，不触发引擎加载）：
 * Gemma/Qwen 对话模板只有 user/model 两类轮次，映射规则见 toHistoryMessages。
 */
class LocalLlmRuntimeMessageMappingTest {

    private fun textOf(message: com.google.ai.edge.litertlm.Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun roleOf(message: com.google.ai.edge.litertlm.Message): Role = message.role

    @Test
    fun `system 轮被跳过`() {
        val result = LocalLlmRuntime.toHistoryMessages(
            listOf(AiMessage(role = "system", content = "系统提示"), AiMessage(role = "user", content = "你好"))
        )
        assertEquals(1, result.size)
        assertEquals(Role.USER, roleOf(result[0]))
        assertEquals("你好", textOf(result[0]))
    }

    @Test
    fun `tool 结果映射为 user 轮并加前缀`() {
        val result = LocalLlmRuntime.toHistoryMessages(
            listOf(AiMessage(role = "tool", content = "共 3 条记录"))
        )
        assertEquals(1, result.size)
        assertEquals(Role.USER, roleOf(result[0]))
        assertEquals("工具结果：\n共 3 条记录", textOf(result[0]))
    }

    @Test
    fun `连续同角色轮合并`() {
        val result = LocalLlmRuntime.toHistoryMessages(
            listOf(
                AiMessage(role = "tool", content = "结果A"),
                AiMessage(role = "tool", content = "结果B"),
                AiMessage(role = "assistant", content = "回答")
            )
        )
        assertEquals(2, result.size)
        assertEquals("工具结果：\n结果A\n\n工具结果：\n结果B", textOf(result[0]))
        assertEquals(Role.MODEL, roleOf(result[1]))
    }

    @Test
    fun `assistant 带 toolCalls 时还原为 JSON 形态`() {
        val args = JsonObject().apply { addProperty("keyword", "纱布") }
        val result = LocalLlmRuntime.toHistoryMessages(
            listOf(
                AiMessage(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(AiToolCall(id = "json_1", name = "search_products", arguments = args))
                )
            )
        )
        assertEquals(1, result.size)
        assertEquals(Role.MODEL, roleOf(result[0]))
        assertTrue(textOf(result[0]).contains(""""tool":"search_products""""))
        assertTrue(textOf(result[0]).contains("纱布"))
    }

    @Test
    fun `空文本与空 toolCalls 的 assistant 轮被跳过`() {
        val result = LocalLlmRuntime.toHistoryMessages(
            listOf(
                AiMessage(role = "assistant", content = ""),
                AiMessage(role = "user", content = "提问")
            )
        )
        assertEquals(1, result.size)
        assertEquals(Role.USER, roleOf(result[0]))
    }
}
