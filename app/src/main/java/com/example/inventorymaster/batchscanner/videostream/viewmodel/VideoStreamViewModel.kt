package com.example.inventorymaster.batchscanner.videostream.viewmodel

import android.app.Application
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.util.BarcodeStatusAssigner
import com.example.inventorymaster.batchscanner.videostream.models.*
import com.example.inventorymaster.batchscanner.videostream.util.DirectionDetector
import com.example.inventorymaster.batchscanner.videostream.util.FrameProcessor
import com.example.inventorymaster.batchscanner.videostream.util.GridManager
import com.example.inventorymaster.batchscanner.videostream.util.ImuMotionDetector
import com.example.inventorymaster.batchscanner.videostream.util.MLKitFrameProcessor
import com.example.inventorymaster.batchscanner.videostream.util.TrackerEngine
import com.example.inventorymaster.batchscanner.videostream.util.VideoRecordHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VideoStreamViewModel(application: Application) : AndroidViewModel(application) {

    // --- UI 状态 ---
    private val _uiState = MutableStateFlow<VideoStreamUiState>(VideoStreamUiState.Idle)
    val uiState: StateFlow<VideoStreamUiState> = _uiState.asStateFlow()

    // --- 录像状态 ---
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // --- 活跃 Tracker 列表（给 Overlay 用） ---
    private val _activeTrackers = MutableStateFlow<List<ActiveTracker>>(emptyList())
    val activeTrackers: StateFlow<List<ActiveTracker>> = _activeTrackers.asStateFlow()

    // --- 扫描会话 ---
    private var session: ScanSession? = null

    // --- 核心组件 ---
    private val frameProcessor: FrameProcessor = MLKitFrameProcessor()
    private val trackerEngine = TrackerEngine()
    private val gridManager = GridManager()
    private val directionDetector = DirectionDetector()
    val imuDetector = ImuMotionDetector(application)
    var videoRecordHelper: VideoRecordHelper? = null

    // --- 线程安全 ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // --- 单据目标池 ---
    private var targetList: MutableList<String>? = null

    // --- 图像坐标变换参数 ---
    private var imageWidth = 1280
    private var imageHeight = 720
    private var viewWidth = 0f
    private var viewHeight = 0f

    /**
     * 获取当前 Session（供 Screen 读取统计数据）
     */
    fun getSession(): ScanSession? = session

    /**
     * 初始化：接收单据目标列表
     */
    fun initTargetList(targets: List<String>?) {
        this.targetList = targets?.toMutableList()
    }

    /**
     * 设置图像/视图尺寸参数（用于坐标变换）
     */
    fun setDimensions(imgW: Int, imgH: Int, vW: Float, vH: Float) {
        imageWidth = imgW
        imageHeight = imgH
        viewWidth = vW
        viewHeight = vH
    }

    /**
     * 开始扫描
     */
    fun startStreaming() {
        val config = ScanConfig()
        session = ScanSession(config = config)
        gridManager.init(config)
        trackerEngine.reset()
        directionDetector.reset()
        imuDetector.start()

        _uiState.value = VideoStreamUiState.Streaming
        _activeTrackers.value = emptyList()
        _isAnalyzing.value = true
    }

    /**
     * 暂停扫描
     */
    fun pause() {
        _isAnalyzing.value = false
        imuDetector.stop()

        val sess = session ?: return

        // 保存网格状态到 Session
        gridManager.saveToSession(sess.gridCells)
        sess.statistics.coveragePercent = gridManager.getCoveragePercent()
        sess.statistics.trackerCount = sess.trackerPool.size

        // 将 Tracker 池转换为 GlobalBarcode 列表
        val barcodes = sess.trackerPool.values
            .filter { it.isCounted || it.confidence >= sess.config.confidenceThreshold }
            .map { tracker ->
                GlobalBarcode(
                    originalBarcode = null,
                    displayValue = tracker.displayValue,
                    globalBoundingBox = Rect(
                        tracker.boundingBox.left.toInt(),
                        tracker.boundingBox.top.toInt(),
                        tracker.boundingBox.right.toInt(),
                        tracker.boundingBox.bottom.toInt()
                    ),
                    globalCornerPoints = emptyArray(),
                    globalCenterX = tracker.centerX.toInt(),
                    globalCenterY = tracker.centerY.toInt(),
                    status = if (tracker.isCounted) ScanStatus.MATCHED else ScanStatus.UNKNOWN,
                    engine = "video_stream"
                )
            }

        // 传入 targetList 的副本进行状态分配
        val snapshot = targetList?.toMutableList()
        BarcodeStatusAssigner.assignStatus(barcodes, snapshot)

        _uiState.value = VideoStreamUiState.Paused(barcodes, sess.statistics.coveragePercent, sess.statistics.totalCount)
    }

    /**
     * 恢复扫描
     */
    fun resume() {
        val sess = session ?: return

        // Resume：保留业务状态，清空视觉状态
        sess.resume()
        trackerEngine.reset()
        directionDetector.reset()
        gridManager.restoreFromSession(sess.gridCells)

        imuDetector.start()
        _isAnalyzing.value = true

        // 重新开始流式状态
        _uiState.value = VideoStreamUiState.Streaming
    }

    /**
     * 确认提交
     */
    fun confirm(): List<GlobalBarcode> {
        return (_uiState.value as? VideoStreamUiState.Paused)?.detections ?: emptyList()
    }

    /**
     * 重置
     */
    fun reset() {
        videoRecordHelper?.deleteLastRecording()
        session?.reset()
        session = null
        gridManager.reset()
        trackerEngine.reset()
        directionDetector.reset()
        imuDetector.stop()

        _uiState.value = VideoStreamUiState.Idle
        _activeTrackers.value = emptyList()
        _recordingState.value = RecordingState()
        _isAnalyzing.value = false
    }

    /**
     * 处理单帧
     */
    suspend fun processFrame(imageProxy: androidx.camera.core.ImageProxy): List<FrameDetection> {
        return frameProcessor.processFrame(imageProxy)
    }

    /**
     * 每帧回调：更新 Tracker、Grid、Direction
     */
    fun onFrameDetected(detections: List<FrameDetection>) {
        if (!_isAnalyzing.value) return

        val sess = session ?: return

        // 1. 更新 TrackerEngine
        val newlyCounted = trackerEngine.update(sess, detections)

        // 2. 更新 Grid 覆盖
        for (tracker in sess.trackerPool.values) {
            if (tracker.state == TrackerState.EXPIRED || tracker.state == TrackerState.LOCKED) continue
            // 将 Tracker 中心点转换为 View 坐标后传入 GridManager
            val viewX = tracker.centerX  // TODO: 需要 Matrix 变换
            val viewY = tracker.centerY  // TODO: 需要 Matrix 变换
            gridManager.markCovered(viewX, viewY, viewWidth.toFloat(), viewHeight.toFloat())
        }

        // 3. 更新方向检测
        directionDetector.update(sess.trackerPool.values.toList(), imageHeight)
        sess.currentDirection = directionDetector.getCurrentDirection()

        // 4. 更新覆盖率
        sess.statistics.coveragePercent = gridManager.getCoveragePercent()
        sess.statistics.trackerCount = sess.trackerPool.size

        // 5. 暴露 Tracker 列表给 Overlay
        _activeTrackers.value = sess.trackerPool.values.toList()
    }

    fun updateRecordingState(state: RecordingState) {
        _recordingState.value = state
    }

    fun onRecordingUnavailable() {
        _recordingState.value = RecordingState(recordingAvailable = false)
    }

    override fun onCleared() {
        super.onCleared()
        frameProcessor.close()
        imuDetector.stop()
        reset()
    }
}
