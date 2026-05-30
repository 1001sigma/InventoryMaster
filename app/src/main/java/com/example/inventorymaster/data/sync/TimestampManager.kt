package com.example.inventorymaster.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.inventorymaster.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.max

/**
 * 统一时间戳管理器 (Phase 2)
 *
 * 职责：
 * - 为每个 session 维护【拉取锚点】和【推送锚点】两个独立的时间戳
 * - 所有锚点更新均内置防倒退逻辑：始终取 max(current, new)
 * - 与增量下载配合，查询边界由 > 改为 >=，配合 UUID 去重避免毫秒边界遗漏
 * - 首次读取时自动从旧 key (string) 迁移到新 key (long)
 */
class TimestampManager(private val context: Context) {

    /**
     * 获取指定 session 的拉取锚点（毫秒）
     * 0L 表示从未拉取过，将触发全量拉取。
     * 首次调用时自动迁移旧格式的时间戳。
     */
    suspend fun getLastPullAnchor(sessionId: Long): Long {
        val newKey = longPreferencesKey("last_pull_anchor_$sessionId")
        val current = context.dataStore.data.map { it[newKey] ?: 0L }.first()
        if (current > 0L) return current

        // 尝试从旧 key 迁移
        val oldKey = stringPreferencesKey("last_sync_time_$sessionId")
        val oldValue = context.dataStore.data.map { it[oldKey] }.first()
        val migrated = oldValue?.toLongOrNull() ?: 0L
        if (migrated > 0L) {
            context.dataStore.edit { prefs ->
                prefs[newKey] = migrated
                prefs.remove(oldKey)
            }
        }
        return migrated
    }

    /**
     * 更新拉取锚点，内置防倒退逻辑
     */
    suspend fun saveLastPullAnchor(sessionId: Long, timestamp: Long) {
        val current = getLastPullAnchor(sessionId)
        val safe = max(current, timestamp)
        if (safe > current) {
            val key = longPreferencesKey("last_pull_anchor_$sessionId")
            context.dataStore.edit { it[key] = safe }
        }
    }

    /**
     * 获取指定 session 的推送锚点（毫秒）
     */
    suspend fun getLastPushAnchor(sessionId: Long): Long {
        val key = longPreferencesKey("last_push_anchor_$sessionId")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }

    /**
     * 更新推送锚点，内置防倒退逻辑
     */
    suspend fun saveLastPushAnchor(sessionId: Long, timestamp: Long) {
        val current = getLastPushAnchor(sessionId)
        val safe = max(current, timestamp)
        if (safe > current) {
            val key = longPreferencesKey("last_push_anchor_$sessionId")
            context.dataStore.edit { it[key] = safe }
        }
    }
}
