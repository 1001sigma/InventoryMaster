package com.example.inventorymaster.batchscanner.core.models

import android.graphics.Bitmap

sealed class ScannerUiState {
    object Capture : ScannerUiState()     // 取景拍照阶段
    data class Cropping(val rawBitmap: Bitmap) : ScannerUiState() // 2. 新增：手动裁剪阶段
    object Processing : ScannerUiState()  // 图片解析阶段
    object Review : ScannerUiState()      // 核对补录阶段
}
