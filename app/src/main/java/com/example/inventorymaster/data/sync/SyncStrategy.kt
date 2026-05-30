package com.example.inventorymaster.data.sync

/**
 * 同步策略接口。
 * 四种同步类型（全量上/增量上/全量下/增量下）各自实现此接口。
 */
interface SyncStrategy {
    /**
     * 执行同步操作。
     * @return 成功时返回 Result.success(描述信息)，失败时返回 Result.failure(异常)
     */
    suspend fun execute(context: SyncContext): Result<String>
}

/**
 * 同步类型枚举，供 SyncOrchestrator 路由到对应策略。
 */
enum class SyncType {
    FULL_UPLOAD,
    INCREMENTAL_UPLOAD,
    FULL_DOWNLOAD,
    INCREMENTAL_DOWNLOAD
}
