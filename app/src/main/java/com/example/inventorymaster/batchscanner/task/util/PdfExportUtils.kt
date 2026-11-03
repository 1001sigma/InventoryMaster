package com.example.inventorymaster.batchscanner.task.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.inventorymaster.batchscanner.core.models.ScanStatus
import com.example.inventorymaster.batchscanner.task.models.FieldStatus
import com.example.inventorymaster.batchscanner.task.models.InventoryRecord
import com.example.inventorymaster.batchscanner.task.models.PhotoScanResult
import com.example.inventorymaster.batchscanner.task.models.RowStatus
import com.example.inventorymaster.batchscanner.task.models.TargetDocument
import com.example.inventorymaster.utils.Gs1Parser
import com.example.inventorymaster.utils.DateUtils
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtils {

    // A4 纵向（pt）
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    // 行高节奏
    private const val ROW_H = 18f
    private const val HEADER_H = 22f
    private const val WRAP_LINE_H = 12f
    private const val SUB_LINE_H = 14f
    private const val FOOTER_TOP = 30f

    // 配色（印刷友好）
    private const val COLOR_HEADER_BG = 0xFFF2F2F2.toInt()
    private const val COLOR_GRID = 0xFFDDDDDD.toInt()
    private const val COLOR_ZEBRA = 0xFFFAFAFA.toInt()
    private const val COLOR_RED = 0xFFC62828.toInt()
    private const val COLOR_AMBER = 0xFFB26A00.toInt()
    private const val COLOR_GREEN = 0xFF2E7D32.toInt()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // ========== 分页上下文：统一翻页 + 每页页脚 ==========

    private class PdfPager(
        private val doc: PdfDocument,
        private val exportTime: String,
        private val footnotePaint: Paint,
    ) {
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        lateinit var canvas: Canvas
        var y = MARGIN

        fun newPage() {
            page?.let { finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun finish() {
            page?.let { finishPage(it) }
            page = null
        }

        /** 剩余空间不足时翻页；翻页后回调（通常用于补画表头） */
        fun ensureSpace(neededHeight: Float, onNewPage: () -> Unit = {}) {
            if (y + neededHeight <= PAGE_H - FOOTER_TOP) return
            newPage()
            onNewPage()
        }

        private fun finishPage(p: PdfDocument.Page) {
            p.canvas.drawText("导出时间: $exportTime", MARGIN, PAGE_H - 20f, footnotePaint)
            val pageText = "第 $pageNo 页"
            p.canvas.drawText(pageText, PAGE_W - MARGIN - footnotePaint.measureText(pageText), PAGE_H - 20f, footnotePaint)
            doc.finishPage(p)
        }
    }

    // ========== 表格绘制：表头底色 / 网格 / 斑马纹 / 列对齐 ==========

    private enum class ColAlign { LEFT, CENTER, RIGHT }

    private class TableSpec(
        val headers: List<String>,
        val widths: FloatArray,
        val aligns: List<ColAlign>,
    )

    private class TablePainter(
        private val pager: PdfPager,
        private val fillPaint: Paint,
        private val gridPaint: Paint,
        private val headerPaint: Paint,
    ) {
        fun drawHeader(spec: TableSpec) {
            fillPaint.color = COLOR_HEADER_BG
            pager.canvas.drawRect(MARGIN, pager.y, MARGIN + CONTENT_W, pager.y + HEADER_H, fillPaint)
            var x = MARGIN
            for (i in spec.headers.indices) {
                drawCellText(spec.headers[i], x, spec.widths[i], spec.aligns[i], pager.y + 2f, headerPaint)
                x += spec.widths[i]
            }
            pager.y += HEADER_H
            pager.canvas.drawLine(MARGIN, pager.y, MARGIN + CONTENT_W, pager.y, gridPaint)
            drawVerticals(spec, pager.y - HEADER_H, pager.y)
        }

        /** 画一行数据（空间不足自动翻页并补表头）；条码列可换行，返回实际行高 */
        fun drawRow(
            spec: TableSpec,
            cells: List<String>,
            paint: Paint,
            zebraIndex: Int,
            wrapCol: Int = -1,
            wrapPaint: Paint? = null,
            cellPaints: Map<Int, Paint> = emptyMap(),
        ): Float {
            val wrapLines = if (wrapCol >= 0 && wrapPaint != null) {
                wrapText(cells[wrapCol], wrapPaint, spec.widths[wrapCol] - 4f)
            } else null
            val rowH = wrapLines?.let { maxOf(ROW_H, it.size * WRAP_LINE_H + 6f) } ?: ROW_H

            pager.ensureSpace(rowH) { drawHeader(spec) }

            if (zebraIndex % 2 == 1) {
                fillPaint.color = COLOR_ZEBRA
                pager.canvas.drawRect(MARGIN, pager.y, MARGIN + CONTENT_W, pager.y + rowH, fillPaint)
            }

            var x = MARGIN
            for (i in cells.indices) {
                val cp = if (i == wrapCol && wrapPaint != null) wrapPaint else cellPaints[i] ?: paint
                if (i == wrapCol && wrapLines != null) {
                    var lineTop = pager.y
                    for (line in wrapLines) {
                        drawCellText(line, x, spec.widths[i], spec.aligns[i], lineTop, cp)
                        lineTop += WRAP_LINE_H
                    }
                } else {
                    drawCellText(truncateText(cells[i], cp, spec.widths[i] - 4f), x, spec.widths[i], spec.aligns[i], pager.y, cp)
                }
                x += spec.widths[i]
            }

            val bottom = pager.y + rowH
            pager.canvas.drawLine(MARGIN, bottom, MARGIN + CONTENT_W, bottom, gridPaint)
            drawVerticals(spec, pager.y, bottom)

            pager.y += rowH
            return rowH
        }

        private fun drawCellText(text: String, colX: Float, colW: Float, align: ColAlign, rowTop: Float, paint: Paint) {
            val tx = when (align) {
                ColAlign.LEFT -> colX + 2f
                ColAlign.CENTER -> colX + (colW - paint.measureText(text)) / 2f
                ColAlign.RIGHT -> colX + colW - 2f - paint.measureText(text)
            }
            pager.canvas.drawText(text, tx, rowTop + 12f, paint)
        }

        private fun drawVerticals(spec: TableSpec, top: Float, bottom: Float) {
            var vx = MARGIN
            for (i in 0..spec.widths.size) {
                pager.canvas.drawLine(vx, top, vx, bottom, gridPaint)
                if (i < spec.widths.size) vx += spec.widths[i]
            }
        }
    }

    fun generatePdf(
        context: Context,
        outputStream: OutputStream,
        documents: List<TargetDocument>,
        photoResults: List<PhotoScanResult>,
        recordList: List<InventoryRecord>,
        materialDiMap: Map<String, String> = emptyMap()
    ) {
        val doc = PdfDocument()
        val exportTime = dateFormat.format(Date())

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val sectionPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val tableHeaderPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { textSize = 10f; isAntiAlias = true }
        val footnotePaint = Paint().apply { textSize = 8f; color = Color.GRAY; isAntiAlias = true }
        val errorPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = COLOR_RED }
        val warnPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = COLOR_AMBER }
        val matchPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = COLOR_GREEN }
        val errorNotePaint = Paint().apply { textSize = 8f; isAntiAlias = true; color = COLOR_RED }
        val warnNotePaint = Paint().apply { textSize = 8f; isAntiAlias = true; color = COLOR_AMBER }
        val gridPaint = Paint().apply { strokeWidth = 0.5f; isAntiAlias = true; color = COLOR_GRID }
        val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

        val pager = PdfPager(doc, exportTime, footnotePaint)
        val painter = TablePainter(pager, fillPaint, gridPaint, tableHeaderPaint)
        pager.newPage()

        val hasDocuments = documents.isNotEmpty()
        val hasRecords = recordList.isNotEmpty()

        // ========== 完全无内容：保留单页占位，避免生成 0 页 PDF ==========
        if (!hasDocuments && !hasRecords && photoResults.isEmpty()) {
            pager.canvas.drawText("无单据", MARGIN + CONTENT_W / 2 - 20f, PAGE_H / 2, sectionPaint)
            pager.finish()
            doc.writeTo(outputStream)
            doc.close()
            return
        }

        // ========== 报告头（无单据时不再独占一页，后续内容直接同页续排） ==========
        val title = if (hasDocuments) "单据报告 — ${documents.joinToString(", ") { it.documentName }}" else "单据报告"
        pager.canvas.drawText(truncateText(title, titlePaint, CONTENT_W), MARGIN, pager.y + 18f, titlePaint)
        pager.y += 28f
        val meta = if (hasDocuments) {
            "导出时间: $exportTime · 单据 ${documents.size} 份 · 明细共 ${documents.sumOf { it.items.size }} 项"
        } else {
            "导出时间: $exportTime"
        }
        pager.canvas.drawText(meta, MARGIN, pager.y, footnotePaint)
        pager.y += 24f

        // ========== 单据摘要 ==========
        if (hasDocuments) {
            val summarySpec = TableSpec(
                listOf("DI", "批号", "物料名称", "生产日期", "失效日期", "应发数量"),
                floatArrayOf(110f, 70f, 130f, 65f, 65f, 40f),
                listOf(ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.RIGHT),
            )

            for (document in documents) {
                pager.ensureSpace(130f)

                pager.canvas.drawText("■ 单据名称: ${document.documentName}", MARGIN, pager.y, sectionPaint)
                pager.y += 16f
                pager.canvas.drawText("单据编号: ${document.documentId}", MARGIN, pager.y, bodyPaint)
                pager.y += 16f
                pager.canvas.drawText("单据日期: ${document.documentDate}", MARGIN, pager.y, bodyPaint)
                pager.y += 16f
                pager.canvas.drawText("明细项数: ${document.items.size}", MARGIN, pager.y, bodyPaint)
                pager.y += 12f

                painter.drawHeader(summarySpec)
                document.items.forEachIndexed { ri, item ->
                    painter.drawRow(summarySpec, listOf(
                        materialDiMap[item.materialCode] ?: "",
                        item.batch ?: "无批号",
                        item.productName ?: "-",
                        DateUtils.formatDateForDisplay(item.productionDate),
                        DateUtils.formatDateForDisplay(item.expiryDate),
                        item.targetQty.toString(),
                    ), bodyPaint, ri)
                }
                pager.y += 30f
            }
        } else {
            pager.canvas.drawText("无单据", MARGIN + CONTENT_W / 2 - 20f, PAGE_H / 2, sectionPaint)
        }

        // ========== 盘点结果明细（状态列已移除：行着色保留状态语义，原因以行下小字标注） ==========
        if (hasRecords) {
            // 有单据摘要时结果段另起一页；无单据时紧跟报告头，不空占一页
            if (hasDocuments) pager.newPage()
            pager.canvas.drawText("盘点结果明细", MARGIN, pager.y, titlePaint)
            pager.y += 26f

            val matchCount = recordList.count { it.rowStatus == RowStatus.PASS && it.targetQty > 0 }
            val normalCount = recordList.count { it.rowStatus == RowStatus.PASS && it.targetQty <= 0 }
            val warnCount = recordList.count { it.rowStatus == RowStatus.WARNING }
            val errorCount = recordList.count { it.rowStatus == RowStatus.ERROR }
            drawStatsLine(pager, listOf(
                Triple(COLOR_GREEN, "匹配", matchCount),
                Triple(Color.DKGRAY, "正常", normalCount),
                Triple(COLOR_AMBER, "警告", warnCount),
                Triple(COLOR_RED, "异常", errorCount),
            ), footnotePaint, fillPaint)
            pager.y += 24f

            val resultsSpec = TableSpec(
                listOf("产品名称", "DI", "批号", "生产日期", "失效日期", "目标", "实际"),
                floatArrayOf(100f, 90f, 60f, 62f, 62f, 52f, 54f),
                listOf(ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.RIGHT, ColAlign.RIGHT),
            )

            val groupedRecords = recordList.groupBy { it.documentName }
            for ((docName, records) in groupedRecords) {
                pager.ensureSpace(80f)

                pager.canvas.drawText("■ $docName", MARGIN, pager.y, sectionPaint)
                val countText = "共 ${records.size} 条"
                pager.canvas.drawText(countText, MARGIN + CONTENT_W - footnotePaint.measureText(countText), pager.y, footnotePaint)
                pager.y += 20f

                painter.drawHeader(resultsSpec)
                records.forEachIndexed { ri, record ->
                    var reason: String? = null
                    val rowPaint = when (record.rowStatus) {
                        RowStatus.ERROR -> {
                            reason = when {
                                record.errorMessage.isNotBlank() -> "✘ ${record.errorMessage}"
                                record.qtyStatus == FieldStatus.ERROR -> "✘ 数量溢出"
                                else -> "✘ 异常"
                            }
                            errorPaint
                        }
                        RowStatus.WARNING -> {
                            val warnReasons = mutableListOf<String>()
                            if (record.qtyStatus == FieldStatus.WARNING) warnReasons.add("数量不足")
                            if (record.prodDateStatus == FieldStatus.WARNING || record.expDateStatus == FieldStatus.WARNING) warnReasons.add("日期不符")
                            reason = if (warnReasons.isEmpty()) "⚠ 警告" else "⚠ ${warnReasons.joinToString("/")}"
                            warnPaint
                        }
                        RowStatus.PASS -> if (record.targetQty > 0) matchPaint else bodyPaint
                    }

                    pager.ensureSpace(ROW_H + (if (reason != null) SUB_LINE_H else 0f)) { painter.drawHeader(resultsSpec) }
                    painter.drawRow(resultsSpec, listOf(
                        record.productName,
                        record.di,
                        record.batch,
                        DateUtils.formatDateForDisplay(record.productionDate),
                        DateUtils.formatDateForDisplay(record.expiryDate),
                        record.targetQty.toString(),
                        record.actualQty.toString(),
                    ), rowPaint, ri)

                    reason?.let { r ->
                        val notePaint = if (record.rowStatus == RowStatus.ERROR) errorNotePaint else warnNotePaint
                        pager.canvas.drawText(r, MARGIN + 8f, pager.y + 10f, notePaint)
                        pager.y += SUB_LINE_H
                    }
                }
                pager.y += 16f
            }
        }

        // ========== 照片页（扫码明细去掉 DI 列，条码内容完整换行显示） ==========
        val photoCount = photoResults.size
        val scanSpec = TableSpec(
            listOf("#", "条码内容", "坐标", "批号", "状态"),
            floatArrayOf(24f, 240f, 100f, 76f, 40f),
            listOf(ColAlign.CENTER, ColAlign.LEFT, ColAlign.LEFT, ColAlign.LEFT, ColAlign.CENTER),
        )
        val monoPaint = Paint().apply { textSize = 8.5f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
        val scanBodyPaint = Paint().apply { textSize = 9f; isAntiAlias = true }
        val scanErrorPaint = Paint().apply { textSize = 9f; isAntiAlias = true; color = COLOR_RED }
        val scanMatchPaint = Paint().apply { textSize = 9f; isAntiAlias = true; color = COLOR_GREEN }

        for ((idx, result) in photoResults.withIndex()) {
            // 首页若无摘要/结果内容，第一张照片直接续排在报告头后，不空占一页
            if (idx > 0 || hasDocuments || hasRecords) pager.newPage()
            pager.canvas.drawText("照片 ${idx + 1} / $photoCount", MARGIN, pager.y, sectionPaint)
            pager.y += 22f

            val photo = loadDownsampledBitmap(context, result.photoUri)
            if (photo != null) {
                val maxW = CONTENT_W
                val maxH = PAGE_H * 0.55f
                val scale = minOf(maxW / photo.bitmap.width, maxH / photo.bitmap.height)
                val drawW = photo.bitmap.width * scale
                val drawH = photo.bitmap.height * scale
                val drawX = MARGIN + (CONTENT_W - drawW) / 2f
                pager.canvas.drawBitmap(photo.bitmap, null, RectF(drawX, pager.y, drawX + drawW, pager.y + drawH), null)
                pager.y += drawH + 16f
                pager.canvas.drawText("原图: ${photo.srcWidth}×${photo.srcHeight}  |  条码: ${result.barcodes.size} 个", MARGIN, pager.y, footnotePaint)
                pager.y += 18f
                photo.bitmap.recycle()
            } else {
                pager.canvas.drawText("(照片加载失败)", MARGIN, pager.y, bodyPaint)
                pager.y += 20f
            }

            pager.ensureSpace(80f)
            pager.canvas.drawText("扫码明细:", MARGIN, pager.y, sectionPaint)
            pager.y += 20f
            painter.drawHeader(scanSpec)

            result.barcodes.forEachIndexed { bi, barcode ->
                val box = barcode.globalBoundingBox
                val coordStr = "${box.left},${box.top},${box.width()},${box.height()}"
                val parseResult = Gs1Parser.parse(barcode.displayValue)
                val matched = barcode.status == ScanStatus.MATCHED

                painter.drawRow(
                    scanSpec,
                    listOf("${bi + 1}", barcode.displayValue, coordStr, parseResult.batch ?: "-", if (matched) "✔" else "✘"),
                    scanBodyPaint, bi,
                    wrapCol = 1, wrapPaint = monoPaint,
                    cellPaints = mapOf(4 to if (matched) scanMatchPaint else scanErrorPaint),
                )
            }
        }

        pager.finish()
        doc.writeTo(outputStream)
        doc.close()
    }

    private class DecodedPhoto(val bitmap: Bitmap, val srcWidth: Int, val srcHeight: Int)

    /** 两段式解码：先读原始尺寸，再按绘制区约 2 倍的目标降采样，避免整图解码造成内存峰值 */
    private fun loadDownsampledBitmap(context: Context, uri: Uri): DecodedPhoto? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val targetW = (CONTENT_W * 2).toInt()
            val targetH = (PAGE_H * 0.55f * 2).toInt()
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetW && bounds.outHeight / (sampleSize * 2) >= targetH) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            DecodedPhoto(bitmap, bounds.outWidth, bounds.outHeight)
        } catch (e: Exception) {
            null
        }
    }

    /** 汇总条：彩色小方块图例 + 数量 */
    private fun drawStatsLine(pager: PdfPager, items: List<Triple<Int, String, Int>>, labelPaint: Paint, fillPaint: Paint) {
        var x = MARGIN
        for ((color, label, count) in items) {
            fillPaint.color = color
            pager.canvas.drawRect(x, pager.y - 6f, x + 6f, pager.y, fillPaint)
            x += 10f
            val text = "$label $count"
            pager.canvas.drawText(text, x, pager.y, labelPaint)
            x += labelPaint.measureText(text) + 16f
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

    /** 按宽度逐字符断行（CJK 安全），最多 maxLines 行，末行超宽时截断加省略号 */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int = 3): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in text) {
            if (cur.isNotEmpty() && paint.measureText("$cur$ch") > maxWidth) {
                lines.add(cur.toString())
                cur.clear()
                if (lines.size == maxLines) {
                    var last = lines.last()
                    while (last.isNotEmpty() && paint.measureText("$last...") > maxWidth) last = last.dropLast(1)
                    return lines.dropLast(1) + "$last..."
                }
            }
            cur.append(ch)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }
}
