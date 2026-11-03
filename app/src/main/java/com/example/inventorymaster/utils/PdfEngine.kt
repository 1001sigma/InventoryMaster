package com.example.inventorymaster.utils
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment // 💡 导入官方对齐的枚举
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 现代化 PDF 签名与水印工具引擎（iText7 稳定版）
 */
object PdfEngine {

    /**
     * 功能一：一键打文字水印（原生完美支持中文）
     * @param pages 需要盖章的页码（1-based）；留空 = 全部页面
     * @param density 每页水印数量 1..16，网格自动均布（1=居中，4=2×2，9=3×3，16=4×4）
     */
    fun addWatermark(
        inputPath: String,
        outputPath: String,
        text: String,
        fontSize: Float = 30f,
        colorLong: Long = 0xFFFF0000, // 💡 修改 1：直接接收设置里的 Long 类型颜色
        opacity: Float = 0.3f,        // 💡 修改 2：直接接收设置里的 Float 透明度
        rotationAngle: Float = 45f,
        pages: List<Int> = emptyList(),
        density: Int = 1
    ) {
        val pdfDoc = PdfDocument(PdfReader(inputPath), PdfWriter(outputPath))
        val document = Document(pdfDoc)

        val font = PdfFontFactory.createFont("STSong-Light", "UniGB-UTF16-H")

        // 💡 自动从 Long 中提取 RGB（忽略自带的 Alpha 通道，使用外部传入的 opacity）
        val red = ((colorLong shr 16) and 0xFF).toInt()
        val green = ((colorLong shr 8) and 0xFF).toInt()
        val blue = (colorLong and 0xFF).toInt()
        val textColor = DeviceRgb(red, green, blue)

        // 💡 直接使用传入的 Float 透明度
        val extGState = PdfExtGState().setFillOpacity(opacity)

        // 密度网格：cols=ceil(√n)，rows=ceil(n/cols)，每枚锚点取所在格子中心
        val densityFixed = density.coerceIn(1, 16)
        val cols = ceil(sqrt(densityFixed.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(densityFixed / cols.toDouble()).toInt().coerceAtLeast(1)

        val numberOfPages = pdfDoc.numberOfPages
        val targetPages = pages.filter { it in 1..numberOfPages }.ifEmpty { (1..numberOfPages).toList() }
        for (i in targetPages) {
            val page = pdfDoc.getPage(i)
            val pageSize = page.pageSize

            val p = Paragraph(text)
                .setFont(font)
                .setFontSize(fontSize)
                .setFontColor(textColor)
                .setTextAlignment(TextAlignment.CENTER)

            val canvas = PdfCanvas(page)
            canvas.saveState().setExtGState(extGState)

            for (tile in 0 until densityFixed) {
                val col = tile % cols
                val row = tile / cols
                val x = pageSize.left + pageSize.width * ((col + 0.5f) / cols)
                val y = pageSize.top + pageSize.height * ((row + 0.5f) / rows)

                document.showTextAligned(
                    p, x, y, i,
                    TextAlignment.CENTER,
                    VerticalAlignment.MIDDLE,
                    Math.toRadians(rotationAngle.toDouble()).toFloat()
                )
            }

            canvas.restoreState()
        }

        document.close()
        pdfDoc.close()
    }

    /**
     * 功能二：在 PDF 指定页面的特定坐标按上电子签名
     * @param percentX/percentY 签名中心点在页面上的百分比坐标（percentY 自页面顶部起算）
     * @param opacity 签名整体不透明度 0f..1f
     */
    fun addSignature(
        inputPath: String,
        outputPath: String,
        signatureBitmap: Bitmap,
        pageIndex: Int,
        percentX: Float,
        percentY: Float,
        scaleSize: Float = 0.2f,
        opacity: Float = 1f
    ) {
        val pdfDoc = PdfDocument(PdfReader(inputPath), PdfWriter(outputPath))

        if (pdfDoc.numberOfPages == 0) {
            pdfDoc.close()
            return
        }

        val targetPageNum = (pageIndex + 1).coerceIn(1, pdfDoc.numberOfPages)
        val page = pdfDoc.getPage(targetPageNum)
        val pageSize = page.pageSize

        val stream = ByteArrayOutputStream()
        signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageData = ImageDataFactory.create(stream.toByteArray())
        val image = Image(imageData)

        val pageWidth = pageSize.width
        val pageHeight = pageSize.height
        val targetWidth = pageWidth * scaleSize
        // 按原始位图宽高比推导高度，保证签名不变形
        val aspect = imageData.width.toFloat() / imageData.height.toFloat().coerceAtLeast(1f)
        val targetHeight = targetWidth / aspect

        // 中心点定位 + 越界钳制：签名永远完整落在页面内
        val x = (pageWidth * percentX - targetWidth / 2f)
            .coerceIn(0f, (pageWidth - targetWidth).coerceAtLeast(0f))
        val y = (pageHeight * (1f - percentY) - targetHeight / 2f)
            .coerceIn(0f, (pageHeight - targetHeight).coerceAtLeast(0f))

        image.setWidth(targetWidth)
        image.setHeight(targetHeight)
        image.setOpacity(opacity.coerceIn(0f, 1f))

        val document = Document(pdfDoc)
        image.setFixedPosition(targetPageNum, x, y)
        document.add(image)

        document.close()
        pdfDoc.close()
    }
}