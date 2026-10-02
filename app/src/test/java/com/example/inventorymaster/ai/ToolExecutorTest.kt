package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.ai.tools.AiTool
import com.example.inventorymaster.ai.tools.ToolErrorKind
import com.example.inventorymaster.ai.tools.ToolExecutor
import com.example.inventorymaster.ai.tools.ToolRegistry
import com.example.inventorymaster.ai.tools.ToolResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具执行器测试：查找→校验→超时→异常归一（docs/AI_Runtime §10） */
class ToolExecutorTest {

    private class SimpleTool(
        override val name: String,
        override val parameters: JsonObject = JsonParser.parseString(
            """{"type":"object","properties":{},"required":["session_id"]}"""
        ).asJsonObject,
        private val result: String = "ok",
        private val delayMs: Long = 0,
        private val throwMessage: String? = null
    ) : AiTool {
        override val description = "simple"
        override suspend fun execute(toolCall: AiToolCall): ToolResult {
            if (delayMs > 0) delay(delayMs)
            throwMessage?.let { throw IllegalStateException(it) }
            return ToolResult.success(toolCall, result)
        }
    }

    private fun toolCall(name: String, args: String = "{}") =
        AiToolCall(id = "c1", name = name, arguments = JsonParser.parseString(args).asJsonObject)

    @Test
    fun `成功结果透传且携带callId`() = runBlocking {
        val registry = ToolRegistry().apply { register(SimpleTool("t1", result = "结果A")) }
        val result = ToolExecutor(registry).execute(toolCall("t1", """{"session_id":1}"""))
        assertTrue(result.success)
        assertEquals("c1", result.callId)
        assertEquals("t1", result.toolName)
        assertEquals("结果A", result.content)
    }

    @Test
    fun `未知工具返回NOT_FOUND并列出可用清单`() = runBlocking {
        val registry = ToolRegistry().apply {
            register(SimpleTool("query_records"))
            register(SimpleTool("list_sessions"))
        }
        val result = ToolExecutor(registry).execute(toolCall("nope"))
        assertFalse(result.success)
        assertEquals(ToolErrorKind.NOT_FOUND, result.error?.kind)
        assertTrue(result.content.contains("未找到工具"))
        assertTrue(result.content.contains("query_records"))
        assertTrue(result.content.contains("list_sessions"))
    }

    @Test
    fun `必填参数缺失被拦截工具不执行`() = runBlocking {
        val registry = ToolRegistry().apply { register(SimpleTool("t1")) }
        val result = ToolExecutor(registry).execute(toolCall("t1"))
        assertFalse(result.success)
        assertEquals(ToolErrorKind.INVALID_ARGUMENTS, result.error?.kind)
        assertTrue(result.content.contains("session_id"))
    }

    @Test
    fun `工具超时归一为TIMEOUT结果`() = runBlocking {
        val registry = ToolRegistry().apply { register(SimpleTool("slow", delayMs = 500)) }
        val result = ToolExecutor(registry, timeoutMs = 50).execute(toolCall("slow", """{"session_id":1}"""))
        assertFalse(result.success)
        assertEquals(ToolErrorKind.TIMEOUT, result.error?.kind)
        assertTrue(result.content.contains("超时"))
    }

    @Test
    fun `工具异常归一为INTERNAL_ERROR不外抛`() = runBlocking {
        val registry = ToolRegistry().apply { register(SimpleTool("boom", throwMessage = "炸了")) }
        val result = ToolExecutor(registry).execute(toolCall("boom", """{"session_id":1}"""))
        assertFalse(result.success)
        assertEquals(ToolErrorKind.INTERNAL_ERROR, result.error?.kind)
        assertTrue(result.content.contains("炸了"))
    }
}
