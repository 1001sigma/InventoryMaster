package com.example.inventorymaster.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Uri.toBitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, this@toBitmap)
            ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, false)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, this@toBitmap)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Bitmap.downsample(maxDimension: Int = 1920): Bitmap {
    val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height, 1f)
    if (ratio >= 1f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * ratio).toInt(),
        (height * ratio).toInt(),
        true
    )
}

fun Uri.toInputImage(context: Context): InputImage? {
    return try {
        InputImage.fromFilePath(context, this)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
