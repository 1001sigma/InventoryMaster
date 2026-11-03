package com.example.inventorymaster.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.inventorymaster.InventoryApplication
import com.example.inventorymaster.data.connection.DoorbellEvent
import com.example.inventorymaster.data.connection.InventoryDoorbellClient
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.entity.ExpiryState
import com.example.inventorymaster.data.entity.HighlightField
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.uncheck
import com.example.inventorymaster.data.entity.StockRecordUiModel
import com.example.inventorymaster.data.model.MappingTemplate
import com.example.inventorymaster.data.model.StockRecordCombined
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.utils.BatchCodeProcessor
import com.example.inventorymaster.utils.DateUtils
import com.example.inventorymaster.utils.ExcelUtils
import com.example.inventorymaster.utils.Gs1Parser
import com.example.inventorymaster.utils.MappingTemplateManager
import com.example.inventorymaster.utils.ProductJsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.UUID


// 1. 定义 UI 状态
data class InventoryUiState(
    val searchResults: List<StockRecordUiModel> = emptyList(),
    val currentSessionId: Long? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val productList: List<ProductBase> = emptyList(),
    val productCount: Int = 0,
    val importSummary: String? = null,
    val isAnalyzing: Boolean = false,
    val userMessage: String? = null,
    val isBatchProcessorEnabled: Boolean = false,
    // 👇 新增：DI 校验状态和错误列表
    val isDiValidationEnabled: Boolean = false, // 开关状态
    val invalidDiList: List<StockRecordCombined> = emptyList(), // 校验失败的清单，只要不为空，UI就弹窗
    // 产品库删除模式
    val isProductDeleteMode: Boolean = false,
    val selectedProductsForDelete: Set<String> = emptySet(),
    // Excel 映射模板
    val mappingTemplates: List<MappingTemplate> = emptyList(),
    // 近效期提醒天数
    val nearExpiryThresholdDays: Int = 30,
    // 记录卡片五个信息位的字段映射（设置 → 盘存设置 → 记录卡片样式）
    val cardConfig: com.example.inventorymaster.data.model.RecordCardConfig = com.example.inventorymaster.data.model.RecordCardConfig(),
    // 产品库导入诊断信息
    val importDiagnostics: com.example.inventorymaster.data.model.ImportDiagnostics? = null,
    // 服务器 Token 提示弹窗
    val showTokenPrompt: Boolean = false,
    val tokenPromptMessage: String? = null,
    val sinkVerifiedItems: Boolean = false,
    val emptySearchShowsNothing: Boolean = false
)


