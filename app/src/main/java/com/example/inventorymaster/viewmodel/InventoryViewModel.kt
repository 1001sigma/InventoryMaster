package com.example.inventorymaster.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.inventorymaster.InventoryApplication
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.utils.BatchCodeProcessor
import com.example.inventorymaster.utils.ExcelUtils
import com.example.inventorymaster.utils.Gs1Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// 1. 定义 UI 状态
data class InventoryUiState(
    val searchResults: List<StockRecordCombined> = emptyList(),
    val currentSessionId: Long? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val productList: List<ProductBase> = emptyList(),
    val conflictList: List<ProductConflict> = emptyList(),
    val isAnalyzing: Boolean = false,
    val userMessage: String? = null,
    val isBatchProcessorEnabled: Boolean = false,
    val autoPushSessionId: Long? = null,
    // 👇 新增：DI 校验状态和错误列表
    val isDiValidationEnabled: Boolean = false, // 开关状态
    val invalidDiList: List<StockRecordCombined> = emptyList() // 校验失败的清单，只要不为空，UI就弹窗
)


class InventoryViewModel(private val repository: InventoryRepository, private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    private var pendingExcelProducts: List<ProductBase> = emptyList()
    private var pendingExcelRecords: List<StockRecord> = emptyList()
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()
    // 👇 新增 1：初始化时监听 DataStore
    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(isDiValidationEnabled = settings.enableDiValidation)
            }
        }
    }
    // 👇 新增 2：供 UI 调用的开关切换方法
    fun toggleDiValidation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDiValidation(enabled)
        }
    }


    fun toggleBatchProcessor(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isBatchProcessorEnabled = enabled)

        // [可选优化]：用户切换开关时，如果搜索框里已经有字了，要不要立即重新搜索？
        // 如果想立即刷新结果，加上这句：
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery, isInventoryMode = true) // 这里的参数根据你的实际情况传
        }
    }


    // --- 核心搜索逻辑 ---
    fun performSearch(query: String, isInventoryMode: Boolean) {
        val cleanQuery = query.trim()
        _uiState.value = _uiState.value.copy(isLoading = true, searchQuery = cleanQuery)
        val useSmartBatch = _uiState.value.isBatchProcessorEnabled

        viewModelScope.launch {
            try {
                val sessionId = _uiState.value.currentSessionId
                if (sessionId == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val scanResult = Gs1Parser.parse(cleanQuery)
                val processedBatch = BatchCodeProcessor.process(cleanQuery)
                val results =
                    if (useSmartBatch) {
                        repository.getRecordsBybatchorexpiryDate(sessionId,processedBatch)
                    } else if (scanResult.isUdi && !scanResult.di.isNullOrBlank()) {
                        val di = scanResult.di!!
                        val batch = scanResult.batch
                        if (!batch.isNullOrBlank()) {
                            val diandand = repository.getRecordsByUdi(sessionId, di, batch)
                            if (diandand.isEmpty()) {
                                repository.getRecordsBybatchorexpiryDate(sessionId,batch)
                            } else {
                                repository.getRecordsByDi(sessionId, di)
                            }
                        } else {
                        repository.getRecordsByDi(sessionId, di)
                    }
                } else {
                    repository.searchRecords(sessionId, cleanQuery)
                }

                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isLoading = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // =========================================================================
    // 🔥 [核心修改区域] 增删改查 (自动填充同步字段)
    // =========================================================================

    /**
     * 自动填充同步字段 (装饰器模式)
     * 在保存到数据库前，统一打上：时间戳、操作人、待同步标记、历史日志
     */
    private fun prepareRecordForSync(record: StockRecord, logAction: String): StockRecord {
        val now = System.currentTimeMillis()
        val operatorName = "手机端" // 后续可从 Settings 获取
        val deviceModel = android.os.Build.MODEL // 获取手机型号，如 "Xiaomi 13"
        val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(now))

        // 1. 生成日志: "[10-27 14:30 Xiaomi 13] 手机端: 修改内容..."
        val newLogEntry = "[$timeStr $deviceModel] $operatorName: $logAction"

        // 2. 追加到现有历史记录 (防止 null)
        val currentHistory = record.historyLog ?: ""
        val updatedHistory = if (currentHistory.isBlank()) newLogEntry else "$currentHistory\n$newLogEntry"

        // 3. 返回这一刻最新的 Record 对象
        return record.copy(
            lastUpdateTime = now,
            syncStatus = 1, // 🔥 1 = 待上传 (UNSYNCED)
            operator = operatorName,
            historyLog = updatedHistory
        )
    }

    // 1. 新增记录
    fun addRecord(record: StockRecord) {
        viewModelScope.launch {
            // 🔥 自动填充同步字段
            val syncedRecord = prepareRecordForSync(record, "扫码新增: 数量=${record.quantity}")

            repository.saveRecord(syncedRecord)

            val currentQuery = _uiState.value.searchQuery
            performSearch(currentQuery, isInventoryMode = true)
        }
    }

    // 2. 修改记录 (数量、备注)
    fun updateRecord(record: StockRecord) {
        viewModelScope.launch {
            // 🔥 自动填充同步字段
            // 注意：这里简单记录了"手动修改"，如果需要更详细的 Diff (如 1->5)，需要在 UI 层传进来或者先查库对比
            // 1. 准备数据：利用 copy() 强制标记为脏数据
            // 不管之前 prepareRecordForSync 做了什么，这里必须确保 syncStatus = 1
            val recordToSave = record.copy(
                syncStatus = 1, // 🚩 关键！标记为未同步
                lastUpdateTime = System.currentTimeMillis(),
                // 更新时间戳
                // 如果 prepareRecordForSync 里有特殊备注逻辑，可以在这里把 remarks = ... 加上
            )
            repository.updateRecord(recordToSave)
            _uiState.value = _uiState.value.copy(autoPushSessionId = record.sessionId)
            val currentQuery = _uiState.value.searchQuery
            performSearch(currentQuery, isInventoryMode = false)

            // 5. (可选) 给用户一个轻提示
            //_syncMessage.value = "保存成功，正在同步..."
        }
    }

    // 3. 删除记录 (通常是软删除，这里先保留物理删除，但要考虑同步问题)
    // ⚠️ 注意：如果是物理删除，同步时需要特殊处理(发一个 delete 指令给电脑)
    // 暂时先保持原样，后续做同步逻辑时可能要改成"软删除"(status = -1)
    fun deleteRecord(record: StockRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
            val currentQuery = _uiState.value.searchQuery
            performSearch(currentQuery, isInventoryMode = false)
        }
    }

    // 4. 快速查验
    fun quickCheck(combined: StockRecordCombined) {
        viewModelScope.launch {
            val record = combined.record
            // 1. 业务逻辑：修改备注和实际数量
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(record.lastUpdateTime)
            val appendText = "已查验[$time]${record.operator};"
            val newRemarks = if (record.remarks.isNullOrBlank()) appendText else "$appendText ${record.remarks} "

            // 构造业务变更后的对象
            val businessUpdatedRecord = record.copy(
                actualQuantity = record.quantity, //自动填充同步字段
                remarks = newRemarks,
                syncStatus = 1,                   // 🚩 标记脏数据
                lastUpdateTime = record.lastUpdateTime
            )
            // 3. 保存
            repository.updateRecord(businessUpdatedRecord)
            //立即触发上传
            _uiState.value = _uiState.value.copy(autoPushSessionId = record.sessionId)
            // 4. 原地刷新 UI
            val currentList = _uiState.value.searchResults
            val newList = currentList.map {
                if (it.record.id == record.id) {
                    it.copy(record = businessUpdatedRecord)
                } else {
                    it
                }
            }
            _uiState.value = _uiState.value.copy(searchResults = newList)
        }
    }

    fun clearAutoPushSignal() {
        _uiState.value = _uiState.value.copy(autoPushSessionId = null)
    }

    // =========================================================================

    //region--- 导入导出 ---
//    fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>) {
//        viewModelScope.launch {
//            _uiState.value = _uiState.value.copy(isLoading = true)
//            try {
//                repository.importExcelData(sessionId, products, records)
//                _uiState.value = _uiState.value.copy(isLoading = false, searchResults = emptyList())
//            } catch (e: Exception) {
//                e.printStackTrace()
//                _uiState.value = _uiState.value.copy(isLoading = false)
//            }
//        }
//    }
    //endregion

    // 导入逻辑
    fun importExcelFile(context: Context, uri: Uri, sessionId: Long) {
        // 开启一个协程，指定在 IO 线程运行
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 设置加载状态 (UI层可以观察这个状态来显示转圈圈)
                // _uiState.update { it.copy(isLoading = true) }
                val isValidationEnabled = _uiState.value.isDiValidationEnabled

                // 2. 解析 (耗时操作)
                val result = ExcelUtils.parseExcel(context, uri, sessionId)

                // 3. 保存 Schema
                ExcelUtils.SchemaHelper.saveSchema(context, sessionId, result.schema)

                // 4. 处理业务数据
                analyzeExcelImport(result.products, result.records)

                // 5. 提示成功 (如果要弹 Toast，建议用 SharedFlow 发送事件给 UI，或者简单点直接在 UI 回调里做)
                // 这里的处理稍微复杂一点，简单的做法看下面 UI 层的改动
                if (result.invalidItems.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(invalidDiList = result.invalidItems)
                } else if (isValidationEnabled) {
                    _uiState.value = _uiState.value.copy(userMessage = "导入成功，所有 DI 校验通过！")
                } else {
                    _uiState.value = _uiState.value.copy(userMessage = "导入成功")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 处理错误状态
            } finally {
                // _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // 导出逻辑
    fun exportExcelFile(context: Context, uri: Uri, sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 准备数据
                val data = getExportDataForExcel(sessionId)

                // 2. 获取 Schema
                val schema = ExcelUtils.SchemaHelper.getSchema(context, sessionId)

                // 3. 写入流
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ExcelUtils.exportExcel(outputStream, data, schema, "管理员")
                }

                // 4. 通知 UI 成功 (可以通过 State 或 Channel)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getExportDataForExcel(sessionId: Long): List<StockRecordCombined> {
        return withContext(Dispatchers.IO) {
            repository.getExportData(sessionId)
        }
    }

    fun selectSession(sessionId: Long) {
        _uiState.value = _uiState.value.copy(
            currentSessionId = sessionId,
            searchResults = emptyList(),
            searchQuery = ""
        )
    }

    // 新增：清空搜索结果的通用方法
    fun clearSearchResults() {
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            searchQuery = ""
        )
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(searchQuery = newQuery)
    }

    suspend fun getProductInfo(di: String): ProductBase? {
        return repository.getProductByDi(di)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as InventoryApplication)
                return InventoryViewModel(application.repository,com.example.inventorymaster.data.SettingsRepository(application)) as T
            }
        }
    }

    // 搜索产品字典
    fun searchProducts(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val list = if (query.isBlank()) {
                    emptyList()
                } else {
                    repository.searchProducts(query)
                }
                _uiState.value = _uiState.value.copy(productList = list, isLoading = false)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // 更新产品信息
    fun updateProductBase(product: ProductBase) {
        viewModelScope.launch {
            repository.updateProduct(product)
            val currentList = _uiState.value.productList.map {
                if (it.di == product.di) product else it
            }
            _uiState.value = _uiState.value.copy(productList = currentList)
        }
    }

    // A. 解析 Excel
    fun analyzeExcelImport(products: List<ProductBase>, records: List<StockRecord>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            pendingExcelProducts = products
            pendingExcelRecords = records
            val conflicts = repository.checkProductConflicts(products)
            if (conflicts.isEmpty()) {
                repository.saveImportDataWithResolution(products, records)
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    conflictList = conflicts,
                    isAnalyzing = false
                )
            }
        }
    }

    fun setAllConflictsAction(action: ConflictAction) {
        val current = _uiState.value.conflictList
        val updated = current.map { it.copy(resolveAction = action) }
        _uiState.value = _uiState.value.copy(conflictList = updated)
    }

    fun toggleConflictAction(di: String) {
        val current = _uiState.value.conflictList
        val updated = current.map {
            if (it.di == di) {
                val nextAction = if (it.resolveAction == ConflictAction.OVERWRITE) ConflictAction.IGNORE else ConflictAction.OVERWRITE
                it.copy(resolveAction = nextAction)
            } else it
        }
        _uiState.value = _uiState.value.copy(conflictList = updated)
    }

    fun confirmConflictResolution() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val conflicts = _uiState.value.conflictList
            val finalProducts = pendingExcelProducts.toMutableList()

            for (conflict in conflicts) {
                if (conflict.resolveAction == ConflictAction.IGNORE) {
                    val index = finalProducts.indexOfFirst { it.di == conflict.di }
                    if (index != -1) {
                        finalProducts[index] = conflict.oldProduct
                    }
                }
            }
            repository.saveImportDataWithResolution(finalProducts, pendingExcelRecords)
            _uiState.value = _uiState.value.copy(conflictList = emptyList(), isAnalyzing = false)
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(conflictList = emptyList(), isAnalyzing = false)
        pendingExcelProducts = emptyList()
        pendingExcelRecords = emptyList()
    }

    // 👇 新增：手动校验当前列表的所有 DI
    fun validateCurrentTaskDIs() {
        val currentData = _uiState.value.searchResults
        val invalidList = currentData.filter { item ->
            val di = item.record.di
            if (di.isBlank()) {
                false // 跳过空 DI
            } else {
                !Gs1Parser.isValidDI(di) // 不合法的挑出来
            }
        }

        if (invalidList.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(invalidDiList = invalidList)
        } else {
            _uiState.value = _uiState.value.copy(userMessage = "当前任务非空 DI 均校验通过！")
        }
    }

    // 👇 新增：关闭警告弹窗并清空错误列表
    fun clearInvalidDiList() {
        _uiState.value = _uiState.value.copy(invalidDiList = emptyList())
    }
}
