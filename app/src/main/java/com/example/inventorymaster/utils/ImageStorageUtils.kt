package com.example.inventorymaster.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.DashPathEffect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.core.util.BarcodeClassifier
import com.example.inventorymaster.batchscanner.core.util.BarcodeColorCategory
import java.io.File
import java.io.FileOutputStream

// 建议放在 utils 包下
object ImageStorageUtils {
    fun saveBitmapToInternal(context: Context, bitmap: Bitmap): Uri? {
        // 定义文件夹路径：/data/user/0/包名/files/inventory_photos
        val folder = File(context.filesDir, "inventory_photos")
        if (!folder.exists()) {
            folder.mkdirs() // 如果文件夹不存在，自动创建
        }

        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val file = File(folder, fileName)

        return try {
            val fos = FileOutputStream(file)
            // 降低采样率压缩存储，质量 80 既清晰又省空间
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            fos.flush()
            fos.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存原始 Bitmap 到系统相册（Pictures/InventoryMaster），返回 MediaStore Uri
     * 用于拍照原图回流相册（解码率对比测试/留档）；minSdk 29，RELATIVE_PATH 免存储权限
     * @param displayName 不含扩展名的文件名
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/InventoryMaster")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                // 质量拉满：这张图的用途是还原"解码管线看到的原始输入"
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 在 Bitmap 上绘制条码标记（淡蓝/淡红/淡黄分类框），返回带标记的图片副本
     * @param original 原始图片 Bitmap
     * @param barcodes 需要在图片上绘制的条码列表（含坐标和状态）
     * @return 带标记的 Bitmap 副本（mutable，可直接保存）
     */
    fun drawBarcodesOnBitmap(original: Bitmap, barcodes: List<GlobalBarcode>): Bitmap {
        // 1. 创建可变的图片副本
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)

        // 根据图片分辨率动态缩放标记的大小
        val scaleFactor = bitmap.width / 1920f

        val classifiedList = BarcodeClassifier.classify(barcodes)
        val categoryMap = classifiedList.associateBy { it.barcode }

        barcodes.forEach { barcode ->
            val box = barcode.globalBoundingBox
            val category = categoryMap[barcode]?.category ?: BarcodeColorCategory.MAJORITY_STANDARD

            val (strokeColor, fillColor) = when (category) {
                BarcodeColorCategory.MAJORITY_STANDARD -> Pair(
                    android.graphics.Color.rgb(30, 136, 229), // #1E88E5 蓝
                    android.graphics.Color.argb(40, 33, 150, 243)
                )
                BarcodeColorCategory.MAJORITY_DIFF_BATCH -> Pair(
                    android.graphics.Color.rgb(229, 57, 53), // #E53935 红
                    android.graphics.Color.argb(40, 244, 67, 54)
                )
                BarcodeColorCategory.OTHER_MATERIAL -> Pair(
                    android.graphics.Color.rgb(255, 160, 0), // #FFA000 黄
                    android.graphics.Color.argb(45, 255, 179, 0)
                )
            }

            // 绘制底色填充
            val fillPaint = Paint().apply {
                color = fillColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRect(
                box.left.toFloat(), box.top.toFloat(),
                box.right.toFloat(), box.bottom.toFloat(),
                fillPaint
            )

            // 2. 绘制外边框
            val borderPaint = Paint().apply {
                color = strokeColor
                style = Paint.Style.STROKE
                strokeWidth = 6f * scaleFactor
                isAntiAlias = true
            }
            canvas.drawRect(
                box.left.toFloat(), box.top.toFloat(),
                box.right.toFloat(), box.bottom.toFloat(),
                borderPaint
            )

        }

        return bitmap
    }

    /**
     * 在已标记条码的 Bitmap 上追加绘制 OCR 选框（黄色虚线）
     * @param bitmap 已绘制条码标记的 Bitmap（会被就地修改）
     * @param ocrCropRects OCR 选框列表（原图像素坐标）
     * @return 同一 Bitmap 引用（方便链式调用）
     */
    fun drawOcrCropRectsOnBitmap(bitmap: Bitmap, ocrCropRects: List<android.graphics.Rect>): Bitmap {
        if (ocrCropRects.isEmpty()) return bitmap
        val canvas = Canvas(bitmap)
        val scaleFactor = bitmap.width / 1920f

        val strokePaint = Paint().apply {
            color = android.graphics.Color.rgb(255, 235, 59) // 黄色
            style = Paint.Style.STROKE
            strokeWidth = 5f * scaleFactor
            isAntiAlias = true
            pathEffect = DashPathEffect(floatArrayOf(20f * scaleFactor, 10f * scaleFactor), 0f)
        }
        val fillPaint = Paint().apply {
            color = android.graphics.Color.argb(38, 255, 235, 59) // 半透明黄色
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        ocrCropRects.forEach { rect ->
            val r = rect.left.toFloat()
            val t = rect.top.toFloat()
            val b = rect.right.toFloat()
            val bot = rect.bottom.toFloat()
            canvas.drawRect(r, t, b, bot, fillPaint)
            canvas.drawRect(r, t, b, bot, strokePaint)
        }

        return bitmap
    }
}
