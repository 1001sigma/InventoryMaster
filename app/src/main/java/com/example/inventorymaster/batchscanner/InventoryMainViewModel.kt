package com.example.inventorymaster.batchscanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.utils.Gs1Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InventoryMainViewModel(
    private val app: Application,
    private val repository: InventoryRepository
) : ViewModel() {

    // 1. 上半区的轮播图片状态
    private val _photoList = MutableStateFlow<List<Uri>>(emptyList())
    val photoList: StateFlow<List<Uri>> = _photoList.asStateFlow()

    // 照片对应的扫码数据（用于 PDF 导出）
    private val _photoScanResults = MutableStateFlow<List<PhotoScanResult>>(emptyList())

    // 2. 下半区的结果表单状态
    private val _recordList = MutableStateFlow<List<InventoryRecord>>(emptyList())
    val recordList: StateFlow<List<InventoryRecord>> = _recordList.asStateFlow()

    // --- 👇 新增：当前导入的单据表头状态 👇 ---
    private val _currentDocuments = MutableStateFlow<List<TargetDocument>>(emptyList())
    val currentDocuments: StateFlow<List<TargetDocument>> = _currentDocuments.asStateFlow()

    // --- 👇 新增：与 Python 服务端的网络交互状态 👇 ---
    private val _networkTasks = MutableStateFlow<List<com.example.inventorymaster.data.network.TaskSummary>>(emptyList())
    val networkTasks: StateFlow<List<com.example.inventorymaster.data.network.TaskSummary>> = _networkTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun fetchTaskListFromNetwork(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchPythonTaskList()
            if (result.isSuccess) {
                // 仅展示未完成 (status=0) 的任务
                _networkTasks.value = result.getOrNull()?.filter { it.status == 0 } ?: emptyList()
                onResult(null) // 成功，无报错信息
            } else {
                onResult(result.exceptionOrNull()?.message ?: "未知错误")
            }
            _isLoading.value = false
        }
    }

    fun uploadPdfResultToNetwork(context: android.content.Context, uri: Uri, documentId: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tempFile =
                    com.example.inventorymaster.utils.FileConvertUtils.uriToTempFile(context, uri)
                if (tempFile != null) {
                    val result = repository.uploadPythonTaskPdf(documentId, tempFile)
                    tempFile.delete() // 清理临时文件
                    withContext(Dispatchers.Main) {
                        onComplete(if (result.isSuccess) "上传归档成功" else "上传失败: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete("处理 PDF 文件失败")
                    }
                }
            }
        }
    }
    /**
     * 接收 BatchScanner 传回的最终结果
     * @param bitmaps 本次盘点拍下的所有照片
     * @param rawBarcodes 识别出的所有合规原始字符串
     */
    /**
     * 核心业务：处理扫描结果并匹配产品库
     */
    fun processScannerResults(bitmaps: List<Uri>, rawBarcodes: List<String>, barcodes: List<GlobalBarcode> = emptyList()) {
        _photoList.update { current -> current + bitmaps }
        if (barcodes.isNotEmpty() && bitmaps.isNotEmpty()) {
            _photoScanResults.update { current ->
                current + PhotoScanResult(bitmaps.first(), barcodes)
            }
        }

        viewModelScope.launch {
            val currentRecords = _recordList.value.toMutableList()
            val newEntries = mutableListOf<InventoryRecord>()
            val docItems = _currentDocuments.value.flatMap { it.items }
            val hasDoc = docItems.isNotEmpty()

            withContext(Dispatchers.IO) {
                for (raw in rawBarcodes) {
                    val parseResult = Gs1Parser.parse(raw)

                    // --- 无法解析 → × ---
                    if (!parseResult.isUdi || parseResult.di == null) {
                        newEntries.add(
                            InventoryRecord(
                                di = "未知",
                                productName = "解析失败",
                                batch = "未知",
                                actualQty = 1,
                                isError = true,
                                errorMessage = "非 UDI 格式"
                            )
                        )
                        continue
                    }

                    val di = parseResult.di!!
                    val batch = parseResult.batch ?: "无批号"

                    val product = repository.getProductByKey(di)
                    val finalProductName = product?.productName ?: "未知商品"
                    val isUnknown = product?.productName == null

                    // --- 核对单据：DI + 批号 都匹配才算在单据中 ---
                    val inDocument = if (hasDoc) {
                        docItems.any { it.di == di && it.batch == batch }
                    } else null // null = 无单据模式，不做此检查

                    // 不在单据中 → × (isError)；未知商品仅记录描述，不影响 isError
                    val isNotInDoc = inDocument == false
                    val errorMsg = when {
                        isNotInDoc -> "不在单据中"
                        isUnknown -> "库中无记录"
                        else -> ""
                    }


                    val matchingRecords = currentRecords.filter { it.di == di && it.batch == batch }

                    if (matchingRecords.isNotEmpty()) {
                        // 【贪心算法】：按列表顺序找第一个【还没装满】的单据
                        val notFullRecord = matchingRecords.firstOrNull { it.actualQty < it.targetQty }

                        if (notFullRecord != null) {
                            // 装进还没满的单据里
                            val index = currentRecords.indexOf(notFullRecord)
                            currentRecords[index] = notFullRecord.copy(
                                actualQty = notFullRecord.actualQty + 1,
                                isError = false
                            )
                        } else {
                            // 如果所有匹配单据都【装满】了，那就溢出到最后一张单据里（标记溢出）
                            val lastRecord = matchingRecords.last()
                            val index = currentRecords.indexOf(lastRecord)
                            currentRecords[index] = lastRecord.copy(
                                actualQty = lastRecord.actualQty + 1,
                                isError = false
                                // 注意：因为 actualQty > targetQty，模型内 status 会自动变成 OVERFLOW
                            )
                        }
                    } else {
                        // 根本不在任何单据中 (报错)
                        currentRecords.add(
                            InventoryRecord(
                                documentId = "UNKNOWN",
                                documentName = "未知单据",
                                di = di, productName = finalProductName, batch = batch,
                                actualQty = 1, isError = true,
                                errorMessage = errorMsg
                            )
                        )
                    }
                }
                currentRecords.addAll(newEntries)
            }

            _recordList.value = currentRecords
        }
    }

    fun updateRecordQuantity(recordId: String, newQty: Int) {
        _recordList.update { current ->
            current.map { if (it.id == recordId) it.copy(actualQty = newQty, isManualModified = true) else it }
        }
    }

    fun removeRecord(recordId: String) {
        _recordList.update { current ->
            if (_currentDocuments.value.isNotEmpty()) {
                current.map { if (it.id == recordId) it.copy(actualQty = 0) else it }
            } else {
                current.filterNot { it.id == recordId }
            }
        }
    }

    fun getPhotoScanResults(): List<PhotoScanResult> = _photoScanResults.value
    fun getRecordList(): List<InventoryRecord> = _recordList.value
    // 将方法名改为复数，并返回 List
    fun getDocumentsForExport(): List<TargetDocument> = _currentDocuments.value

    /**
     * 导出就绪检查：有 isError 或单据内数量未匹配 → HAS_ISSUES，否则 CLEAN
     */
    fun checkExportReadiness(): ExportReadiness {
        val records = _recordList.value
        val hasError = records.any { it.isError }
        val hasMismatch = records.any { it.targetQty > 0 && it.actualQty != it.targetQty }
        return if (hasError || hasMismatch) ExportReadiness.HAS_ISSUES else ExportReadiness.CLEAN
    }

    /**
     * 获取当前单据的 (DI|BATCH) 列表（每个条目按其 targetQty 重复），供扫码器核验。
     * 只有 DI 和批号同时对上才算匹配。
     */
    fun getTargetDiList(): List<String>? {
        val docs = _currentDocuments.value
        if (docs.isEmpty()) return null
        // 遍历所有单据，展平所有 item 的 DI 和批号
        return docs.flatMap { doc ->
            doc.items.flatMap { item -> List(item.targetQty) { "${item.di}|${item.batch}" } }
        }
    }

    // 导入单据时也可以在这里加入基于 DI 的查库补
    // InventoryMainViewModel.kt

    fun removePhoto(uri: android.net.Uri) {
        _photoList.update { current ->
            // 过滤掉当前选中的图片 Uri
            current.filter { it != uri }
        }

         //(可选) 建议在这里同步处理物理文件的删除，避免占用手机存储空间
         try {
             val file = java.io.File(uri.path ?: "")
             if (file.exists()) file.delete()
         } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 导入单据 (预填充逻辑)
     */
    /**
     * 解析外部传入的 JSON 单据
     */
    fun importTargetDocumentFromJson(uri: Uri) {
        viewModelScope.launch {
            // 切换到 IO 线程进行文件读取和解析
            withContext(Dispatchers.IO) {
                try {
                    val contentResolver = app.contentResolver
                    val inputStream = contentResolver.openInputStream(uri)

                    // 读取文件内容为 String
                    val jsonString = inputStream?.bufferedReader().use { it?.readText() }

                    if (jsonString != null) {
                        // 配置 Json 实例：忽略 JSON 中有但数据类中没有的未知字段，防止应用崩溃
                        val jsonParser = Json { ignoreUnknownKeys = true }
                        val parsedDoc = jsonParser.decodeFromString<TargetDocument>(jsonString)

                        // 映射数据：将 TargetItem 转换为 UI 需要的 InventoryRecord
                        val newRecords = parsedDoc.items.map { item ->
                            InventoryRecord(
                                di = item.di,
                                productName = item.productName,
                                batch = item.batch,
                                productionDate = item.productionDate ?: "-", //  新增映射
                                expiryDate = item.expiryDate ?: "-",         //  新增映射
                                targetQty = item.targetQty,
                                actualQty = 0, // 初始盘点数量为 0
                                isError = false
                            )
                        }

                        // 切回主线程更新 UI 状态
                        withContext(Dispatchers.Main) {
                            _currentDocuments.value = listOf(parsedDoc)
                            _recordList.value = newRecords
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 这里后续可以加入错误提示，例如通过 SharedFlow 通知 UI 弹出 Toast："JSON 解析失败"
                }
            }
        }
    }

    /**批量任务拉取*/
    fun loadMultipleTaskDetails(documentIds: List<String>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val docs = mutableListOf<TargetDocument>()
            val newRecords = mutableListOf<InventoryRecord>()
            var hasError = false

            for (id in documentIds) {
                val result = repository.fetchPythonTaskDetail(id)
                if (result.isSuccess) {
                    val parsedDoc = result.getOrNull()
                    if (parsedDoc != null) {
                        docs.add(parsedDoc)
                        // 把每个任务里的明细打上该任务的烙印，塞进一个大池子里
                        newRecords.addAll(parsedDoc.items.map { item ->
                            InventoryRecord(
                                documentId = parsedDoc.documentId,
                                documentName = parsedDoc.documentName,
                                di = item.di,
                                productName = item.productName,
                                batch = item.batch,
                                productionDate = item.productionDate ?: "-",
                                expiryDate = item.expiryDate ?: "-",
                                targetQty = item.targetQty,
                                actualQty = 0,
                                isError = false
                            )
                        })
                    }
                } else {
                    hasError = true
                }
            }

            // ⚠️ 破旧立新：新任务加载成功后，直接替换（覆盖）原有的池子和照片！
            _photoList.value = emptyList()
            _photoScanResults.value = emptyList()
            _currentDocuments.value = docs
            _recordList.value = newRecords

            _isLoading.value = false
            onResult(if (hasError) "部分单据获取失败，请检查网络" else null)
        }
    }
}