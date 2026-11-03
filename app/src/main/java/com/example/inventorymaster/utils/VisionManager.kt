package com.example.inventorymaster.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.opencv.core.Rect as OpenCvRect

/**
 * 扫描结果
 */
data class ScanResult(
    val barcodes: List<DecodedBarcode>,
    val regions: List<LocatedRegion>,
    val message: String? = null
)

/**
 * 定位结果
 */
data class LocatedRegion(
    val boundingBox: org.opencv.core.Rect,
    val centerPoint: org.opencv.core.Point
)

/**
 * 全局视觉大脑
 * Two-Pass AI 管线：YOLO 定位 → OpenCV 增强 → 多引擎解码
 */
class VisionManager(
    context: Context,
    private val decoderChain: DecoderChain = DecoderChain(
        listOf(
            MlKitDecoder(),
            LibDmtxDecoder()
        )
    )
) {

    private val yoloDetector = YoloDetector(context)

    companion object {
        private const val TAG = "VisionManager"
        private const val THUMBNAIL_MAX_WIDTH = 1920
        private const val ROI_PADDING = 40
    }

    init {
        yoloDetector.load()
    }

    /**
     * 扫描管线主入口
     *
     * Two-Pass AI 架构：
     * 1. 缩略图上 YOLO 定位，获取所有条码区域 BoundingBox
     * 2. 坐标映射回原图，裁剪 ROI
     * 3. OpenCV 增强 + 多引擎解码
     */
    suspend fun scanImage(
        originalImage: Bitmap,
        onProgress: ((String) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.Default) {
        if (originalImage.isRecycled) {
            Log.w(TAG, "originalImage is recycled, aborting scanImage")
            return@withContext ScanResult(emptyList(), emptyList(), "图片已被回收")
        }

        // 0. OpenCV 初始化（第二阶段增强仍需要）
        if (!OpenCVInit.init()) {
            Log.w(TAG, "OpenCV not available, returning empty result")
            return@withContext ScanResult(emptyList(), emptyList(), "OpenCV 未就绪")
        }

        onProgress?.invoke("正在使用 AI 快速巡视画面...")

        // 1. 生成缩略图
        val thumbnail = MatBitmapConverter.createThumbnail(originalImage, THUMBNAIL_MAX_WIDTH)
        Log.d(TAG, "Thumbnail created: ${thumbnail.width}x${thumbnail.height} (original: ${originalImage.width}x${originalImage.height})")

        // 2. YOLO 检测：获取所有条码区域 BoundingBox
        val detections = yoloDetector.detect(thumbnail)
        Log.d(TAG, "YOLO detected ${detections.size} regions")

        if (detections.isEmpty()) {
            thumbnail.recycle()
            return@withContext ScanResult(emptyList(), emptyList(), "未检测到条码特征区域")
        }

        onProgress?.invoke("YOLO 发现 ${detections.size} 个疑似目标，准备高分重建...")

        // 3. 坐标映射
        val scaleX = originalImage.width.toDouble() / thumbnail.width.toDouble()
        val scaleY = originalImage.height.toDouble() / thumbnail.height.toDouble()

        val allBarcodes = mutableListOf<DecodedBarcode>()
        val allRegions = mutableListOf<LocatedRegion>()

        for (det in detections) {
            if (originalImage.isRecycled) {
                Log.w(TAG, "originalImage recycled during processing, stopping")
                break
            }

            // 映射回原图坐标 + padding
            val globalBox = AndroidRect(
                maxOf(0, (det.bbox.left * scaleX).toInt() - ROI_PADDING),
                maxOf(0, (det.bbox.top * scaleY).toInt() - ROI_PADDING),
                minOf(originalImage.width, (det.bbox.right * scaleX).toInt() + ROI_PADDING),
                minOf(originalImage.height, (det.bbox.bottom * scaleY).toInt() + ROI_PADDING)
            )

            val cropWidth = globalBox.width()
            val cropHeight = globalBox.height()
            if (cropWidth <= 0 || cropHeight <= 0) continue

            Log.d(TAG, "ROI: local=(${det.bbox.left.toInt()},${det.bbox.top.toInt()},${det.bbox.right.toInt()},${det.bbox.bottom.toInt()}) -> global=(${globalBox.left},${globalBox.top},${globalBox.right},${globalBox.bottom})")

            // 4. 原图裁剪 → 增强 → 解码
            val roiBitmap = Bitmap.createBitmap(
                originalImage, globalBox.left, globalBox.top, cropWidth, cropHeight
            )
            val roiMat = MatBitmapConverter.bitmapToMat(roiBitmap)
            val enhanced = ImagePreprocessor.enhanceForBarcode(roiMat)
            val enhancedBitmap = MatBitmapConverter.matToBitmap(enhanced)
            roiMat.release()
            enhanced.release()

            val decoded = decoderChain.decodeWithFallback(enhancedBitmap)
            enhancedBitmap.recycle()
            roiBitmap.recycle()

            // 5. 收集结果
            for (item in decoded) {
                Log.d(TAG, "ROI decoded: engine=${item.engine}, value=${item.value}, format=${item.format}")
                allBarcodes.add(item.copy(boundingBox = globalBox))
            }

            allRegions.add(
                LocatedRegion(
                    boundingBox = OpenCvRect(globalBox.left, globalBox.top, cropWidth, cropHeight),
                    centerPoint = org.opencv.core.Point(globalBox.centerX().toDouble(), globalBox.centerY().toDouble())
                )
            )
        }

        thumbnail.recycle()
        onProgress?.invoke("AI 巡视完毕，锁定 ${allBarcodes.size} 个精确目标")

        ScanResult(allBarcodes, allRegions)
    }

    /**
     * 局部补扫（增强版）
     * 使用 OpenCV 双三次插值 + CLAHE 增强
     */
    suspend fun rescanLocal(
        originalImage: Bitmap,
        centerX: Float,
        centerY: Float,
        cropSize: Int = 300,
        scaleFactor: Float = 3f
    ): DecodedBarcode? {
        if (!OpenCVInit.init()) return null

        val startX = maxOf(0, (centerX - cropSize / 2).toInt())
        val startY = maxOf(0, (centerY - cropSize / 2).toInt())
        val width = minOf(cropSize, originalImage.width - startX)
        val height = minOf(cropSize, originalImage.height - startY)

        if (width <= 0 || height <= 0) return null

        val croppedBitmap = Bitmap.createBitmap(originalImage, startX, startY, width, height)

        val croppedMat = MatBitmapConverter.bitmapToMat(croppedBitmap)
        val scaled = ImagePreprocessor.resizeWithInterpolation(croppedMat, scaleFactor.toDouble())
        val enhanced = ImagePreprocessor.enhanceForBarcode(scaled)
        val enhancedBitmap = MatBitmapConverter.matToBitmap(enhanced)
        croppedMat.release()
        scaled.release()
        enhanced.release()
        croppedBitmap.recycle()

        val barcodes = decoderChain.decodeWithFallback(enhancedBitmap)
        enhancedBitmap.recycle()

        val result = barcodes.firstOrNull()
        if (result != null) {
            Log.d(TAG, "rescanLocal decoded: engine=${result.engine}, value=${result.value}")
        } else {
            Log.d(TAG, "rescanLocal: no barcodes found")
        }
        return result
    }

    /**
     * 预留：计数管线入口
     * P2 阶段实现
     */
    suspend fun countObjects(image: Bitmap): Int {
        TODO("计数管线待实现")
    }

    fun close() {
        yoloDetector.close()
    }
}
