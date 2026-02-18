package com.example.inventorymaster.utils

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.google.gson.Gson
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream


object ExcelUtils {
    // --- 新增：Schema 数据结构 (用于记录表头格式) --
    /**
     * 字段类型枚举：用于标识这一列对应业务数据的哪个字段
     * 增加了 UNKNOWN (未知列) 和 SYSTEM_... (系统追加列)
     */
    enum class FieldType {
        // --- 基础信息 ---
        DI,           // UDI/条码
        MAT_CODE,     // 物料编码
        NAME,         // 名称
        SPEC,         // 规格
        MODEL,        // 型号
        MFR,          // 厂家
        REG_CERT,     // 注册证
        UNIT,         // 单位
        // --- 库存信息 ---
        BATCH,        // 批号
        EXPIRY,       // 效期
        QTY,          // 数量
        LOC,          // 库位
        ACTUAL_QTY,    //实际数量
        MEMO,         //备注
        // --- 特殊 ---
        UNKNOWN,      // 程序不认识的列 (导出时原样保留表头，内容留空)

        // --- 导出时追加的系统字段 (预留) ---
        SYS_ACTUAL,
        SYS_OPERATOR, // 操作人
        SYS_TIME,     // 盘点时间
        SYS_REMARK    // 备注
    }
    /**
     * 单列配置：记录某一列的所有特征
     */
    data class ColumnSchema(
        val columnIndex: Int,   // 列索引 (0, 1, 2...)
        val headerName: String, // 原表头文字 (例如 "物料  名称")
        val width: Int,         // 列宽 (POI 单位，用于还原视觉效果)
        val fieldType: FieldType // 这一列被识别成了什么
    ) : java.io.Serializable

    /**
     * 表格结构：包含所有列的配置
     */
    data class TableSchema(
        val columns: List<ColumnSchema>
    ) : java.io.Serializable

    // --- 修改：更新结果容器 ---
    data class ImportResult(
        val products: List<ProductBase>, // 字典数据
        val records: List<StockRecord>,  // 账本数据
        val schema: TableSchema          // <--- 新增：表格格式定义
    )

    // ---  扩充关键词字典 (支持 NMPA 标准字段) ---
    private val KEYWORDS_DI = listOf("udi", "di", "条码", "id", "gtin", "01")
    // 基础信息类
    private val KEYWORDS_NAME = listOf("物料名称", "品名", "通用名" ,"name", "product")
    private val KEYWORDS_SPEC = listOf("规格", "spec", "ggxh") // NMPA: GGXH
    private val KEYWORDS_MODEL = listOf("型号", "model", "type")
    private val KEYWORDS_MFR = listOf("厂家", "生产企业", "制造商", "mfr", "manufacturer", "qymc") // NMPA: QYMC
    private val KEYWORDS_REG_CERT = listOf("注册证", "备案号", "reg", "cert", "zcz") // NMPA: ZCZ
    private val KEYWORDS_UNIT = listOf("单位","最小销售单元", "unit", "dw")
    private val KEYWORDS_MAT_CODE = listOf("物料编码","物料代码")

    // 库存变动类
    private val KEYWORDS_BATCH = listOf("批号", "批次", "batch", "lot", "10")
    private val KEYWORDS_EXPIRY = listOf("效期","有效期", "expiry", "exp", "date", "17")
    private val KEYWORDS_QTY = listOf("库存数量", "qty", "count", "amount")
    private val KEYWORDS_ACTUAL_QTY = listOf("实际数量", "实盘", "实点", "清点数", "actual", "checked")
    private val KEYWORDS_LOC = listOf("库位", "位置","仓库名称", "location", "loc")
    private val KEYWORDS_MEMO = listOf("备注", "说明", "remarks", "comment", "note", "memo")
    // --- 3. 核心导入方法 (升级版) ---
    fun parseExcel(context: Context, uri: Uri, sessionId: Long): ImportResult {
        val products = mutableListOf<ProductBase>()
        val records = mutableListOf<StockRecord>()
        var inputStream: InputStream? = null

        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0) // 默认读第一个 Sheet

