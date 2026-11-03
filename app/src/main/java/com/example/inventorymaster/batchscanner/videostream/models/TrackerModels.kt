package com.example.inventorymaster.batchscanner.videostream.models

import android.graphics.RectF

/**
 * Tracker 状态机
 */
enum class TrackerState {
    NEW,        // 首次识别
    TRACKING,   // 持续跟踪
    COUNTED,    // 已入账
    LOCKED,     // 已锁定（离开扫描区后永不重新计数，除非 Session 结束）
    LOST,       // 短暂丢失，等待恢复
    EXPIRED     // 超时移除
}

/**
 * 活跃追踪器 — 视觉状态（临时，Resume 后清空重建）
 */
data class ActiveTracker(
    val id: Long,
    val displayValue: String,
    val hasSerial: Boolean,
    var boundingBox: RectF,
    var centerX: Float,
    var centerY: Float,
    var velocityY: Float = 0f,
    val firstSeenTime: Long,
    var lastSeenTime: Long,
    var frameCount: Int = 0,
    var missFrameCount: Int = 0,
    var state: TrackerState = TrackerState.NEW,
    var confidence: Int = 0,
    var isCounted: Boolean = false
)

/**
 * 扫描方向
 */
enum class ScanDirection {
    DOWN,   // 当前列向下
    UP,     // 当前列向上
    RIGHT,  // 正在横向换列
    IDLE    // 静止/未确定
}
