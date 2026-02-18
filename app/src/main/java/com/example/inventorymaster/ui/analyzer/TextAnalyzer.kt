package com.example.inventorymaster.ui.analyzer

import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.inventorymaster.utils.RecognitionUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OCR 文字分析器
 * 特点：默认不工作（省电），只有当用户按下快门（isCaptureRequested = true）时，
 * 才截取当前这一帧画面进行识别。
 */
class TextAnalyzer(
    private val isCaptureRequested: AtomicBoolean, // 控制开关：是否请求截图
    private val onOcrResult: (Text) -> Unit,       // 成功回调
    private val onError: (String) -> Unit          // 失败回调
) : BaseImageAnalyzer() { // 同样继承 Base，复用格式转换逻辑

    override fun process(image: InputImage, imageProxy: ImageProxy) {
        // 1. 检查开关：如果没有按下快门，直接释放这一帧，什么都不做
        // getAndSet(false) 意味着：读取当前值，如果是 true，读完立刻把它置为 false
        // 这样保证了只处理这一帧，不会连续识别
        if (!isCaptureRequested.getAndSet(false)) {
            imageProxy.close()
            return
        }

        // 2. 用户按了快门，开始识别逻辑
        Log.d("TextAnalyzer", "Capture triggered, starting OCR...")

        // 直接调用你现有的工具类
        RecognitionUtils.recognizeText(
            image,
            onSuccess = { result ->
                onOcrResult(result)
                imageProxy.close() // 识别成功，释放
            },
            onFailure = { e ->
                onError(e.message ?: "Unknown error")
                imageProxy.close() // 识别失败，也要释放
            },
            onComplete = {
                // 如果工具类有 onComplete，也可以在这里释放，
                // 但为了保险，建议在 Success/Failure 里都显式调用 close
            }
        )
    }
}