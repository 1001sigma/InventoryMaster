package com.example.inventorymaster.ocr

import android.graphics.Bitmap

interface TextRecognizer {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
