package com.example.inventorymaster.utils

import android.content.Context
import android.net.Uri
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream


object ExcelUtils {

    // --- 1. 扩充关键词字典 (支持 NMPA 标准字段) ---
    private val KEYWORDS_DI = listOf("udi", "di", "条码", "id", "gtin", "01")

    // 基础信息类
    private val KEYWORDS_NAME = listOf("名称", "品名", "通用名" ,"name", "product")
    private val KEYWORDS_SPEC = listOf("规格", "spec", "ggxh") // NMPA: GGXH
    private val KEYWORDS_MODEL = listOf("型号", "model", "type")
    private val KEYWORDS_MFR = listOf("厂家", "企业", "制造商", "mfr", "manufacturer", "qymc") // NMPA: QYMC
    private val KEYWORDS_REG_CERT = listOf("注册证", "备案号", "reg", "cert", "zcz") // NMPA: ZCZ
    private val KEYWORDS_UNIT = listOf("单位", "unit", "dw")
    private val KEYWORDS_MAT_CODE = listOf("物料编码","物料代码")

    // 库存变动类
    private val KEYWORDS_BATCH = listOf("批号", "批次", "batch", "lot", "10")
    private val KEYWORDS_EXPIRY = listOf("效期","有效期", "expiry", "exp", "date", "17")
    private val KEYWORDS_QTY = listOf("数量", "库存", "qty", "count", "amount")
    private val KEYWORDS_LOC = listOf("库位", "位置","仓库名称", "location", "loc")

    // --- 2. 结果容器 ---
    data class ImportResult(
        val products: List<ProductBase>, // 字典数据
        val records: List<StockRecord>   // 账本数据
    )

    // --- 3. 核心导入方法 ---
    fun parseExcel(context: Context, uri: Uri, sessionId: Long): ImportResult {
        val products = mutableListOf<ProductBase>()
        val records = mutableListOf<StockRecord>()
        var inputStream: InputStream? = null

        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // A. 寻找表头
            val headerRow = sheet.getRow(0) ?: throw Exception("表格为空")

            // B. 映射列索引
            val idxDi = findColumnIndex(headerRow, KEYWORDS_DI)
            val idxMatCode = findColumnIndex(headerRow,KEYWORDS_MAT_CODE)
            if (idxDi == -1 && idxMatCode == -1) {throw Exception("未找到 'UDI/条码' 列，请检查表头")}

            val idxName = findColumnIndex(headerRow, KEYWORDS_NAME)
            val idxSpec = findColumnIndex(headerRow, KEYWORDS_SPEC)
            val idxModel = findColumnIndex(headerRow, KEYWORDS_MODEL)
            val idxMfr = findColumnIndex(headerRow, KEYWORDS_MFR)
            val idxReg = findColumnIndex(headerRow, KEYWORDS_REG_CERT)
            val idxUnit = findColumnIndex(headerRow, KEYWORDS_UNIT)

            val idxBatch = findColumnIndex(headerRow, KEYWORDS_BATCH)
            val idxExpiry = findColumnIndex(headerRow, KEYWORDS_EXPIRY)
            val idxQty = findColumnIndex(headerRow, KEYWORDS_QTY)
            val idxLoc = findColumnIndex(headerRow, KEYWORDS_LOC)

            // C. 遍历数据
            val rowIterator = sheet.iterator()
            if (rowIterator.hasNext()) rowIterator.next() // 跳过表头

            while (rowIterator.hasNext()) {
                val row = rowIterator.next()

                // 1. 获取主键 DI
                //安全获取数据 (加 idx != -1 判断)
                val rawDi = if (idxDi != -1) getCellValue(row.getCell(idxDi)) else ""
                val rawMatCode = if (idxMatCode != -1) getCellValue(row.getCell(idxMatCode)) else ""

                // 2. 提取 [基础字典信息]
                // 即使有些字段是空的，也先读出来
                val name = if (idxName != -1) getCellValue(row.getCell(idxName)) else "未命名产品"
                val spec = if (idxSpec != -1) getCellValue(row.getCell(idxSpec)) else ""
                val model = if (idxModel != -1) getCellValue(row.getCell(idxModel)) else ""
                val mfr = if (idxMfr != -1) getCellValue(row.getCell(idxMfr)) else "未知厂家"
                val regCert = if (idxReg != -1) getCellValue(row.getCell(idxReg)) else ""
                val unit = if (idxUnit != -1) getCellValue(row.getCell(idxUnit)) else ""
                val findDi = when {
                    rawDi.isNotBlank() -> rawDi
                    rawMatCode.isNotBlank() -> rawMatCode
                    name.isNotBlank() -> {
                        val rawString = "$name$spec$model"
                        "LOCAL-${rawString.hashCode().toString().replace("-","N")}"
                    }
                    else -> continue
                }

                // 构建 ProductBase 对象
                val product = ProductBase(
                    di = findDi,
                    productName = if (name.isBlank()) "未知商品" else name,
                    specification = spec,
                    model = model,
                    manufacturer = mfr,
                    registrationCert = regCert,
                    unit = unit,
                    source = when {
                        rawDi.isNotBlank() -> "scan"
                        rawMatCode.isNotBlank() -> "erp"
                        else -> "local_gen"
                    },
                    materialCode = rawMatCode, // Excel里没读到就留空
                    categoryCode = null
                )
                products.add(product)

                // 3. 提取 [库存账本信息]
                val batch = if (idxBatch != -1) getCellValue(row.getCell(idxBatch)) else ""
                val expiryStr = if (idxExpiry != -1) getCellValue(row.getCell(idxExpiry)) else ""
                val qtyStr = if (idxQty != -1) getCellValue(row.getCell(idxQty)) else "0"
                val loc = if (idxLoc != -1) getCellValue(row.getCell(idxLoc)) else ""

                // 简单清洗数据
                val expiry = expiryStr.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                val qty = qtyStr.toDoubleOrNull() ?: 0.0

                // 构建 StockRecord 对象
                val record = StockRecord(
                    sessionId = sessionId,
                    di = findDi, // 外键关联
                    batchNumber = batch,
                    expiryDate = expiry,
                    quantity = qty,
                    actualQuantity = null,
                    location = loc,
                    sourceType = 1 // Excel导入
                )
                records.add(record)
            }
            workbook.close()

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            inputStream?.close()
        }

