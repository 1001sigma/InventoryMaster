package com.example.inventorymaster.ui.analyzer.multiscan

import kotlin.math.hypot

/**
 * 画面中定位到的条码归一化几何数据
 *
 * @property rawValue 条码原始文本内容
 * @property normX 相对 PreviewView 视图归一化坐标 X (0.0 ~ 1.0)
 * @property normY 相对 PreviewView 视图归一化坐标 Y (0.0 ~ 1.0)
 * @property timestampNs 捕获该条码时的系统纳秒时间戳
 */
data class DetectedBarcode(
    val rawValue: String,
    val normX: Float,
    val normY: Float,
    val timestampNs: Long = System.nanoTime()
)

/**
 * 聚合器单帧决策结果
 */
sealed class AggregationDecision {
    /** 尚在静默收集或手持移动中，不触发动作 */
    object Idle : AggregationDecision()

    /** 稳定捕捉到多码，触发微信级 1:1 无缝原地定格选择 */
    data class Freeze(val barcodes: List<DetectedBarcode>) : AggregationDecision()

    /** 稳定确认镜头内仅有单码，触发单码常规直接返回 */
    data class SingleScan(val rawValue: String) : AggregationDecision()
}

/**
 * 内部维护的条码跟踪状态
 */
private data class TrackedBarcodeItem(
    var barcode: DetectedBarcode,
    var stableHitCount: Int = 1,
    var firstSeenTimestampNs: Long,
    var lastSeenTimestampNs: Long
)

/**
 * 独立跨帧多码聚合器 (MultiBarcodeAggregator)
 *
 * 核心设计目标：
 * 1. 解决单帧算力与密集小码瓶颈：单帧由于光影或反光往往只能解出部分码（如 9 盒试剂单帧仅解出 2~3 个），
 *    通过多帧动态拼图累积（retentionNs = 1200ms），自动汇总视野内所有条码；
 * 2. 解决手持移动进入镜头时的“假失效”：手在移动时静默追踪，手停稳的一瞬间才触发定格，彻底消除由于手抖时间差导致的锚点漂移；
 * 3. 彻底杜绝多码场景被单码早退误杀：引入 poolPeakSize 记忆，凡曾探测到多码即锁定多码定格通道，禁止 500ms 单码早退；
 * 4. 彻底解耦，可纯脱机单元测试，便于未来在任何扫码场景（盘库、批量补扫、巡检等）即插即用。
 *
 * @property stableMovementThreshold 判定为“手持静止稳定”的最大归一化位移（默认 0.035，即屏幕宽高的 3.5%，兼容自然手抖）
 * @property largeMovementThreshold 判定为“镜头大幅度平移/换区”的归一化位移门槛（默认 0.08，即屏幕宽高的 8%），触发淘汰位移过大码
 * @property minStableFrames 多码定格所需的连续稳定帧数（默认 2 帧）
 * @property singleCodeConfirmationNs 单码静止确认时长（默认 500ms = 500_000_000L 纳秒）
 * @property retentionNs 跨帧拼图条码保留寿命（默认 1200ms = 1_200_000_000L 纳秒），给足试剂反光拼图时间
 * @property emptyResetFrameThreshold 连续空帧重置门槛（默认 20 帧，约 660ms，确保手持移动不被轻易误清空）
 */
