package com.example.inventorymaster.batchscanner.task.tracking

import kotlin.math.hypot

/**
 * 图像单帧中检测到的条码几何信息
 *
 * 所有坐标均为正向（upright）分析图像坐标系下的像素值
 *
 * @property value 条码原始文本 (rawValue)
 * @property cx 条码几何中心 X 坐标（由 4 点角点平均计算得出）
 * @property cy 条码几何中心 Y 坐标（由 4 点角点平均计算得出）
 * @property size 条码特征尺寸（通常为四边平均长度或对角线长）
 */
data class Detection(
    val value: String,
    val cx: Float,
    val cy: Float,
    val size: Float
)

/**
 * 跟踪轨迹对象，代表画面中的一个物理实物码
 *
 * @property id 全局递增的唯一轨迹编号
 * @property value 条码原始文本 (rawValue)
 * @property cx 当前中心 X 坐标
 * @property cy 当前中心 Y 坐标
 * @property size 当前特征尺寸
 * @property lastTs 最近一次被检测到的图像时间戳（纳秒，来自 ImageInfo.timestamp）
 * @property hits 累计连续被匹配到的帧数（用于防单帧幽灵码）
 * @property counted 是否已经完成计数（每个实物 Track 保证仅计数一次）
 */
class Track(
    val id: Long,
    val value: String,
    var cx: Float,
    var cy: Float,
    var size: Float,
    var lastTs: Long,
    var hits: Int = 1,
    var counted: Boolean = false,
    var missingFrames: Int = 0 // 连续未匹配到的帧数（用于区分短暂手抖遮挡与实物撤换）
)

/**
 * 动态多目标条码跟踪器 (CodeTracker)
 *
 * 核心设计目标：
 * 解决 IVD 试剂/耗材场景下“不同实物码内容完全相同，无法按内容去重”的痛点。
 * 通过在正向分析图像坐标系内建立空间位置与运动关联，做到：
 * 1. 同一实物码在画面中停留不重复计数；
 * 2. 镜头平移扫过下一个同内容实物码时，独立识别并完成计数；
 * 3. 滤除单帧闪现的幽灵码 (minHits 机制)；
 * 4. 容忍短暂遮挡或漏帧 (3 帧以内容忍)；
 * 5. 实物撤换（离场超过 300ms 或 8 帧）后，同位置再次出现的同内容码正常作为新件计入。
 *
 * @property onCounted 首次稳定确认并计数的物理 Track 回调（保证每个 Track 只触发一次）
 * @property minHits 判定为真实稳定实物所需的最小命中帧数（默认 2 帧，有效过滤单帧噪点）
 * @property lostTimeoutNs 未计数轨迹丢失多长时间后销毁（默认 700ms = 700_000_000 纳秒）
 * @property countedLostTimeoutNs 已计数的轨迹离场多长时间后销毁（默认 200ms = 200_000_000 纳秒，精准支持连续移动与原地换盒）
 * @property matchRadiusRatio 候选匹配半径与自身尺寸的比例（默认 1.2，容纳手持微抖）
 * @property imgWidth 图像宽度限制（可选，超出画面边缘适当范围提前清理）
 * @property imgHeight 图像高度限制（可选，超出画面边缘适当范围提前清理）
 */
