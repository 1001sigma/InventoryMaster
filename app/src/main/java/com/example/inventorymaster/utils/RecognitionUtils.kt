package com.example.inventorymaster.utils

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.Text

object RecognitionUtils {

    // 1. 识别二维码 (返回二维码内容的字符串)
    fun recognizeQRCode(
        image: InputImage,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onComplete: () -> Unit ={}
    ) {
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onSuccess(value)
                        return@addOnSuccessListener // 只取第一个
                    }
                }
                // 如果没扫到，什么都不做，继续下一帧
            }
            .addOnFailureListener { onFailure(it) }
            .addOnCompleteListener {
                onComplete()
                scanner.close()
            }
    }

    // 2. 识别文字 (返回整段文本)
    fun recognizeText(
        image: InputImage,
        onSuccess: (Text) -> Unit,
        onFailure: (Exception) -> Unit,
        onComplete: () -> Unit ={}
    ) {
        // 使用默认的拉丁字母识别器 (英文/数字)
        // 如果你引入了中文库，这里改成 TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onSuccess(visionText)
            }
            .addOnFailureListener { onFailure(it) }
            .addOnCompleteListener {
                onComplete()
                recognizer.close()
            }
        // TextRecognizer 建议复用，但在简单场景下用完关闭也可以，或者放在 ViewModel 里单例持有
    }

    // 辅助：从 URI 创建 InputImage (用于相册)
    fun imageFromUri(context: Context, uri: Uri): InputImage? {
        return try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}