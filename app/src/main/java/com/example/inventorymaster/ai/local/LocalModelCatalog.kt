package com.example.inventorymaster.ai.local

/**
 * 本地模型元数据目录（模型文件获取的唯一事实源）。
 *
 * 新增模型 = 补一条 [LocalModel] + （可选）ProviderPresets 预设；
 * [LocalModel.modelId] 必须与 `AiProviderConfig.model` 对齐（设置页按它匹配下载/导入），
 * [LocalModel.fileName] 是 ModelFileManager 模型目录内的落盘文件名。
 * sha256 取自 HF LFS oid——App 内下载后强校验；手动导入仅校验大小（用户侧文件可能被网盘转存）。
 */
object LocalModelCatalog {

    data class LocalModel(
        val modelId: String,
        val displayName: String,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String,
        /** 建议最低设备内存（App 运行时占用 ≈ 文件体积 + KV cache） */
        val minRamGb: Int = 6
    )

    val models: List<LocalModel> = listOf(
        LocalModel(
            modelId = "gemma-4-e2b",
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        ),
        LocalModel(
            modelId = "qwen3-4b-instruct-2507",
            displayName = "Qwen3 4B Instruct 2507",
            fileName = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/resolve/main/qwen3_4b_instruct_2507_mixed_int4.litertlm",
            sizeBytes = 2_659_057_664L,
            sha256 = "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd"
        )
    )

    fun byId(modelId: String): LocalModel? =
        models.firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }

    fun byFileName(fileName: String): LocalModel? =
        models.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
}
