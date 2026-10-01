package com.example.inventorymaster.ai.local

import android.graphics.Bitmap

interface ModelEngine {
    val engineType: EngineType
    suspend fun initialize(config: ModelConfig): Result<Unit>
    fun isLoaded(): Boolean
    fun release()
}

enum class EngineType { ONNX, LLM }

data class ModelConfig(
    val modelPath: String,
    val engineType: EngineType,
    val hardwareAccel: HardwareAccel = HardwareAccel.AUTO,
    val maxMemoryMb: Long = 2048,
    val contextWindowSize: Int = 4096
)

enum class HardwareAccel { AUTO, CPU, GPU, NPU }

sealed class MediaData {
    data class Image(val bitmap: Bitmap, val description: String? = null) : MediaData()
    data class Audio(val audioBytes: ByteArray, val format: String = "wav") : MediaData()
}

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val classId: Int
)

data class ClassificationResult(
    val classId: Int,
    val label: String,
    val confidence: Float
)

class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
