package com.example.inventorymaster.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.local.LocalModelCatalog
import com.example.inventorymaster.ai.local.ModelFileManager
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.UserSettings
import com.example.inventorymaster.data.model.RecordCardConfig
import com.example.inventorymaster.data.model.RecordCardSlot
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.ui.theme.ColorTuple
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val inventoryRepository: InventoryRepository,  // M2：绑定操作员走盘库服务端
    private val modelFileManager: ModelFileManager  // 本地模型下载/导入（LiteRT-LM）
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    private val _modelList = MutableStateFlow<List<String>>(emptyList())
    val modelList: StateFlow<List<String>> = _modelList.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _aiProviders = MutableStateFlow<List<AiProviderConfig>>(emptyList())
    val aiProviders: StateFlow<List<AiProviderConfig>> = _aiProviders.asStateFlow()

    private val _activeProviderId = MutableStateFlow("")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _isSettingsLoaded = MutableStateFlow(false)
    val isSettingsLoaded: StateFlow<Boolean> = _isSettingsLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.first()
            _isSettingsLoaded.value = true
        }
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _aiProviders.value = settings.getAiProviders()
                _activeProviderId.value = settings.activeProviderId
            }
        }
    }

    fun setColorTuple(tuple: ColorTuple) {
        viewModelScope.launch { repository.updateColorTuple(tuple) }
    }

    fun addCustomColorTuple(tuple: ColorTuple) {
        viewModelScope.launch { repository.addCustomColorTuple(tuple) }
    }

    fun removeCustomColorTuple(tuple: ColorTuple) {
        viewModelScope.launch { repository.removeCustomColorTuple(tuple) }
    }

    fun setSeedColor(colorValue: Long) {
        val argb = colorValue.toInt()
        val matchPreset = ModernColorSchemes.allSchemes.find { it.primary.toArgb() == argb }
        val tuple = matchPreset?.asColorTuple() ?: ColorTuple(Color(argb))
        setColorTuple(tuple)
    }

    fun setDynamicColor(enable: Boolean) {
        viewModelScope.launch { repository.updateDynamicColor(enable) }
    }

    fun setAmoledMode(enable: Boolean) {
        viewModelScope.launch { repository.updateAmoledMode(enable) }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun setServerIp(ip: String) {
        viewModelScope.launch { repository.updateServerIp(ip) }
    }

    fun setServerToken(token: String) {
        viewModelScope.launch {
            repository.saveServerToken(token)
            inventoryRepository.saveToken(token)
        }
    }

    fun verifyAndSaveToken(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ip = repository.getServerIp()
            val result = inventoryRepository.verifyAndSaveToken(ip, token)
            result.fold(
                onSuccess = { onResult(true, it) },
                onFailure = { onResult(false, it.message ?: "验证失败") }
            )
        }
    }

    fun setWatermarkText(text: String) {
        viewModelScope.launch { repository.updateWatermarkText(text) }
    }

    fun setWatermarkOpacity(opacity: Float) {
        viewModelScope.launch { repository.updateWatermarkOpacity(opacity) }
    }

    fun setWatermarkAngle(angle: Float) {
        viewModelScope.launch { repository.updateWatermarkAngle(angle) }
    }

    fun setWatermarkFontSize(size: Float) {
        viewModelScope.launch { repository.updateWatermarkFontSize(size) }
    }

    fun setWatermarkColor(color: Long) {
        viewModelScope.launch { repository.updateWatermarkColor(color) }
    }

    fun setWatermarkDensity(density: Int) {
        viewModelScope.launch { repository.updateWatermarkDensity(density) }
    }

    fun setSignatureOffsetX(x: Float) {
        viewModelScope.launch { repository.updateSignatureOffsetX(x) }
    }

    fun setSignatureOffsetY(y: Float) {
        viewModelScope.launch { repository.updateSignatureOffsetY(y) }
    }

    fun setSignatureScale(scale: Float) {
        viewModelScope.launch { repository.updateSignatureScale(scale) }
    }

    fun setSignatureOpacity(opacity: Float) {
        viewModelScope.launch { repository.updateSignatureOpacity(opacity) }
    }

    /** PDF 本地储存位置：SAF 文件夹 tree URI；空字符串 = 清除（恢复每次弹系统选位器） */
    fun setPdfExportDirUri(uri: String) {
        viewModelScope.launch { repository.updatePdfExportDirUri(uri) }
    }

    fun setNearExpiryThreshold(days: Int) {
        viewModelScope.launch { repository.updateNearExpiryThreshold(days) }
    }

    fun setUseOpenCV(enable: Boolean) {
        viewModelScope.launch { repository.updateUseOpenCV(enable) }
    }

    fun setSaveOriginalPhotoToGallery(enable: Boolean) {
        viewModelScope.launch { repository.updateSaveOriginalPhotoToGallery(enable) }
    }

    /** 更换记录卡片某位置显示的字段；字段全卡唯一，被顶掉的原位置自动留空 */
    fun setRecordCardSlotField(slot: RecordCardSlot, fieldId: String) {
        viewModelScope.launch {
            // 从 DataStore 读最新值再裁决，避免 WhileSubscribed 未激活时 uiState 停留在初始值
            val current = repository.settingsFlow.first().recordCardConfig
            repository.updateRecordCardConfig(current.withSlotField(slot, fieldId))
        }
    }

    fun resetRecordCardConfig() {
        viewModelScope.launch { repository.updateRecordCardConfig(RecordCardConfig()) }
    }

    fun setCalcRetainLastResult(retain: Boolean) {
        viewModelScope.launch { repository.updateCalcRetainLastResult(retain) }
    }

    fun setHybiomeMode(enable: Boolean) {
        viewModelScope.launch { repository.updateHybiomeMode(enable) }
    }

    fun setSinkVerifiedItems(enable: Boolean) {
        viewModelScope.launch { repository.updateSinkVerifiedItems(enable) }
    }

    fun setEmptySearchShowsNothing(enable: Boolean) {
        viewModelScope.launch { repository.updateEmptySearchShowsNothing(enable) }
    }

    fun setRealtimePushEnabled(enable: Boolean) {
        viewModelScope.launch { repository.updateRealtimePushEnabled(enable) }
    }

    fun setInventoryPushEnabled(enable: Boolean) {
        viewModelScope.launch { repository.updateInventoryPushEnabled(enable) }
    }

    fun setAiShowThinking(enable: Boolean) {
        viewModelScope.launch { repository.updateAiShowThinking(enable) }
    }

    /** 设置操作者昵称 */
    fun setOperatorName(name: String) {
        viewModelScope.launch {
            repository.saveOperatorName(name.trim())
        }
    }

    // ============================================================
    // 服务商管理方法
    // ============================================================

    fun addAiProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            repository.addAiProvider(provider)
        }
    }

    fun updateAiProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            repository.updateAiProvider(provider)
        }
    }

    fun deleteAiProvider(id: String) {
        viewModelScope.launch {
            repository.deleteAiProvider(id)
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            repository.setActiveProvider(id)
        }
    }

    fun fetchModelsForProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            if (provider.isLocal || provider.modelsEndpoint.isBlank()) return@launch

            _isFetchingModels.value = true
            try {
                val service = CloudAiService(provider)
                _modelList.value = service.fetchModels()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Fetch models failed", e)
                _modelList.value = emptyList()
            } finally {
                _isFetchingModels.value = false
            }
        }
    }

    // ============================================================
    // 本地模型文件管理（下载 / SAF 导入），见 ai/local/LocalModelCatalog
    // ============================================================

    /** 本地模型文件操作状态：一次只有一个模型在下载/导入 */
    data class LocalModelState(
        val busyModelId: String? = null,
        val downloadProgress: Float = 0f,
        /** 完成后的一次性提示（成功/失败文案），UI 消费后调 [clearLocalModelMessage] */
        val message: String? = null,
        val isSuccess: Boolean = false
    )

    private val _localModelState = MutableStateFlow(LocalModelState())
    val localModelState: StateFlow<LocalModelState> = _localModelState.asStateFlow()

    fun downloadLocalModel(modelId: String) {
        val entry = LocalModelCatalog.byId(modelId) ?: return
        if (_localModelState.value.busyModelId != null) return
        _localModelState.value = LocalModelState(busyModelId = modelId)
        viewModelScope.launch {
            modelFileManager.downloadModel(
                modelId = entry.modelId,
                url = entry.downloadUrl,
                expectedSha256 = entry.sha256
            ) { progress ->
                _localModelState.value = _localModelState.value.copy(downloadProgress = progress)
            }.onSuccess {
                _localModelState.value = LocalModelState(
                    message = "${entry.displayName} 下载完成（${entry.sizeBytes / (1024 * 1024)}MB）",
                    isSuccess = true
                )
            }.onFailure { e ->
                Log.e("SettingsViewModel", "Download local model failed", e)
                _localModelState.value = LocalModelState(
                    message = "下载失败：${e.message ?: "网络异常"}（已支持断点续传，可重试或改用本地导入）",
                    isSuccess = false
                )
            }
        }
    }

    fun importLocalModel(modelId: String, uri: Uri) {
        if (_localModelState.value.busyModelId != null) return
        val entry = LocalModelCatalog.byId(modelId)
        _localModelState.value = LocalModelState(busyModelId = modelId)
        viewModelScope.launch {
            modelFileManager.importModel(modelId, uri)
                .onSuccess {
                    _localModelState.value = LocalModelState(
                        message = entry?.let { "${it.displayName} 导入成功" } ?: "模型导入成功",
                        isSuccess = true
                    )
                }
                .onFailure { e ->
                    Log.e("SettingsViewModel", "Import local model failed", e)
                    _localModelState.value = LocalModelState(
                        message = "导入失败：${e.message ?: "无法读取所选文件"}",
                        isSuccess = false
                    )
                }
        }
    }

    fun clearLocalModelMessage() {
        if (_localModelState.value.busyModelId == null) {
            _localModelState.value = LocalModelState()
        }
    }

    /** 模型文件是否已在 models 目录就绪（设置页保存 localModelPath / 聊天前置检查用） */
    fun isLocalModelAvailable(modelId: String): Boolean = modelFileManager.isModelAvailable(modelId)

    fun localModelPathFor(modelId: String): String = modelFileManager.getModelPath(modelId)
}