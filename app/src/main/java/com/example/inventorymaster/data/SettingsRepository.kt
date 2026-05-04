package com.example.inventorymaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 定义 DataStore 的扩展属性，单例模式
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

// 定义数据模型：用于在 App 中传递设置状态
data class UserSettings(
    val seedColor: Long = 0xFF4CAF50, // 默认绿色
    val useDynamicColor: Boolean = false, // 默认关闭动态取色
    val isAmoledMode: Boolean = false,    // 默认关闭纯黑模式
    val themeMode: Int = 0, // 0:跟随系统, 1:浅色, 2:深色
    // --- 扫码设置 (默认值写在这里) ---
    val isSimpleMode: Boolean = false,      // 简单分词模式
    val showOcrMask: Boolean = true,        // OCR 遮罩
    val enableMultiScan: Boolean = false,    // 多码模式
    // 👇 新增：DI 校验开关状态
    val enableDiValidation: Boolean = false
)

class SettingsRepository(private val context: Context) {

    // 定义存储的 Key (键名)
    private object Keys {
        val SEED_COLOR = longPreferencesKey("seed_color")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val IS_AMOLED = booleanPreferencesKey("is_amoled")
        val THEME_MODE = intPreferencesKey("theme_mode")
        // 【新增】扫码的 Key
        val IS_SIMPLE_MODE = booleanPreferencesKey("is_simple_mode")
        val SHOW_OCR_MASK = booleanPreferencesKey("show_ocr_mask")
        val ENABLE_MULTI_SCAN = booleanPreferencesKey("enable_multi_scan")
        // 👇 新增：DI 校验的 Key
        val ENABLE_DI_VALIDATION = booleanPreferencesKey("enable_di_validation")

        // ============================================================
        // 🆕 网络同步相关 Key（原 SharedPreferences 迁移至 DataStore）
        // ============================================================
        // 服务器 IP 地址
        val SERVER_IP = stringPreferencesKey("server_ip")
    }

    // 1. 读取数据 (暴露为一个 Flow，这样数据一变，UI 就能收到通知)
    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .map { preferences ->
            UserSettings(
                seedColor = preferences[Keys.SEED_COLOR] ?: 0xFF4CAF50,
                useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: false,
                isAmoledMode = preferences[Keys.IS_AMOLED] ?: false,
                themeMode = preferences[Keys.THEME_MODE] ?: 0,
                // 【新增】读取扫码设置
                isSimpleMode = preferences[Keys.IS_SIMPLE_MODE] ?: false,
                showOcrMask = preferences[Keys.SHOW_OCR_MASK] ?: true,
                enableMultiScan = preferences[Keys.ENABLE_MULTI_SCAN] ?: false,
                // 👇 新增：读取 DI 校验开关状态
                enableDiValidation = preferences[Keys.ENABLE_DI_VALIDATION] ?: false
            )
        }

    // 2. 写入数据的方法
    suspend fun updateSeedColor(color: Long) {
        context.dataStore.edit { it[Keys.SEED_COLOR] = color }
    }

    suspend fun updateDynamicColor(useDynamic: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = useDynamic }
    }

    suspend fun updateAmoledMode(isAmoled: Boolean) {
        context.dataStore.edit { it[Keys.IS_AMOLED] = isAmoled }
    }

    // themeMode: 0=System, 1=Light, 2=Dark
    suspend fun updateThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    // --- 【新增】扫码设置的写入方法 ---
    suspend fun updateSimpleMode(isSimple: Boolean) {
        context.dataStore.edit { it[Keys.IS_SIMPLE_MODE] = isSimple }
    }

    suspend fun updateOcrMask(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_OCR_MASK] = show }
    }

    suspend fun updateMultiScan(enable: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_MULTI_SCAN] = enable }
    }

    suspend fun updateDiValidation(enable: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_DI_VALIDATION] = enable }
    }

    // ============================================================
    // 🆕 网络同步相关方法（原 SharedPreferences 迁移至 DataStore）
    // ============================================================

    /**
     * 保存服务器 IP 地址到 DataStore
     * @param ip 服务器 IP 地址
     */
    suspend fun saveServerIp(ip: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SERVER_IP] = ip
        }
    }

    /**
     * 从 DataStore 读取服务器 IP 地址
     * @return 保存的 IP 地址，如果没有则返回空字符串
     */
    suspend fun getServerIp(): String {
        return context.dataStore.data.map { preferences ->
            preferences[Keys.SERVER_IP] ?: ""
        }.first()
    }

    /**
     * 保存指定任务的同步时间戳到 DataStore
     * 用于增量同步时记录上次拉取的时间点，避免重复拉取
     * @param sessionId 任务 ID
     * @param timestamp 同步时间戳（毫秒）
     */
    suspend fun saveLastSyncTime(sessionId: Long, timestamp: Long) {
        val key = stringPreferencesKey("last_sync_time_$sessionId")
        context.dataStore.edit { preferences ->
            preferences[key] = timestamp.toString()
        }
    }

    /**
     * 读取指定任务的同步时间戳
     * @param sessionId 任务 ID
     * @return 上次同步时间戳，如果没有则返回 0L
     */
    suspend fun getLastSyncTime(sessionId: Long): Long {
        val key = stringPreferencesKey("last_sync_time_$sessionId")
        return context.dataStore.data.map { preferences ->
            preferences[key]?.toLongOrNull() ?: 0L
        }.first()
    }
}
