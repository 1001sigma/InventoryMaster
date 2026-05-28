package com.example.inventorymaster.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.inventorymaster.batchscanner.InventoryMainViewModel
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository

class AppViewModelFactory(
    private val application: Application,
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(repository) as T
        }
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            return InventoryViewModel(repository, settingsRepository) as T
        }
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository) as T
        }
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            return SyncViewModel(repository) as T
        }
        if (modelClass.isAssignableFrom(InventoryMainViewModel::class.java)) {
            return InventoryMainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类")
    }
}