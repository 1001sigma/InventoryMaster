package com.example.inventorymaster.ai

import com.example.inventorymaster.ai.local.LocalModelCatalog
import com.example.inventorymaster.ai.provider.ProviderPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地模型目录完整性（LiteRT-LM 接入）：预设里的本地模型必须都能在目录找到
 * 下载元数据，否则设置页下载/导入链路对该模型失效。
 */
class LocalModelCatalogTest {

    @Test
    fun `本地预设模型全部收录于目录`() {
        val localPresetModels = ProviderPresets.presets
            .filter { it.config.isLocal }
            .map { it.config.model }
        assertTrue("至少应有两个本地预设", localPresetModels.size >= 2)
        localPresetModels.forEach { modelId ->
            assertNotNull("预设模型 $modelId 未收录于 LocalModelCatalog", LocalModelCatalog.byId(modelId))
        }
    }

    @Test
    fun `目录条目元数据合法`() {
        LocalModelCatalog.models.forEach { model ->
            assertTrue(model.modelId.isNotBlank())
            assertTrue(model.sizeBytes > 0)
            assertTrue("sha256 应为 64 位十六进制", model.sha256.matches(Regex("[0-9a-fA-F]{64}")))
            assertTrue(
                "downloadUrl 应直指 fileName 所在文件",
                model.downloadUrl.endsWith("/" + model.fileName)
            )
            assertTrue(model.downloadUrl.startsWith("https://"))
            assertTrue(model.minRamGb > 0)
        }
    }

    @Test
    fun `byId 大小写不敏感且未知模型返回 null`() {
        assertNotNull(LocalModelCatalog.byId("GEMMA-4-E2B"))
        assertEquals(null, LocalModelCatalog.byId("gemma-4-e4b"))
    }

    @Test
    fun `modelId 唯一`() {
        val ids = LocalModelCatalog.models.map { it.modelId.lowercase() }
        assertEquals(ids.size, ids.toSet().size)
    }
}
