package com.example.inventorymaster.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.inventorymaster.InventoryApplication
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.utils.BatchCodeProcessor
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
import android.net.Uri
import com.example.inventorymaster.utils.ExcelUtils


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
    val isBatchProcessorEnabled: Boolean = false
)

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    private var pendingExcelProducts: List<ProductBase> = emptyList()
    private var pendingExcelRecords: List<StockRecord> = emptyList()
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // 🔥 新增：同步状态 (用于控制 UI 上的 loading 转圈或提示)
    // 状态含义: false=空闲, true=正在同步中
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    // 🔥 新增：同步结果消息 (用于弹 Toast，比如 "同步成功" 或 "网络错误")
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    // 🔒 真正的同步锁（不是 UI 状态）
    private var isSyncRunning = false

    // 📨 同步期间是否又收到新触发
    private var hasPendingSync = false
    // ==========================================
    // 🟢 动作 A: 增量下载 (Pull)
    // 建议在 init {} 块中或者 Activity 的 onResume 调用它
    private fun refreshDataPull(sessionId: Long) {
        if (isSyncRunning) {
            hasPendingSync = true
            return
        }
        isSyncRunning = true
        hasPendingSync = false
        _isSyncing.value = true   // UI loading
        viewModelScope.launch {
            do {
                val result = withContext(Dispatchers.IO) {
                    repository.pullNewData(sessionId)
                }

                if (result.isFailure) {
                    _syncMessage.value = "同步失败: ${result.exceptionOrNull()?.message}"
                    break
                }

            } while (hasPendingSync)

            isSyncRunning = false
            _isSyncing.value = false
        }
    }

    fun refreshData (sessionId: Long) {
        refreshDataPull(sessionId)
    }

    // 🔴 动作 B: 增量上传 (Push)
    // ==========================================
    // 建议在 saveRecord / scanBarcode 等修改数据库的方法 *之后* 调用
    //private
    fun triggerPush(sessionId: Long) {
        viewModelScope.launch {
            // 注意：上传通常是静默的，不需要让 _isSyncing 转圈，以免打扰用户操作
            // 除非你希望用户看着它传完
            val result = withContext(Dispatchers.IO) {
                repository.pushUnsyncedData(sessionId)
            }

            if (result.isFailure) {
                // 这里可以选择不打扰用户，或者显示一个小感叹号
                println("Push failed: ${result.exceptionOrNull()?.message}")
            } else {
                println("Push success")
            }
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

    var isGlobalLoading by mutableStateOf(false)
        private set
    var lastServerIp by mutableStateOf("")
        private set
    fun updateServerIp(ip: String) {
        lastServerIp = ip
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
            triggerPush(record.sessionId)
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
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date())
            val appendText = "已查验, $time"
            val newRemarks = if (record.remarks.isNullOrBlank()) appendText else "${record.remarks}, $appendText"

            // 构造业务变更后的对象
            val businessUpdatedRecord = record.copy(
                actualQuantity = record.quantity,
                remarks = newRemarks,
                syncStatus = 1,                   // 🚩 标记脏数据
                lastUpdateTime = System.currentTimeMillis()
            )

            // 2. 🔥 自动填充同步字段
            // val finalRecord = prepareRecordForSync(businessUpdatedRecord, "快速查验: 数量=${businessUpdatedRecord.quantity}")

            // 3. 保存
            repository.updateRecord(businessUpdatedRecord)
            //立即触发上传
            triggerPush(record.sessionId)

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

    // InventoryViewModel.kt

    // 导入逻辑
    fun importExcelFile(context: Context, uri: Uri, sessionId: Long) {
        // 开启一个协程，指定在 IO 线程运行
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 设置加载状态 (UI层可以观察这个状态来显示转圈圈)
                // _uiState.update { it.copy(isLoading = true) }

                // 2. 解析 (耗时操作)
                val result = ExcelUtils.parseExcel(context, uri, sessionId)

                // 3. 保存 Schema
                ExcelUtils.SchemaHelper.saveSchema(context, sessionId, result.schema)

                // 4. 处理业务数据
                analyzeExcelImport(result.products, result.records)

                // 5. 提示成功 (如果要弹 Toast，建议用 SharedFlow 发送事件给 UI，或者简单点直接在 UI 回调里做)
                // 这里的处理稍微复杂一点，简单的做法看下面 UI 层的改动

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
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId)
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
                return InventoryViewModel(application.repository) as T
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

    // 上传功能 (预留)
    fun uploadDataToServer(ip: String) {
        val sessionId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.exportFullSession(ip, sessionId)
            if (result.isSuccess) {
                repository.saveServerIp(ip)
                updateServerIp(ip)
            }
            _uiState.value = _uiState.value.copy(isLoading = false,
                userMessage = if (result.isSuccess) result.getOrNull() else "上传失败: ${result.exceptionOrNull()?.message}")
        }
    }

    // 下载数据
    fun downloadDataFromPC(ip: String, targetSessionId: Long) {
        viewModelScope.launch {
            isGlobalLoading = true
            if (!com.example.inventorymaster.utils.NetworkUtils.isValidIpAddress(ip)) {
                _uiState.value = _uiState.value.copy(userMessage = "IP 地址格式错误")
                isGlobalLoading = false
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.exportdownloadFromPC(ip, targetSessionId)
            if (result.isSuccess) {
                repository.saveServerIp(ip)
                updateServerIp(ip)
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                userMessage = if (result.isSuccess) result.getOrNull() else "下载失败: ${result.exceptionOrNull()?.message}"
            )
            if (result.isSuccess) {
                updateServerIp(ip)
                _uiState.value = _uiState.value.copy(userMessage = "加入成功！")
            } else {
                _uiState.value = _uiState.value.copy(
                    userMessage = "连接失败: ${result.exceptionOrNull()?.message}"
                )
            }
            isGlobalLoading = false
        }
    }


}
