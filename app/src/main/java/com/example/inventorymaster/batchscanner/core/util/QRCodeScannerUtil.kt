package com.example.inventorymaster.batchscanner.core.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.utils.Decoder
import com.example.inventorymaster.utils.OpenCVInit
import com.example.inventorymaster.utils.ScanResult
import com.example.inventorymaster.utils.VisionManager
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 扫描结果包装，包含 OpenCV 使用状态
 */
data class ScanOutput(
    val barcodes: List<GlobalBarcode>,
    val usedOpenCV: Boolean,
    val regionCount: Int = 0,
    val message: String = ""
)

class QRCodeScannerUtil(private val context: android.content.Context) {

    companion object {
        private const val TAG = "QRCodeScannerUtil"
        private const val TOP_LAYER_MAX_SIDE = 2560
        private const val BOTTOM_LAYER_TARGET_TILE = 600
        private const val OVERLAP_RATIO = 0.35f
    }

    var useOpenCV: Boolean = true

    // 动态信号量：根据设备 CPU 核心数自适应并发度
    private val concurrencySemaphore = Semaphore(
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    )

    // OpenCV 视觉管理器（YOLO 定位 + 多引擎解码）
    private val visionManager = VisionManager(context)

    /**
     * 主扫描入口
     * 优先使用 OpenCV 智能检测，失败则降级到多尺度金字塔暴力分片
     */
    suspend fun processBitmap(
        originalImage: Bitmap
    ): ScanOutput = withContext(Dispatchers.Default) {
        if (originalImage.isRecycled) {
            Log.w(TAG, "originalImage is recycled, aborting processBitmap")
            return@withContext ScanOutput(
                barcodes = emptyList(),
                usedOpenCV = false,
                message = "图片已被回收"
            )
        }

        // 尝试 OpenCV 智能扫描
        if (useOpenCV && OpenCVInit.init()) {
            try {
                val scanResult = visionManager.scanImage(originalImage)
                if (scanResult.barcodes.isNotEmpty()) {
                    val convertedBarcodes = convertScanResult(scanResult)
                    val uniqueBarcodes = BarcodeDeduplicator.deduplicate(convertedBarcodes)
                    return@withContext ScanOutput(
                        barcodes = uniqueBarcodes,
                        usedOpenCV = true,
                        regionCount = scanResult.regions.size,
                        message = "OpenCV 发现 ${scanResult.regions.size} 个区域，去重后精准识别 ${uniqueBarcodes.size} 个"
                    )
                }
                Log.w(TAG, "OpenCV found 0 regions, falling back to pyramid scan")
            } catch (e: Exception) {
                Log.w(TAG, "OpenCV scan failed, falling back to pyramid scan", e)
            }
        }

        // 降级：多尺度金字塔扫描（顶层 + 底层）
        val barcodes = pyramidScan(originalImage)
        ScanOutput(
            barcodes = barcodes,
            usedOpenCV = false,
            regionCount = 0,
            message = if (OpenCVInit.isInitialized()) "OpenCV 未检测到区域，使用金字塔扫描" else "OpenCV 未加载，使用金字塔扫描"
        )
    }

