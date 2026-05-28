package com.example.inventorymaster.data.model

import androidx.room.Embedded
import com.example.inventorymaster.data.entity.InventorySession

data class SessionWithProgress(
    @Embedded val session: InventorySession,
    val totalCount: Int,
    val verifiedCount: Int
) {
    val progress: Float
        get() = if (totalCount > 0) verifiedCount.toFloat() / totalCount else 0f

    val percentageText: String
        get() = "${(progress * 100).toInt()}%"
}
