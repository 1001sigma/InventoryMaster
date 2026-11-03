package com.example.inventorymaster.batchscanner.videostream.util

import android.graphics.RectF
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.videostream.models.*
import com.example.inventorymaster.utils.Gs1Parser
import kotlin.math.hypot

/**
 * 追踪器引擎 — 贪心分配 + 双分支记账 + 可信度评分
 */
class TrackerEngine {

    private var nextTrackerId = 1L
    private val countedSerials = mutableSetOf<String>()

    /**
     * 处理一帧检测结果，更新 Session 的 trackerPool
     * @return 新增的已计数条码列表
     */
    fun update(
        session: ScanSession,
        detections: List<FrameDetection>
    ): List<GlobalBarcode> {
        val now = System.currentTimeMillis()
        val newlyCounted = mutableListOf<GlobalBarcode>()

        // 1. 清理过期 Tracker
        cleanupExpiredTrackers(session, now)

        // 2. 贪心分配：严格一对一映射
        val unassignedTrackers = session.trackerPool.values
            .filter { it.state != TrackerState.EXPIRED && it.state != TrackerState.LOCKED }
            .toMutableList()

        // 按 confidence 降序排列检测结果，优先匹配高置信度
        val sortedDetections = detections
            .filter { it.displayValue.isNotEmpty() }
            .sortedByDescending { it.confidence }

        for (det in sortedDetections) {
            val bestMatch = findBestMatch(det, unassignedTrackers)

            if (bestMatch != null) {
                // 匹配成功：更新 Tracker
                updateTracker(bestMatch, det, now)
                unassignedTrackers.remove(bestMatch)
            } else {
                // 未匹配：创建新 Tracker
                val tracker = createTracker(det, now)
                session.trackerPool[tracker.id] = tracker
            }
        }

        // 3. 更新未匹配 Tracker 的 missFrameCount
        for (tracker in unassignedTrackers) {
            tracker.missFrameCount++
        }

        // 4. 更新可信度 + 执行记账
        for (tracker in session.trackerPool.values) {
            if (tracker.state == TrackerState.EXPIRED || tracker.state == TrackerState.LOCKED) continue

            updateConfidence(tracker, session.config.confidenceThreshold)

            // WarmUp 期间不记账
            if (session.warmUpFrameCount > 0) continue

            val counted = tryCount(tracker, session)
            if (counted != null) {
                newlyCounted.add(counted)
                session.countedBarcodes.add(counted)
                session.statistics.totalCount++
            }
        }

        // 5. 递减 WarmUp 计数
        if (session.warmUpFrameCount > 0) {
            session.warmUpFrameCount--
        }

        return newlyCounted
    }

    /**
     * 贪心匹配：找最佳匹配 Tracker（一对一，防劫持）
     */
    private fun findBestMatch(
        detection: FrameDetection,
        candidates: List<ActiveTracker>
    ): ActiveTracker? {
        var bestTracker: ActiveTracker? = null
        var bestScore = 0

        for (tracker in candidates) {
            val score = calculateMatchScore(detection, tracker)
            if (score > bestScore) {
                bestScore = score
                bestTracker = tracker
            }
        }

        return if (bestScore >= 80) bestTracker else null
    }

    /**
     * 计算匹配分数
     */
    private fun calculateMatchScore(detection: FrameDetection, tracker: ActiveTracker): Int {
        var score = 0

        // 内容一致 (+40)
        if (detection.displayValue == tracker.displayValue) {
            score += 40
        } else {
            return 0
        }

        // 空间距离 (+30)
        val detCx = detection.boundingBox.centerX().toFloat()
        val detCy = detection.boundingBox.centerY().toFloat()
        val distance = hypot(
            (detCx - tracker.centerX).toDouble(),
            (detCy - tracker.centerY).toDouble()
        ).toFloat()
        if (distance < 80f) {
            score += 30
        }

        // 大小比例 (+20)
        val detArea = detection.boundingBox.width().toFloat() * detection.boundingBox.height().toFloat()
        val trackerArea = tracker.boundingBox.width() * tracker.boundingBox.height()
        if (trackerArea > 0f) {
            val ratio = minOf(detArea, trackerArea) / maxOf(detArea, trackerArea)
            if (ratio > 0.7f) {
                score += 20
            }
        }

        // 时间接近 (+10)
        val timeDelta = detection.timestamp - tracker.lastSeenTime
        if (timeDelta in 0..300) {
            score += 10
        }

        return score
    }

    /**
     * 更新已有 Tracker
     */
    private fun updateTracker(tracker: ActiveTracker, detection: FrameDetection, now: Long) {
        val prevCenterY = tracker.centerY

        tracker.boundingBox = RectF(detection.boundingBox)
        tracker.centerX = detection.boundingBox.centerX().toFloat()
        tracker.centerY = detection.boundingBox.centerY().toFloat()
        tracker.velocityY = tracker.centerY - prevCenterY
        tracker.lastSeenTime = now
        tracker.frameCount++
        tracker.missFrameCount = 0

        // 更新状态
        if (tracker.state == TrackerState.NEW || tracker.state == TrackerState.LOST) {
            tracker.state = TrackerState.TRACKING
        }
    }

