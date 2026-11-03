package com.example.inventorymaster.batchscanner.core.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.ai.provider.LocalAiService
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.models.ScannerUiState
import com.example.inventorymaster.batchscanner.core.util.BarcodeStatusAssigner
import com.example.inventorymaster.batchscanner.core.util.QRCodeScannerUtil
import com.example.inventorymaster.batchscanner.ocr.models.ExtractSource
import com.example.inventorymaster.batchscanner.ocr.models.OcrExtractedData
import com.example.inventorymaster.batchscanner.ocr.models.OcrScanState
import com.example.inventorymaster.batchscanner.ocr.OcrInfoExtractor
import com.example.inventorymaster.data.AppDatabase
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.ocr.OcrProvider
import com.example.inventorymaster.utils.Gs1Parser
import com.example.inventorymaster.utils.ImageStorageUtils
import com.example.inventorymaster.utils.downsample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val scannerUtil = QRCodeScannerUtil(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val productDao = AppDatabase.getDatabase(appContext).productDao()

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Capture)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var remainingTargets: MutableList<String>? = null

    private val _scannedBarcodes = MutableStateFlow<List<GlobalBarcode>>(emptyList())
    val scannedBarcodes: StateFlow<List<GlobalBarcode>> = _scannedBarcodes.asStateFlow()

    var currentReviewBitmap: Bitmap? = null
        private set

    // 新增：补扫模式状态
    private val _isRescanMode = MutableStateFlow(false)
    val isRescanMode: StateFlow<Boolean> = _isRescanMode.asStateFlow()

    // 新增：OpenCV 使用状态
    private val _useOpenCV = MutableStateFlow(true)
    val useOpenCV: StateFlow<Boolean> = _useOpenCV.asStateFlow()

    // 拍照原图存系统相册开关（来源于扫描设置，默认关）
    private val _saveOriginalToGallery = MutableStateFlow(false)
    val saveOriginalToGallery: StateFlow<Boolean> = _saveOriginalToGallery.asStateFlow()

    // 新增：单向事件流，通知 UI 拉起单码扫码（传递坐标）
    private val _singleScannerEvent = MutableSharedFlow<Pair<Float, Float>>()
    val singleScannerEvent: SharedFlow<Pair<Float, Float>> = _singleScannerEvent

    // ================= 多选图片队列 =================
    private val _isQueueMode = MutableStateFlow(false)
    val isQueueMode: StateFlow<Boolean> = _isQueueMode.asStateFlow()
    private val _queueRemaining = MutableStateFlow(0) // 当前张之后还剩几张
    val queueRemaining: StateFlow<Int> = _queueRemaining.asStateFlow()

    // 队列累积的原始条码（跨图片合并；每次合并后重置配额池整体重算核对状态）
    private val accumulatedRawBarcodes = mutableListOf<GlobalBarcode>()

    // 当前这一张图的条码标记（仅用于复核页绘制和照片标记，坐标只对当前图有效；
    // _scannedBarcodes 是跨图累积全集，画到当前图上会错位）
    private val _currentImageBarcodes = MutableStateFlow<List<GlobalBarcode>>(emptyList())
    val currentImageBarcodes: StateFlow<List<GlobalBarcode>> = _currentImageBarcodes.asStateFlow()

    // OCR 识别状态
    private val _ocrScanState = MutableStateFlow<OcrScanState>(OcrScanState.Idle)
    val ocrScanState: StateFlow<OcrScanState> = _ocrScanState.asStateFlow()

    // OCR 提取的记录列表
    private val _ocrRecords = MutableStateFlow<List<OcrExtractedData>>(emptyList())
    val ocrRecords: StateFlow<List<OcrExtractedData>> = _ocrRecords.asStateFlow()

    // OCR 选框列表（确认后保留，用于在 Review 图片上绘制）
    private val _ocrCropRects = MutableStateFlow<List<android.graphics.Rect>>(emptyList())
    val ocrCropRects: StateFlow<List<android.graphics.Rect>> = _ocrCropRects.asStateFlow()

    // 缓存解析匹配到的商品名称 Map (DI -> productName)
    private val _productNameMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val productNameMap: StateFlow<Map<String, String>> = _productNameMap.asStateFlow()

    // OCR 确认事件（通知外层将结果送入对比池）
    private val _ocrConfirmedEvent = MutableSharedFlow<OcrExtractedData>()
    val ocrConfirmedEvent: SharedFlow<OcrExtractedData> = _ocrConfirmedEvent

    // 当前正在处理的 OCR 选框（用于传递到 Extracted 状态）
    private var pendingOcrCropRect: android.graphics.Rect? = null

    private var scanJob: Job? = null
    private var ocrJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _useOpenCV.value = settings.useOpenCV
                scannerUtil.useOpenCV = settings.useOpenCV
                _saveOriginalToGallery.value = settings.saveOriginalPhotoToGallery
            }
        }
        viewModelScope.launch {
            _currentImageBarcodes.collect { barcodes ->
                updateProductNames(barcodes)
            }
        }
    }

    private suspend fun updateProductNames(barcodes: List<GlobalBarcode>) = withContext(Dispatchers.IO) {
        if (barcodes.isEmpty()) return@withContext
        val dis = barcodes.mapNotNull {
            val parsed = Gs1Parser.parse(it.displayValue)
            val di = (parsed.di ?: it.displayValue).trim()
            if (di.isNotEmpty()) di else null
        }.distinct()

        val currentMap = _productNameMap.value.toMutableMap()
        val missingDis = dis.filter { !currentMap.containsKey(it) }

        if (missingDis.isNotEmpty()) {
            for (di in missingDis) {
                try {
                    val list = productDao.getProductsByDi(di)
                    if (list.isNotEmpty()) {
                        currentMap[di] = list.first().productName
                    }
                } catch (e: Exception) {
                    Log.e("BatchScannerVM", "Query product for di=$di failed", e)
                }
            }
            _productNameMap.value = currentMap
        }
    }

    private var initialTargetList: List<String>? = null

    fun toggleRescanMode() {
        _isRescanMode.value = !_isRescanMode.value
    }

    fun toggleUseOpenCV() {
        val newValue = !_useOpenCV.value
        viewModelScope.launch {
            settingsRepository.updateUseOpenCV(newValue)
        }
    }

    fun toggleSaveOriginalToGallery() {
        val newValue = !_saveOriginalToGallery.value
        viewModelScope.launch {
            settingsRepository.updateSaveOriginalPhotoToGallery(newValue)
        }
    }

    /** 拍照原图存系统相册（异步，不阻塞裁剪/解码流程） */
    fun saveOriginalToGallery(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "APP_${System.currentTimeMillis()}"
            val uri = ImageStorageUtils.saveBitmapToGallery(appContext, bitmap, name)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    if (uri != null) "原图已存相册：$name" else "原图保存失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 新增：处理图片点击进行局部补扫
     */
    fun handleImageTapForRescan(x: Float, y: Float) {
        val bitmap = currentReviewBitmap ?: return

        // ================= 防呆拦截 1：点击前去重 =================
        // 用"当前这张图"的标记判断（队列模式下 _scannedBarcodes 是跨图累积全集，
        // 别图的框坐标对当前图无效，会造成大量误判拦截）
        val clickedOnExisting = _currentImageBarcodes.value.any { existing ->
            existing.globalBoundingBox.contains(x.toInt(), y.toInt())
        }
        if (clickedOnExisting) {
            Toast.makeText(appContext, "该条码已识别", Toast.LENGTH_SHORT).show()
            _isRescanMode.value = false
            return
        }

        viewModelScope.launch {
            try {
                // 1. 尝试局部切片解析
                val rawResult = scannerUtil.processLocalCropRescan(bitmap, x, y)

                if (rawResult != null) {
                    // ================= 防呆拦截 2：切片后空间去重 =================
                    // 同样只对本图的标记去重（同值码出现在别的图上不拦截本图的补扫标记）
                    val distanceThreshold = 150.0

                    val isDuplicate = _currentImageBarcodes.value.any { existing ->
                        existing.displayValue == rawResult.displayValue &&
                                Math.hypot(
                                    (existing.globalCenterX - rawResult.globalCenterX).toDouble(),
                                    (existing.globalCenterY - rawResult.globalCenterY).toDouble()
                                ) < distanceThreshold
                    }

                    if (!isDuplicate) {
                        val verifiedBarcode = assignStatusToBarcodes(listOf(rawResult)).first()
                        addIncrementalBarcodes(listOf(verifiedBarcode))
                        Toast.makeText(appContext, "补扫成功: ${rawResult.displayValue}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(appContext, "该条码已存在", Toast.LENGTH_SHORT).show()
                    }

                    _isRescanMode.value = false
                } else {
                    // 局部解析失败，触发事件通知 UI 拉起单码扫码
                    Toast.makeText(appContext, "局部识别失败，请使用单码扫码", Toast.LENGTH_SHORT).show()
                    _singleScannerEvent.emit(Pair(x, y))
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // 补扫入口的任何意外异常降级为提示，拉起单码扫码，不允许闪退
                Log.e("BatchScannerVM", "局部补扫失败", t)
                Toast.makeText(appContext, "补扫异常，请使用单码扫码", Toast.LENGTH_SHORT).show()
                _isRescanMode.value = false
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

        val fakeBarcode = GlobalBarcode(
            originalBarcode = null,
            displayValue = value,
            globalBoundingBox = box,
            globalCornerPoints = emptyArray(),
            globalCenterX = box.centerX(),
            globalCenterY = box.centerY(),
            status = ScanStatus.UNKNOWN
        )
        val verifiedBarcode = assignStatusToBarcodes(listOf(fakeBarcode)).first()
        addIncrementalBarcodes(listOf(verifiedBarcode))

        _isRescanMode.value = false
    }

    /**
     * 核心修改：支持传入外部图片
     */
    fun initScanner(targets: List<String>?, initialBitmap: Bitmap?) {
        this.initialTargetList = targets
        // 初始化全局剩余单据池
        this.remainingTargets = targets?.toMutableList()

        if (initialBitmap != null) {
            _uiState.value = ScannerUiState.Cropping(initialBitmap)
        } else {
            _uiState.value = ScannerUiState.Capture
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Processing
        currentReviewBitmap = bitmap

        scanJob = viewModelScope.launch {
            try {
                val scanOutput = scannerUtil.processBitmap(bitmap)
                // 赋予状态并消耗额度
                _scannedBarcodes.value = assignStatusToBarcodes(scanOutput.barcodes)
                _currentImageBarcodes.value = _scannedBarcodes.value
                _uiState.value = ScannerUiState.Review

                // Toast 显示 OpenCV 状态
                val emoji = if (scanOutput.usedOpenCV) "OpenCV" else "暴力分片"
                Toast.makeText(appContext, "[$emoji] ${scanOutput.message}", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // 解码入口的任何意外异常降级为提示，回到复核页，不允许闪退
                Log.e("BatchScannerVM", "整图解析失败", t)
                _scannedBarcodes.value = emptyList()
                _currentImageBarcodes.value = emptyList()
                _uiState.value = ScannerUiState.Review
                Toast.makeText(appContext, "解析异常：${t.message ?: "未知错误"}，可重拍或单码补扫", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addIncrementalBarcodes(newBarcodes: List<GlobalBarcode>) {
        if (_isQueueMode.value) {
            // 队列模式：补扫码并入累积全集重算状态，并无条件画进当前图标记
            // （值在别的图上出现过不影响本图的几何标记；重复计入与单张/浮窗补扫行为一致）
            accumulatedRawBarcodes.addAll(newBarcodes)
            val all = reassignAccumulated()
            _scannedBarcodes.value = all
            _currentImageBarcodes.value = _currentImageBarcodes.value + newBarcodes
        } else {
            val updatedList = _scannedBarcodes.value.toMutableList()
            updatedList.addAll(newBarcodes)
            _scannedBarcodes.value = updatedList
            _currentImageBarcodes.value = updatedList
        }
    }

    /**
     * 无论之前是传入的照片还是拍的照片，点击重拍统一退回到相机取景器
     */
    fun resetToCapture() {
        _scannedBarcodes.value = emptyList()
        _ocrCropRects.value = emptyList()
        scanJob?.cancel()
        scanJob = null
        currentReviewBitmap = null
        // 重拍时，重置全局单据池
        this.remainingTargets = this.initialTargetList?.toMutableList()
        // 退出多选队列模式
        _isQueueMode.value = false
        _queueRemaining.value = 0
        accumulatedRawBarcodes.clear()
        _currentImageBarcodes.value = emptyList()
        _uiState.value = ScannerUiState.Capture
    }

    /**
     * 连拍模式：准备进行下一张拍照。
     * 若当前处于单拍第一张，平滑升阶为累积模式，将第一张条码归入 accumulatedRawBarcodes；
     * 清理当前图专用的临时状态，返回 Capture 取景器。
     */
    fun prepareNextCapture() {
        scanJob?.cancel()
        scanJob = null
        currentReviewBitmap = null

        // 若尚未进入多图累积模式，正式将第一张的结果并入累积全集
        if (!_isQueueMode.value) {
            _isQueueMode.value = true
            accumulatedRawBarcodes.clear()
            accumulatedRawBarcodes.addAll(_currentImageBarcodes.value)
        }

        // 清理当前图的专用状态
        _currentImageBarcodes.value = emptyList()
        _ocrCropRects.value = emptyList()
        _isRescanMode.value = false
        cancelOcr()

        // 切回取景状态
        _uiState.value = ScannerUiState.Capture
    }

    /**
     * 连拍模式：重拍/放弃当前张。
     * 精准扣除当前图刚刚产生的条码，重新分配剩余单据配额，切回 Capture 取景器；
     * 绝不影响之前已经完成落盘的前置照片与条码。
     */
    fun discardCurrentImageAndRetake() {
        scanJob?.cancel()
        scanJob = null
        currentReviewBitmap = null

        if (_isQueueMode.value) {
            val currentCodes = _currentImageBarcodes.value
            if (currentCodes.isNotEmpty()) {
                accumulatedRawBarcodes.removeAll(currentCodes.toSet())
                _scannedBarcodes.value = reassignAccumulated()
            }
        } else {
            // 首张且尚未进入累积模式，直接清空
            this.remainingTargets = this.initialTargetList?.toMutableList()
            accumulatedRawBarcodes.clear()
            _scannedBarcodes.value = emptyList()
        }

        _currentImageBarcodes.value = emptyList()
        _ocrCropRects.value = emptyList()
        _isRescanMode.value = false
        cancelOcr()

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
        // OCR 选框坐标只对当前图有效，换图必须清空（否则上一张的黄框会画到本张的复核页/存档照片上）
        _ocrCropRects.value = emptyList()

        scanJob = viewModelScope.launch {
            try {
                val scanOutput = scannerUtil.processBitmap(bitmap)
                // 赋予状态并消耗额度（队列模式改为累积合并，配额整体重算）
                if (_isQueueMode.value) {
                    mergeQueueResults(scanOutput.barcodes)
                } else {
                    _scannedBarcodes.value = assignStatusToBarcodes(scanOutput.barcodes)
                    _currentImageBarcodes.value = _scannedBarcodes.value
                }
                _uiState.value = ScannerUiState.Review

                // Toast 显示 OpenCV 状态
                val emoji = if (scanOutput.usedOpenCV) "OpenCV" else "暴力分片"
                Toast.makeText(appContext, "[$emoji] ${scanOutput.message}", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // 解码入口的任何意外异常降级为提示，回到复核页，不允许闪退
                Log.e("BatchScannerVM", "裁剪图解析失败", t)
                if (!_isQueueMode.value) {
                    _scannedBarcodes.value = emptyList()
                    _currentImageBarcodes.value = emptyList()
                }
                _uiState.value = ScannerUiState.Review
                Toast.makeText(appContext, "解析异常：${t.message ?: "未知错误"}，可重拍或单码补扫", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= 多选图片队列 =================

    /**
     * 多选图片队列入口：第一张先进裁剪页，裁剪确认后走 processCroppedImage 的合并分支；
     * 其余图片由页面逐张调 [processNextQueueImage]（同样先进裁剪）
     */
    fun startImageQueue(targetList: List<String>?, firstBitmap: Bitmap, total: Int) {
        this.initialTargetList = targetList
        this.remainingTargets = targetList?.toMutableList()
        accumulatedRawBarcodes.clear()
        _scannedBarcodes.value = emptyList()
        _currentImageBarcodes.value = emptyList()
        _isQueueMode.value = true
        _queueRemaining.value = (total - 1).coerceAtLeast(0)
        currentReviewBitmap = firstBitmap
        _uiState.value = ScannerUiState.Cropping(firstBitmap)
    }

    /**
     * 载入队列中的下一张：进入裁剪页（每张都走完整的 裁剪→解析→复核 流程）
     */
    fun processNextQueueImage(bitmap: Bitmap) {
        _queueRemaining.value = (_queueRemaining.value - 1).coerceAtLeast(0)
        currentReviewBitmap = bitmap
        _uiState.value = ScannerUiState.Cropping(bitmap)
    }

    /** 合并本张结果到累积列表：保留每次出现（同值码=多件，与单张识别一致），当前图标记=本张自己的解码结果 */
    private fun mergeQueueResults(newBarcodes: List<GlobalBarcode>) {
        accumulatedRawBarcodes.addAll(newBarcodes)
        val all = reassignAccumulated()
        _scannedBarcodes.value = all
        // 标记坐标只对本张图有效，直接采用本张的解码结果；
        // 不能按码值跨图过滤（同值码会被折叠/漏标），也不能复用累积全集（别图的框会错位画到本图）
        _currentImageBarcodes.value = newBarcodes
    }

    /** 重置配额池后对累积列表（含同值多次出现）整体重算核对状态 */
    private fun reassignAccumulated(): List<GlobalBarcode> {
        remainingTargets = initialTargetList?.toMutableList()
        return assignStatusToBarcodes(accumulatedRawBarcodes.toList())
    }

    // ===================== OCR 相关方法 =====================

    /** 进入 OCR 裁剪模式 */
    fun startOcrCrop() {
        _ocrScanState.value = OcrScanState.Cropping
    }

    /** 取消 OCR（含协程取消） */
    fun cancelOcr() {
        ocrJob?.cancel()
        ocrJob = null
        pendingOcrCropRect = null
        _ocrScanState.value = OcrScanState.Idle
    }

    /** 确认裁剪区域 → OCR 识别 → 提取 */
    fun processOcrCrop(cropRect: android.graphics.Rect) {
        val bitmap = currentReviewBitmap ?: return
        _ocrScanState.value = OcrScanState.Recognizing
        pendingOcrCropRect = cropRect

        ocrJob = viewModelScope.launch {
            try {
                // 1. 裁剪 Bitmap
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    cropRect.left.coerceAtLeast(0),
                    cropRect.top.coerceAtLeast(0),
                    cropRect.width().coerceAtMost(bitmap.width - cropRect.left.coerceAtLeast(0)),
                    cropRect.height().coerceAtMost(bitmap.height - cropRect.top.coerceAtLeast(0))
                )

                // 2. OCR 识别
                val ocrResult = OcrProvider.textRecognizer.recognize(cropped.downsample())
                if (ocrResult !is com.example.inventorymaster.ocr.OcrResult.Success || ocrResult.ocrText.text.isBlank()) {
                    _ocrScanState.value = OcrScanState.Error("未识别到文字")
                    return@launch
                }

                // 3. 中转处理（规则引擎 + AI 降级 + 标准化）
                val aiService = getAiService()
                val extracted = OcrInfoExtractor.extract(ocrResult.ocrText.text, aiService)

                // 4. 保存裁剪图片
                val cropFile = saveCropImage(cropped)

                _ocrScanState.value = OcrScanState.Extracted(
                    extracted.copy(
                        cropImagePath = cropFile?.absolutePath,
                        cropRect = pendingOcrCropRect
                    )
                )
            } catch (e: Exception) {
                _ocrScanState.value = OcrScanState.Error("识别失败: ${e.message}")
            }
        }
    }

    /** 用户确认 OCR 结果 */
    fun confirmOcrResult(data: OcrExtractedData) {
        _ocrRecords.update { it + data }
        // 将选框保留到 Review 图片上
        data.cropRect?.let { rect ->
            _ocrCropRects.update { it + rect }
        }
        pendingOcrCropRect = null
        _ocrScanState.value = OcrScanState.Idle
        // 发射事件通知外层将 OCR 结果送入对比池
        viewModelScope.launch { _ocrConfirmedEvent.emit(data) }
        Toast.makeText(appContext, "已添加 OCR 记录: 批号=${data.batch}", Toast.LENGTH_SHORT).show()
    }

    /** 保存裁剪图片到应用内部存储 */
    private fun saveCropImage(bitmap: Bitmap): File? {
        return try {
            val cropDir = File(appContext.filesDir, "ocr_crops")
            cropDir.mkdirs()
            val file = File(cropDir, "${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** 获取 AiService 实例 */
    private suspend fun getAiService(): AiService? {
        return try {
            val settings = settingsRepository.settingsFlow.first()
            val provider = settings.getActiveProvider() ?: return null
            if (provider.isLocal) {
                LocalAiService(provider)
            } else {
                CloudAiService(provider)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        scanJob = null
        ocrJob?.cancel()
        ocrJob = null
        scannerUtil.close()
        currentReviewBitmap = null
    }

    /**
     * 核心业务逻辑：统一对条码赋予状态，并全局消耗单据额度
     * 委托给 BarcodeStatusAssigner 执行，保持与 VideoStream 模块的一致性
     */
    private fun assignStatusToBarcodes(barcodes: List<GlobalBarcode>): List<GlobalBarcode> {
        return BarcodeStatusAssigner.assignStatus(barcodes, remainingTargets)
    }
}
