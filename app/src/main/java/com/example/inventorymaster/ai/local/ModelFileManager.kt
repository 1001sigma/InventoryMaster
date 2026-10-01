package com.example.inventorymaster.ai.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class ModelFileManager(private val context: Context) {

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    fun isModelAvailable(modelId: String): Boolean {
        val file = File(modelsDir, "$modelId.gguf")
        return file.exists() && file.length() > 0
    }

    fun getModelPath(modelId: String): String {
        return File(modelsDir, "$modelId.gguf").absolutePath
    }

    suspend fun downloadModel(
        modelId: String,
        url: String,
        expectedSha256: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(modelsDir, "$modelId.gguf")
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

    fun deleteModel(modelId: String) {
        File(modelsDir, "$modelId.gguf").delete()
        Log.i("ModelFileManager", "Model deleted: $modelId")
    }

    fun listDownloadedModels(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.extension == "gguf" }
            ?.map { it.nameWithoutExtension }
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
