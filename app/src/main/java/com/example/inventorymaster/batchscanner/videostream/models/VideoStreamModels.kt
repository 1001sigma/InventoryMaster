package com.example.inventorymaster.batchscanner.videostream.models

import android.graphics.Rect
import android.net.Uri
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode

/**
 * 单帧检测结果（用于 Overlay 绘制 + 去重）
 */
data class FrameDetection(
    val displayValue: String,
    val boundingBox: Rect,
    val confidence: Float,
    val timestamp: Long
)

/**
 * 视频流 UI 状态机
 */
sealed class VideoStreamUiState {
    object Idle : VideoStreamUiState()
    object Streaming : VideoStreamUiState()
    data class Paused(
        val detections: List<GlobalBarcode>,
        val coveragePercent: Float = 0f,
        val totalCount: Int = 0
    ) : VideoStreamUiState()
}

/**
 * 录像状态
 */
data class RecordingState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0,
    val videoUri: Uri? = null,
    val recordingAvailable: Boolean = true
)