class InventoryViewModel(
    private val app: Application,
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    private var pendingExcelProducts: List<ProductBase> = emptyList()
    private var pendingExcelRecords: List<StockRecord> = emptyList()
    private var pendingImportItems: List<com.example.inventorymaster.data.model.ProductImportItem> = emptyList()
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // M0 基建：盘库 WS 门铃（服务端数据变更 → 静默增量拉取；断线退化为手动下拉刷新）
    private val doorbellClient = InventoryDoorbellClient(
        (app as InventoryApplication).connectionManager,
        settingsRepository
    )
    private var currentSessionUuid: String? = null
    private var cachedOperatorName: String = ""  // M3：绑定的操作员姓名，落库归属用
    private var doorbellPullInFlight = false
    private var doorbellPullPending = false

    // M0 修复：自动推送改为 VM 内防抖直推（原 UI 信号链路有两个丢包点：
    // ① SyncViewModel.isSyncRunning 时意图被静默丢弃且不重试；② StateFlow 同值不发射，
    // 快速连续查验时第二次设值不触发）。防抖合并连续操作，取消重启保证最后一次必定推送，
    // 失败静默（下一次编辑重新调度，门铃/手动同步兜底）。
    private var autoPushJob: Job? = null

    private fun scheduleAutoPush(sessionId: Long) {
        autoPushJob?.cancel()
        autoPushJob = viewModelScope.launch {
            delay(1000) // 防抖：连续查验/编辑合并为一次推送
            repository.pushUnsyncedData(sessionId, userInitiated = false)
                .onFailure { e ->
                    // 本地状态滞后于服务端（任务实际仍待开始）→ 静默重拉对齐状态机落锁；
                    // 本地脏记录保留，任务开始后随自动推送上行
                    val code = (e as? com.example.inventorymaster.data.network.CollabApiException)?.code
                    if (code == "TASK_NOT_STARTED") {
                        silentDoorbellPull(sessionId)
                    }
                }
            repository.reportPendingTaskStatuses()  // M5：同步成功后补报归档
        }
    }

    // 👇 新增 1：初始化时监听 DataStore + 加载产品总数
    init {
        viewModelScope.launch {
            val count = repository.getProductCount()
            _uiState.value = _uiState.value.copy(productCount = count)
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val sinkChanged = _uiState.value.sinkVerifiedItems != settings.sinkVerifiedItems
                val emptySearchChanged = _uiState.value.emptySearchShowsNothing != settings.emptySearchShowsNothing
                val currentResults = _uiState.value.searchResults
                val newResults = if (sinkChanged && currentResults.isNotEmpty()) {
                    sortSearchResults(currentResults, settings.sinkVerifiedItems)
                } else {
                    currentResults
                }
                _uiState.value = _uiState.value.copy(
                    searchResults = newResults,
                    isDiValidationEnabled = settings.enableDiValidation,
                    nearExpiryThresholdDays = settings.nearExpiryThresholdDays,
                    cardConfig = settings.recordCardConfig,
                    isBatchProcessorEnabled = settings.enableHybiomeMode,
                    sinkVerifiedItems = settings.sinkVerifiedItems,
                    emptySearchShowsNothing = settings.emptySearchShowsNothing
                )
                cachedOperatorName = settings.operatorName  // M3：绑定操作员（R1）

                // 如果空搜索不显示设置发生切换且当前搜索框为空，重新执行一次搜索以同步最新状态
                if (emptySearchChanged && _uiState.value.searchQuery.isBlank() && _uiState.value.currentSessionId != null) {
                    performSearch("", isInventoryMode = true, silent = true)
                }
            }
        }
        // M5：启动时补报离线队列中的归档上报
        viewModelScope.launch { repository.reportPendingTaskStatuses() }
        viewModelScope.launch {
            doorbellClient.events.collect { onDoorbellEvent(it) }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect {
                doorbellClient.setEnabled(it.inventoryPushEnabled)
                if (it.inventoryPushEnabled && _uiState.value.currentSessionId != null) {
                    doorbellClient.ensureConnected()
                }
            }
        }
    }

    private fun onDoorbellEvent(e: DoorbellEvent) {
        when (e) {
            is DoorbellEvent.DataChanged -> {
                // 只刷新当前打开的任务；其他任务的变更等用户进入时经全量下载/拉取对齐
                if (e.sessionUuid == currentSessionUuid) {
                    _uiState.value.currentSessionId?.let { silentDoorbellPull(it) }
                }
            }
            is DoorbellEvent.SessionChanged -> {
                // M6：任务生命周期变化（开始/归档）→ 本地存在该任务则静默拉取对齐状态
                viewModelScope.launch {
                    val local = repository.getSessionByUuid(e.sessionUuid)
                    if (local != null) silentDoorbellPull(local.id)
                }
            }
        }
    }

    /** 门铃触发的静默拉取：userInitiated=false（失败不弹发现弹窗），防重入合并 */
    private fun silentDoorbellPull(sessionId: Long) {
        if (doorbellPullInFlight) {
            doorbellPullPending = true
            return
        }
        doorbellPullInFlight = true
        viewModelScope.launch {
            try {
                do {
                    doorbellPullPending = false
                    val pullResult = repository.pullNewData(sessionId, userInitiated = false)
                    repository.reportPendingTaskStatuses()  // M5：拉取成功后补报
                    // searchResults 是一次性快照，拉到数据后静默重建列表（别人推的记录即刻上屏且带徽标）
                    if (pullResult.isSuccess) {
                        performSearch(_uiState.value.searchQuery, isInventoryMode = true, silent = true)
                    }
                } while (doorbellPullPending)
            } finally {
                doorbellPullInFlight = false
            }
        }
    }

    override fun onCleared() {
        doorbellClient.close()
        super.onCleared()
    }

    // 👇 新增 2：供 UI 调用的开关切换方法
    fun toggleDiValidation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDiValidation(enabled)
        }
    }


    fun toggleBatchProcessor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHybiomeMode(enabled)
        }
        _uiState.value = _uiState.value.copy(isBatchProcessorEnabled = enabled)

        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery, isInventoryMode = true)
        }
    }


    // --- 核心搜索逻辑 ---
    // silent=true 供门铃静默拉取后重建列表用：不弹全屏 loading，仅更新结果
    fun performSearch(query: String, isInventoryMode: Boolean, silent: Boolean = false) {
        val cleanQuery = query.trim()
        _uiState.value = _uiState.value.copy(isLoading = !silent, searchQuery = cleanQuery)
        val useSmartBatch = _uiState.value.isBatchProcessorEnabled

        viewModelScope.launch {
            try {
                val sessionId = _uiState.value.currentSessionId
                if (sessionId == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val results = if (cleanQuery.isBlank()) {
                    if (_uiState.value.emptySearchShowsNothing) {
                        // 开关开启：空搜索不显示任何数据
                        emptyList()
                    } else {
                        // 空查询 = 全部记录（进入任务页/清空搜索时全量上屏，且带效期徽标）
                        repository.getSessionRecords(sessionId)
                    }
                } else {
                    // 1. 智能长光条码识别（若开启且符合长光特征）：提取 8 位批号优先检索
                    val hybiomeBatch = if (useSmartBatch && BatchCodeProcessor.isHybiomeBarcode(cleanQuery)) {
                        BatchCodeProcessor.extractBatch(cleanQuery)
                    } else null

                    val hybiomeResults = if (!hybiomeBatch.isNullOrBlank()) {
                        val exactBatch = repository.getRecordsBybatchorexpiryDate(sessionId, hybiomeBatch)
                        if (exactBatch.isNotEmpty()) {
                            exactBatch
                        } else {
                            repository.searchRecords(sessionId, hybiomeBatch)
                        }
                    } else {
                        emptyList()
                    }

                    // 命中了长光批号则直接返回；若未命中则智能平滑降级走常规流程（保证正常 GS1/品名/编码等不被误杀）
                    if (hybiomeResults.isNotEmpty()) {
                        hybiomeResults
                    } else {
                        val scanResult = Gs1Parser.parse(cleanQuery)
                        if (scanResult.isUdi && !scanResult.di.isNullOrBlank()) {
                            val di = scanResult.di!!
                            val batch = scanResult.batch
                            // 组合键下同 DI 可对应多条产品（物料编码不同），按产品表 di 字段 JOIN 全部查出
                            if (!batch.isNullOrBlank()) {
                                val byDiBatch = repository.getRecordsByDiAndBatch(sessionId, di, batch)
                                if (byDiBatch.isEmpty()) {
                                    repository.getRecordsBybatchorexpiryDate(sessionId, batch)
                                } else {
                                    repository.getRecordsByDi(sessionId, di)
                                }
                            } else {
                                repository.getRecordsByDi(sessionId, di)
                            }
                        } else {
                            // 非 UDI 输入：组合键 / DI / 物料编码 任一精确命中（扫码/粘贴场景），
                            // 未命中再走多字段模糊（品名/厂家/库位/批号/规格/型号/备注…）
                            val exact = repository.getRecordsByKeyOrDiOrMat(sessionId, cleanQuery)
                            if (exact.isNotEmpty()) exact
                            else repository.searchRecords(sessionId, cleanQuery)
                        }
                    }
                }

                val uiList = mapToUiModels(results, cleanQuery, _uiState.value.nearExpiryThresholdDays)
                val sortedList = sortSearchResults(uiList, _uiState.value.sinkVerifiedItems)
                _uiState.value = _uiState.value.copy(
                    searchResults = sortedList,
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
    fun sortSearchResults(
        list: List<StockRecordUiModel>,
        sinkVerified: Boolean
    ): List<StockRecordUiModel> {
        return if (sinkVerified) {
            list.sortedWith(compareBy({ it.combined.record.isVerified }, { it.combined.record.id }))
        } else {
            list.sortedBy { it.combined.record.id }
        }
    }

    // 在 ViewModel 中添加这个私有函数
    private fun mapToUiModels(
        dbResults: List<StockRecordCombined>,
        query: String,
        nearExpiryThresholdDays: Int = 30
    ): List<StockRecordUiModel> {
        val today = LocalDate.now()

        return dbResults.map { combined ->
            val record = combined.record
            val product = combined.product

            // 1. 判断哪个字段该高亮 (忽略大小写)
            val highlight = when {
                query.isBlank() -> HighlightField.NONE
                product?.productName?.contains(query, ignoreCase = true) == true -> HighlightField.PRODUCT_NAME
                record.batchNumber.contains(query, ignoreCase = true) -> HighlightField.BATCH_NUMBER
                // 按 DI 字段判断，避免组合键误命中物料编码段
                product?.di?.contains(query, ignoreCase = true) == true -> HighlightField.DI
                product?.materialCode?.contains(query, ignoreCase = true) == true -> HighlightField.DI
                record.location.contains(query, ignoreCase = true) -> HighlightField.LOCATION
                else -> HighlightField.NONE
            }

            // 2. 计算效期状态（DateUtils.toLocalDate 与显示端同一套形态判定：
            //    毫秒时间戳/8位/6位，GS1 日=00 按月末；解析失败为 null → 正常，不抛异常）
            var expiryState = ExpiryState.NORMAL
            val expiryDateObj = DateUtils.toLocalDate(record.expiryDate)
            if (expiryDateObj != null) {
                val remainingDays = ChronoUnit.DAYS.between(today, expiryDateObj)

                // 如果有生产日期，计算总效期天数
                val prodDateObj = DateUtils.toLocalDate(record.productionDate)
                val totalDays = prodDateObj?.let { ChronoUnit.DAYS.between(it, expiryDateObj) }

                // 近效期规则判断
                val isNearExpiryByDays = remainingDays in 0..nearExpiryThresholdDays.toLong()
                // 规则a：剩余时间 < 总有效时间的 25%
                val isNearExpiryByRatio = totalDays != null && totalDays > 0 && remainingDays < (totalDays * 0.25)

                expiryState = when {
                    remainingDays < 0 -> ExpiryState.EXPIRED
                    isNearExpiryByDays || isNearExpiryByRatio -> ExpiryState.NEAR_EXPIRY
                    else -> ExpiryState.NORMAL
                }
            }

            // 组装并返回
            StockRecordUiModel(
                combined = combined,
                highlightField = highlight,
                expiryState = expiryState
            )
        }
    }

    /**
     * 自动填充同步字段 (装饰器模式)
     * 在保存到数据库前，统一打上：时间戳、操作人、待同步标记、历史日志
     */
    private fun prepareRecordForSync(record: StockRecord, logAction: String): StockRecord {
        val now = System.currentTimeMillis()
        // M3（R1）：优先使用绑定的操作员姓名；未绑定时回落"手机端"
        val operatorName = cachedOperatorName.ifBlank { "手机端" }
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
            scheduleAutoPush(record.sessionId)

            val currentQuery = _uiState.value.searchQuery
            performSearch(currentQuery, isInventoryMode = true)
        }
    }

    // 2. 修改记录 (数量、备注)
    fun updateRecord(record: StockRecord) {
        viewModelScope.launch {
            // 🔥 自动填充同步字段
            val recordToSave = record.copy(
                syncStatus = 1, // 🚩 关键！标记为未同步
                lastUpdateTime = System.currentTimeMillis(),
            )
            repository.updateRecord(recordToSave)
            scheduleAutoPush(record.sessionId)

            // 内存原地局部刷新：保持视图位置稳定，若开启自动沉底则按状态重排
            val currentList = _uiState.value.searchResults
            val hasItem = currentList.any { it.combined.record.id == recordToSave.id }
            if (hasItem) {
                val updatedList = currentList.map { uiModel ->
                    if (uiModel.combined.record.id == recordToSave.id) {
                        val updatedCombined = uiModel.combined.copy(record = recordToSave)
                        uiModel.copy(combined = updatedCombined)
                    } else {
                        uiModel
                    }
                }
                val finalList = if (_uiState.value.sinkVerifiedItems) {
                    sortSearchResults(updatedList, true)
                } else {
                    updatedList
                }
                _uiState.value = _uiState.value.copy(searchResults = finalList)
            } else {
                val currentQuery = _uiState.value.searchQuery
                performSearch(currentQuery, isInventoryMode = false, silent = true)
            }
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
            val now = System.currentTimeMillis()
            // 构造业务变更后的对象：标记查验，自动将实盘数量对齐账面数量
            val businessUpdatedRecord = record.copy(
                isVerified = true,
                actualQuantity = record.quantity,
                syncStatus = 1,                   // 🚩 标记脏数据
                lastUpdateTime = now
            )
            // 3. 保存
            repository.updateRecord(businessUpdatedRecord)
            // 立即调度自动推送（防抖合并连续查验）
            scheduleAutoPush(record.sessionId)
            // 4. 原地刷新 UI
            val currentList = _uiState.value.searchResults
            val newList = currentList.map { uiModel ->
                // 从 uiModel 中取出底层的 combined 进行比对
                if (uiModel.combined.record.id == record.id) {
                    // 先更新里层的 combined
                    val updatedCombined = uiModel.combined.copy(record = businessUpdatedRecord)
                    // 再更新外层的 uiModel
                    uiModel.copy(combined = updatedCombined)
                } else {
                    uiModel
                }
            }
            val finalList = if (_uiState.value.sinkVerifiedItems) {
                sortSearchResults(newList, true)
            } else {
                newList
            }
            _uiState.value = _uiState.value.copy(searchResults = finalList)
        }
    }

    // 5. 撤销查验（方案 1-B 与弹窗联动，自动安全备份实盘数据到备注）
    fun cancelVerification(combined: StockRecordCombined) {
        viewModelScope.launch {
            val record = combined.record
            val businessUpdatedRecord = record.uncheck()
            repository.updateRecord(businessUpdatedRecord)
            scheduleAutoPush(record.sessionId)
            val currentList = _uiState.value.searchResults
            val newList = currentList.map { uiModel ->
                if (uiModel.combined.record.id == record.id) {
                    val updatedCombined = uiModel.combined.copy(record = businessUpdatedRecord)
                    uiModel.copy(combined = updatedCombined)
                } else {
                    uiModel
                }
            }
            val finalList = if (_uiState.value.sinkVerifiedItems) {
                sortSearchResults(newList, true)
            } else {
                newList
            }
            _uiState.value = _uiState.value.copy(searchResults = finalList)
        }
    }

    // =========================================================================

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

    /** 使用显式列映射导入 Excel */
    fun importExcelFileWithMapping(
        context: Context,
        uri: Uri,
        sessionId: Long,
        mapping: Map<Int, ExcelUtils.FieldType>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isValidationEnabled = _uiState.value.isDiValidationEnabled
                val result = ExcelUtils.parseExcelWithMapping(
                    context, uri, sessionId, mapping, isValidationEnabled
                )
                ExcelUtils.SchemaHelper.saveSchema(context, sessionId, result.schema)
                analyzeExcelImport(result.products, result.records)
                if (result.invalidItems.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(invalidDiList = result.invalidItems)
                } else if (isValidationEnabled) {
                    _uiState.value = _uiState.value.copy(userMessage = "导入成功，所有 DI 校验通过！")
                } else {
                    _uiState.value = _uiState.value.copy(userMessage = "导入成功")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(userMessage = "导入失败: ${e.message}")
            }
        }
    }

    /** 加载已保存的映射模板列表 */
    fun loadMappingTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            val templates = MappingTemplateManager.getAllTemplates(app)
            _uiState.value = _uiState.value.copy(mappingTemplates = templates)
        }
    }

    fun getCachedTemplates(): List<MappingTemplate> = _uiState.value.mappingTemplates

    /** 保存映射模板（新建或更新） */
    fun saveMappingTemplate(
        name: String,
        fieldMap: Map<Int, ExcelUtils.FieldType>,
        existingId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val template = if (existingId != null) {
                val old = MappingTemplateManager.getAllTemplates(app).find { it.id == existingId }
                MappingTemplate.fromFieldMap(existingId, name, fieldMap, old?.createdAt ?: now, now)
            } else {
                MappingTemplate.fromFieldMap(UUID.randomUUID().toString(), name, fieldMap, now, now)
            }
            MappingTemplateManager.saveTemplate(app, template)
            loadMappingTemplates()
        }
    }

    /** 删除映射模板 */
    fun deleteMappingTemplate(templateId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            MappingTemplateManager.deleteTemplate(app, templateId)
            loadMappingTemplates()
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
        viewModelScope.launch {
            // M0：缓存任务 uuid（门铃事件按 uuid 过滤）并建立长连接
            currentSessionUuid = repository.getSessionById(sessionId)?.uuid
            doorbellClient.ensureConnected()
        }
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

    suspend fun getProductInfo(productKey: String): ProductBase? {
        return repository.getProductByKey(productKey)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as InventoryApplication)
                return InventoryViewModel(application, application.repository, com.example.inventorymaster.data.SettingsRepository(application)) as T
            }
        }
    }

    // 搜索产品字典
    fun searchProducts(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val list = if (query.isBlank()) {
                    val count = repository.getProductCount()
                    _uiState.value = _uiState.value.copy(productCount = count, productList = emptyList(), isLoading = false)
                    return@launch
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

    // 更新产品信息（支持 key 热更新）
    fun updateProductBase(oldKey: String, product: ProductBase) {
        viewModelScope.launch {
            val oldProduct = repository.getProductByKey(oldKey)
            if (oldProduct != null) {
                val newDi = product.di?.trim()
                val newMatCode = product.materialCode?.trim()
                val newKey = ProductBase.computeProductKey(newDi, newMatCode)
                if (newKey != oldKey) {
                    val result = repository.migrateProductKey(oldKey, product.copy(productKey = newKey))
                    if (result.isFailure) {
                        _uiState.value = _uiState.value.copy(userMessage = result.exceptionOrNull()?.message ?: "更新失败")
                        return@launch
                    }
                } else {
                    repository.updateProduct(product)
                }
            } else {
                _uiState.value = _uiState.value.copy(userMessage = "未找到原始产品记录")
            }
            val query = _uiState.value.searchQuery
            searchProducts(query)
        }
    }

    // =========================================================================
    // 统一的导入与解析逻辑 (盘点单 & 产品库)
    // =========================================================================

    // A. 解析 Excel (盘点单导入保留入口)
    fun analyzeExcelImport(products: List<ProductBase>, records: List<StockRecord>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            // 盘点导入不修改产品库：仓库层按组合键/DI/物料编码挂靠已有产品，未命中才兜底新增，
            // 因此不再需要「检测到产品变更」确认弹窗
            val insertedCount = repository.saveImportDataWithResolution(products, records)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                userMessage = if (insertedCount > 0) "导入成功，$insertedCount 条未匹配产品已新增至产品库" else "导入成功"
            )
        }
    }

    // 统一的确认导入逻辑 (彻底无脑按 Excel 覆盖)
    fun confirmImport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, importSummary = null, importDiagnostics = null)

            // 1. 如果是【盘点单】导入有数据，保存盘点单和产品
            if (pendingExcelProducts.isNotEmpty() || pendingExcelRecords.isNotEmpty()) {
                repository.saveImportDataWithResolution(pendingExcelProducts, pendingExcelRecords)
                pendingExcelProducts = emptyList()
                pendingExcelRecords = emptyList()
            }

            // 2. 如果是【独立产品库】导入有数据，走新写的主键转移保存逻辑
            if (pendingImportItems.isNotEmpty()) {
                repository.saveProductsOnly(pendingImportItems)
                pendingImportItems = emptyList()
            }

            _uiState.value = _uiState.value.copy(isAnalyzing = false, userMessage = "数据导入完成")

            // 刷新列表 (兼顾两个界面的搜索刷新)
            val query = _uiState.value.searchQuery
            searchProducts(query)
            performSearch(query, isInventoryMode = true)
        }
    }

    // 统一的取消导入逻辑
    fun cancelImport() {
        _uiState.value = _uiState.value.copy(importSummary = null, importDiagnostics = null, isAnalyzing = false)
        pendingExcelProducts = emptyList()
        pendingExcelRecords = emptyList()
        pendingImportItems = emptyList()
    }



    // =========================================================================
    // region 产品库导入导出 (Product-only, 不绑定 Session)
    // =========================================================================

    fun importProductsFromFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (repository.hasOngoingSessions()) {
                    withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(userMessage = "存在进行中的盘点任务，禁止修改产品库") }
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val fileName = uri.lastPathSegment ?: ""
                val result = when {
                    mimeType.contains("json") || fileName.endsWith(".json") -> {
                        val items = context.contentResolver.openInputStream(uri)?.use { stream ->
                            ProductJsonUtils.importFromJson(stream)
                        } ?: emptyList()
                        Pair(items, com.example.inventorymaster.data.model.ImportDiagnostics())
                    }
                    else -> {
                        // 【重点】使用我们新建的，只针对字典的解析器
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            com.example.inventorymaster.utils.SimpleProductExcelHelper.importProductsFromExcel(stream)
                        } ?: Pair(emptyList(), com.example.inventorymaster.data.model.ImportDiagnostics())
                    }
                }

                val (items, diagnostics) = result
                if (items.isEmpty()) {
                    withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(userMessage = "未解析到有效数据，请检查必填项") }
                    return@launch
                }
                analyzeProductsImport(items, diagnostics)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(userMessage = "导入失败: ${e.message}") }
            }
        }
    }

    private suspend fun analyzeProductsImport(
        items: List<com.example.inventorymaster.data.model.ProductImportItem>,
        diagnostics: com.example.inventorymaster.data.model.ImportDiagnostics = com.example.inventorymaster.data.model.ImportDiagnostics()
    ) {
        _uiState.value = _uiState.value.copy(isAnalyzing = true)
        pendingImportItems = items
        val summary = repository.checkProductConflicts(items)

        if (summary == null && !diagnostics.hasIssues) {
            // 没有变动，也没有诊断问题，直接保存
            repository.saveProductsOnly(items)
            pendingImportItems = emptyList()
            _uiState.value = _uiState.value.copy(isAnalyzing = false, userMessage = "导入完成")
        } else {
            // 有变动或有诊断问题，触发 UI 弹窗
            val displaySummary = summary ?: "未发现与现有产品库的冲突，但存在以下数据问题："
            _uiState.value = _uiState.value.copy(
                importSummary = displaySummary,
                importDiagnostics = if (diagnostics.hasIssues) diagnostics else null,
                isAnalyzing = false
            )
        }
    }

    fun exportProductsToExcelFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val products = repository.getAllProducts()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    com.example.inventorymaster.utils.SimpleProductExcelHelper.exportProductsToExcel(outputStream, products)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(userMessage = "已导出 ${products.size} 条产品")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(userMessage = "导出失败: ${e.message}")
                }
            }
        }
    }

    fun exportProductsToJsonFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val products = repository.getAllProducts()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ProductJsonUtils.exportToJson(outputStream, products)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(userMessage = "已导出 ${products.size} 条产品")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(userMessage = "导出失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 从服务器增量/全量同步产品库（含金蝶物料主档）
     */
    fun syncProductLibrary(userInitiated: Boolean = true, forceFull: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentToken = settingsRepository.getServerToken()
            if (currentToken.isBlank()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showTokenPrompt = true,
                        tokenPromptMessage = "首次同步需配置服务端认证 Token（可在服务端启动控制台查看）"
                    )
                }
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.pullProductLibrary(userInitiated, forceFull)
            val newCount = repository.getProductCount()
            val currentQuery = _uiState.value.searchQuery
            val updatedList = if (currentQuery.isNotBlank()) {
                repository.searchProducts(currentQuery)
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { msg ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            productCount = newCount,
                            productList = updatedList,
                            userMessage = msg
                        )
                    },
                    onFailure = { err ->
                        val errMsg = err.message ?: ""
                        if (errMsg.contains("401") || errMsg.contains("未授权") || errMsg.contains("Token 无效")) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                showTokenPrompt = true,
                                tokenPromptMessage = "Token 无效或未授权 (401)，请重新输入服务端 Token"
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                userMessage = "同步产品库失败: $errMsg"
                            )
                        }
                    }
                )
            }
        }
    }

    fun dismissTokenPrompt() {
        _uiState.value = _uiState.value.copy(showTokenPrompt = false, tokenPromptMessage = null)
    }

    fun saveTokenAndSync(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveToken(token.trim())
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(showTokenPrompt = false, tokenPromptMessage = null)
                syncProductLibrary(userInitiated = true)
            }
        }
    }

    // endregion

    // =========================================================================
    // region 产品库增删
    // =========================================================================

    fun toggleProductDeleteMode() {
        val current = _uiState.value.isProductDeleteMode
        _uiState.value = _uiState.value.copy(
            isProductDeleteMode = !current,
            selectedProductsForDelete = emptySet()
        )
    }

    fun toggleProductSelection(productKey: String) {
        val current = _uiState.value.selectedProductsForDelete.toMutableSet()
        if (current.contains(productKey)) current.remove(productKey) else current.add(productKey)
        _uiState.value = _uiState.value.copy(selectedProductsForDelete = current)
    }

    fun selectAllProducts() {
        viewModelScope.launch {
            val allProducts = repository.getAllProducts()
            val allKeys = allProducts.map { it.productKey }.toSet()
            val currentSelected = _uiState.value.selectedProductsForDelete
            _uiState.value = _uiState.value.copy(
                selectedProductsForDelete = if (currentSelected == allKeys) emptySet() else allKeys
            )
        }
    }

    fun addProduct(product: ProductBase) {
        viewModelScope.launch {
            try {
                if (repository.hasOngoingSessions()) {
                    _uiState.value = _uiState.value.copy(userMessage = "存在进行中的盘点任务，禁止修改产品库")
                    return@launch
                }
                // 组合键 (DI|物料编码) 相同视为同一产品：覆盖更新而非 REPLACE（避免外键冲突闪退），并合并扫码占位
                val newKey = ProductBase.computeProductKey(product.di, product.materialCode)
                val safeProduct = product.copy(productKey = newKey)
                val existing = repository.getProductByKey(newKey)
                if (existing != null) {
                    repository.updateProduct(safeProduct)
                    _uiState.value = _uiState.value.copy(userMessage = "产品已存在，已覆盖更新: ${safeProduct.productName}")
                } else {
                    repository.insertProduct(safeProduct)
                    _uiState.value = _uiState.value.copy(userMessage = "已添加产品: ${safeProduct.productName}")
                }
                // 刷新列表
                val query = _uiState.value.searchQuery
                if (query.isNotBlank()) searchProducts(query)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(userMessage = "添加失败: ${e.message}")
            }
        }
    }

    fun deleteSingleProduct(productKey: String) {
        viewModelScope.launch {
            try {
                if (repository.hasOngoingSessions()) {
                    _uiState.value = _uiState.value.copy(userMessage = "存在进行中的盘点任务，禁止修改产品库")
                    return@launch
                }
                val result = repository.deleteProductByKey(productKey)
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(userMessage = result.exceptionOrNull()?.message ?: "删除失败")
                    return@launch
                }
                val remaining = _uiState.value.productList.filter { it.productKey != productKey }
                val count = repository.getProductCount()
                if (count == 0) {
                    repository.resetProductPullSeq()
                }
                _uiState.value = _uiState.value.copy(
                    productList = remaining,
                    productCount = count,
                    userMessage = "已删除产品"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(userMessage = e.message ?: "删除失败")
            }
        }
    }

    fun deleteSelectedProducts() {
        val selected = _uiState.value.selectedProductsForDelete
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                if (repository.hasOngoingSessions()) {
                    _uiState.value = _uiState.value.copy(userMessage = "存在进行中的盘点任务，禁止修改产品库")
                    return@launch
                }
                val result = repository.deleteProductsByKey(selected.toList())
                val remaining = _uiState.value.productList.filter { it.productKey !in result.deletedKeys }
                val count = repository.getProductCount()
                if (count == 0) {
                    repository.resetProductPullSeq()
                }
                val message = buildString {
                    append("已删除 ${result.deletedKeys.size} 条产品")
                    if (result.hasBlocked) {
                        val blockedDesc = result.blockedKeys.entries.joinToString("、") { (key, ref) ->
                            if (ref > 0) "$key(${ref}条记录引用)" else "$key"
                        }
                        append("，${result.blockedKeys.size} 条被拒：$blockedDesc")
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isProductDeleteMode = false,
                    selectedProductsForDelete = emptySet(),
                    productList = remaining,
                    productCount = count,
                    userMessage = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(userMessage = e.message ?: "批量删除失败")
            }
        }
    }

    // endregion

    // 👇 新增：手动校验当前列表的所有 DI
    fun validateCurrentTaskDIs() {
        val currentData = _uiState.value.searchResults
        val invalidList = currentData.map { it.combined }.filter { item ->
            val productDi = item.product?.di
            if (productDi.isNullOrBlank()) {
                false
            } else {
                !Gs1Parser.isValidDI(productDi)
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

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
