package com.example.inventorymaster.data.model

import com.example.inventorymaster.data.entity.InventorySession

/**
 * 服务端任务"待开始"派生状态（不新增 status 枚举值，避免波及 countOngoingSessions/归档上报等逻辑）：
 * 仅服务端下发（sessionSource=1）且未归档未锁定（status=0）且服务端尚未点开始（startedAt 为空）时成立。
 * 本地任务 sessionSource=0 恒为 false，行为与现状完全一致。
 */
val InventorySession.isPendingStart: Boolean
    get() = sessionSource == 1 && status == 0 && startedAt == null