    /**
     * 创建新 Tracker
     */
    private fun createTracker(detection: FrameDetection, now: Long): ActiveTracker {
        val hasSerial = hasSerialNumber(detection.displayValue)
        return ActiveTracker(
            id = nextTrackerId++,
            displayValue = detection.displayValue,
            hasSerial = hasSerial,
            boundingBox = RectF(detection.boundingBox),
            centerX = detection.boundingBox.centerX().toFloat(),
            centerY = detection.boundingBox.centerY().toFloat(),
            velocityY = 0f,
            firstSeenTime = now,
            lastSeenTime = now,
            frameCount = 1,
            missFrameCount = 0,
            state = TrackerState.NEW,
            confidence = 0,
            isCounted = false
        )
    }

    /**
     * 更新可信度评分
     */
    private fun updateConfidence(tracker: ActiveTracker, threshold: Int) {
        var score = 0

        // 连续识别 >= 5 帧 (+20)
        if (tracker.frameCount >= 5) score += 20

        // 位置稳定：centerX/Y 抖动 < 20px (+20)
        // 简化判断：frameCount > 3 且 velocityY 变化不大
        if (tracker.frameCount > 3 && kotlin.math.abs(tracker.velocityY) < 20f) {
            score += 20
        }

        // 运动连续：velocity 方向一致 (+20)
        // 简化判断：velocityY 不为 0 且 state 为 TRACKING
        if (tracker.state == TrackerState.TRACKING && tracker.velocityY != 0f) {
            score += 20
        }

        // 内容一致 (+40) — 始终满足（displayValue 匹配才创建 Tracker）
        score += 40

        tracker.confidence = score.coerceAtMost(100)
    }

    /**
     * 双分支记账
     * @return 如果成功计数，返回 GlobalBarcode；否则 null
     */
    private fun tryCount(tracker: ActiveTracker, session: ScanSession): GlobalBarcode? {
        if (tracker.isCounted) return null
        if (tracker.state == TrackerState.LOCKED) return null

        // 分支 1：有 (21) 序列号 → HashSet 绝对去重
        if (tracker.hasSerial) {
            val serial = extractSerial(tracker.displayValue)
            if (serial != null && !countedSerials.contains(serial)) {
                countedSerials.add(serial)
                tracker.isCounted = true
                tracker.state = TrackerState.COUNTED
                return toGlobalBarcode(tracker)
            }
            return null
        }

        // 分支 2：无序列号 → 可信度 >= 80 才计数
        if (tracker.confidence >= session.config.confidenceThreshold) {
            tracker.isCounted = true
            tracker.state = TrackerState.COUNTED
            return toGlobalBarcode(tracker)
        }

        return null
    }

    /**
     * 清理过期 Tracker
     */
    private fun cleanupExpiredTrackers(session: ScanSession, now: Long) {
        val toRemove = mutableListOf<Long>()

        for ((id, tracker) in session.trackerPool) {
            when {
                tracker.state == TrackerState.EXPIRED -> {
                    toRemove.add(id)
                }
                tracker.missFrameCount >= session.config.maxMissFrames -> {
                    tracker.state = TrackerState.EXPIRED
                    toRemove.add(id)
                }
                now - tracker.lastSeenTime > session.config.trackerTtlMs -> {
                    tracker.state = TrackerState.EXPIRED
                    toRemove.add(id)
                }
            }
        }

        toRemove.forEach { session.trackerPool.remove(it) }
    }

    /**
     * 检查是否含 (21) 序列号
     */
    private fun hasSerialNumber(displayValue: String): Boolean {
        return displayValue.contains("(21)") || displayValue.contains("\u001D21")
    }

    /**
     * 提取序列号
     */
    private fun extractSerial(displayValue: String): String? {
        val result = Gs1Parser.parse(displayValue)
        return result.items["21"]
    }

    /**
     * 转换为 GlobalBarcode
     */
    private fun toGlobalBarcode(tracker: ActiveTracker): GlobalBarcode {
        return GlobalBarcode(
            originalBarcode = null,
            displayValue = tracker.displayValue,
            globalBoundingBox = android.graphics.Rect(
                tracker.boundingBox.left.toInt(),
                tracker.boundingBox.top.toInt(),
                tracker.boundingBox.right.toInt(),
                tracker.boundingBox.bottom.toInt()
            ),
            globalCornerPoints = emptyArray(),
            globalCenterX = tracker.centerX.toInt(),
            globalCenterY = tracker.centerY.toInt(),
            status = com.example.inventorymaster.batchscanner.core.models.ScanStatus.UNKNOWN,
            engine = "video_stream_tracker"
        )
    }

    /**
     * 重置（新 Session 时）
     */
    fun reset() {
        nextTrackerId = 1L
        countedSerials.clear()
    }
}