            // A. 获取表头行
            val headerRow = sheet.getRow(0) ?: throw Exception("表格为空")
            // ==========================================
            // 新增逻辑：构建 Schema (格式记录器)
            // ==========================================
            val columnSchemas = mutableListOf<ColumnSchema>()
            // 遍历表头的所有单元格 (包含空白表头的情况处理)
            // getLastCellNum() 获取的是最后一列的索引+1
            val lastColIndex = headerRow.lastCellNum.toInt()

            for (i in 0 until lastColIndex) {
                val cell = headerRow.getCell(i)

                // 1. 获取表头文字 (作为导出时的标题)
                val headerName = getCellValue(cell)
                // 2. 获取列宽 (导出时还原视觉效果)
                val colWidth = sheet.getColumnWidth(i)
                // 3. 智能识别：这一列是干嘛的？
                val type = identifyFieldType(headerName)
                // 4. 存入列表
                columnSchemas.add(ColumnSchema(
                    columnIndex = i,
                    headerName = headerName,
                    width = colWidth,
                    fieldType = type
                ))
            }

            // 生成完整的表结构对象
            val tableSchema = TableSchema(columnSchemas)

            // ==========================================
            // 准备读取数据：建立 字段类型 -> 列索引 的快速映射
            // ==========================================
            // 这样我们在循环读取每一行时，可以通过 fieldMap[FieldType.NAME] 快速知道名称在哪一列
            val fieldMap = columnSchemas.associate { it.fieldType to it.columnIndex }
            // 检查关键列是否存在 (UDI 或者 物料编码 必须有一个)
            if (!fieldMap.containsKey(FieldType.DI) && !fieldMap.containsKey(FieldType.MAT_CODE)) {
                throw Exception("未找到 'UDI/条码' 或 '物料编码' 列，无法识别商品")
            }
            // ==========================================
            // B. 遍历数据行 (保持原有的业务逻辑，只是获取 index 的方式变了)
            // ==========================================
            val rowIterator = sheet.iterator()
            if (rowIterator.hasNext()) rowIterator.next() // 跳过表头

            while (rowIterator.hasNext()) {
                val row = rowIterator.next()

                // --- 1. 获取主键 DI ---
                // 使用 fieldMap.get(...) 安全获取索引，如果没找到这列就返回 -1 (getCellValue 会处理 null/空)
                val idxDi = fieldMap[FieldType.DI] ?: -1
                val idxMatCode = fieldMap[FieldType.MAT_CODE] ?: -1

                val rawDi = if (idxDi != -1) getCellValue(row.getCell(idxDi)) else ""
                val rawMatCode = if (idxMatCode != -1) getCellValue(row.getCell(idxMatCode)) else ""

                // --- 2. 提取 [基础字典信息] ---
                // 同样使用 fieldMap 获取索引
                val idxName = fieldMap[FieldType.NAME] ?: -1
                val idxSpec = fieldMap[FieldType.SPEC] ?: -1
                val idxModel = fieldMap[FieldType.MODEL] ?: -1
                val idxMfr = fieldMap[FieldType.MFR] ?: -1
                val idxReg = fieldMap[FieldType.REG_CERT] ?: -1
                val idxUnit = fieldMap[FieldType.UNIT] ?: -1

                val name = if (idxName != -1) getCellValue(row.getCell(idxName)) else "未命名产品"
                val spec = if (idxSpec != -1) getCellValue(row.getCell(idxSpec)) else ""
                val model = if (idxModel != -1) getCellValue(row.getCell(idxModel)) else ""
                val mfr = if (idxMfr != -1) getCellValue(row.getCell(idxMfr)) else "未知厂家"
                val regCert = if (idxReg != -1) getCellValue(row.getCell(idxReg)) else ""
                val unit = if (idxUnit != -1) getCellValue(row.getCell(idxUnit)) else ""

                // 生成唯一标识 (逻辑不变)
                val findDi = when {
                    rawDi.isNotBlank() -> rawDi
                    rawMatCode.isNotBlank() -> rawMatCode
                    name.isNotBlank() -> {
                        val rawString = "$name$spec$model"
                        "LOCAL-${rawString.hashCode().toString().replace("-","N")}"
                    }
                    else -> continue // 这一行没有任何有效信息，跳过
                }
                // 构建 ProductBase (逻辑不变)
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
                    materialCode = rawMatCode,
                    categoryCode = null
                )
                products.add(product)

