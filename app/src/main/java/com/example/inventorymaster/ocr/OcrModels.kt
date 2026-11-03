package com.example.inventorymaster.ocr

import android.graphics.Rect

data class OcrText(
    val text: String,
    val blocks: List<OcrTextBlock>
)

data class OcrTextBlock(
    val text: String,
    val lines: List<String>,
    // TODO: boundingBox 是相对于裁剪区域的坐标。未来若要在屏幕上叠加 OCR UI，
    // 需要在 OcrText 中增加 cropOffset: Offset 字段做坐标补偿：
    // X_screen = X_crop + cropOffset.x, Y_screen = Y_crop + cropOffset.y
    val boundingBox: Rect?
)

sealed class OcrResult {
    data class Success(val ocrText: OcrText) : OcrResult()
    data object Empty : OcrResult()
    data class Error(val message: String, val cause: Throwable? = null) : OcrResult()
}
