package com.example.inventorymaster.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同步协调器（Phase 3）
 *
 * 职责：
 * - 持有四种同步策略的注册表
 * - 通过 Mutex 串行化所有同步请求，替代 ViewModel 中的 isSyncRunning 简陋锁
 * - 将 SyncType + SyncContext 路由到正确的 Strategy
 */
class SyncOrchestrator {

    private val mutex = Mutex()
    private val strategies = mutableMapOf<SyncType, SyncStrategy>()

    /**
     * 注册同步策略。
     */
    fun register(type: SyncType, strategy: SyncStrategy) {
        strategies[type] = strategy
    }

    /**
     * 执行指定类型的同步操作。
     * 所有同步请求在此串行化：同一时刻只有一个同步操作在运行。
     */
    suspend fun execute(type: SyncType, context: SyncContext): Result<String> {
        return mutex.withLock {
            val strategy = strategies[type]
                ?: return@withLock Result.failure(
                    IllegalStateException("未注册的同步类型: $type")
                )
            strategy.execute(context)
        }
    }
}
