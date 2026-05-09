package com.example.inventorymaster.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.example.inventorymaster.batchscanner.GlobalBarcode
import com.example.inventorymaster.batchscanner.ScanStatus
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
     * 在 Bitmap 上绘制条码标记（绿框 ✔ / 红框 ✘），返回带标记的图片副本
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

        barcodes.forEach { barcode ->
            val box = barcode.globalBoundingBox

            // 根据匹配状态选择颜色
            val strokeColor = if (barcode.status == ScanStatus.MATCHED) {
                android.graphics.Color.GREEN
            } else {
                android.graphics.Color.RED
            }

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

            // 3. 绘制右上角的状态图标 (✔ 或 ✘)
            val iconSize = 40f * scaleFactor
            val iconPadding = 10f * scaleFactor
            val iconX = box.right.toFloat() - iconSize - iconPadding
            val iconY = box.top.toFloat() + iconPadding

            val iconPaint = Paint().apply {
                color = strokeColor
                style = Paint.Style.STROKE
                strokeWidth = 8f * scaleFactor
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            val path = Path()
            if (barcode.status == ScanStatus.MATCHED) {
                // 画 ✔
                path.moveTo(iconX, iconY + iconSize * 0.5f)
                path.lineTo(iconX + iconSize * 0.4f, iconY + iconSize)
                path.lineTo(iconX + iconSize, iconY)
            } else if (barcode.status == ScanStatus.MISMATCHED) {
                // 画 ✘
                path.moveTo(iconX, iconY)
                path.lineTo(iconX + iconSize, iconY + iconSize)
                path.moveTo(iconX + iconSize, iconY)
                path.lineTo(iconX, iconY + iconSize)
            }
            canvas.drawPath(path, iconPaint)
        }

        return bitmap
    }
}
