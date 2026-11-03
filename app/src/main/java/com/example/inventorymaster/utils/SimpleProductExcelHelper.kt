package com.example.inventorymaster.utils

import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.model.DuplicateEntry
import com.example.inventorymaster.data.model.DuplicateGroup
import com.example.inventorymaster.data.model.ImportDiagnostics
import com.example.inventorymaster.data.model.ProductImportItem
import com.example.inventorymaster.data.model.SkippedRow
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object SimpleProductExcelHelper {
    // 强制锁死表头顺序与名称
    private val HEADERS = listOf(
        "系统唯一码(勿改)", "DI/条码", "物料编码", "产品名称*", "规格",
        "型号", "生产厂家", "注册证号", "单位", "分类编码", "数据来源"
    )

    fun exportProductsToExcel(outputStream: OutputStream, products: List<ProductBase>) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("产品字典库")

        // 写表头
        val headerRow = sheet.createRow(0)
        HEADERS.forEachIndexed { i, h ->
            headerRow.createCell(i).setCellValue(h)
            sheet.setColumnWidth(i, 4000)
        }

        // 写数据 (严格按 HEADERS 顺序)
        products.forEachIndexed { rowIdx, p ->
            val row = sheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(p.productKey) // 留作下一次导入时的 oldKey
            row.createCell(1).setCellValue(p.di ?: "")
            row.createCell(2).setCellValue(p.materialCode ?: "")
            row.createCell(3).setCellValue(p.productName)
            row.createCell(4).setCellValue(p.specification ?: "")
            row.createCell(5).setCellValue(p.model ?: "")
            row.createCell(6).setCellValue(p.manufacturer)
            row.createCell(7).setCellValue(p.registrationCert ?: "")
            row.createCell(8).setCellValue(p.unit ?: "")
            row.createCell(9).setCellValue(p.categoryCode ?: "")
            row.createCell(10).setCellValue(p.source)
        }

        workbook.write(outputStream)
        workbook.close()
    }

    fun importProductsFromExcel(inputStream: InputStream): Pair<List<ProductImportItem>, ImportDiagnostics> {
        val items = mutableListOf<ProductImportItem>()
        val itemRowNumbers = mutableListOf<Int>() // 平行列表，记录每个item对应的Excel行号
        val skippedRows = mutableListOf<SkippedRow>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        val headerRow = sheet.getRow(0) ?: throw Exception("表格为空")

        // 1. 强校验表头
        if (headerRow.lastCellNum < HEADERS.size) {
            throw Exception("表头列数不匹配，请使用系统导出的标准模板")
        }
        for (i in HEADERS.indices) {
            val cellValue = headerRow.getCell(i)?.stringCellValue?.trim() ?: ""
            if (cellValue != HEADERS[i]) {
                throw Exception("表头不匹配: 期望 '${HEADERS[i]}', 实际得到 '$cellValue'。请使用标准模板。")
            }
        }

        // 2. 读取数据
        val rowIterator = sheet.iterator()
        if (rowIterator.hasNext()) rowIterator.next() // 跳过表头

        while (rowIterator.hasNext()) {
            val row = rowIterator.next()
            val rowNum = row.rowNum + 1 // Excel行号(从1开始)

            // 提取数据
            val oldKey = getCellText(row.getCell(0))
            val di = getCellText(row.getCell(1)).ifBlank { null }
            val matCode = getCellText(row.getCell(2)).ifBlank { null }
            val name = getCellText(row.getCell(3))

            // 必填项拦截
            if (name.isBlank()) {
                skippedRows.add(SkippedRow(rowNum, "(空)", di, matCode, "产品名称为空"))
                continue
            }
            if (di == null && matCode == null) {
                skippedRows.add(SkippedRow(rowNum, name, null, null, "DI和物料编码都为空"))
                continue
            }

            val spec = getCellText(row.getCell(4)).ifBlank { null }
            val model = getCellText(row.getCell(5)).ifBlank { null }
            val mfr = getCellText(row.getCell(6)).ifBlank { "未知厂家" }
            val reg = getCellText(row.getCell(7)).ifBlank { null }
            val unit = getCellText(row.getCell(8)).ifBlank { null }
            val catCode = getCellText(row.getCell(9)).ifBlank { null }
            val source = getCellText(row.getCell(10)).ifBlank { "excel_import" }

            // 重新计算真正的 ProductKey
            val newKey = ProductBase.computeProductKey(di, matCode)

            val product = ProductBase(
                productKey = newKey, di = di, materialCode = matCode,
                productName = name, specification = spec, model = model,
                manufacturer = mfr, registrationCert = reg, unit = unit,
                categoryCode = catCode, source = source
            )

            // oldKey 如果为空，说明是用户手动在 Excel 里插入的一行新数据，oldKey 就是 newKey
            items.add(ProductImportItem(oldKey.ifBlank { newKey }, product))
            itemRowNumbers.add(rowNum)
        }

        workbook.close()

        // 3. 检测 (DI, 物料编码) 组合重复——只有两者完全相同才视为重复行
        val keyGroups = mutableMapOf<String, MutableList<Pair<Int, ProductImportItem>>>()
        for ((index, item) in items.withIndex()) {
            val di = item.newProduct.di?.trim() ?: ""
            val mat = item.newProduct.materialCode?.trim() ?: ""
            val keyLabel = "$di|$mat"
            keyGroups.getOrPut(keyLabel) { mutableListOf() }.add(itemRowNumbers[index] to item)
        }
        val duplicateGroups = keyGroups
            .filter { it.value.size > 1 }
            .map { (keyLabel, entries) ->
                DuplicateGroup(keyLabel, entries.map { (rowNum, item) ->
                    DuplicateEntry(rowNum, item.newProduct.productName, item.newProduct.manufacturer)
                })
            }
            .sortedByDescending { it.entries.size }

        val diagnostics = ImportDiagnostics(skippedRows, duplicateGroups)
        return Pair(items, diagnostics)
    }

    private fun getCellText(cell: org.apache.poi.ss.usermodel.Cell?): String {
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
}