package com.example.inventorymaster.ui.analyzer.multiscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiBarcodeAggregatorTest {

    private lateinit var aggregator: MultiBarcodeAggregator

    @Before
    fun setUp() {
        aggregator = MultiBarcodeAggregator(
            stableMovementThreshold = 0.035f,
            largeMovementThreshold = 0.08f,
            minStableFrames = 2,
            singleCodeConfirmationNs = 500_000_000L,
            retentionNs = 1_200_000_000L,
            emptyResetFrameThreshold = 20
        )
    }

    @Test
    fun testMultiCodeStationaryTriggersFreezeAfterTwoStableFrames() {
        val t0 = 1_000_000_000L
        // 帧 1：初次捕获到两个码（首帧无历史对比，未形成稳定态）
        val frame1 = listOf(
            DetectedBarcode("0106970000000001", 0.30f, 0.40f, t0),
            DetectedBarcode("0106970000000002", 0.60f, 0.40f, t0)
        )
        val decision1 = aggregator.update(frame1, t0)
        assertTrue("帧 1 尚在构建历史，应为 Idle", decision1 is AggregationDecision.Idle)
        assertEquals(2, aggregator.currentCount)

        // 帧 2：手持稳定，微弱抖动 0.001f <= 0.035f，达成第 1 帧稳定
        val t1 = t0 + 33_000_000L
        val frame2 = listOf(
            DetectedBarcode("0106970000000001", 0.301f, 0.401f, t1),
            DetectedBarcode("0106970000000002", 0.601f, 0.399f, t1)
        )
        val decision2 = aggregator.update(frame2, t1)
        assertTrue("帧 2 仅第 1 帧稳定，门槛为 2 帧，应为 Idle", decision2 is AggregationDecision.Idle)

        // 帧 3：继续保持手持稳定，达成连续 2 帧稳定，立即触发定格
        val t2 = t1 + 33_000_000L
        val frame3 = listOf(
            DetectedBarcode("0106970000000001", 0.300f, 0.400f, t2),
            DetectedBarcode("0106970000000002", 0.600f, 0.400f, t2)
        )
        val decision3 = aggregator.update(frame3, t2)
        assertTrue("帧 3 达成连续 2 帧稳定，必须触发 Freeze", decision3 is AggregationDecision.Freeze)
        val frozen = decision3 as AggregationDecision.Freeze
        assertEquals(2, frozen.barcodes.size)
        // 触发定格后聚合器应自动复位
        assertEquals(0, aggregator.currentCount)
    }

    @Test
    fun testMovingPhoneSuppressesFreezeUntilStopped() {
        var t = 1_000_000_000L
        // 连续移动帧（每次位移 0.05 > 0.035f）
        for (i in 0 until 5) {
            val posX = 0.1f + i * 0.05f
            val frame = listOf(
                DetectedBarcode("CODE_A", posX, 0.3f, t),
                DetectedBarcode("CODE_B", posX + 0.3f, 0.3f, t)
            )
            val decision = aggregator.update(frame, t)
            assertTrue("移动中严禁触发定格，当前步 $i", decision is AggregationDecision.Idle)
            t += 33_000_000L
        }

        // 停稳第 1 帧（位移 0.001 <= 0.035f）
        val frameStop1 = listOf(
            DetectedBarcode("CODE_A", 0.301f, 0.3f, t),
            DetectedBarcode("CODE_B", 0.601f, 0.3f, t)
        )
        val decStop1 = aggregator.update(frameStop1, t)
        assertTrue("停稳第 1 帧不触发", decStop1 is AggregationDecision.Idle)

        // 停稳第 2 帧
        t += 33_000_000L
        val frameStop2 = listOf(
            DetectedBarcode("CODE_A", 0.301f, 0.301f, t),
            DetectedBarcode("CODE_B", 0.601f, 0.301f, t)
        )
        val decStop2 = aggregator.update(frameStop2, t)
        assertTrue("停稳第 2 帧立即触发定格", decStop2 is AggregationDecision.Freeze)
    }

    @Test
    fun testMultiFramePuzzleAccumulationAcrossFrames() {
        var t = 1_000_000_000L
        // 模拟密集多码试剂场景：单帧由于反光只扫出一部分
        // 帧 1：只识别出 A, B
        val frame1 = listOf(
            DetectedBarcode("CODE_A", 0.2f, 0.2f, t),
            DetectedBarcode("CODE_B", 0.5f, 0.2f, t)
        )
        aggregator.update(frame1, t)

        // 帧 2：只识别出 B, C（手持静止，B 的位移为 0.001）
        t += 33_000_000L
        val frame2 = listOf(
            DetectedBarcode("CODE_B", 0.501f, 0.201f, t),
            DetectedBarcode("CODE_C", 0.8f, 0.2f, t)
        )
        aggregator.update(frame2, t)

        // 帧 3：只识别出 A, C（手持静止）
        t += 33_000_000L
        val frame3 = listOf(
            DetectedBarcode("CODE_A", 0.201f, 0.201f, t),
            DetectedBarcode("CODE_C", 0.801f, 0.201f, t)
        )
        val decision = aggregator.update(frame3, t)

        assertTrue("跨帧拼图聚合后稳定应触发 Freeze", decision is AggregationDecision.Freeze)
        val frozen = decision as AggregationDecision.Freeze
        assertEquals("A、B、C 三个码应全部汇聚入定格列表中", 3, frozen.barcodes.size)
        val codeSet = frozen.barcodes.map { it.rawValue }.toSet()
        assertTrue(codeSet.contains("CODE_A"))
        assertTrue(codeSet.contains("CODE_B"))
        assertTrue(codeSet.contains("CODE_C"))
    }

    @Test
    fun testSingleCodeConfirmedAfterConfirmationWindow() {
        val t0 = 1_000_000_000L
        val code = listOf(DetectedBarcode("SINGLE_CODE", 0.5f, 0.5f, t0))
        aggregator.update(code, t0)

        // 稳定对准经过 100ms
        val t1 = t0 + 100_000_000L
        val dec1 = aggregator.update(listOf(DetectedBarcode("SINGLE_CODE", 0.501f, 0.501f, t1)), t1)
        assertTrue(dec1 is AggregationDecision.Idle)

        val tFinal = t0 + 510_000_000L
        val decFinal = aggregator.update(listOf(DetectedBarcode("SINGLE_CODE", 0.501f, 0.501f, tFinal)), tFinal)
        assertTrue("对准单码稳定满 500ms 后应按单码直接返回", decFinal is AggregationDecision.SingleScan)
        assertEquals("SINGLE_CODE", (decFinal as AggregationDecision.SingleScan).rawValue)
    }

    @Test
    fun testSingleCodeMovingResetsConfirmationTimer() {
        var t = 1_000_000_000L
        // 初始看到单码
        aggregator.update(listOf(DetectedBarcode("SINGLE_CODE", 0.2f, 0.5f, t)), t)

        // 持续移动（每次位移 0.05 > 0.035f）持续 600ms
        for (i in 1..20) {
            t += 30_000_000L
            val pos = 0.2f + i * 0.05f
            val dec = aggregator.update(listOf(DetectedBarcode("SINGLE_CODE", pos, 0.5f, t)), t)
            assertTrue("移动中单码倒计时必须被抑制，不得误判单码跳出", dec is AggregationDecision.Idle)
        }
    }

    @Test
    fun testEmptyFramesResetAfterThreshold() {
        val t0 = 1_000_000_000L
        val frame = listOf(DetectedBarcode("CODE_A", 0.5f, 0.5f, t0))
        aggregator.update(frame, t0)
        assertEquals(1, aggregator.currentCount)

        // 连续 20 帧空帧
        var t = t0
        for (i in 1..20) {
            t += 33_000_000L
            aggregator.update(emptyList(), t)
        }

        assertEquals("连续 20 帧完全丢失目标，必须彻底清空重置聚合池", 0, aggregator.currentCount)
    }

    @Test
    fun testLargeMovementPurgesStaleCodes() {
        val t0 = 1_000_000_000L
        // 区域 1：扫到 Code 1
        aggregator.update(listOf(DetectedBarcode("CODE_AREA_1", 0.1f, 0.1f, t0)), t0)
        assertEquals(1, aggregator.currentCount)

        // 镜头大跨度平移（位移 0.35 > 0.08f），进入区域 2
        val t1 = t0 + 50_000_000L
        val frame2 = listOf(
            DetectedBarcode("CODE_AREA_1", 0.45f, 0.1f, t1), // 大幅度移动
            DetectedBarcode("CODE_AREA_2", 0.8f, 0.8f, t1)
        )
        aggregator.update(frame2, t1)

        // 大幅移动后，立即淘汰未在当前帧出现的历史旧码
        val t2 = t1 + 33_000_000L
        val frame3 = listOf(DetectedBarcode("CODE_AREA_2", 0.801f, 0.801f, t2))
        aggregator.update(frame3, t2)

        // CODE_AREA_1 已不在视野中，且此前经历了大幅平移
        assertEquals("大幅平移后历史旧码已被淘汰", 1, aggregator.currentCount)
    }

    @Test
    fun testMultiCodeSceneNeverPrematurelyExitsAsSingleScan() {
        var t = 1_000_000_000L
        // 帧 1：移动中扫到两码 A 和 B（处于移动态，位移较大，不会直接定格）
        val frame1 = listOf(
            DetectedBarcode("CODE_A", 0.1f, 0.5f, t),
            DetectedBarcode("CODE_B", 0.4f, 0.5f, t)
        )
        aggregator.update(frame1, t)

        // 帧 2：继续移动，B 移出画面（位移 0.12 > 0.08f 被淘汰），只剩下 A 移动中
        t += 33_000_000L
        val frame2 = listOf(
            DetectedBarcode("CODE_A", 0.16f, 0.5f, t),
            DetectedBarcode("CODE_B", 0.52f, 0.5f, t) // B 大幅度位移淘汰
        )
        aggregator.update(frame2, t)
        // 此时 pool 仅剩 CODE_A，但 poolPeakSize 曾经为 2！
        assertEquals(1, aggregator.currentCount)

        // 随后在 CODE_A 处停稳并保持超过 600ms
        for (i in 1..20) {
            t += 33_000_000L
            val frameOnlyA = listOf(DetectedBarcode("CODE_A", 0.161f, 0.501f, t))
            val dec = aggregator.update(frameOnlyA, t)
            // 铁律：曾见过多码（poolPeakSize > 1），即使当前池内只剩 1 码且稳定对准，也绝不能作为 SingleScan 提前弹出！
            assertTrue("多码场景绝不允许 SingleScan 提前退出", dec !is AggregationDecision.SingleScan)
        }

        // 当 CODE_B 重新进入视野时，由于此前手持已对准稳定且池内重新恢复多码，即刻触发定格
        t += 33_000_000L
        val frameBoth = listOf(
            DetectedBarcode("CODE_A", 0.161f, 0.501f, t),
            DetectedBarcode("CODE_B", 0.461f, 0.501f, t)
        )
        val decBoth = aggregator.update(frameBoth, t)
        assertTrue("多码重新出现且手持稳定，即刻触发 Freeze", decBoth is AggregationDecision.Freeze)
    }
}
