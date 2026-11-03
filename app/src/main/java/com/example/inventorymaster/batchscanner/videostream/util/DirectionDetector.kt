package com.example.inventorymaster.batchscanner.videostream.util

import com.example.inventorymaster.batchscanner.videostream.models.ActiveTracker
import com.example.inventorymaster.batchscanner.videostream.models.ScanDirection

/**
 * 方向检测器 — 根据 Tracker 整体运动趋势自动判断扫描方向
 *
 * 核心逻辑：
 * 1. 收集最近 N 帧所有 Tracker 的归一化 Y 轴速度
 * 2. 判断当前列的扫描方向（向上/向下）
 * 3. 检测蛇形换列（方向反转）
 */
class DirectionDetector {

    private val recentVelocities = mutableListOf<Float>()
    private var currentDirection = ScanDirection.IDLE
    private var lastDirectionChangeTime = 0L

    // 归一化速度阈值（velocityRatio = avgVy / imageHeight）
    // 0.005 = 每帧移动 imageHeight 的 0.5%
    private val VELOCITY_THRESHOLD = 0.005f

    /**
     * 更新方向检测
     *
     * @param trackers 当前帧的活跃 Tracker 列表
     * @param imageHeight 图像高度（用于速度归一化）
     */
    fun update(trackers: List<ActiveTracker>, imageHeight: Int) {
        if (trackers.isEmpty() || imageHeight <= 0) return

        // 计算当前帧所有 Tracker 的平均 velocityY，归一化为 imageHeight 的比例
        val avgVy = trackers.map { it.velocityY }.average().toFloat()
        val normalizedVy = avgVy / imageHeight
        recentVelocities.add(normalizedVy)
        if (recentVelocities.size > 30) recentVelocities.removeAt(0)

        // 判断方向（使用归一化阈值）
        val positiveCount = recentVelocities.count { it > VELOCITY_THRESHOLD }
        val negativeCount = recentVelocities.count { it < -VELOCITY_THRESHOLD }
        val now = System.currentTimeMillis()

        val newDirection = when {
            positiveCount > 20 -> ScanDirection.DOWN
            negativeCount > 20 -> ScanDirection.UP
            else -> ScanDirection.IDLE
        }

        // 检测换列：方向反转
        if (newDirection != currentDirection && newDirection != ScanDirection.IDLE) {
            if (now - lastDirectionChangeTime < 1000) {
                // 短时间内方向反转 → 可能是换列
                currentDirection = ScanDirection.RIGHT
            } else {
                currentDirection = newDirection
            }
            lastDirectionChangeTime = now
        } else if (newDirection == ScanDirection.IDLE && currentDirection == ScanDirection.RIGHT) {
            // 换列完成后恢复纵向方向
            currentDirection = newDirection
        }
    }

    /**
     * 获取当前扫描方向
     */
    fun getCurrentDirection(): ScanDirection = currentDirection

    /**
     * 重置（Resume 后调用）
     */
    fun reset() {
        recentVelocities.clear()
        currentDirection = ScanDirection.IDLE
        lastDirectionChangeTime = 0L
    }
}
