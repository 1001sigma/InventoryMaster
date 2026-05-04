package com.example.inventorymaster.batchscanner

import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.barcode.common.Barcode

// 定义条码的业务校验状态
enum class ScanStatus {
    MATCHED,    // 匹配成功（在单据中，或无单据时的默认成功状态） -> 对应绿框 ✔
    MISMATCHED, // 异常多出（不在单据中） -> 对应红框 ✘
    UNKNOWN     // 初始状态
}

// 全局条码数据类（包含原图绝对坐标和业务状态）
data class GlobalBarcode(
    val originalBarcode: Barcode,
    val displayValue: String,
    val globalBoundingBox: Rect,
    val globalCornerPoints: Array<Point>,
    val globalCenterX: Int,
    val globalCenterY: Int,
    var status: ScanStatus = ScanStatus.UNKNOWN // 新增状态字段
)