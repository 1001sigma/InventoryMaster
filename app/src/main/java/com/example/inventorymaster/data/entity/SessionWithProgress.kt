package com.example.inventorymaster.data.entity

import androidx.room.Embedded

data class SessionWithProgress(
    @Embedded val session: InventorySession,
    val totalCount: Int,
    val verifiedCount: Int
) {
    // 辅助计算百分比 (0.0 - 1.0)
    val progress: Float
        get() = if (totalCount > 0) verifiedCount.toFloat() / totalCount else 0f

    // 格式化百分比文字
    val percentageText: String
        get() = "${(progress * 100).toInt()}%"
}