class MultiBarcodeAggregator(
    private val stableMovementThreshold: Float = 0.035f,
    private val largeMovementThreshold: Float = 0.08f,
    private val minStableFrames: Int = 2,
    private val singleCodeConfirmationNs: Long = 500_000_000L,
    private val retentionNs: Long = 1_200_000_000L,
    private val emptyResetFrameThreshold: Int = 20
) {
    private val pool = mutableMapOf<String, TrackedBarcodeItem>()
    private var consecutiveStableFrames = 0
    private var consecutiveEmptyFrames = 0
    // 记录本次观察周期内累积见过的最大条码数量（防止多码场景因反光偶发单码而误触发 SingleScan 退出）
    private var poolPeakSize = 0

    /**
     * 重置聚合器状态
     */
    fun reset() {
        pool.clear()
        consecutiveStableFrames = 0
        consecutiveEmptyFrames = 0
        poolPeakSize = 0
    }

    /**
     * 当前累积池中的去重条码数量
     */
    val currentCount: Int get() = pool.size

    /**
     * 逐帧送入当前帧识别出的条码列表，由聚合器输出当前决策
     *
     * @param frameBarcodes 当前帧内识别出的条码集合（已反算为视图像素归一化坐标）
     * @param frameTimestampNs 当前帧时间戳（纳秒）
     * @return 聚合器当前的行动决策
     */
    fun update(
        frameBarcodes: List<DetectedBarcode>,
        frameTimestampNs: Long = System.nanoTime()
    ): AggregationDecision {
        if (frameBarcodes.isNotEmpty()) {
            consecutiveEmptyFrames = 0

            // 1. 测量跟踪条码的相对位移并更新池子
            var maxMovement = 0f
            var hasTrackedMovement = false

            for (item in frameBarcodes) {
                val existing = pool[item.rawValue]
                if (existing != null) {
                    val dist = hypot(item.normX - existing.barcode.normX, item.normY - existing.barcode.normY)
                    if (dist > maxMovement) maxMovement = dist
                    hasTrackedMovement = true

                    if (dist > largeMovementThreshold) {
                        // 该条码发生大幅度位移（镜头掠过或快速平移），淘汰该条码以防跨区拼图残留
                        pool.remove(item.rawValue)
                    } else {
                        // 更新为最新鲜、最锐利的坐标
                        existing.barcode = item
                        existing.stableHitCount++
                        existing.lastSeenTimestampNs = frameTimestampNs

                        if (dist > stableMovementThreshold) {
                            // 发生微小抖动以上的位移，重置单码稳定计时起点
                            existing.firstSeenTimestampNs = frameTimestampNs
                        }
                    }
                } else {
                    pool[item.rawValue] = TrackedBarcodeItem(
                        barcode = item,
                        stableHitCount = 1,
                        firstSeenTimestampNs = frameTimestampNs,
                        lastSeenTimestampNs = frameTimestampNs
                    )
                }
            }

            // 2. 常规自然过期淘汰（超过 retentionNs 未见到的历史码）
            pool.entries.removeIf { frameTimestampNs - it.value.lastSeenTimestampNs > retentionNs }

            // 维护本次周期的峰值条码数
            if (pool.size > poolPeakSize) {
                poolPeakSize = pool.size
            }

            // 3. 判断手持稳定状态
            // 条件：至少有一个跟踪的码，且最大位移未超出稳定门槛
            val isStationary = if (hasTrackedMovement) {
                maxMovement <= stableMovementThreshold
            } else {
                // 如果本帧全是新码，首帧无法计算位移，需要下一帧再验证位移稳定性
                false
            }

            if (isStationary) {
                consecutiveStableFrames++
            } else {
                consecutiveStableFrames = 0
            }

            // 4. 决策 A：多码稳定触发定格（优先判定）
            // 当池内累积 >= 2 个码，且手持连续稳定 >= minStableFrames
            if (pool.size > 1 && consecutiveStableFrames >= minStableFrames) {
                val snapshot = pool.values.map { it.barcode }.toList()
                reset()
                return AggregationDecision.Freeze(snapshot)
            }

            // 5. 决策 B：单码静止稳定倒计时判定
            // 铁律：只有历史中从未见过多码（poolPeakSize <= 1），且当前视野确实仅有 1 个码并保持稳定时，才允许单码直接返回；
            // 若此前曾见过多码（poolPeakSize > 1），绝不误走单码通道提前跳出，给足拼图与定格机会
            if (pool.size == 1 && isStationary && poolPeakSize <= 1) {
                val singleItem = pool.values.first()
                if (frameTimestampNs - singleItem.firstSeenTimestampNs >= singleCodeConfirmationNs) {
                    val singleValue = singleItem.barcode.rawValue
                    reset()
                    return AggregationDecision.SingleScan(singleValue)
                }
            }

            return AggregationDecision.Idle
        } else {
            // 本帧未检出条码
            consecutiveStableFrames = 0
            consecutiveEmptyFrames++

            // 连续缺失多帧（约 660ms），表明用户将手机完全移离条码区域，彻底重置收集池
            if (consecutiveEmptyFrames >= emptyResetFrameThreshold) {
                reset()
            }

            return AggregationDecision.Idle
        }
    }
}
