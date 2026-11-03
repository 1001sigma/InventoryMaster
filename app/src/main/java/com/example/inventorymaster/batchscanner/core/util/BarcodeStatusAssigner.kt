package com.example.inventorymaster.batchscanner.core.util

import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.utils.Gs1Parser

/**
 * 条码状态分配工具类
 * 从 BatchScannerViewModel 提取的共用业务逻辑，供 BatchScanner 和 VideoStream 模块共同使用。
 */
object BarcodeStatusAssigner {
    /**
     * 统一对条码赋予状态，并消耗单据额度
     * @param barcodes 待分配状态的条码列表
     * @param remainingTargets 可变的单据目标池（null = 无单据模式，全部 MATCHED）
     * @return 带状态的条码列表
     */
    fun assignStatus(
        barcodes: List<GlobalBarcode>,
        remainingTargets: MutableList<String>?
    ): List<GlobalBarcode> {
        if (remainingTargets == null) {
            // 模式 A：没有提供单据，所有扫出来的码全部视为 MATCHED
            barcodes.forEach { it.status = ScanStatus.MATCHED }
            return barcodes
        }

        // 模式 B：提供了单据，DI（含多候选 DI）+ 批号双匹配，且实时消耗额度
        for (barcode in barcodes) {
            val parseResult = Gs1Parser.parse(barcode.displayValue)
            val di = parseResult.di ?: ""
            val batch = parseResult.batch ?: ""

            val matchIndex = remainingTargets.indexOfFirst { target ->
                val parts = target.split("|", limit = 2)
                val targetDis = parts[0].split(",")
                val targetBatch = if (parts.size > 1) parts[1] else ""
                targetDis.contains(di) && targetBatch == batch
            }
            if (matchIndex != -1) {
                barcode.status = ScanStatus.MATCHED
                remainingTargets.removeAt(matchIndex)
            } else {
                barcode.status = ScanStatus.MISMATCHED
            }
        }
        return barcodes
    }
}
