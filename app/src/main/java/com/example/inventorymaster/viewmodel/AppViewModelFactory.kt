package com.example.inventorymaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository

class AppViewModelFactory(
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 如果系统想要 SessionViewModel
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(repository) as T
        }
        // 如果系统想要 InventoryViewModel
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            return InventoryViewModel(repository,settingsRepository) as T
        }
        // 如果系统想要 SettingsViewModel
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository) as T
        }
        // 接管创建的 SyncViewModel
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            return SyncViewModel(repository) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类")
    }
}