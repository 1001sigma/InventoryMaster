package com.example.inventorymaster.utils.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 电子签名库：手绘签名以透明底 PNG 形式持久化到 filesDir/signatures/，
 * 供 PDF 导出时盖章与「电子签名库」设置页管理。
 *
 * 存储策略对齐 ImageToolbox 的 AndroidPdfHelper：文件名 signature_<时间戳>.png，
 * 按最后修改时间倒序展示，数量超过上限时淘汰最旧的签名。
 */
class SignatureStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val updateMutex = Mutex()

    private val _signatures = MutableStateFlow<List<File>>(emptyList())

    /** 全部已存签名（File），按保存时间倒序（最新在前） */
    val signatures: StateFlow<List<File>> = _signatures.asStateFlow()

    private val signaturesDir: File
        get() = File(appContext.filesDir, "signatures").apply { mkdirs() }

    init {
        refresh()
    }

    /** 重新扫描签名目录并更新 StateFlow */
    fun refresh() {
        _signatures.value = signaturesDir
            .listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
    }

    /**
     * 保存一张签名为透明底 PNG。超出容量上限时自动删除最旧的一份。
     * @return 保存成功的文件，失败返回 null
     */
    suspend fun saveSignature(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            runCatching {
                val target = File(signaturesDir, "signature_${System.currentTimeMillis()}.png")
                target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // 容量淘汰：保存后超限则删最旧（列表倒序，末尾即最旧）
                val all = signaturesDir.listFiles { f -> f.isFile && f.extension.equals("png", true) }
                    .orEmpty()
                    .sortedByDescending { it.lastModified() }
                all.drop(SIGNATURES_LIMIT).forEach { it.delete() }
                refresh()
                target
            }.getOrNull()
        }
    }

    /** 删除指定签名文件 */
    suspend fun deleteSignature(file: File): Boolean = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            refresh()
            deleted
        }
    }

    /** 按目标尺寸解码签名缩略图（约 2 倍屏显尺寸，避免 OOM） */
    fun decodeThumbnail(file: File, targetSizePx: Int): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= targetSizePx) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }.getOrNull()

    companion object {
        /** 签名库容量上限，超限淘汰最旧（对齐 ImageToolbox SIGNATURES_LIMIT） */
        private const val SIGNATURES_LIMIT = 20

        @Volatile
        private var instance: SignatureStore? = null

        /** App 级单例（同 AppDatabase.getDatabase 模式） */
        fun get(context: Context): SignatureStore =
            instance ?: synchronized(this) {
                instance ?: SignatureStore(context.applicationContext).also { instance = it }
            }
    }
}
