package com.example.inventorymaster.data.sync

/**
 * 同步上下文，封装单次同步请求所需的环境参数。
 */
data class SyncContext(
    /** 服务端 IP 地址（含端口时由调用方组装为 http://ip:port/ 格式） */
    val ip: String,
    /** 本地盘库任务 ID */
    val sessionId: Long,
)
