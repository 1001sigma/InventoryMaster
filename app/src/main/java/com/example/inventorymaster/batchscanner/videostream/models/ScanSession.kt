package com.example.inventorymaster.batchscanner.videostream.models

import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import java.util.UUID

/**
 * 扫描检查点 — 每完成一列自动保存
 */
data class ScanCheckpoint(
    val columnIndex: Int,
    val timestamp: Long,
    val coveragePercent: Float,
    val totalCount: Int
)

/**
 * 扫描配置（硬编码默认值，后续接入设置页）
 */
data class ScanConfig(
    val gridCols: Int = 8,
    val gridRows: Int = 12,
    val confidenceThreshold: Int = 80,
    val trackerTtlMs: Long = 800,
    val maxMissFrames: Int = 10,
    val warmUpFrames: Int = 5
)

/**
 * 扫描统计
 */
data class ScanStatistics(
    var totalCount: Int = 0,
    var coveragePercent: Float = 0f,
    var scanDurationMs: Long = 0,
    var trackerCount: Int = 0,
    var columnCount: Int = 0
)

/**
 * 扫描会话 — 业务状态（永久）
 *
 * 设计原则：
 * - Session 负责业务状态，Tracker 负责视觉状态
 * - Pause 后保留：GridMap、CountResult、Statistics、CoveredArea
 * - Resume 后保留：同上
 * - Resume 后清空：TrackerPool、DirectionDetector、velocityY
 */
data class ScanSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,

    // === 永久数据（Pause/Resume 后保留） ===
    val gridCells: MutableMap<String, GridCellData> = mutableMapOf(),
    val countedBarcodes: MutableList<GlobalBarcode> = mutableListOf(),
    val statistics: ScanStatistics = ScanStatistics(),
    val checkpoints: MutableList<ScanCheckpoint> = mutableListOf(),
    val config: ScanConfig = ScanConfig()
) {
    // === 临时数据（Resume 后清空重建，标记为 transient） ===
    @Transient
    val trackerPool: MutableMap<Long, ActiveTracker> = mutableMapOf()

    @Transient
    var currentDirection: ScanDirection = ScanDirection.IDLE

    @Transient
    var warmUpFrameCount: Int = 0

    /**
     * Resume：保留业务状态，清空视觉状态
     */
    fun resume() {
        trackerPool.clear()
        currentDirection = ScanDirection.IDLE
        warmUpFrameCount = config.warmUpFrames
    }

    /**
     * 完全重置
     */
    fun reset() {
        gridCells.clear()
        countedBarcodes.clear()
        statistics.reset()
        checkpoints.clear()
        trackerPool.clear()
        currentDirection = ScanDirection.IDLE
        warmUpFrameCount = 0
        endTime = null
    }
}

/**
 * 网格单元格数据（持久化到 ScanSession）
 */
data class GridCellData(
    var visited: Boolean = false,
    var visitCount: Int = 0,
    var lastVisitTime: Long = 0L,
    var coverageRatio: Float = 0f
)

/**
 * ScanStatistics 扩展
 */
fun ScanStatistics.reset() {
    totalCount = 0
    coveragePercent = 0f
    scanDurationMs = 0
    trackerCount = 0
    columnCount = 0
}
