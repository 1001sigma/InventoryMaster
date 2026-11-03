package com.example.inventorymaster.batchscanner.core.util

import androidx.compose.ui.graphics.Color
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.utils.Gs1Parser

/**
 * 条码颜色与物料分类枚举
 */
enum class BarcodeColorCategory {
    MAJORITY_STANDARD,   // 数量最多的物料 + 主批次 (淡蓝)
    MAJORITY_DIFF_BATCH, // 数量最多的物料 + 混批 (淡红)
    OTHER_MATERIAL       // 数量最多的物料之外的异种物料 (淡黄)
}

/**
 * 分类处理后的条码封装
 */
data class ClassifiedBarcode(
    val barcode: GlobalBarcode,
    val index: Int, // 1-based 序号
    val di: String?,
    val batch: String?,
    val category: BarcodeColorCategory
)

/**
 * 主题视觉色值定义
 */
object BarcodeThemeColors {
    // 淡蓝 (MAJORITY_STANDARD)
    val BlueBorder = Color(0xFF1E88E5)
    val BlueFill = Color(0xFF2196F3).copy(alpha = 0.16f)
    val BlueCardBg = Color(0xFFE3F2FD)
    val BlueCardBorder = Color(0xFF90CAF9)
    val BlueText = Color(0xFF1565C0)

    // 淡红 (MAJORITY_DIFF_BATCH)
    val RedBorder = Color(0xFFE53935)
    val RedFill = Color(0xFFF44336).copy(alpha = 0.16f)
    val RedCardBg = Color(0xFFFFEBEE)
    val RedCardBorder = Color(0xFFEF9A9A)
    val RedText = Color(0xFFC62828)

    // 淡黄 (OTHER_MATERIAL)
    val YellowBorder = Color(0xFFFFA000)
    val YellowFill = Color(0xFFFFB300).copy(alpha = 0.18f)
    val YellowCardBg = Color(0xFFFFF8E1)
    val YellowCardBorder = Color(0xFFFFE082)
    val YellowText = Color(0xFFB78103)
}

/**
 * 单图条码分类器
 */
object BarcodeClassifier {
    /**
     * 根据单图条码集合，按物料及批次频次进行三色分类判定：
     * 1. 统计各物料（DI）频次，最多的为基准物料 majorityDi；
     * 2. 在 majorityDi 中统计各批号（Batch）频次，最多的为基准批号 majorityBatch；
     * 3. 判定规则：
     *    - 同物料且同主批次 -> MAJORITY_STANDARD (淡蓝)
     *    - 同物料但不同批号 -> MAJORITY_DIFF_BATCH (淡红)
     *    - 不同物料 -> OTHER_MATERIAL (淡黄)
     */
    fun classify(barcodes: List<GlobalBarcode>): List<ClassifiedBarcode> {
        if (barcodes.isEmpty()) return emptyList()

        val parsedItems = barcodes.mapIndexed { idx, barcode ->
            val parseResult = Gs1Parser.parse(barcode.displayValue)
            val di = (parseResult.di ?: barcode.displayValue).trim()
            val batch = (parseResult.batch ?: "").trim()
            Triple(barcode, idx + 1, Pair(di, batch))
        }

        // 1. 统计 DI 频次，找到数量最多的物料
        val diCounts = parsedItems.groupingBy { it.third.first }.eachCount()
        val majorityDi = diCounts.maxByOrNull { it.value }?.key

        // 2. 在 majorityDi 范围内统计各批号频次，找到最多的批号
        val majorityBatchCounts = parsedItems
            .filter { it.third.first == majorityDi }
            .groupingBy { it.third.second }
            .eachCount()
        val majorityBatch = majorityBatchCounts.maxByOrNull { it.value }?.key

        // 3. 生成分类结果
        return parsedItems.map { (barcode, index, pair) ->
            val (di, batch) = pair
            val category = when {
                majorityDi == null -> BarcodeColorCategory.MAJORITY_STANDARD
                di == majorityDi && batch == majorityBatch -> BarcodeColorCategory.MAJORITY_STANDARD
                di == majorityDi && batch != majorityBatch -> BarcodeColorCategory.MAJORITY_DIFF_BATCH
                else -> BarcodeColorCategory.OTHER_MATERIAL
            }
            ClassifiedBarcode(
                barcode = barcode,
                index = index,
                di = di.ifBlank { null },
                batch = batch.ifBlank { null },
                category = category
            )
        }
    }
}
