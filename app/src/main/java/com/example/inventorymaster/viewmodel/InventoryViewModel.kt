package com.example.inventorymaster.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.inventorymaster.InventoryApplication
import com.example.inventorymaster.data.InventoryRepository
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import com.example.inventorymaster.data.model.ConflictAction
import com.example.inventorymaster.data.model.ProductConflict
import com.example.inventorymaster.utils.Gs1Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.inventorymaster.data.dto.SessionDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. 定义 UI 状态
data class InventoryUiState(
    val sessions: List<InventorySession> = emptyList(),
    val currentSessionId: Long? = null,
    val searchResults: List<StockRecordCombined> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val productList: List<ProductBase> = emptyList(),
    val conflictList: List<ProductConflict> = emptyList(),
    val isAnalyzing: Boolean = false,
    val userMessage: String? = null
)

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    private var pendingExcelProducts: List<ProductBase> = emptyList()
    private var pendingExcelRecords: List<StockRecord> = emptyList()
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()
    private val _cloudSessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val cloudSessions: StateFlow<List<SessionDto>> = _cloudSessions

    init {
        viewModelScope.launch {
            repository.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
            }
        }
    }

    var isGlobalLoading by mutableStateOf(false)
        private set
    var lastServerIp by mutableStateOf("")
        private set

    fun updateServerIp(ip: String) {
        lastServerIp = ip
    }

    // --- Session 操作 ---
    fun createNewSession(name: String) {
        viewModelScope.launch { repository.createSession(name) }
    }

    suspend fun tryFinishSession(sessionId: Long): Int {
        val unverifiedCount = repository.getUnverifiedCount(sessionId)
        if (unverifiedCount == 0) {
            repository.updateSessionStatus(sessionId, 1)
            return -1
        }
        return unverifiedCount
    }

    fun toggleLockSession(session: InventorySession) {
        viewModelScope.launch {
            val newStatus = if (session.status == 2) 1 else 2
            repository.updateSessionStatus(session.id, newStatus)
        }
    }

    fun deleteSession(session: InventorySession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    // --- 核心搜索逻辑 ---
    fun performSearch(query: String, isInventoryMode: Boolean) {
        val cleanQuery = query.trim()
        _uiState.value = _uiState.value.copy(isLoading = true, searchQuery = cleanQuery)

        viewModelScope.launch {
            try {
                val sessionId = _uiState.value.currentSessionId
                if (sessionId == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val scanResult = Gs1Parser.parse(cleanQuery)
                val results = if (scanResult.isUdi && !scanResult.di.isNullOrBlank()) {
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
            val syncedRecord = prepareRecordForSync(record, "手动修改: 数量=${record.quantity}, 备注=${record.remarks ?: "无"}")

            repository.updateRecord(syncedRecord)

            val currentQuery = _uiState.value.searchQuery
            performSearch(currentQuery, isInventoryMode = false)
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
                remarks = newRemarks
            )

            // 2. 🔥 自动填充同步字段
            val finalRecord = prepareRecordForSync(businessUpdatedRecord, "快速查验: 数量=${businessUpdatedRecord.quantity}")

            // 3. 保存
            repository.updateRecord(finalRecord)

            // 4. 原地刷新 UI
            val currentList = _uiState.value.searchResults
            val newList = currentList.map {
                if (it.record.id == record.id) {
                    it.copy(record = finalRecord)
                } else {
                    it
                }
            }
            _uiState.value = _uiState.value.copy(searchResults = newList)
        }
    }

    // =========================================================================

    // --- 导入导出 ---
    fun importExcelData(sessionId: Long, products: List<ProductBase>, records: List<StockRecord>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.importExcelData(sessionId, products, records)
                _uiState.value = _uiState.value.copy(isLoading = false, searchResults = emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    suspend fun getExportDataForExcel(sessionId: Long): List<StockRecordCombined> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            val result = repository.uploadSessionData(ip, sessionId)
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
            val result = repository.downloadFromPC(ip, targetSessionId)
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

    fun fetchCloudTasks(ip: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.fetchCloudSessions(ip)
            if (result.isSuccess) {
                _cloudSessions.value = result.getOrNull() ?: emptyList()
                if (_cloudSessions.value.isEmpty()) {
                    _uiState.value = _uiState.value.copy(userMessage = "服务端暂无任务")
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    userMessage = "连接失败: ${result.exceptionOrNull()?.message}"
                )
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
