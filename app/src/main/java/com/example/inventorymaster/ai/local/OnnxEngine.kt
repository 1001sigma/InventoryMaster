package com.example.inventorymaster.ai.local

import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

class OnnxEngine : ModelEngine {

    override val engineType = EngineType.ONNX
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var loaded = false
    private var inputSize = 640

    override suspend fun initialize(config: ModelConfig): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = File(config.modelPath)
            if (!modelFile.exists()) {
                throw ModelLoadException("Model file not found: ${config.modelPath}")
            }
            val modelBytes = modelFile.readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                when (config.hardwareAccel) {
                    HardwareAccel.AUTO, HardwareAccel.GPU, HardwareAccel.NPU -> {
                        try {
                            addNnapi()
                        } catch (_: Exception) {
                            // NNAPI not available, fall back to CPU
                        }
                    }
                    HardwareAccel.CPU -> { /* default */ }
                }
            }
            ortSession = ortEnv!!.createSession(modelBytes, sessionOptions)
            loaded = true
            Log.i("OnnxEngine", "Model loaded: ${config.modelPath}")
        }
    }

    override fun isLoaded() = loaded

    override fun release() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
        loaded = false
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        if (!loaded || ortEnv == null || ortSession == null) return emptyList()

        return try {
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
                            0 -> (pixel shr 16 and 0xFF) / 255.0f
                            1 -> (pixel shr 8 and 0xFF) / 255.0f
                            else -> (pixel and 0xFF) / 255.0f
                        }
                        floatValues[idx++] = value
                    }
                }
            }

            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(floatValues), shape)

            val inputName = ortSession!!.inputNames.first()
            val results = ortSession!!.run(mapOf(inputName to inputTensor))
            val outputTensor = results.first().value as OnnxTensor
            val output = outputTensor.floatBuffer.array()
            inputTensor.close()

            val shapeInfo = outputTensor.info.shape ?: return emptyList()
            val numFeatures = shapeInfo[1].toInt()
            val numDetections = shapeInfo[2].toInt()

            postprocess(output, numFeatures, numDetections, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e("OnnxEngine", "Detection failed", e)
            emptyList()
        }
    }

    private fun postprocess(
        output: FloatArray,
        numFeatures: Int,
        numDetections: Int,
        imgWidth: Int,
        imgHeight: Int,
        confThreshold: Float = 0.7f,
        iouThreshold: Float = 0.45f
    ): List<Detection> {
        val candidates = mutableListOf<Detection>()
        val numClasses = numFeatures - 4

        for (i in 0 until numDetections) {
            val cx = output[0 * numDetections + i]
            val cy = output[1 * numDetections + i]
            val w = output[2 * numDetections + i]
            val h = output[3 * numDetections + i]

            var maxScore = 0f
            var bestClass = 0
            for (c in 0 until numClasses) {
                val logit = output[(4 + c) * numDetections + i]
                val score = 1f / (1f + Math.exp((-logit).toDouble())).toFloat()
                if (score > maxScore) {
                    maxScore = score
                    bestClass = c
                }
            }

            if (maxScore < confThreshold) continue

            val left = (cx - w / 2) * imgWidth / inputSize
            val top = (cy - h / 2) * imgHeight / inputSize
            val right = (cx + w / 2) * imgWidth / inputSize
            val bottom = (cy + h / 2) * imgHeight / inputSize

            candidates.add(Detection(left, top, right, bottom, maxScore, bestClass))
        }

        return nms(candidates, iouThreshold)
    }

    private fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { other ->
                iou(best, other) >= iouThreshold
            }
        }
        return result
    }

    private fun iou(a: Detection, b: Detection): Float {
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
}
