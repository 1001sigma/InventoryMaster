package com.example.inventorymaster.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 解码结果
 */
data class DecodedBarcode(
    val value: String,
    val format: Int,
    val boundingBox: android.graphics.Rect? = null,
    val confidence: Float = 1f,
    val engine: String = "unknown"
)

/**
 * 解码引擎接口
 */
interface BarcodeDecoder {
    suspend fun decode(bitmap: Bitmap): List<DecodedBarcode>
    val engineName: String
}

/**
 * ML Kit 解码器（第一梯队）
 */
class MlKitDecoder : BarcodeDecoder {

    override val engineName = "ML Kit"

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    override suspend fun decode(bitmap: Bitmap): List<DecodedBarcode> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val barcodes = processImage(inputImage)
            barcodes.mapNotNull { barcode ->
                val value = barcode.displayValue ?: return@mapNotNull null
                DecodedBarcode(
                    value = value,
                    format = barcode.format,
                    boundingBox = barcode.boundingBox,
                    confidence = 1f,
                    engine = engineName
                )
            }
        } catch (e: Exception) {
            Log.e("MlKitDecoder", "Decode failed", e)
            emptyList()
        }
    }

    private suspend fun processImage(inputImage: InputImage): List<Barcode> =
        suspendCancellableCoroutine { continuation ->
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    continuation.resume(barcodes)
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }

    /** 供统一门面使用底层 ML Kit Task（回调风格） */
    fun processTask(inputImage: InputImage) = scanner.process(inputImage)

    fun close() {
        scanner.close()
    }
}

/**
 * 多引擎解码链
 * 按优先级尝试多个解码引擎，首个成功即返回
 */
class DecoderChain(private val decoders: List<BarcodeDecoder>) {

    /**
     * 带降级的解码
     * @return 找到的第一个有效结果，全部失败返回空列表
     */
    suspend fun decodeWithFallback(bitmap: Bitmap): List<DecodedBarcode> {
        for (decoder in decoders) {
            try {
                val results = decoder.decode(bitmap)
                if (results.isNotEmpty()) {
                    Log.d("DecoderChain", "[${decoder.engineName}] found ${results.size} barcodes: ${results.joinToString { it.value }}")
                    return results
                } else {
                    Log.d("DecoderChain", "[${decoder.engineName}] found 0 barcodes, trying next...")
                }
            } catch (e: Exception) {
                Log.w("DecoderChain", "[${decoder.engineName}] failed with exception", e)
            }
        }
        Log.d("DecoderChain", "All decoders returned 0 results")
        return emptyList()
    }
}

/**
 * 统一解码门面（进程级单例）
 *
 * 全 app 所有条码解码的唯一入口，此前 5 处各自创建的 ML Kit client 均已收口到这里：
 * - 实时帧走 [decodeFrame]/[decodeFrameAwait]：仅 ML Kit（libdmtx 不做逐帧兜底，
 *   否则空画面每帧都会白跑一遍 native 解码）
 * - 静态图走 [decodeStill]：ML Kit → libdmtx 全链，内部强制切 Default 线程
 */
object Decoder {

    /** 全 app 唯一的 ML Kit client（7 码制全格式表，进程级复用） */
    val mlKit: MlKitDecoder by lazy { MlKitDecoder() }

    private val chain: DecoderChain by lazy {
        DecoderChain(listOf(mlKit, LibDmtxDecoder()))
    }

    /** 实时帧解码：回调风格，适配 ImageAnalysis.Analyzer（imageProxy 生命周期由调用方管理） */
    fun decodeFrame(
        image: InputImage,
        onSuccess: (List<Barcode>) -> Unit = {},
        onFailure: (Exception) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        mlKit.processTask(image)
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
            .addOnCompleteListener { onComplete() }
    }

    /** 实时帧解码：挂起风格，失败返回空列表 */
    suspend fun decodeFrameAwait(image: InputImage): List<Barcode> =
        suspendCancellableCoroutine { continuation ->
            mlKit.processTask(image)
                .addOnSuccessListener { barcodes ->
                    if (continuation.isActive) continuation.resume(barcodes)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }

    /** 静态图解码：ML Kit → libdmtx 全链 */
    suspend fun decodeStill(bitmap: Bitmap): List<DecodedBarcode> = withContext(Dispatchers.Default) {
        chain.decodeWithFallback(bitmap)
    }
}
