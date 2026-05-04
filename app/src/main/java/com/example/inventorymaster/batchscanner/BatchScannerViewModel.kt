package com.example.inventorymaster.batchscanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScannerUiState {
    object Capture : ScannerUiState()     // 取景拍照阶段
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

    /**
     * 核心修改：支持传入外部图片
     */
    fun initScanner(targets: List<String>?, initialBitmap: Bitmap?) {
        this.targetList = targets

        if (initialBitmap != null) {
            // 如果传入了外部照片，直接进入解析流程
            processCapturedImage(initialBitmap)
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

    override fun onCleared() {
        super.onCleared()
        scannerUtil.close()
        currentReviewBitmap?.recycle()
    }
}