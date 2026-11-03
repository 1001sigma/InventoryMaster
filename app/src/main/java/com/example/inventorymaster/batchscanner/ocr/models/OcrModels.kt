package com.example.inventorymaster.batchscanner.ocr.models

/** OCR 提取结果的处理状态 */
enum class OcrStatus { NEW, USED, IGNORED }

/** OCR 提取的结构化数据（仅批号和日期） */
data class OcrExtractedData(
    val batch: String = "",
    val prodDate: String? = null,
    val expDate: String? = null,
    val rawText: String = "",
    val confidence: Float = 0f,
    val source: ExtractSource = ExtractSource.RULE,
    val cropImagePath: String? = null,
    val cropRect: android.graphics.Rect? = null,
    val status: OcrStatus = OcrStatus.NEW
)

enum class ExtractSource { RULE, AI }

/** OCR 识别流程的 UI 状态 */
sealed class OcrScanState {
    data object Idle : OcrScanState()
    data object Cropping : OcrScanState()
    data object Recognizing : OcrScanState()
    data class Extracted(val data: OcrExtractedData) : OcrScanState()
    data class Error(val message: String) : OcrScanState()
}
