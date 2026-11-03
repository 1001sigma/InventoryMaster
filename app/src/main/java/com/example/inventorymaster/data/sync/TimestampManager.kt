package com.example.inventorymaster.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.inventorymaster.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.max

/**
 * 增量同步游标管理器（M0 基建：时间戳 → change_seq）
 *
 * 职责：
 * - 为每个 session 维护【拉取游标】= 服务端 change_seq 单调序号（records/products/deletion_log 共用序号空间）
 * - 锚点更新内置防倒退逻辑：始终取 max(current, new)
 * - 历史 last_sync_time / last_pull_anchor 存的是毫秒时间戳，与 seq 单位不兼容，
 *   故不迁移：新 key 默认 0，升级后首次拉取为超集交付（幂等 upsert，无害）
 */
class TimestampManager(private val context: Context) {

    /**
     * 获取指定 session 的拉取游标（服务端 change_seq）
     * 0L 表示从未拉取过，将触发全量拉取。
     */
    suspend fun getLastPullSeq(sessionId: Long): Long {
        val key = longPreferencesKey("last_pull_seq_$sessionId")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }

    /**
     * 更新拉取游标，内置防倒退逻辑
     */
    suspend fun saveLastPullSeq(sessionId: Long, seq: Long) {
        val current = getLastPullSeq(sessionId)
        val safe = max(current, seq)
        if (safe > current) {
            val key = longPreferencesKey("last_pull_seq_$sessionId")
            context.dataStore.edit { it[key] = safe }
        }
    }

    /**
     * 获取全局产品库拉取游标（服务端 change_seq）
     * 0L 表示从未拉取过，将触发全量拉取。
     */
    suspend fun getLastProductPullSeq(): Long {
        val key = longPreferencesKey("last_product_pull_seq")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }

    /**
     * 更新全局产品库拉取游标，内置防倒退逻辑
     */
    suspend fun saveLastProductPullSeq(seq: Long) {
        val current = getLastProductPullSeq()
        val safe = max(current, seq)
        if (safe > current) {
            val key = longPreferencesKey("last_product_pull_seq")
            context.dataStore.edit { it[key] = safe }
        }
    }

    /**
     * 重置全局产品库拉取游标（例如本地产品库被全部清空后，允许重新全量拉取）
     */
    suspend fun resetProductPullSeq() {
        val key = longPreferencesKey("last_product_pull_seq")
        context.dataStore.edit { it[key] = 0L }
    }
}
