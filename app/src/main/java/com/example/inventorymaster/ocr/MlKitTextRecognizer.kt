package com.example.inventorymaster.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MlKitTextRecognizer : TextRecognizer {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val rawResult: Text? = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            val result = rawResult ?: return@withContext OcrResult.Error("ML Kit 识别失败")

            val blocks = result.textBlocks.map { block ->
                OcrTextBlock(
                    text = block.text,
                    lines = block.lines.map { it.text },
                    boundingBox = block.boundingBox
                )
            }
            val fullText = blocks.joinToString(" ") { it.text }.replace("\n", " ")
            if (fullText.isBlank()) OcrResult.Empty
            else OcrResult.Success(OcrText(text = fullText, blocks = blocks))
        } catch (e: Exception) {
            OcrResult.Error("ML Kit 识别失败: ${e.message}", e)
        }
    }
}