    /**
     * 多尺度金字塔扫描
     * 顶层：缩放全图 → 捕捉大条码
     * 底层：动态网格切片 → 捕捉小条码
     * 最终：IoU + 中心点距离双重去重
     */
    private suspend fun pyramidScan(originalImage: Bitmap): List<GlobalBarcode> = withContext(Dispatchers.Default) {
        val allBarcodes = mutableListOf<GlobalBarcode>()

        // ==================== 顶层：缩放全图扫描（捕捉大条码）====================
        // 顶层只跑 ML Kit：libdmtx 在无码大图上是穷尽式扫描（秒级），逐层兜底会拖垮整个降级路径；
        // DataMatrix 二次解码保留在 OpenCV 路径的 ROI 解码（VisionManager 链）中
        val downsampleResult = downsampleToSafeSize(originalImage, TOP_LAYER_MAX_SIDE)
        if (downsampleResult != null) {
            val (downsampledBitmap, scale) = downsampleResult
            try {
                val inputImage = InputImage.fromBitmap(downsampledBitmap, 0)
                val topBarcodes = Decoder.decodeFrameAwait(inputImage)

                // 还原坐标到原图坐标系
                topBarcodes.forEach { barcode ->
                    val value = barcode.rawValue ?: return@forEach
                    val localBox = barcode.boundingBox ?: return@forEach
                    val globalBox = Rect(
                        (localBox.left / scale).toInt(),
                        (localBox.top / scale).toInt(),
                        (localBox.right / scale).toInt(),
                        (localBox.bottom / scale).toInt()
                    )
                    allBarcodes.add(
                        GlobalBarcode(
                            originalBarcode = barcode,
                            displayValue = value,
                            globalBoundingBox = globalBox,
                            globalCornerPoints = emptyArray(),
                            globalCenterX = globalBox.centerX(),
                            globalCenterY = globalBox.centerY(),
                            engine = "ML Kit (pyramid-top)"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Top-level scan failed", e)
            } finally {
                downsampledBitmap.recycle()
            }
        }

        // ==================== 底层：动态网格切片扫描（捕捉小条码）====================
        val bottomBarcodes = decodeBitmapByTiling(originalImage)
        allBarcodes.addAll(bottomBarcodes)

        // ==================== 统一去重（IoU + 中心点距离）====================
        val uniqueBarcodes = BarcodeDeduplicator.deduplicate(allBarcodes)
        uniqueBarcodes
    }

    /**
     * 顶层缩放：将图片缩放到安全尺寸，失败时返回 null 让系统降级
     */
    private fun downsampleToSafeSize(bitmap: Bitmap, maxSide: Int): Pair<Bitmap, Float>? {
        val scale = min(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height)
        if (scale >= 1.0f) return null // 图已经很小，无需缩放
        return try {
            val matrix = Matrix().apply { postScale(scale, scale) }
            val downsampled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            Pair(downsampled, scale)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during top-level downscale", e)
            null // 降级：跳过顶层，仅依赖底层切片
        }
    }

    /**
     * 底层：动态网格切片扫描
     * 根据原图尺寸自适应计算网格数和重叠像素
     */
    private suspend fun decodeBitmapByTiling(
        originalImage: Bitmap
    ): List<GlobalBarcode> = withContext(Dispatchers.Default) {
        if (originalImage.isRecycled) {
            Log.w(TAG, "originalImage is recycled, aborting decodeBitmapByTiling")
            return@withContext emptyList()
        }

        // 动态计算网格数
        val cols = max(1, (originalImage.width.toFloat() / BOTTOM_LAYER_TARGET_TILE).roundToInt())
        val rows = max(1, (originalImage.height.toFloat() / BOTTOM_LAYER_TARGET_TILE).roundToInt())

        val baseWidth = originalImage.width / cols
        val baseHeight = originalImage.height / rows

        val overlapX = (baseWidth * OVERLAP_RATIO).toInt()
        val overlapY = (baseHeight * OVERLAP_RATIO).toInt()

        Log.d(TAG, "Dynamic grid: ${cols}x${rows}, baseTile=${baseWidth}x${baseHeight}, overlap=${overlapX}x${overlapY}")

        val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<List<GlobalBarcode>>>()

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (originalImage.isRecycled) {
                    Log.w(TAG, "originalImage recycled during tiling, stopping")
                    return@withContext emptyList()
                }

                val startX = max(0, j * baseWidth - overlapX)
                val startY = max(0, i * baseHeight - overlapY)
                val endX = min(originalImage.width, (j + 1) * baseWidth + overlapX)
                val endY = min(originalImage.height, (i + 1) * baseHeight + overlapY)

                val width = endX - startX
                val height = endY - startY
                if (width <= 0 || height <= 0) continue

                val deferred = async {
                    concurrencySemaphore.withPermit {
                        // Bitmap 安全保护：防止并发回收和 OOM
                        val tileBitmap = try {
                            Bitmap.createBitmap(originalImage, startX, startY, width, height)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "Bitmap recycled during slicing", e)
                            return@withPermit emptyList()
                        } catch (e: OutOfMemoryError) {
                            Log.e(TAG, "OOM during slicing", e)
                            return@withPermit emptyList()
                        }

                        try {
                            // 瓦片只跑 ML Kit（与改动前一致：并发、秒级）；libdmtx 不做逐片兜底
                            val inputImage = InputImage.fromBitmap(tileBitmap, 0)
                            val localBarcodes = Decoder.decodeFrameAwait(inputImage)

                            localBarcodes.mapNotNull { barcode ->
                                val value = barcode.rawValue ?: return@mapNotNull null
                                val localBox = barcode.boundingBox ?: return@mapNotNull null
                                val localCorners = barcode.cornerPoints ?: return@mapNotNull null

                                val globalBox = Rect(
                                    localBox.left + startX, localBox.top + startY,
                                    localBox.right + startX, localBox.bottom + startY
                                )
                                val globalCorners = localCorners.map {
                                    Point(it.x + startX, it.y + startY)
                                }.toTypedArray()

                                GlobalBarcode(
                                    originalBarcode = barcode,
                                    displayValue = value,
                                    globalBoundingBox = globalBox,
                                    globalCornerPoints = globalCorners,
                                    globalCenterX = globalBox.centerX(),
                                    globalCenterY = globalBox.centerY(),
                                    engine = "ML Kit (pyramid-bottom)"
                                )
                            }
                        } finally {
                            tileBitmap.recycle()
                        }
                    }
                }
                deferredResults.add(deferred)
            }
        }

        deferredResults.awaitAll().flatten()
    }

