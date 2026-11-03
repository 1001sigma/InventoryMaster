package com.example.inventorymaster.batchscanner.core.util

import android.graphics.Rect
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 增强版空间物理去重工具。
 * 基于 IoU + 中心点欧氏距离双重校验，支持 displayValue 短路保护。
 */
object BarcodeDeduplicator {

    /**
     * 计算两个 Rect 之间的交并比 (IoU)
     */
    fun calculateIoU(box1: Rect, box2: Rect): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        val intersectionWidth = max(0, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        if (unionArea <= 0) return 0f
        return intersectionArea.toFloat() / unionArea.toFloat()
    }

    /**
     * 增强版去重：IoU + 中心点欧氏距离 + displayValue 短路保护
     *
     * @param barcodes 金字塔两层扫描出的所有结果
     * @param iouThreshold IoU 阈值（适当放宽，如 0.4）
     * @param centerDistanceRatio 中心点距离占框宽度的最大容忍比例（如 0.3 即 30%）
     */
    fun deduplicate(
        barcodes: List<GlobalBarcode>,
        iouThreshold: Float = 0.4f,
        centerDistanceRatio: Float = 0.3f
    ): List<GlobalBarcode> {
        val uniqueBarcodes = mutableListOf<GlobalBarcode>()

        // 优先保留面积更大的框，大框通常包含更完整的静空区和细节
        val sortedBarcodes = barcodes.sortedByDescending {
            it.globalBoundingBox.width() * it.globalBoundingBox.height()
        }

        for (candidate in sortedBarcodes) {
            var isDuplicate = false

            for (existing in uniqueBarcodes) {
                // 短路保护：displayValue 不同 → 强制保留，防止物理重叠的不同条码被误杀
                if (candidate.displayValue != existing.displayValue) continue

                val candidateBox = candidate.globalBoundingBox
                val existingBox = existing.globalBoundingBox

                // 1. 计算 IoU
                val iou = calculateIoU(candidateBox, existingBox)

                // 2. 计算中心点欧氏距离
                val dx = candidateBox.centerX().toFloat() - existingBox.centerX().toFloat()
                val dy = candidateBox.centerY().toFloat() - existingBox.centerY().toFloat()
                val centerDistance = hypot(dx, dy)

                // 3. 动态距离阈值（以两个框中较小的宽度为基准）
                val referenceWidth = min(candidateBox.width(), existingBox.width())
                val maxAllowedDistance = referenceWidth * centerDistanceRatio

                // 4. 双重校验：IoU 足够高 OR 中心点非常近 → 判定为重复
                if (iou > iouThreshold || centerDistance < maxAllowedDistance) {
                    isDuplicate = true
                    break
                }
            }

            if (!isDuplicate) {
                uniqueBarcodes.add(candidate)
            }
        }

        return uniqueBarcodes
    }
}
