package com.example.inventorymaster.batchscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.example.inventorymaster.utils.Gs1Parser
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class QRCodeScannerUtil {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_DATA_MATRIX
        )
        .build()

    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

    /**
     * 核心入口：支持传入目标单据进行解析和状态标记
     */
    suspend fun processBitmapWithTarget(
        originalImage: Bitmap,
        targetList: List<String>? = null
    ): List<GlobalBarcode> = withContext(Dispatchers.Default) {

        val rawBarcodes = decodeBitmapByTiling(originalImage)

        // 2. 执行单据核对逻辑
        assignStatusByTarget(rawBarcodes, targetList)
    }

    private suspend fun decodeBitmapByTiling(
        originalImage: Bitmap,
        targetTileSize: Int = 800,
        overlapPixels: Int = 200
    ): List<GlobalBarcode> = withContext(Dispatchers.Default) {

        val cols = ceil(originalImage.width.toDouble() / targetTileSize).toInt()
        val rows = ceil(originalImage.height.toDouble() / targetTileSize).toInt()

        val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<List<GlobalBarcode>>>()
        val concurrencySemaphore = Semaphore(4)

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                val startX = max(0, j * targetTileSize - overlapPixels)
                val startY = max(0, i * targetTileSize - overlapPixels)
                val endX = min(originalImage.width, (j + 1) * targetTileSize + overlapPixels)
                val endY = min(originalImage.height, (i + 1) * targetTileSize + overlapPixels)

                val width = endX - startX
                val height = endY - startY

                val deferred = async {
                    concurrencySemaphore.withPermit {
                        val tileBitmap = Bitmap.createBitmap(originalImage, startX, startY, width, height)
                        val inputImage = InputImage.fromBitmap(tileBitmap, 0)

                        val localBarcodes = scanSingleImage(inputImage)
                        tileBitmap.recycle()

                        localBarcodes.mapNotNull { barcode ->
                            val value = barcode.displayValue ?: return@mapNotNull null
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
                                globalCenterY = globalBox.centerY()
                            )
                        }
                    }
                }
                deferredResults.add(deferred)
            }
        }

        val allGlobalBarcodes = deferredResults.awaitAll().flatten()

        // 空间物理去重
        val uniqueBarcodes = mutableListOf<GlobalBarcode>()
        val distanceThreshold = overlapPixels * 0.8

        for (barcode in allGlobalBarcodes) {
            val isDuplicate = uniqueBarcodes.any { existing ->
                existing.displayValue == barcode.displayValue &&
                        Math.hypot(
                            (existing.globalCenterX - barcode.globalCenterX).toDouble(),
                            (existing.globalCenterY - barcode.globalCenterY).toDouble()
                        ) < distanceThreshold
            }
            if (!isDuplicate) {
                uniqueBarcodes.add(barcode)
            }
        }
        return@withContext uniqueBarcodes
    }

    /**
     * 根据目标单据分配状态 (打勾或打叉)
     */
    private fun assignStatusByTarget(
        barcodes: List<GlobalBarcode>,
        targetList: List<String>?
    ): List<GlobalBarcode> {
        if (targetList.isNullOrEmpty()) {
            // 模式 A：没有提供单据，所有扫出来的码全部视为 MATCHED (打勾)
            barcodes.forEach { it.status = ScanStatus.MATCHED }
            return barcodes
        }

        // 模式 B：提供了单据，DI + 批号双匹配
        // targetList 每个元素为 "DI|BATCH"，扫码条码用 Gs1Parser 解析后构建同样 key 比对
        val remainingTargets = targetList.toMutableList()

        for (barcode in barcodes) {
            val parseResult = Gs1Parser.parse(barcode.displayValue)
            val di = parseResult.di ?: ""
            val batch = parseResult.batch ?: ""
            val key = "$di|$batch"

            val matchIndex = remainingTargets.indexOfFirst { it == key }
            if (matchIndex != -1) {
                barcode.status = ScanStatus.MATCHED
                remainingTargets.removeAt(matchIndex)
            } else {
                barcode.status = ScanStatus.MISMATCHED
            }
        }
        return barcodes
    }

    private suspend fun scanSingleImage(inputImage: InputImage): List<Barcode> =
        kotlin.coroutines.suspendCoroutine { continuation ->
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    continuation.resumeWith(Result.success(barcodes))
                }
                .addOnFailureListener {
                    continuation.resumeWith(Result.success(emptyList()))
                }
        }

    /**
     * 新增：局部切片放大解析
     * @param originalImage 原始大图
     * @param centerX 点击的原图 X 坐标
     * @param centerY 点击的原图 Y 坐标
     * @param targetList 单据池（用于状态判定）
     */
    suspend fun processLocalCropRescan(
        originalImage: Bitmap,
        centerX: Float,
        centerY: Float,
        targetList: List<String>?,
        cropSize: Int = 300,   // 切片大小 400x400
        scaleFactor: Float = 3.0f // 放大倍数
    ): GlobalBarcode? = withContext(Dispatchers.Default) {

        // 1. 计算裁剪边界 (防止越界)
        val startX = max(0, (centerX - cropSize / 2).toInt())
        val startY = max(0, (centerY - cropSize / 2).toInt())
        val endX = min(originalImage.width, (centerX + cropSize / 2).toInt())
        val endY = min(originalImage.height, (centerY + cropSize / 2).toInt())

        val width = endX - startX
        val height = endY - startY
        if (width <= 0 || height <= 0) return@withContext null

        // 2. 裁剪并放大
        val croppedBitmap = Bitmap.createBitmap(originalImage, startX, startY, width, height)
        val matrix = android.graphics.Matrix().apply { postScale(scaleFactor, scaleFactor) }
        val scaledBitmap = Bitmap.createBitmap(croppedBitmap, 0, 0, width, height, matrix, false)//使用最近邻插值Nearest-Neighbor

        val inputImage = InputImage.fromBitmap(scaledBitmap, 0)

        // 3. 扫描局部图像
        val localBarcodes = scanSingleImage(inputImage)

        croppedBitmap.recycle()
        if (scaledBitmap != croppedBitmap) scaledBitmap.recycle()

        if (localBarcodes.isEmpty()) return@withContext null

        // 4. 取第一个扫出来的结果，并把坐标映射回原大图
        val barcode = localBarcodes.first()
        val value = barcode.displayValue ?: return@withContext null
        val localBox = barcode.boundingBox ?: return@withContext null
        val localCorners = barcode.cornerPoints ?: return@withContext null

        // 反向除以放大倍数，加上起始偏移量
        val globalBox = Rect(
            (localBox.left / scaleFactor).toInt() + startX,
            (localBox.top / scaleFactor).toInt() + startY,
            (localBox.right / scaleFactor).toInt() + startX,
            (localBox.bottom / scaleFactor).toInt() + startY
        )
        val globalCorners = localCorners.map {
            Point((it.x / scaleFactor).toInt() + startX, (it.y / scaleFactor).toInt() + startY)
        }.toTypedArray()

        val globalBarcode = GlobalBarcode(
            originalBarcode = barcode,
            displayValue = value,
            globalBoundingBox = globalBox,
            globalCornerPoints = globalCorners,
            globalCenterX = globalBox.centerX(),
            globalCenterY = globalBox.centerY()
        )

        // 5. 状态分配
        assignStatusByTarget(listOf(globalBarcode), targetList).firstOrNull()
    }

    /**
     * 图像预处理管线：灰度化 → 直方图均衡 → 锐化
     * 解决拍照场景下因光照不均、对比度低、轻微模糊导致的识别率低问题
     */

    fun close() {
        barcodeScanner.close()
    }
}