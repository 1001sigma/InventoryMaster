package com.example.inventorymaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // 将 Repository 的 Flow 转换为 UI 可以观察的 StateFlow
    val uiState: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings() // 初始值
        )

    // 提供给 UI 调用的方法
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
}