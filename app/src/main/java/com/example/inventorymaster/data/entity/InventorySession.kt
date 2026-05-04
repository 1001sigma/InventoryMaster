package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_sessions")
data class InventorySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),

    // 1. 业务核心
    val name: String,
    val sessionType: Int = 0, // 0:盘库, 1:核对

    // 2. 时间与状态
    val date: Long = System.currentTimeMillis(),
    val status: Int = 0,      // 0:进行中, 1:已归档, 2:已锁定
    val isLocked: Boolean = false,

    // 3. 数据来源
    val sessionSource: Int = 0,      // 0:本地创建, 1:服务器下发
    val remoteId: String? = null, // 服务器端的原始ID

    // 4. 统计冗余 (可选，为了UI流畅)
    val totalItems: Int = 0,  // 总货位数/单据行数
    val scannedItems: Int = 0, // 已扫描数

    // 5. 预留字段 (仅一个，存JSON用)
    val extJson: String? = null
)