        return ImportResult(products, records)
    }

    // --- 辅助方法 (保持不变) ---
    private fun findColumnIndex(headerRow: Row, keywords: List<String>): Int {
        for (cell in headerRow) {
            val cellValue = getCellValue(cell).trim().lowercase()
            for (keyword in keywords) {
                if (cellValue.contains(keyword.lowercase())) return cell.columnIndex
            }
        }
        return -1
    }

    private fun getCellValue(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> {
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                }
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    // --- 导出方法 (升级为支持新结构) ---
    fun exportExcel(outputStream: OutputStream, dataList: List<StockRecordCombined>) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("盘点数据")

        // 导出表头：包含产品信息 + 库存信息
        val headers = arrayOf("UDI/条码", "产品名称", "规格", "型号", "厂家", "批号", "效期", "数量", "库位", "备注")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title -> headerRow.createCell(index).setCellValue(title) }

        dataList.forEachIndexed { index, combined ->
            val row = sheet.createRow(index + 1)
            val r = combined.record
            val p = combined.product // 这里可能为空(虽然理论上不应该)

            row.createCell(0).setCellValue(r.di)
            row.createCell(1).setCellValue(p?.productName ?: "未知")
            row.createCell(2).setCellValue(p?.specification ?: "")
            row.createCell(3).setCellValue(p?.model ?: "")
            row.createCell(4).setCellValue(p?.manufacturer ?: "")
            row.createCell(5).setCellValue(r.batchNumber)
            row.createCell(6).setCellValue(r.expiryDate.toString())
            row.createCell(7).setCellValue(r.quantity)
            row.createCell(8).setCellValue(r.location)
            row.createCell(9).setCellValue(r.remarks ?: "")
        }
        workbook.write(outputStream)
        workbook.close()
    }
}