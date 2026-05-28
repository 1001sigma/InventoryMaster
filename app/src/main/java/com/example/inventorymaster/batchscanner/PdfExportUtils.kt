package com.example.inventorymaster.batchscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.inventorymaster.utils.Gs1Parser
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtils {

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - MARGIN * 2
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generatePdf(
        context: Context,
        outputStream: OutputStream,
        documents: List<TargetDocument>,
        photoResults: List<PhotoScanResult>,
        recordList: List<InventoryRecord>
    ) {
        val doc = PdfDocument()

        val titlePaint = Paint().apply {
            textSize = 20f; isFakeBoldText = true; isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 13f; isFakeBoldText = true; isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            textSize = 10f; isAntiAlias = true
        }
        val linePaint = Paint().apply {
            strokeWidth = 0.5f; isAntiAlias = true
        }
        val footnotePaint = Paint().apply {
            textSize = 8f; color = android.graphics.Color.GRAY; isAntiAlias = true
        }

        // ========== Page 1: Document Summary ==========
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN

        // Title
        // Title (拼接多个单据的名称)
        val docNames = if (documents.isEmpty()) "无单据" else documents.joinToString(", ") { it.documentName }
        val title = "单据报告 — $docNames"
        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 30f

        // Date
        canvas.drawText("导出时间: ${dateFormat.format(Date())}", MARGIN, y, footnotePaint)
        y += 20f

        if (documents.isNotEmpty()) {
            // 遍历渲染每一个单据
            for (document in documents) {
                // 为了区分不同单据，加粗单据名称
                canvas.drawText("■ 单据名称: ${document.documentName}", MARGIN, y, headerPaint)
                y += 16f
                canvas.drawText("单据编号: ${document.documentId}", MARGIN, y, bodyPaint)
                y += 16f
                canvas.drawText("单据日期: ${document.documentDate}", MARGIN, y, bodyPaint)
                y += 16f
                canvas.drawText("明细项数: ${document.items.size}", MARGIN, y, bodyPaint)
                y += 24f

                // Table header
                drawTableRow(canvas, y, headerPaint, bodyPaint,  listOf("DI", "批号", "物料名称", "生产日期", "失效日期", "应发数量"), floatArrayOf(110f, 70f, 130f, 65f, 65f, 40f))
                y += 22f

                // Table rows
                for (item in document.items) {
                    drawTableRow(canvas, y, headerPaint, bodyPaint,
                        listOf(item.di, item.batch, item.productName, item.productionDate ?: "-", item.expiryDate ?: "-",  item.targetQty.toString()),
                        floatArrayOf(110f, 70f, 130f, 65f, 65f, 40f))
                    y += 18f
                    if (y > PAGE_H - 60f) {
                        doc.finishPage(page)
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageInfo.pageNumber + 1).create()
                        page = doc.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN
                    }
                }
                y += 30f // 每个单据表格画完后，留出一些间距给下一个单据
            }
        } else {
            canvas.drawText("无单据", MARGIN + CONTENT_W / 2 - 20f, PAGE_H / 2, headerPaint)
        }

        doc.finishPage(page)

        // ========== Inventory Results Section ==========
        if (recordList.isNotEmpty()) {
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageInfo.pageNumber + 1).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN

            canvas.drawText("盘点结果明细", MARGIN, y, titlePaint)
            y += 30f

            val errorPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = android.graphics.Color.RED }
            val warnPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = android.graphics.Color.rgb(200, 150, 0) }
            val matchPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = android.graphics.Color.rgb(0, 128, 0) }

            val resultColWidths = floatArrayOf(90f, 75f, 55f, 48f, 48f, 36f, 36f, 87f)
            val resultHeaders = listOf("产品名称", "DI", "批号", "生产日期", "失效日期", "目标", "实际", "状态")

            val groupedRecords = recordList.groupBy { it.documentName }

            for ((docName, records) in groupedRecords) {
                if (y > PAGE_H - 80f) {
                    doc.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageInfo.pageNumber + 1).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN
                }

                canvas.drawText("■ $docName", MARGIN, y, headerPaint)
                y += 20f

                drawTableRow(canvas, y, headerPaint, bodyPaint, resultHeaders, resultColWidths)
                y += 22f

                for (record in records) {
                    val statusLabel = when (record.status) {
                        RecordStatus.ERROR -> if (record.errorMessage.isNotBlank()) "✘ ${record.errorMessage}" else "✘ 异常"
                        RecordStatus.MISSING -> "⚠ 数量不足"
                        RecordStatus.OVERFLOW -> "⚠ 数量超出"
                        RecordStatus.MATCHED -> "✔ 已匹配"
                        RecordStatus.NORMAL -> "✔ 正常"
                    }

                    val rowPaint = when (record.status) {
                        RecordStatus.ERROR -> errorPaint
                        RecordStatus.MISSING, RecordStatus.OVERFLOW -> warnPaint
                        RecordStatus.MATCHED -> matchPaint
                        RecordStatus.NORMAL -> bodyPaint
                    }

                    drawTableRow(canvas, y, headerPaint, rowPaint,
                        listOf(record.productName, record.di, record.batch,
                            record.productionDate ?: "-", record.expiryDate ?: "-",
                            record.targetQty.toString(), record.actualQty.toString(),
                            statusLabel),
                        resultColWidths)
                    y += 18f

                    if (y > PAGE_H - 60f) {
                        doc.finishPage(page)
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageInfo.pageNumber + 1).create()
                        page = doc.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN
                        // 换页后重绘表头
                        drawTableRow(canvas, y, headerPaint, bodyPaint, resultHeaders, resultColWidths)
                        y += 22f
                    }
                }

                y += 16f
            }

            doc.finishPage(page)
        }

        // ========== Photo Pages ==========
        val photoCount = photoResults.size
        var photoPageNum = pageInfo.pageNumber + 1

        for ((idx, result) in photoResults.withIndex()) {
            var photoCanvas: Canvas
            var photoY: Float

            pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), photoPageNum).create()
            page = doc.startPage(pageInfo)
            photoCanvas = page.canvas
            photoY = MARGIN

            // Page header
            photoCanvas.drawText("照片 ${idx + 1} / $photoCount", MARGIN, photoY, headerPaint)
            photoY += 25f

            // Load and draw photo
            val photo = loadFullBitmap(context, result.photoUri)
            if (photo != null) {
                val maxW = CONTENT_W
                val maxH = PAGE_H * 0.55f
                val scale = minOf(maxW / photo.width, maxH / photo.height)
                val drawW = photo.width * scale
                val drawH = photo.height * scale

                photoCanvas.drawBitmap(photo, null,
                    android.graphics.RectF(MARGIN, photoY, MARGIN + drawW, photoY + drawH), null)
                photoY += drawH + 20f

                // Photo info
                photoCanvas.drawText("原图: ${photo.width}×${photo.height}  |  条码: ${result.barcodes.size} 个",
                    MARGIN, photoY, footnotePaint)
                photoY += 14f

                photo.recycle()
            } else {
                photoCanvas.drawText("(照片加载失败)", MARGIN, photoY, bodyPaint)
                photoY += 20f
            }

            // Barcode data table
            photoCanvas.drawText("扫码明细:", MARGIN, photoY, headerPaint)
            photoY += 18f

            drawTableRow(photoCanvas, photoY, headerPaint, bodyPaint,
                listOf("#", "条码内容", "坐标 (x,y,w,h)", "DI", "批号", "状态"),
                floatArrayOf(24f, 170f, 110f, 95f, 65f, 40f))
            photoY += 22f

            for ((bi, barcode) in result.barcodes.withIndex()) {
                val box = barcode.globalBoundingBox
                val coordStr = "${box.left},${box.top},${box.width()},${box.height()}"
                val parseResult = Gs1Parser.parse(barcode.displayValue)
                val di = parseResult.di ?: "-"
                val batch = parseResult.batch ?: "-"
                val statusLabel = if (barcode.status == ScanStatus.MATCHED) "✔" else "✘"

                val rowBodyPaint = Paint().apply {
                    textSize = 9f; isAntiAlias = true
                    if (barcode.status != ScanStatus.MATCHED) color = android.graphics.Color.RED
                }

                drawTableRow(photoCanvas, photoY, headerPaint, rowBodyPaint,
                    listOf("${bi + 1}", barcode.displayValue, coordStr, di, batch, statusLabel),
                    floatArrayOf(24f, 170f, 110f, 95f, 65f, 40f))
                photoY += 16f

                if (photoY > PAGE_H - 50f) {
                    doc.finishPage(page)
                    photoPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), photoPageNum).create()
                    page = doc.startPage(pageInfo)
                    photoCanvas = page.canvas
                    photoY = MARGIN
                }
            }

            // Page footer
            photoCanvas.drawText("第 ${photoPageNum} 页", MARGIN, PAGE_H - 20f, footnotePaint)

            doc.finishPage(page)
            photoPageNum++
        }

        doc.writeTo(outputStream)
        doc.close()
    }

    private fun loadFullBitmap(context: Context, uri: android.net.Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawTableRow(
        canvas: Canvas, y: Float,
        headerPaint: Paint, bodyPaint: Paint,
        cells: List<String>, widths: FloatArray
    ) {
        var x = MARGIN
        for (i in cells.indices) {
            val paint = if (i == cells.lastIndex && (cells[i] == "✔" || cells[i] == "✘")) Paint(bodyPaint) else bodyPaint
            // Truncate text if too wide
            val maxTextWidth = widths[i] - 4f
            val text = truncateText(cells[i], paint, maxTextWidth)
            canvas.drawText(text, x + 2f, y + 12f, paint)
            x += widths[i]
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var result = text
        while (result.length > 3 && paint.measureText("$result...") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result..."
    }
}
