package com.example.inventorymaster.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * YOLO 目标检测器（ONNX Runtime）
 * 用于在缩略图上检测条码/二维码区域，输出 BoundingBox 列表
 */
class YoloDetector(
    private val context: Context,
    private val modelPath: String = "barcode_detector.onnx",
    private val inputSize: Int = 640,
    private val confThreshold: Float = 0.7f,
    private val iouThreshold: Float = 0.45f,
    private val maxDetections: Int = 50
) {

    data class Detection(
        val bbox: RectF,
        val confidence: Float,
        val classId: Int
    )

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isLoaded = false

    /**
     * 加载模型，失败不会崩溃，返回 false
     */
    fun load(): Boolean {
        if (isLoaded) return true
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
            ortSession = ortEnv!!.createSession(modelBytes)
            isLoaded = true
            Log.i(TAG, "YOLO model loaded: $modelPath (inputSize=$inputSize)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YOLO model: $modelPath", e)
            isLoaded = false
            false
        }
    }

    /**
     * 在缩略图上检测条码区域
     * @return 检测到的区域列表（bbox 为缩略图坐标系）
     */
     fun detect(thumbnail: Bitmap): List<Detection> {
        if (!isLoaded || ortEnv == null || ortSession == null) {
            Log.w(TAG, "Model not loaded, returning empty detections")
            return emptyList()
        }

        val t0 = System.currentTimeMillis()
        val inputTensor: OnnxTensor
        val output: FloatArray
        val numFeatures: Int
        val numDetections: Int

        try {
            inputTensor = preprocess(thumbnail)
            val t1 = System.currentTimeMillis()
            Log.d(TAG, "[timing] preprocess: ${t1 - t0}ms")

            val inputName = ortSession!!.inputNames.first()
            val results = ortSession!!.run(mapOf(inputName to inputTensor))
            val t2 = System.currentTimeMillis()
            Log.d(TAG, "[timing] inference: ${t2 - t1}ms")

            val outputTensor = results.first().value as OnnxTensor
            output = outputTensor.floatBuffer.array()

            val shape = outputTensor.info.shape
            Log.d(TAG, "Output shape: ${shape?.toList()}, arraySize=${output.size}")
            inputTensor.close()

            if (shape == null || shape.size < 3) {
                Log.w(TAG, "Invalid output shape")
                return emptyList()
            }

            numFeatures = shape[1].toInt()
            numDetections = shape[2].toInt()
        } catch (e: Exception) {
            Log.e(TAG, "YOLO inference failed", e)
            return emptyList()
        }

        val t3 = System.currentTimeMillis()
        val result = postprocess(output, numFeatures, numDetections, thumbnail.width, thumbnail.height)
        val t4 = System.currentTimeMillis()
        Log.d(TAG, "[timing] postprocess: ${t4 - t3}ms, total: ${t4 - t0}ms, detections=${result.size}")
        return result
    }

    /**
     * 预处理: Bitmap → NCHW float tensor [1, 3, inputSize, inputSize]
     */
    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (resized !== bitmap) resized.recycle()

        val floatValues = FloatArray(3 * inputSize * inputSize)
        var idx = 0
        for (ch in 0 until 3) {
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val pixel = pixels[y * inputSize + x]
                    val value = when (ch) {
                        0 -> (pixel shr 16 and 0xFF) / 255.0f  // R
                        1 -> (pixel shr 8 and 0xFF) / 255.0f   // G
                        else -> (pixel and 0xFF) / 255.0f       // B
                    }
                    floatValues[idx++] = value
                }
            }
        }

        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        return OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(floatValues), shape)
    }

    /**
     * 后处理: 解析 YOLO 输出 → 过滤 → NMS → List<Detection>
     *
     * @param numFeatures 4 + numClasses (e.g. 6 for 2 classes)
     * @param numDetections 检测数量 (e.g. 8400)
     */
    private fun postprocess(
        output: FloatArray,
        numFeatures: Int,
        numDetections: Int,
        imgWidth: Int,
        imgHeight: Int
    ): List<Detection> {
        val candidates = mutableListOf<Detection>()
        val numClasses = numFeatures - 4

        Log.d(TAG, "postprocess: numFeatures=$numFeatures, numClasses=$numClasses, numDetections=$numDetections, confThreshold=$confThreshold")

        // 统计分数分布，帮助调参
        var above03 = 0; var above05 = 0; var above07 = 0; var above09 = 0

        for (i in 0 until numDetections) {
            val cx = output[0 * numDetections + i]
            val cy = output[1 * numDetections + i]
            val w = output[2 * numDetections + i]
            val h = output[3 * numDetections + i]

            var maxScore = 0f
            var bestClass = 0
            for (c in 0 until numClasses) {
                val logit = output[(4 + c) * numDetections + i]
                val score = sigmoid(logit)
                if (score > maxScore) {
                    maxScore = score
                    bestClass = c
                }
            }

            if (maxScore > 0.3f) above03++
            if (maxScore > 0.5f) above05++
            if (maxScore > 0.7f) above07++
            if (maxScore > 0.9f) above09++

            if (maxScore < confThreshold) continue

            val left = (cx - w / 2) * imgWidth / inputSize
            val top = (cy - h / 2) * imgHeight / inputSize
            val right = (cx + w / 2) * imgWidth / inputSize
            val bottom = (cy + h / 2) * imgHeight / inputSize

            candidates.add(
                Detection(
                    bbox = RectF(left, top, right, bottom),
                    confidence = maxScore,
                    classId = bestClass
                )
            )
        }

        Log.d(TAG, "Score distribution: >0.3=$above03, >0.5=$above05, >0.7=$above07, >0.9=$above09")
        Log.d(TAG, "postprocess: ${candidates.size} candidates before NMS")
        val nmsResult = nms(candidates)
        val finalResult = if (nmsResult.size > maxDetections) {
            Log.w(TAG, "Capping ${nmsResult.size} detections to $maxDetections")
            nmsResult.take(maxDetections)
        } else nmsResult
        Log.d(TAG, "postprocess: final ${finalResult.size} detections")
        return finalResult
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + Math.exp((-x).toDouble()))).toFloat()

    /**
     * Non-Maximum Suppression
     */
    private fun nms(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            sorted.removeAll { other ->
                iou(best.bbox, other.bbox) >= iouThreshold
            }
        }

        Log.d(TAG, "NMS: ${detections.size} -> ${result.size} detections")
        return result
    }

    /**
     * Intersection over Union
     */
    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = aArea + bArea - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
        isLoaded = false
    }

    companion object {
        private const val TAG = "YoloDetector"
    }
}