class CodeTracker(
    private val onCounted: (Track) -> Unit,
    private val minHits: Int = 2,
    private val lostTimeoutNs: Long = 700_000_000L,
    private val countedLostTimeoutNs: Long = 200_000_000L,
    private val matchRadiusRatio: Float = 1.2f,
    private val imgWidth: Float = 0f,
    private val imgHeight: Float = 0f
) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 1L

    /** 额外的轨迹计数监听器（用于 UI 高亮联动等） */
    var onCountedListener: ((Track) -> Unit)? = null

    /**
     * 重置所有跟踪状态（换任务、切换去重模式或重新打开弹窗时调用）
     */
    fun reset() {
        synchronized(tracks) {
            tracks.clear()
            nextId = 1L
        }
    }

    /**
     * 获取当前活跃的轨迹列表快照（只读，供调试或 UI 绘制）
     */
    fun getActiveTracks(): List<Track> = synchronized(tracks) { tracks.toList() }

    /**
     * 逐帧更新跟踪器
     *
     * @param ts 当前帧图像纳秒时间戳（imageProxy.imageInfo.timestamp）
     * @param dets 当前帧中检测到的所有有效条码几何信息
     * @param globalDx 相机造成的画面整体平移量 X（上一帧 -> 这一帧，v1 无位移估计时传 0f）
     * @param globalDy 相机造成的画面整体平移量 Y（上一帧 -> 这一帧，v1 无位移估计时传 0f）
     */
    fun update(
        ts: Long,
        dets: List<Detection>,
        globalDx: Float = 0f,
        globalDy: Float = 0f
    ) {
        val newlyCounted = mutableListOf<Track>()

        synchronized(tracks) {
            // 1. 预测：所有 track（包含本帧可能暂时被遮挡的）整体平移
            if (globalDx != 0f || globalDy != 0f) {
                for (t in tracks) {
                    t.cx += globalDx
                    t.cy += globalDy
                }
            }

            // 2. 候选配对：同内容 && 空间欧式距离 <= 匹配半径
            data class MatchPair(val t: Track, val d: Detection, val dist: Float)
            val pairs = ArrayList<MatchPair>()

            for (t in tracks) {
                for (d in dets) {
                    if (t.value != d.value) continue
                    val dist = hypot(t.cx - d.cx, t.cy - d.cy)
                    val maxRadius = t.size * matchRadiusRatio
                    if (dist <= maxRadius) {
                        pairs += MatchPair(t, d, dist)
                    }
                }
            }

            // 贪心一对一匹配：按距离升序排列，最近的优先锁定
            pairs.sortBy { it.dist }
            val usedTracks = HashSet<Track>()
            val usedDetections = HashSet<Detection>()

            for (pair in pairs) {
                if (pair.t in usedTracks || pair.d in usedDetections) continue
                usedTracks += pair.t
                usedDetections += pair.d

                // 成功匹配：更新 Track 的当前位置、尺寸、时间戳与命中计数
                pair.t.cx = pair.d.cx
                pair.t.cy = pair.d.cy
                pair.t.size = pair.d.size
                pair.t.lastTs = ts
                pair.t.hits++
            }

            // 更新所有轨迹的连续未匹配帧数 (missingFrames)
            for (t in tracks) {
                if (t in usedTracks) {
                    t.missingFrames = 0
                } else {
                    t.missingFrames++
                }
            }

            // 3. 没匹配上的检测 -> 初始化为新 Track（暂不计数，等待稳定帧数）
            for (d in dets) {
                if (d !in usedDetections) {
                    tracks += Track(
                        id = nextId++,
                        value = d.value,
                        cx = d.cx,
                        cy = d.cy,
                        size = d.size,
                        lastTs = ts,
                        hits = 1,
                        counted = false,
                        missingFrames = 0
                    )
                }
            }

            // 4. 清理失活的 Track：
            // - 已计数 Track：离场超过 countedLostTimeoutNs（默认 200ms）或连续缺失 ≥ 4 帧（约 130ms），即可判定原物已移开并销毁轨迹，允许后续换盒被正常计入
            // - 未计数 Track：保留 lostTimeoutNs（默认 700ms）丢失超时
            // - 或显著移出画面边缘
            tracks.removeAll { t ->
                val isTimeout = if (t.counted) {
                    (ts - t.lastTs) > countedLostTimeoutNs || t.missingFrames >= 4
                } else {
                    (ts - t.lastTs) > lostTimeoutNs
                }
                val isFarOutOfBounds = if (imgWidth > 0f && imgHeight > 0f) {
                    t.cx < -t.size || t.cx > imgWidth + t.size ||
                    t.cy < -t.size || t.cy > imgHeight + t.size
                } else false
                isTimeout || isFarOutOfBounds
            }

            // 5. 稳定出现 minHits 帧才确认计数，且每个 Track 终身仅计一次
            for (t in tracks) {
                if (!t.counted && t.hits >= minHits) {
                    t.counted = true
                    newlyCounted += t
                }
            }
        }

        // 在锁作用域外分发业务回调，避免外部死锁风险
        for (t in newlyCounted) {
            onCounted(t)
            onCountedListener?.invoke(t)
        }
    }
}
