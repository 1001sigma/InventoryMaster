package com.example.inventorymaster.batchscanner.videostream.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat

/**
 * 录像辅助类：管理 VideoCapture + Recorder，处理文件存储和废弃视频清理
 */
class VideoRecordHelper(
    private val context: Context
) {
    private var currentRecording: Recording? = null
    var videoCapture: VideoCapture<Recorder>? = null
        private set
    var lastRecordedUri: Uri? = null
        private set

    /**
     * 创建 Recorder 和 VideoCapture（在 CameraX 绑定前调用）
     */
    fun createVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val vc = VideoCapture.withOutput(recorder)
        this.videoCapture = vc
        return vc
    }

    /**
     * 开始录像
     */
    fun startRecording(
        onRecordStarted: () -> Unit = {},
        onRecordFinalized: (Uri?) -> Unit = {}
    ) {
        val vc = videoCapture ?: return
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "inventory_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }).build()

        currentRecording = vc.output
            .prepareRecording(context, mediaStoreOutput)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onRecordStarted()
                    is VideoRecordEvent.Finalize -> {
                        lastRecordedUri = event.outputResults.outputUri
                        onRecordFinalized(lastRecordedUri)
                    }
                }
            }
    }

    /**
     * 停止录像
     */
    fun stopRecording(): Uri? {
        currentRecording?.stop()
        currentRecording = null
        return lastRecordedUri
    }

    /**
     * 删除废弃的录像文件
     */
    fun deleteLastRecording() {
        lastRecordedUri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            lastRecordedUri = null
        }
    }
}
