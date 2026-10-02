package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.ModelCapabilityCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型级能力目录测试（AI_Runtime Phase 6：能力跟模型走，用户开关永远优先） */
class ModelCapabilityCatalogTest {

    @Test
    fun `已知强模型家族解析为全能力`() {
        assertTrue(ModelCapabilityCatalog.forModel("qwen-plus").functionCalling)
        assertTrue(ModelCapabilityCatalog.forModel("deepseek-chat").functionCalling)
        assertTrue(ModelCapabilityCatalog.forModel("gpt-4o").functionCalling)
        assertTrue(ModelCapabilityCatalog.forModel("gemini-2.0-flash").functionCalling)
    }

    @Test
    fun `推理系模型保守关闭函数调用`() {
        val caps = ModelCapabilityCatalog.forModel("deepseek-reasoner")
        assertFalse(caps.functionCalling)
        assertTrue(caps.streaming)
    }

    @Test
    fun `未知模型按保守默认_FC关_流式开`() {
        val caps = ModelCapabilityCatalog.forModel("mystery-model-v9")
        assertFalse(caps.functionCalling)
        assertTrue(caps.streaming)
    }

    @Test
    fun `用户显式关闭永远优先于目录`() {
        val config = AiProviderConfig(
            name = "qwen", baseUrl = "https://x/v1",
            model = "qwen-plus", supportsTools = false
        )
        val caps = ModelCapabilityCatalog.resolve(config)
        assertFalse(caps.functionCalling)
        assertTrue(caps.streaming)
    }

    @Test
    fun `本地占位引擎双关`() {
        val config = AiProviderConfig(
            name = "gemma", baseUrl = "",
            model = "gemma-4-e2b", isLocal = true
        )
        val caps = ModelCapabilityCatalog.resolve(config)
        assertFalse(caps.functionCalling)
        assertFalse(caps.streaming)
    }

    @Test
    fun `解析结果与目录和开关一致`() {
        val config = AiProviderConfig(
            name = "ollama", baseUrl = "http://localhost:11434/v1",
            model = "llama3", supportsTools = true
        )
        val caps = ModelCapabilityCatalog.resolve(config)
        val expected = ModelCapabilityCatalog.forModel("llama3")
        assertEquals(expected.functionCalling, caps.functionCalling)
        assertEquals(expected.streaming, caps.streaming)
    }
}
