package com.example.inventorymaster.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FileConvertUtils {
    /**
     * 将 Uri 复制到私有缓存目录用于 Retrofit 上传
     * @return 临时 File，上传完毕后记得调用 delete() 删除
     */
    fun uriToTempFile(context: Context, uri: Uri, fileName: String = "upload_temp.pdf"): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, fileName)
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}