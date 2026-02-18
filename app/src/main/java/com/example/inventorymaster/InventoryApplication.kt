package com.example.inventorymaster

import android.app.Application
import com.example.inventorymaster.data.AppDatabase
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.data.repository.InventoryRepositoryImpl

class InventoryApplication : Application() {
    // 1. 初始化数据库 (lazy 表示用到时再创建，节省启动资源)
    val database by lazy { AppDatabase.getDatabase(this) }

    // 2. 初始化仓库 (Repository)，供 ViewModel 使用
    val repository: InventoryRepository by lazy {
        InventoryRepositoryImpl(
            productDao = database.productDao(),
            sessionDao = database.sessionDao(),
            stockRecordDao = database.stockRecordDao(),
            prefs = getSharedPreferences("inventory_prefs", MODE_PRIVATE)
        )
    }

    val settingsRepository by lazy { SettingsRepository(this) }
}