    /**
     * 将 VisionManager.ScanResult 转换为 GlobalBarcode 列表
     */
    private fun convertScanResult(scanResult: ScanResult): List<GlobalBarcode> {
        return scanResult.barcodes.map { decoded ->
            GlobalBarcode(
                originalBarcode = null,
                displayValue = decoded.value,
                globalBoundingBox = decoded.boundingBox ?: Rect(0, 0, 0, 0),
                globalCornerPoints = emptyArray(),
                globalCenterX = decoded.boundingBox?.centerX() ?: 0,
                globalCenterY = decoded.boundingBox?.centerY() ?: 0
            )
        }
    }

    /**
     * 局部补扫（OpenCV 增强版）
     */
    suspend fun processLocalCropRescan(
        originalImage: Bitmap,
        centerX: Float,
        centerY: Float,
        cropSize: Int = 300,
        scaleFactor: Float = 3.0f
    ): GlobalBarcode? = withContext(Dispatchers.Default) {

        if (useOpenCV && OpenCVInit.init()) {
            try {
                val decoded = visionManager.rescanLocal(originalImage, centerX, centerY, cropSize, scaleFactor)
                if (decoded != null) {
                    val startX = max(0, (centerX - cropSize / 2).toInt())
                    val startY = max(0, (centerY - cropSize / 2).toInt())

                    val localBox = decoded.boundingBox
                    val globalBox = if (localBox != null) {
                        Rect(
                            (localBox.left / scaleFactor).toInt() + startX,
                            (localBox.top / scaleFactor).toInt() + startY,
                            (localBox.right / scaleFactor).toInt() + startX,
                            (localBox.bottom / scaleFactor).toInt() + startY
                        )
                    } else {
                        val halfSize = 50
                        Rect(
                            (centerX - halfSize).toInt(),
                            (centerY - halfSize).toInt(),
                            (centerX + halfSize).toInt(),
                            (centerY + halfSize).toInt()
                        )
                    }

                    return@withContext GlobalBarcode(
                        originalBarcode = null,
                        displayValue = decoded.value,
                        globalBoundingBox = globalBox,
                        globalCornerPoints = emptyArray(),
                        globalCenterX = globalBox.centerX(),
                        globalCenterY = globalBox.centerY(),
                        engine = decoded.engine
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenCV rescan failed, falling back to legacy", e)
            }
        }

        legacyLocalCropRescan(originalImage, centerX, centerY, cropSize, scaleFactor)
    }

    /**
     * 降级方案：nearest-neighbor 局部补扫
     */
    private suspend fun legacyLocalCropRescan(
        originalImage: Bitmap,
        centerX: Float,
        centerY: Float,
        cropSize: Int = 300,
        scaleFactor: Float = 2.0f
    ): GlobalBarcode? = withContext(Dispatchers.Default) {

        val startX = max(0, (centerX - cropSize / 2).toInt())
        val startY = max(0, (centerY - cropSize / 2).toInt())
        val endX = min(originalImage.width, (centerX + cropSize / 2).toInt())
        val endY = min(originalImage.height, (centerY + cropSize / 2).toInt())

        val width = endX - startX
        val height = endY - startY
        if (width <= 0 || height <= 0) return@withContext null

        val croppedBitmap = Bitmap.createBitmap(originalImage, startX, startY, width, height)
        val matrix = Matrix().apply { postScale(scaleFactor, scaleFactor) }
        val scaledBitmap = Bitmap.createBitmap(croppedBitmap, 0, 0, width, height, matrix, false)

        val decoded = Decoder.decodeStill(scaledBitmap).firstOrNull()

        croppedBitmap.recycle()
        if (scaledBitmap != croppedBitmap) scaledBitmap.recycle()

        decoded ?: return@withContext null

        val localBox = decoded.boundingBox
        val globalBox = if (localBox != null) {
            Rect(
                (localBox.left / scaleFactor).toInt() + startX,
                (localBox.top / scaleFactor).toInt() + startY,
                (localBox.right / scaleFactor).toInt() + startX,
                (localBox.bottom / scaleFactor).toInt() + startY
            )
        } else {
            val halfSize = 50
            Rect(
                (centerX - halfSize).toInt(),
                (centerY - halfSize).toInt(),
                (centerX + halfSize).toInt(),
                (centerY + halfSize).toInt()
            )
        }

        GlobalBarcode(
            originalBarcode = null,
            displayValue = decoded.value,
            globalBoundingBox = globalBox,
            globalCornerPoints = emptyArray(),
            globalCenterX = globalBox.centerX(),
            globalCenterY = globalBox.centerY(),
            engine = decoded.engine
        )
    }

    fun close() {
        // ML Kit client 已统一为 Decoder 单例（进程级），保留空实现兼容既有调用方
    }
}
