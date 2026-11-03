package com.example.inventorymaster.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibDmtxDecoder : BarcodeDecoder {

    override val engineName = "libdmtx"

    companion object {
        // 全局串行化 native 解码：Decoder 门面与 VisionManager 各自持有 LibDmtxDecoder 实例，
        // 实例级 Mutex 挡不住跨实例并发；libdmtx 多线程安全性没有官方承诺，宁可全局排队
        private val nativeMutex = Mutex()
    }

    override suspend fun decode(bitmap: Bitmap): List<DecodedBarcode> = nativeMutex.withLock {
        if (bitmap.isRecycled) return@withLock emptyList()
        try {
            // 不依赖 OpenCV：金字塔降级路径正是 OpenCV 未加载（init 失败/useOpenCV 关闭）时才走，
            // 此前用 Utils.bitmapToMat 会在该场景抛 UnsatisfiedLinkError 导致闪退
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val gray = ByteArray(width * height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // ITU-R BT.601 亮度（与原 COLOR_RGBA2GRAY 一致）
                gray[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }

            val results = LibDmtxNative.nativeDecode(gray, width, height)

            results.map { value ->
                DecodedBarcode(
                    value = value,
                    format = Barcode.FORMAT_DATA_MATRIX,
                    boundingBox = null,
                    confidence = 1f,
                    engine = engineName
                )
            }
        } catch (e: Exception) {
            Log.e("LibDmtxDecoder", "Decode failed", e)
            emptyList()
        }
    }
}