                // --- 3. 提取 [库存账本信息] ---
                val idxBatch = fieldMap[FieldType.BATCH] ?: -1
                val idxExpiry = fieldMap[FieldType.EXPIRY] ?: -1
                val idxQty = fieldMap[FieldType.QTY] ?: -1
                val idxLoc = fieldMap[FieldType.LOC] ?: -1

                val batch = if (idxBatch != -1) getCellValue(row.getCell(idxBatch)) else ""
                val expiryStr = if (idxExpiry != -1) getCellValue(row.getCell(idxExpiry)) else ""
                val qtyStr = if (idxQty != -1) getCellValue(row.getCell(idxQty)) else "0"
                val loc = if (idxLoc != -1) getCellValue(row.getCell(idxLoc)) else ""

                // 数据清洗
                val expiry = expiryStr.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                val qty = qtyStr.toDoubleOrNull() ?: 0.0

                // 构建 StockRecord (逻辑不变)
                val record = StockRecord(
                    sessionId = sessionId,
                    di = findDi,
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

            // 返回结果：携带了 schema
            return ImportResult(products, records, tableSchema)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            inputStream?.close()
        }
    }

    // --- 导出方法 (升级为支持新结构) ---
    fun exportExcel(
        outputStream: OutputStream,
        dataList: List<StockRecordCombined>,
        originalSchema: TableSchema?, // 传入之前导入时保存的 Schema (如果没有则传 null)
        operatorName: String = "管理员" // 新增：传入当前操作人名字
    ) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("盘点数据")
        sheet.defaultRowHeightInPoints = 45f // 设置默认行高为 20 (比默认的 15 稍微宽敞一点)

        // 1. 样式准备 (加粗表头，居中)
        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont().apply { bold = true }
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
        // ==========================================
        // A. 构建最终列结构 (Merge Schema)
        // ==========================================
        val finalColumns = mutableListOf<ColumnSchema>()

        // --- 定义一个标记，记录原表是否包含备注 ---
        var hasOriginalRemark = false
        var hasOriginalActual = false
        if (originalSchema != null && originalSchema.columns.isNotEmpty()) {
            // --- 情况 1: 基于原表还原 ---
            // 1.1 先加入所有原表列
            finalColumns.addAll(originalSchema.columns)
            hasOriginalRemark = finalColumns.any { it.fieldType == FieldType.MEMO }
            hasOriginalActual = finalColumns.any { it.fieldType == FieldType.ACTUAL_QTY }

            // 1.2 找到当前最大的列索引 (用于确定追加列的位置)
            val maxIndex = finalColumns.maxOf { it.columnIndex }
            var nextIndex = maxIndex + 1
            // 1.3 动态追加系统列 (定义我们想追加的 APP 独有字段)
            // 这里我们追加：[实际盘点数量]、[差异]、[操作人]、[盘点时间]
            // 注意：如果原表里已经有了 "实际数量" (ACTUAL_QTY)，这里应该判重，但简单起见我们强制追加在最后
            if (!hasOriginalActual) {
                finalColumns.add(ColumnSchema(nextIndex++, "实际盘点数", 3000, FieldType.SYS_ACTUAL))
            }
             // 稍后处理填值
//            finalColumns.add(ColumnSchema(nextIndex++, "盘点差异", 3000, FieldType.UNKNOWN))   // 稍后处理填值
            finalColumns.add(ColumnSchema(nextIndex++, "操作人", 3000, FieldType.SYS_OPERATOR))
//            finalColumns.add(ColumnSchema(nextIndex++, "盘点时间", 4000, FieldType.SYS_TIME))
            if (!hasOriginalRemark) {
                finalColumns.add(ColumnSchema(nextIndex++, "系统备注", 4000, FieldType.SYS_REMARK))
            }

        } else {
            // --- 情况 2: 无原表 (本地新建数据)，使用默认模板 ---
            // 这里为了代码简洁，我就不重复写默认模板了，逻辑同上，只是手动 add ColumnSchema
            // 实际项目中建议给一个默认的 defaultColumns 列表
        }

        // ==========================================
        // B. 绘制表头 (Restore Header)
        // ==========================================
        val headerRow = sheet.createRow(0)

        // 按照 index 排序确保从左到右
        val sortedColumns = finalColumns.sortedBy { it.columnIndex }

        for (col in sortedColumns) {
            val cell = headerRow.createCell(col.columnIndex)
            cell.setCellValue(col.headerName)
            cell.cellStyle = headerStyle

            // 还原列宽 (如果有记录宽度就用记录的，没有就用默认的)
            if (col.width > 0) {
                sheet.setColumnWidth(col.columnIndex, col.width)
            } else {
                sheet.setDefaultColumnWidth(15)
            }
        }
        // ==========================================
        // C. 填充数据 (Fill Data)
        // ==========================================
        val creationHelper = workbook.creationHelper
        val dateStyle = workbook.createCellStyle().apply {
            dataFormat = creationHelper.createDataFormat().getFormat("yyyy-MM-dd HH:mm")
        }

        dataList.forEachIndexed { rowIndex, item ->
            // Excel 行索引从 1 开始 (0 是表头)
            val row = sheet.createRow(rowIndex + 1)
            val r = item.record
            val p = item.product

            // 遍历每一列定义，填坑
            for (col in sortedColumns) {
                val cell = row.createCell(col.columnIndex)

                // --- 核心填值逻辑 ---
                // 1. 判断是否是刚才追加的 "自定义列" (通过 HeaderName 判断，或者定义新的 FieldType)
                if (col.headerName == "实际盘点数") {
                    cell.setCellValue(r.actualQuantity ?: 0.0)
                    continue
                }
                if (col.headerName == "盘点差异") {
                    val sysQty = r.quantity
                    val actQty = r.actualQuantity ?: 0.0
                    cell.setCellValue(actQty - sysQty)
                    continue
                }

                // 2. 根据 FieldType 填值
                when (col.fieldType) {
                    // --- 基础信息 ---
                    FieldType.DI -> cell.setCellValue(r.di)
                    FieldType.MAT_CODE -> cell.setCellValue(p?.materialCode ?: "")
                    FieldType.NAME -> cell.setCellValue(p?.productName ?: "")
                    FieldType.SPEC -> cell.setCellValue(p?.specification ?: "")
                    FieldType.MODEL -> cell.setCellValue(p?.model ?: "")
                    FieldType.MFR -> cell.setCellValue(p?.manufacturer ?: "")
                    FieldType.REG_CERT -> cell.setCellValue(p?.registrationCert ?: "")
                    FieldType.UNIT -> cell.setCellValue(p?.unit ?: "")

                    // --- 库存信息 ---
                    FieldType.BATCH -> cell.setCellValue(r.batchNumber)
                    FieldType.QTY -> cell.setCellValue(r.quantity) // 系统库存
                    FieldType.LOC -> cell.setCellValue(r.location)
                    FieldType.EXPIRY -> {
                        // 简单处理日期：假设 expiryDate 是 long 时间戳
                        // 如果是 YYYYMMDD 格式的 Long，需要转换一下，这里假设是纯文本或时间戳
                        cell.setCellValue(r.expiryDate.toString())
                    }

                    FieldType.ACTUAL_QTY, FieldType.SYS_ACTUAL -> {
                        cell.setCellValue(r.actualQuantity ?: 0.0)
                    }
                    // --- 系统追加字段 ---
                    FieldType.SYS_OPERATOR -> cell.setCellValue(operatorName)
                    FieldType.SYS_TIME -> {
                        // 使用当前时间导出，或者使用 record.updateTime
                        cell.setCellValue(java.util.Date())
                        cell.cellStyle = dateStyle
                    }
                    // --- [关键]：遇到原表的 MEMO，直接填入现在的备注数据 ---
                    // 这就是“原位填充”，复用了原表的位置和列宽
                    FieldType.MEMO -> cell.setCellValue(r.remarks ?: "")
                    // 如果原表没有，这是我们追加的 SYS_REMARK，逻辑是一样的
                    FieldType.SYS_REMARK -> cell.setCellValue(r.remarks ?: "")

                    // --- 未知列 / 其他 ---
                    else -> {
                        // FieldType.UNKNOWN
                        // 这里目前留空。如果你实现了 extraData Map，就是 cell.setCellValue(p.extraData[col.headerName])
                        cell.setCellValue("")
                    }
                }
            }
        }

        workbook.write(outputStream)
        workbook.close()
    }

    // ---智能识别字段类型的辅助方法 ---
    // 将之前的 findColumnIndex 逻辑下沉到这里
    private fun identifyFieldType(headerName: String): FieldType {
        val h = headerName.lowercase().trim()
        if (h.isEmpty()) return FieldType.UNKNOWN

        return when {
            // 包含任意一个关键词即命中
            KEYWORDS_DI.any { h.contains(it) } -> FieldType.DI
            KEYWORDS_MAT_CODE.any { h.contains(it) } -> FieldType.MAT_CODE
            // 注意优先级：有些包含关系的词需要小心，比如 "库存数量" 包含 "库存"
            KEYWORDS_NAME.any { h.contains(it) } -> FieldType.NAME
            KEYWORDS_SPEC.any { h.contains(it) } -> FieldType.SPEC
            KEYWORDS_MODEL.any { h.contains(it) } -> FieldType.MODEL
            KEYWORDS_MFR.any { h.contains(it) } -> FieldType.MFR
            KEYWORDS_REG_CERT.any { h.contains(it) } -> FieldType.REG_CERT
            KEYWORDS_UNIT.any { h.contains(it) } -> FieldType.UNIT
            KEYWORDS_ACTUAL_QTY.any { h.contains(it) } -> FieldType.ACTUAL_QTY
            KEYWORDS_BATCH.any { h.contains(it) } -> FieldType.BATCH
            KEYWORDS_EXPIRY.any { h.contains(it) } -> FieldType.EXPIRY
            KEYWORDS_QTY.any { h.contains(it) } -> FieldType.QTY
            KEYWORDS_LOC.any { h.contains(it) } -> FieldType.LOC
            KEYWORDS_MEMO.any { h.contains(it) } -> FieldType.MEMO

            else -> FieldType.UNKNOWN
        }
    }

    // --- 辅助方法 (保持不变) ---
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

    // ==========================================
    // 6. Schema 存储工具 (JSON版)
    // ==========================================
    object SchemaHelper {
        private const val PREFS_NAME = "excel_schema_prefs"
        private val gson = Gson()

        // 保存
        fun saveSchema(context: Context, sessionId: Long, schema: TableSchema) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(schema)
            prefs.edit().putString("schema_$sessionId", json).apply()
        }

        // 读取
        fun getSchema(context: Context, sessionId: Long): TableSchema? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("schema_$sessionId", null) ?: return null
            return try {
                gson.fromJson(json, TableSchema::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // 删除 (可选：盘点结束清空缓存)
        fun clearSchema(context: Context, sessionId: Long) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove("schema_$sessionId").apply()
        }
    }
}