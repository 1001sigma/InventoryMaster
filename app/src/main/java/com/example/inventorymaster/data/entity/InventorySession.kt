package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_sessions")
data class InventorySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,         // 例如 "2023-10月盘点"
    val date: Long,      // 创建时间 (存毫秒数)
    val isLocked: Boolean = false, // 是否锁定
    // 👇 [新增] 状态字段
    // 0 = 进行中 (可改, 可删)
    // 1 = 已归档 (只读, 可删)
    // 2 = 已锁定 (只读, 不可删)
    val status: Int = 0
)