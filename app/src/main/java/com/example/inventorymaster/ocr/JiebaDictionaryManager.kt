package com.example.inventorymaster.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.huaban.analysis.jieba.WordDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

class JiebaDictionaryManager : DictionaryManager {
    private var isDictLoaded = false

    override suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        if (isDictLoaded) return@withContext

        try {
            val fileName = "user_dict.txt"
            val cacheFile = File(context.cacheDir, fileName)

            context.assets.open(fileName).use { inputStream ->
                cacheFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val path = Paths.get(cacheFile.absolutePath)
            WordDictionary.getInstance().loadUserDict(path)

            isDictLoaded = true
            Log.d("JiebaDictManager", "自定义词典加载成功！路径: ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("JiebaDictManager", "自定义词典加载失败: ${e.message}")
        }
    }

    override suspend fun updateDict(context: Context, uri: Uri): Unit = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(context.cacheDir, "user_dict.txt")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            reload(targetFile)
            Log.d("JiebaDictManager", "词典更新成功！")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        Unit
    }

    private fun reload(file: File) {
        val path = Paths.get(file.absolutePath)
        WordDictionary.getInstance().loadUserDict(path)
        isDictLoaded = true
    }
}
