package com.example.inventorymaster.batchscanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed class ScannerUiState {
    object Capture : ScannerUiState()     // 取景拍照阶段
    data class Cropping(val rawBitmap: Bitmap) : ScannerUiState() // 2. 新增：手动裁剪阶段
    object Processing : ScannerUiState()  // 图片解析阶段
    object Review : ScannerUiState()      // 核对补录阶段
}

class BatchScannerViewModel : ViewModel() {

    private val scannerUtil = QRCodeScannerUtil()

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Capture)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var targetList: List<String>? = null

    private val _scannedBarcodes = MutableStateFlow<List<GlobalBarcode>>(emptyList())
    val scannedBarcodes: StateFlow<List<GlobalBarcode>> = _scannedBarcodes.asStateFlow()

    var currentReviewBitmap: Bitmap? = null
        private set

    // 新增：补扫模式状态
    private val _isRescanMode = MutableStateFlow(false)
    val isRescanMode: StateFlow<Boolean> = _isRescanMode.asStateFlow()

    // 新增：单向事件流，通知 UI 拉起单码扫码（传递坐标）
    private val _singleScannerEvent = MutableSharedFlow<Pair<Float, Float>>()
    val singleScannerEvent: SharedFlow<Pair<Float, Float>> = _singleScannerEvent

    fun toggleRescanMode() {
        _isRescanMode.value = !_isRescanMode.value
    }

    /**
     * 新增：处理图片点击进行局部补扫
     */
    fun handleImageTapForRescan(x: Float, y: Float) {
        val bitmap = currentReviewBitmap ?: return

        // ================= 防呆拦截 1：点击前去重 =================
        // 判断用户点击的这个坐标，是不是已经在一个成功的条码框内了？
        // 如果是，说明用户点了一个已经扫出来的条码，直接拦截，不作处理。
        val clickedOnExisting = _scannedBarcodes.value.any { existing ->
            existing.globalBoundingBox.contains(x.toInt(), y.toInt())
        }
        if (clickedOnExisting) {
            // 可选：你可以在这里抛个 Toast 事件告诉用户“该条码已识别”
            _isRescanMode.value = false // 退出补扫模式
            return
        }

        viewModelScope.launch {
            // 1. 尝试局部切片解析
            val result = scannerUtil.processLocalCropRescan(bitmap, x, y, targetList)

            if (result != null) {
                // ================= 防呆拦截 2：切片后空间去重 =================
                // 切片范围内可能扫到了旁边的条码，判断扫出来的条码是否和已有的条码在物理空间上重合
                val distanceThreshold = 150.0 // 容错距离，可根据实际图片分辨率调整

                val isDuplicate = _scannedBarcodes.value.any { existing ->
                    // 内容相同，且物理中心点距离很近，认为是同一个码
                    existing.displayValue == result.displayValue &&
                            Math.hypot(
                                (existing.globalCenterX - result.globalCenterX).toDouble(),
                                (existing.globalCenterY - result.globalCenterY).toDouble()
                            ) < distanceThreshold
                }

                if (!isDuplicate) {
                    // 真的是一个全新的漏网之鱼，加入列表！
                    addIncrementalBarcodes(listOf(result))
                }

                // 扫到了（不管是不是重复的），都退出补扫模式
                _isRescanMode.value = false
            } else {
                // 局部解析失败，触发事件通知 UI 拉起单码扫码
                _singleScannerEvent.emit(Pair(x, y))
            }
        }
    }

    /**
     * 新增：单码扫码成功后，手动添加一个标记点
     */
    fun addManualBarcodeAfterSingleScan(value: String, originalX: Float, originalY: Float) {
        val halfSize = 50
        val box = android.graphics.Rect(
            (originalX - halfSize).toInt(),
            (originalY - halfSize).toInt(),
            (originalX + halfSize).toInt(),
            (originalY + halfSize).toInt()
        )

        // 根据单据核验状态：DI + 批号 都匹配才算 MATCHED
        val scanStatus = if (targetList != null) {
            val parseResult = com.example.inventorymaster.utils.Gs1Parser.parse(value)
            val di = parseResult.di ?: ""
            val batch = parseResult.batch ?: ""
            val key = "$di|$batch"
            if (targetList!!.any { it == key }) ScanStatus.MATCHED else ScanStatus.MISMATCHED
        } else {
            ScanStatus.MATCHED
        }

        val fakeBarcode = GlobalBarcode(
            originalBarcode = null,
            displayValue = value,
            globalBoundingBox = box,
            globalCornerPoints = emptyArray(),
            globalCenterX = box.centerX(),
            globalCenterY = box.centerY(),
            status = scanStatus
        )
        addIncrementalBarcodes(listOf(fakeBarcode))
        _isRescanMode.value = false // 完成后退出模式
    }

    /**
     * 核心修改：支持传入外部图片
     */
    fun initScanner(targets: List<String>?, initialBitmap: Bitmap?) {
        this.targetList = targets

        if (initialBitmap != null) {
            // 如果传入了外部照片，直接进入解析流程
            _uiState.value = ScannerUiState.Cropping(initialBitmap)
        } else {
            // 没有照片，进入相机取景模式
            _uiState.value = ScannerUiState.Capture
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Processing
        currentReviewBitmap = bitmap

        viewModelScope.launch {
            val results = scannerUtil.processBitmapWithTarget(bitmap, targetList)
            _scannedBarcodes.value = results
            _uiState.value = ScannerUiState.Review
        }
    }

    fun addIncrementalBarcodes(newBarcodes: List<GlobalBarcode>) {
        val updatedList = _scannedBarcodes.value.toMutableList()
        updatedList.addAll(newBarcodes)
        _scannedBarcodes.value = updatedList
    }

    /**
     * 无论之前是传入的照片还是拍的照片，点击重拍统一退回到相机取景器
     */
    fun resetToCapture() {
        _scannedBarcodes.value = emptyList()
        currentReviewBitmap?.recycle()
        currentReviewBitmap = null
        _uiState.value = ScannerUiState.Capture
    }

    /**
     * 新增：拍照完成 -> 进入裁剪阶段
     */
    fun onImageCapturedForCrop(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Cropping(bitmap)
    }

    /**
     * 新增：裁剪完成 -> 接收裁剪好的图，进入解析
     */
    fun processCroppedImage(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Processing
        currentReviewBitmap = bitmap

        viewModelScope.launch {
            val results = scannerUtil.processBitmapWithTarget(bitmap, targetList)
            _scannedBarcodes.value = results
            _uiState.value = ScannerUiState.Review
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerUtil.close()
        currentReviewBitmap?.recycle()
    }
}