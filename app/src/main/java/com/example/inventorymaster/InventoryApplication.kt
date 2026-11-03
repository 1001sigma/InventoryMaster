package com.example.inventorymaster

import android.app.Application
import com.example.inventorymaster.ai.local.LocalLlmRuntime
import com.example.inventorymaster.ai.local.ModelFileManager
import com.example.inventorymaster.data.AppDatabase
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.connection.ServerConnectionManager
import com.example.inventorymaster.data.sync.TimestampManager
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.data.repository.InventoryRepositoryImpl
import com.example.inventorymaster.ocr.OcrProvider

class InventoryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OcrProvider.init(this)
        LocalLlmRuntime.init(this)
    }
    // 1. 初始化数据库 (lazy 表示用到时再创建，节省启动资源)
    val database by lazy { AppDatabase.getDatabase(this) }

    // 2. 初始化设置仓库 (DataStore)，统一管理所有配置
    val settingsRepository by lazy { SettingsRepository(this) }

    // 本地大模型文件管理（下载/导入/删除，models 目录），供设置页与 ViewModel 使用
    val modelFileManager by lazy { ModelFileManager(this) }

    // Phase 2: 统一时间戳管理器（双锚点：pull / push）
    val timestampManager by lazy { TimestampManager(this) }

    // 服务端连接管理器：缓存 IP → 探测 → UDP 自动发现（全局弹窗），见 data/connection/
    val connectionManager by lazy { ServerConnectionManager(this, settingsRepository) }

    // 3. 初始化仓库 (Repository)，供 ViewModel 使用
    // [重构] 用 SettingsRepository 替代 SharedPreferences，统一配置管理
    val repository: InventoryRepository by lazy {
        InventoryRepositoryImpl(
            productDao = database.productDao(),
            sessionDao = database.sessionDao(),
            stockRecordDao = database.stockRecordDao(),
            settingsRepository = settingsRepository,
            timestampManager = timestampManager,
            database = database,
            connectionManager = connectionManager
        )
    }
}
