package com.example.inventorymaster.ai.local

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class ModelFileManager(private val context: Context) {

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val supportedExtensions = setOf("litertlm", "gguf")

    /** 模型落盘文件名：目录条目优先（含官方文件名），未收录模型回退 <modelId>.litertlm */
    fun modelFile(modelId: String): File {
        val fileName = LocalModelCatalog.byId(modelId)?.fileName ?: "$modelId.litertlm"
        return File(modelsDir, fileName)
    }

    fun isModelAvailable(modelId: String): Boolean {
        val file = modelFile(modelId)
        return file.exists() && file.length() > 0
    }

    fun getModelPath(modelId: String): String = modelFile(modelId).absolutePath

    suspend fun downloadModel(
        modelId: String,
        url: String,
        expectedSha256: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = modelFile(modelId)
            val existingBytes = if (file.exists()) file.length() else 0L

            val connection = URL(url).openConnection()
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            connection.inputStream.use { input ->
                FileOutputStream(file, existingBytes > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = existingBytes
                    val contentLength = connection.contentLengthLong + existingBytes

                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            val actualHash = file.sha256()
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                file.delete()
                throw SecurityException("SHA-256 mismatch: expected=$expectedSha256, actual=$actualHash")
            }

            Log.i("ModelFileManager", "Model downloaded: $modelId (${file.length()} bytes)")
            file
        }
    }

    /**
     * SAF 手动导入：从用户选择的文件复制到模型目录。
     * 目录条目存在时校验文件大小（sha256 全量读 2.6GB 太慢，导入链路只做大小校验）。
     */
    suspend fun importModel(modelId: String, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = modelFile(modelId)
            val expected = LocalModelCatalog.byId(modelId)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file, false).use { output ->
                    input.copyTo(output, 8192)
                }
            } ?: throw IllegalStateException("无法读取所选文件")

            if (file.length() == 0L) {
                file.delete()
                throw IllegalStateException("所选文件为空")
            }
            if (expected != null && file.length() != expected.sizeBytes) {
                val actual = file.length()
                file.delete()
                throw IllegalStateException(
                    "文件大小不匹配（期望 ${expected.sizeBytes / (1024 * 1024)}MB，实际 ${actual / (1024 * 1024)}MB）：" +
                        "请确认选择的是 ${expected.fileName}"
                )
            }

            Log.i("ModelFileManager", "Model imported: $modelId (${file.length()} bytes)")
            file
        }
    }

    fun deleteModel(modelId: String) {
        modelFile(modelId).delete()
        Log.i("ModelFileManager", "Model deleted: $modelId")
    }

    fun listDownloadedModels(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.extension.lowercase() in supportedExtensions }
            ?.map { it.name }
            ?: emptyList()
    }

    fun getCacheSizeMb(): Long {
        val totalBytes = modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
        return totalBytes / (1024 * 1024)
    }

    fun clearAll() {
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = this.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
