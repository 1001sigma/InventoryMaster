package com.example.inventorymaster.batchscanner.task.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeTrackerTest {

    @Test
    fun testSingleBarcodeStationaryOverMultipleFrames_onlyCountedOnce() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            lostTimeoutNs = 700_000_000L
        )

        var ts = 1_000_000_000L // 1.0s
        val frameIntervalNs = 33_333_333L // ~30 fps

        // 连续 20 帧检测到同一位置的码
        for (i in 1..20) {
            val det = Detection(value = "0106912345678901", cx = 500f, cy = 500f, size = 80f)
            tracker.update(ts, listOf(det))
            ts += frameIntervalNs
        }

        // 第 2 帧触发计数，后续 18 帧绝不再计
        assertEquals(1, countedTracks.size)
        assertEquals("0106912345678901", countedTracks[0].value)
    }

    @Test
    fun testGhostBarcodeSingleFrame_notCounted() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2
        )

        // 第 1 帧闪现一个误检码
        val ts1 = 1_000_000_000L
        tracker.update(ts1, listOf(Detection("GHOST_CODE", 100f, 100f, 50f)))

        // 此时 hits=1 < minHits(2)，不计
        assertEquals(0, countedTracks.size)

        // 第 2 帧该码消失
        val ts2 = ts1 + 33_000_000L
        tracker.update(ts2, emptyList())

        // 依然不计
        assertEquals(0, countedTracks.size)
    }

    @Test
    fun testTwoSameContentBarcodesDistinctPositions_countedTwice() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            matchRadiusRatio = 0.8f
        )

        var ts = 1_000_000_000L
        val interval = 33_000_000L

        // 画面中有两个物理码，内容完全相同，但位置相距 300px（大于 size * 0.8 = 64px）
        val det1 = Detection(value = "SAME_REAGENT_LOT", cx = 200f, cy = 400f, size = 80f)
        val det2 = Detection(value = "SAME_REAGENT_LOT", cx = 600f, cy = 400f, size = 80f)

        // 帧 1：发现两码（hits=1）
        tracker.update(ts, listOf(det1, det2))
        assertEquals(0, countedTracks.size)

        // 帧 2：稳定存在（hits=2）
        ts += interval
        tracker.update(ts, listOf(det1, det2))

        // 两码均稳定，应各计一次，总计 2
        assertEquals(2, countedTracks.size)
        assertEquals("SAME_REAGENT_LOT", countedTracks[0].value)
        assertEquals("SAME_REAGENT_LOT", countedTracks[1].value)
        assertTrue(countedTracks[0].id != countedTracks[1].id)
    }

    @Test
    fun testSequentialPanningAcrossSameContentBarcodes_eachCountedOnce() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            matchRadiusRatio = 0.8f,
            lostTimeoutNs = 500_000_000L // 0.5s
        )

        var ts = 1_000_000_000L
        val interval = 50_000_000L // 50ms

        // 1. 先扫第 1 个试剂盒（位置 150px）持续 5 帧
        for (i in 1..5) {
            val d1 = Detection("IVD_BOX_A", cx = 150f, cy = 300f, size = 70f)
            tracker.update(ts, listOf(d1))
            ts += interval
        }
        assertEquals(1, countedTracks.size)

        // 2. 镜头平移至第 2 个相同内容的试剂盒（位置 450px，超出匹配半径 70*0.8=56px）
        for (i in 1..5) {
            val d2 = Detection("IVD_BOX_A", cx = 450f, cy = 300f, size = 70f)
            tracker.update(ts, listOf(d2))
            ts += interval
        }
        // 应识别为第二个独立实物码，总计 2
        assertEquals(2, countedTracks.size)

        // 3. 镜头平移至第 3 个试剂盒（位置 750px）
        for (i in 1..5) {
            val d3 = Detection("IVD_BOX_A", cx = 750f, cy = 300f, size = 70f)
            tracker.update(ts, listOf(d3))
            ts += interval
        }
        assertEquals(3, countedTracks.size)
    }

    @Test
    fun testTemporaryOcclusionWithinLostTimeout_notReCounted() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            lostTimeoutNs = 700_000_000L // 700ms 容忍
        )

        var ts = 1_000_000_000L
        val d = Detection("ITEM_1", cx = 300f, cy = 300f, size = 60f)

        // 正常扫到并计数 (2 帧)
        tracker.update(ts, listOf(d))
        ts += 33_000_000L
        tracker.update(ts, listOf(d))
        assertEquals(1, countedTracks.size)

        // 短暂手部遮挡或漏帧 3 帧（约 100ms < 700ms）
        for (i in 1..3) {
            ts += 33_000_000L
            tracker.update(ts, emptyList())
        }

        // 重新露出原码
        ts += 33_000_000L
        tracker.update(ts, listOf(d))

        // 此时旧 Track 未超时销毁，重新匹配成功，绝不重复计数
        assertEquals(1, countedTracks.size)
    }

    @Test
    fun testLostTimeoutExceeded_reCountedAsNewTrack() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            lostTimeoutNs = 700_000_000L // 700ms
        )

        var ts = 1_000_000_000L
        val d = Detection("ITEM_1", cx = 300f, cy = 300f, size = 60f)

        // 正常扫到并计数
        tracker.update(ts, listOf(d))
        ts += 33_000_000L
        tracker.update(ts, listOf(d))
        assertEquals(1, countedTracks.size)

        // 移开相机很久（1.5 秒 > 700ms），旧 Track 被清理
        ts += 1_500_000_000L
        tracker.update(ts, emptyList())

        // 1.5 秒后重新对准该码（相当于重新进入画面）
        ts += 33_000_000L
        tracker.update(ts, listOf(d))
        ts += 33_000_000L
        tracker.update(ts, listOf(d))

        // 因为已超过丢失超时窗口，旧实物轨迹已消亡，建立新轨迹计入
        assertEquals(2, countedTracks.size)
    }

    @Test
    fun testResetClearsAllTracks() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2
        )

        val d = Detection("ITEM_1", cx = 300f, cy = 300f, size = 60f)
        tracker.update(1_000_000_000L, listOf(d))
        tracker.update(1_033_000_000L, listOf(d))
        assertEquals(1, countedTracks.size)

        // 重置
        tracker.reset()
        assertTrue(tracker.getActiveTracks().isEmpty())

        // 重置后立即再送入同一码，重新建立 Track 并能再次稳定计数
        tracker.update(1_066_000_000L, listOf(d))
        tracker.update(1_099_000_000L, listOf(d))
        assertEquals(2, countedTracks.size)
    }

    @Test
    fun testSamePositionReplacementAfterMissingFrames_countedAsNew() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            countedLostTimeoutNs = 300_000_000L // 300ms
        )

        var ts = 1_000_000_000L
        val interval = 33_000_000L // ~30fps
        val d = Detection("SAME_IVD_LOT", cx = 400f, cy = 400f, size = 70f)

        // 1. 扫到第 1 盒试剂并计数
        tracker.update(ts, listOf(d))
        ts += interval
        tracker.update(ts, listOf(d))
        assertEquals(1, countedTracks.size)

        // 2. 拿走第 1 盒试剂：连续 9 帧（~300ms）无码
        for (i in 1..9) {
            ts += interval
            tracker.update(ts, emptyList())
        }

        // 3. 在同一位置放上第 2 盒一模一样的试剂
        ts += interval
        tracker.update(ts, listOf(d))
        ts += interval
        tracker.update(ts, listOf(d))

        // 此时应成功识别为换货新件，总计数为 2
        assertEquals(2, countedTracks.size)
        assertTrue(countedTracks[0].id != countedTracks[1].id)
    }

    @Test
    fun testBriefOcclusionWithinTwoFrames_doesNotReCount() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            countedLostTimeoutNs = 300_000_000L
        )

        var ts = 1_000_000_000L
        val interval = 33_000_000L
        val d = Detection("SAME_IVD_LOT", cx = 400f, cy = 400f, size = 70f)

        // 1. 扫到第 1 盒试剂并计数
        tracker.update(ts, listOf(d))
        ts += interval
        tracker.update(ts, listOf(d))
        assertEquals(1, countedTracks.size)

        // 2. 手指轻微晃动遮挡仅 2 帧（~66ms < 300ms，未达实物离场阈值）
        for (i in 1..2) {
            ts += interval
            tracker.update(ts, emptyList())
        }

        // 3. 重新露出原试剂
        ts += interval
        tracker.update(ts, listOf(d))
        ts += interval
        tracker.update(ts, listOf(d))

        // 仍为同一实物，绝不重复计数，总计数保持 1
        assertEquals(1, countedTracks.size)
    }

    @Test
    fun testRapidSequentialReplacement_countedEachTime() {
        val countedTracks = mutableListOf<Track>()
        val tracker = CodeTracker(
            onCounted = { countedTracks.add(it) },
            minHits = 2,
            countedLostTimeoutNs = 300_000_000L
        )

        var ts = 1_000_000_000L
        val interval = 33_000_000L
        val d = Detection("IDENTICAL_REAGENT", cx = 350f, cy = 350f, size = 80f)

        // 连续测试原地换 3 盒相同试剂
        for (boxIndex in 1..3) {
            // 放上并稳定 2 帧计件
            tracker.update(ts, listOf(d))
            ts += interval
            tracker.update(ts, listOf(d))
            ts += interval
            assertEquals(boxIndex, countedTracks.size)

            // 拿走该盒：空挡 10 帧（~330ms）
            for (emptyFrame in 1..10) {
                ts += interval
                tracker.update(ts, emptyList())
            }
        }

        assertEquals(3, countedTracks.size)
    }
}

