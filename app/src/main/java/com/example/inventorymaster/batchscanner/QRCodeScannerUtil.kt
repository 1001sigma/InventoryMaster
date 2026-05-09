package com.example.inventorymaster.batchscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
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

        // 1. 调用滑动窗口切片解析 (复用你写好的算法)
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

        // 模式 B：提供了单据，进行严格校验
        // 考虑到单据中可能有重复的物料 (如 20个一模一样的码)，我们需要一个可变副本来扣减
        val remainingTargets = targetList.toMutableList()

        for (barcode in barcodes) {
            val matchIndex = remainingTargets.indexOf(barcode.displayValue)
            if (matchIndex != -1) {
                // 在单据中找到了，标记为匹配，并从剩余单据池中扣除一个额度
                barcode.status = ScanStatus.MATCHED
                remainingTargets.removeAt(matchIndex)
            } else {
                // 单据中不存在，或者数量已经超出了单据要求，标记为异常多出
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

    fun close() {
        barcodeScanner.close()
    }
}