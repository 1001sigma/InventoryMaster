package com.example.inventorymaster.utils

import android.graphics.Bitmap

/**
 * 静态图扫码辅助（统一走 [Decoder] 全链：ML Kit → libdmtx）
 */
object QrScanner {

    /**
     * 识别静态图中的条码，返回首个有效结果；无结果返回 null
     * 入参为 Bitmap——旧 InputImage 版本拿不到像素，无法接入 libdmtx 二次解码
     */
    suspend fun recognizeQRCode(image: Bitmap): String? =
        Decoder.decodeStill(image).firstOrNull()?.value
}
