package com.example.inventorymaster.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.UserSettings
import com.example.inventorymaster.data.model.RecordCardConfig
import com.example.inventorymaster.data.model.RecordCardSlot
import com.example.inventorymaster.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val inventoryRepository: InventoryRepository  // M2：绑定操作员走盘库服务端
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

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _aiProviders.value = settings.getAiProviders()
                _activeProviderId.value = settings.activeProviderId
            }
        }
    }

    fun setSeedColor(colorValue: Long) {
        viewModelScope.launch { repository.updateSeedColor(colorValue) }
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

    fun setNearExpiryThreshold(days: Int) {
        viewModelScope.launch { repository.updateNearExpiryThreshold(days) }
    }

    fun setUseOpenCV(enable: Boolean) {
        viewModelScope.launch { repository.updateUseOpenCV(enable) }
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

    fun setRealtimePushEnabled(enable: Boolean) {
        viewModelScope.launch { repository.updateRealtimePushEnabled(enable) }
    }

    fun setInventoryPushEnabled(enable: Boolean) {
        viewModelScope.launch { repository.updateInventoryPushEnabled(enable) }
    }

    fun setAiShowThinking(enable: Boolean) {
        viewModelScope.launch { repository.updateAiShowThinking(enable) }
    }

    /** M2（R1）：扫码/手动输入绑定码 → 绑定设备与操作员；成功后操作员姓名自动入本地设置 */
    fun bindOperator(bindingCode: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            inventoryRepository.bindOperator(bindingCode)
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: "绑定失败") }
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
}