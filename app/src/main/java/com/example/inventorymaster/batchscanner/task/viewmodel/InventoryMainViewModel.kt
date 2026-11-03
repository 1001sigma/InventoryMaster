package com.example.inventorymaster.batchscanner.task.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.batchscanner.core.models.GlobalBarcode
import com.example.inventorymaster.batchscanner.ocr.OcrFuzzyMatcher
import com.example.inventorymaster.batchscanner.task.models.DocStatus
import com.example.inventorymaster.batchscanner.task.models.FieldStatus
import com.example.inventorymaster.batchscanner.task.models.InventoryRecord
import com.example.inventorymaster.batchscanner.task.models.PhotoScanResult
import com.example.inventorymaster.batchscanner.task.models.RecordSource
import com.example.inventorymaster.batchscanner.task.models.RowStatus
import com.example.inventorymaster.batchscanner.task.models.TargetDocument
import com.example.inventorymaster.InventoryApplication
import com.example.inventorymaster.batchscanner.realtime.EntryPushEvent
import com.example.inventorymaster.batchscanner.realtime.EntryRealtimeClient
import com.example.inventorymaster.batchscanner.realtime.RealtimeConnState
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.data.network.EntryJobDto
import com.example.inventorymaster.data.network.EntryJobDuplicate
import com.example.inventorymaster.data.network.EntryJobTerminalException
import com.example.inventorymaster.utils.Gs1Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 实时录入模式状态（v1.1，契约见方案文档 §7）*/
data class RealtimeEntryState(
    val active: Boolean = false,          // 是否处于录入模式
    val jobId: Long? = null,              // 当前关注的 job
    val titleBase: String = "",           // 批次名基名（自动开新批次时 -2/-3 递增）
    val batchSeq: Int = 1,
    val job: EntryJobDto? = null,         // 最近一次轮询结果
    val pendingScans: List<String> = emptyList(), // 录入模式下补扫、待 append 的码
    val notice: String? = null,           // 一次性提示（UI Toast 后清除）
    val wsConnected: Boolean = false      // 实时推送长连接是否在线（v1.3 一期）
)

/** 序列号范围生成弹窗状态（v1.3）：template 由扫码确定只读，起始流水号可手动修改 */
data class SerialGenUiState(
    val visible: Boolean = false,
    val fromEntryMode: Boolean = false,   // true=实时录入页（append/新建任务），false=批量输入页（进扫码池）
    val template: String = "",            // 清洗后 UDI 前缀（含末尾 "21"）
    val startSerial: String = "",         // 起始流水号，扫码自动填入，可手动修改
    val endSerial: String = "",           // 结束流水号，用户输入
    val name: String = "",                // 新建任务/UDI 组名称（追加到已有任务时忽略）
    val scannedParse: Gs1Parser.ParseResult? = null, // 起始码解析结果（批量页表格一次性 +N 用）
    val previewing: Boolean = false,      // 正在调 /api/serial/expand
    val preview: com.example.inventorymaster.data.network.SerialExpandData? = null,
    val previewError: String? = null,
    val submitting: Boolean = false
)

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

    // 本次扫码的原始条码字符串累积列表（用于上传解码信息）
    private val _rawBarcodeList = MutableStateFlow<List<String>>(emptyList())
    val rawBarcodeList: StateFlow<List<String>> = _rawBarcodeList.asStateFlow()

    // --- 👇 新增：当前导入的单据表头状态 👇 ---
    private val _currentDocuments = MutableStateFlow<List<TargetDocument>>(emptyList())
    val currentDocuments: StateFlow<List<TargetDocument>> = _currentDocuments.asStateFlow()

    // --- 👇 新增：与 Python 服务端的网络交互状态 👇 ---
    private val _networkTasks = MutableStateFlow<List<com.example.inventorymaster.data.network.TaskSummary>>(emptyList())
    val networkTasks: StateFlow<List<com.example.inventorymaster.data.network.TaskSummary>> = _networkTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 评估扫码日期与单据目标日期的比对状态。
     * 使用 LocalDate 标准化后再比较，避免格式差异导致误报。
     */
    private fun evaluateDateStatus(targetDate: String?, scannedDate: String?): FieldStatus {
        if (targetDate.isNullOrBlank() && scannedDate.isNullOrBlank()) return FieldStatus.NOT_CHECKED
        if (targetDate.isNullOrBlank()) return FieldStatus.WARNING
        if (scannedDate.isNullOrBlank()) return FieldStatus.WARNING

        val targetLocal = com.example.inventorymaster.utils.DateUtils.parseToLocalDate(targetDate)
        val scannedLocal = com.example.inventorymaster.utils.DateUtils.parseToLocalDate(scannedDate)

        return when {
            targetLocal == null || scannedLocal == null -> FieldStatus.WARNING
            targetLocal == scannedLocal -> FieldStatus.MATCHED
            else -> FieldStatus.WARNING
        }
    }

    private fun computeQtyStatus(actualQty: Int, targetQty: Int): FieldStatus {
        return when {
            targetQty == 0 -> FieldStatus.MATCHED
            actualQty == targetQty -> FieldStatus.MATCHED
            actualQty < targetQty -> FieldStatus.WARNING
            else -> FieldStatus.ERROR
        }
    }

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
     * 上传一份已在本地生成好的 PDF 文件（导出时不留存本地的路径使用；文件由调用方负责清理）
     */
    fun uploadPdfFileToNetwork(file: java.io.File, documentIds: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.uploadPythonTaskPdf(documentIds, file)
            }
            withContext(Dispatchers.Main) {
                onComplete(result.isSuccess, if (result.isSuccess) "上传归档成功" else "上传失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun submitDecodeInfoToNetwork(title: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val barcodes = _rawBarcodeList.value
            if (barcodes.isEmpty()) {
                _isLoading.value = false
                onResult("暂无解码数据，请先扫描")
                return@launch
            }
            val result = repository.submitUdiData(title, barcodes)
            if (result.isSuccess) {
                _rawBarcodeList.value = emptyList()
            }
            _isLoading.value = false
            onResult(if (result.isSuccess) null else result.exceptionOrNull()?.message)
        }
    }

    // =========================================================
    // 实时录入（v1.1）：提交 → 服务端 entry_jobs → PC 脚本认领逐条录入 → 本端轮询看逐条状态
    // =========================================================
    private val _entryState = MutableStateFlow(RealtimeEntryState())
    val entryState: StateFlow<RealtimeEntryState> = _entryState.asStateFlow()

    private val _serialGenState = MutableStateFlow(SerialGenUiState())
    val serialGenState: StateFlow<SerialGenUiState> = _serialGenState.asStateFlow()

    private var appendFlushJob: Job? = null
    private var appendRetryJob: Job? = null

    // 实时推送长连接（v1.3 一期，协议见 pythonUDI WebSocket长连接方案.md）：
    // 服务端 /ws/entry 只推通知+进度镜像，数据以 REST 为准；断线自动退回 2.5s 轮询
    private val entryRealtime = EntryRealtimeClient(
        (app as InventoryApplication).connectionManager,
        (app as InventoryApplication).settingsRepository
    )

    init {
        // 连接状态 → 驱动 UI 圆点与轮询节奏
        viewModelScope.launch {
            entryRealtime.connState.collect { st ->
                _entryState.update { it.copy(wsConnected = st is RealtimeConnState.Connected) }
            }
        }
        // 推送事件 → 镜像直刷 / 触发 REST 复核
        viewModelScope.launch {
            entryRealtime.events.collect { onEntryPush(it) }
        }
        // 设置页开关：关闭即断开长连接，完全回到纯轮询；录入中途打开则立即补连
        viewModelScope.launch {
            (app as InventoryApplication).settingsRepository.settingsFlow.collect {
                entryRealtime.setEnabled(it.realtimePushEnabled)
                if (it.realtimePushEnabled && _entryState.value.active) {
                    entryRealtime.setJobId(_entryState.value.jobId)
                    entryRealtime.ensureConnected()
                }
            }
        }
    }

    private fun onEntryPush(e: EntryPushEvent) {
        when (e) {
            is EntryPushEvent.Progress -> {
                val st = _entryState.value
                // 镜像非事实源：只覆盖进行中、且属于当前任务的进度；终态一律走 REST 复核
                if (e.jobId == st.jobId && e.job.status < 2 && (st.job?.status ?: 0) < 2) {
                    _entryState.update { it.copy(job = e.job) }
                }
            }
            is EntryPushEvent.Claimed -> if (e.jobId == _entryState.value.jobId) pollEntryJobOnce()
            is EntryPushEvent.Appended -> if (e.jobId == _entryState.value.jobId) pollEntryJobOnce()
            is EntryPushEvent.Finished -> if (e.jobId == _entryState.value.jobId) pollEntryJobOnce()
        }
    }

    override fun onCleared() {
        entryRealtime.close()
        super.onCleared()
    }

    fun clearEntryNotice() {
        _entryState.update { it.copy(notice = null) }
    }

    /** 提交前清洗：规整 + 批内去重（保序），保证手机列表 ↔ 金蝶录入 1:1、seq 不错位 */
    private fun normalizeEntryItems(items: List<String>): List<String> =
        items.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** 提交流程入口：先查重（命中历史回调 onNeedConfirm，由用户决定是否仍然提交），无命中直接提交。
     *  v1.2：rawCodes 允许为空 = 创建空任务（0 条开任务、边扫边录），空列表跳过查重 */
    fun beginEntrySubmit(
        title: String,
        rawCodes: List<String>,
        onNeedConfirm: (List<EntryJobDuplicate>) -> Unit,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val items = normalizeEntryItems(rawCodes)
            if (items.isEmpty()) {
                _isLoading.value = false
                performEntrySubmit(title, emptyList(), onResult)
                return@launch
            }
            val dup = repository.checkEntryJobDuplicates(items).getOrNull().orEmpty()
            if (dup.isNotEmpty()) {
                _isLoading.value = false
                onNeedConfirm(dup)
                return@launch
            }
            performEntrySubmit(title, items, onResult)
        }
    }

    /** 查重确认后（或无需确认）的实际提交；v1.2 允许空列表创建空任务 */
    fun performEntrySubmit(title: String, rawCodes: List<String>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val items = normalizeEntryItems(rawCodes)
            // 批次名基名去掉结尾序号，自动开新批次时追加 -2/-3
            val titleBase = title.replace(Regex("-\\d+$"), "")
            repository.submitEntryJob(title, items)
                .onSuccess { jobId ->
                    _rawBarcodeList.value = emptyList()
                    _entryState.value = RealtimeEntryState(
                        active = true,
                        jobId = jobId,
                        titleBase = titleBase,
                        batchSeq = 1,
                        notice = if (items.isEmpty()) "已创建空任务 #$jobId，可通过补扫逐条追加"
                        else "已创建实时录入任务 #$jobId（${items.size} 条），等待 PC 脚本认领"
                    )
                    entryRealtime.setJobId(jobId)
                    entryRealtime.ensureConnected()
                    pollEntryJobOnce()
                    onResult(null)
                }
                .onFailure { e ->
                    onResult(e.message ?: "提交失败")
                }
            _isLoading.value = false
        }
    }

    /** 点击“实时录入”入口：有进行中的 job 直接续看（不重复提交），否则回调 null 由 UI 走提交流程 */
    fun resumeOrPrepareEntry(onResult: (EntryJobDto?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val jobs = repository.fetchActiveEntryJobs().getOrNull().orEmpty()
            val resume = jobs.firstOrNull { it.status == 0 || it.status == 1 }
            if (resume != null) {
                _entryState.value = RealtimeEntryState(
                    active = true,
                    jobId = resume.jobId,
                    titleBase = resume.title.replace(Regex("-\\d+$"), ""),
                    batchSeq = 1,
                    job = resume,
                    notice = "已关联进行中的任务 #${resume.jobId}"
                )
                entryRealtime.setJobId(resume.jobId)
                entryRealtime.ensureConnected()
            }
            _isLoading.value = false
            onResult(resume)
        }
    }

    /** 轮询一次（WS 断开时录入模式 2.5s；暂离后普通页 15s 低频，用于角标与完成通知——job 终态后自动停）
     *  WS 在线时事件也会触发本方法做 REST 复核；防重入：进行中的轮询合并、结束后按需补一次 */
    private var pollInFlight = false
    private var pollPending = false

    fun pollEntryJobOnce() {
        val st = _entryState.value
        val id = st.jobId ?: return
        if (!st.active && (st.job?.status ?: 0) >= 2) return
        if (pollInFlight) {
            pollPending = true
            return
        }
        pollInFlight = true
        viewModelScope.launch {
            try {
                do {
                    pollPending = false
                    val jobs = repository.fetchActiveEntryJobs().getOrNull() ?: return@launch
                    val job = jobs.firstOrNull { it.jobId == id } ?: return@launch
                    val prev = _entryState.value.job
                    _entryState.update { it.copy(job = job) }
                    // 终态跃迁提示一次（Toast 由 UI 消费 notice）
                    if (prev != null && prev.status in 0..1 && job.status in 2..3) {
                        _entryState.update {
                            it.copy(
                                notice = if (job.status == 2)
                                    "录入完成：成功 ${job.doneCount} / 失败 ${job.failCount} / 跳过 ${job.skipCount}"
                                else
                                    "录入任务已结束"
                            )
                        }
                    }
                } while (pollPending)
            } finally {
                pollInFlight = false
            }
        }
    }

    /** 录入模式下补扫入队：攒 2s（或下次扫描触发）后合并 append 进当前 job */
    fun queueEntryScan(raw: String) {
        if (!_entryState.value.active) return
        val code = raw.trim()
        if (code.isEmpty()) return
        _entryState.update {
            if (code in it.pendingScans) it
            else it.copy(pendingScans = it.pendingScans + code)
        }
        appendFlushJob?.cancel()
        appendFlushJob = viewModelScope.launch {
            delay(2000)
            flushPendingAppends()
        }
    }

    private fun scheduleAppendRetry() {
        appendRetryJob?.cancel()
        appendRetryJob = viewModelScope.launch {
            delay(5000)
            flushPendingAppends()
        }
    }

    private fun flushPendingAppends() {
        val st = _entryState.value
        if (!st.active) return
        val jobId = st.jobId ?: return
        val items = normalizeEntryItems(st.pendingScans)
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.appendEntryJob(jobId, items)
                .onSuccess {
                    _entryState.update { s -> s.copy(pendingScans = s.pendingScans.filter { code -> code !in items }) }
                }
                .onFailure { e ->
                    if (e is EntryJobTerminalException) {
                        autoStartNextBatch()
                    } else {
                        scheduleAppendRetry()
                    }
                }
        }
    }

    /** 当前 job 已终态：携带全部待追加码自动开新批次（批次名 -2/-3 递增）*/
    private fun autoStartNextBatch() {
        val st = _entryState.value
        val all = normalizeEntryItems(st.pendingScans)
        if (all.isEmpty()) return
        val nextSeq = st.batchSeq + 1
        val base = st.titleBase.ifBlank { "实时批次" }
        viewModelScope.launch {
            repository.submitEntryJob("$base-$nextSeq", all)
                .onSuccess { newId ->
                    _entryState.update {
                        it.copy(
                            jobId = newId,
                            batchSeq = nextSeq,
                            pendingScans = emptyList(),
                            notice = "原批次已结束，已自动开启新批次 #$newId（${all.size} 条）"
                        )
                    }
                    entryRealtime.setJobId(newId)
                    pollEntryJobOnce()
                }
                .onFailure { scheduleAppendRetry() }
        }
    }

    // ==================== 序列号范围生成（v1.3） ====================

    /** 点「生成序列号」：先记下来源页并唤起扫码，扫到 (21) 后才弹出生成弹窗（visible 在 onSerialGenScan 成功时置 true）。
     *  默认名带时间戳：UDI 组按名 UPSERT 整体覆盖，带时间戳防同名覆盖历史组 */
    fun beginSerialGenScan(fromEntryMode: Boolean) {
        val stamp = java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.CHINA).format(java.util.Date())
        _serialGenState.value = SerialGenUiState(fromEntryMode = fromEntryMode, name = "序列生成-$stamp")
    }

    fun closeSerialGenDialog() {
        if (_serialGenState.value.submitting) return
        _serialGenState.value = SerialGenUiState()
    }

    fun updateSerialGenStart(value: String) {
        _serialGenState.update { it.copy(startSerial = value.filter { c -> c.isDigit() }, preview = null, previewError = null) }
    }

    fun updateSerialGenEnd(value: String) {
        _serialGenState.update { it.copy(endSerial = value.filter { c -> c.isDigit() }, preview = null, previewError = null) }
    }

    /** 扫码回调（FloatingRescanOverlay 最高优先分支）：解析 (21) 确定模板串与起始流水号，
     *  成功即弹出生成弹窗。返回 null=成功；否则返回错误提示文案（扫码窗保持打开可重试） */
    fun onSerialGenScan(raw: String): String? {
        val parsed = Gs1Parser.extractSerialTemplate(raw)
        return if (parsed == null) {
            "未解析到位于条码末尾的 (21) 纯数字序列号"
        } else {
            _serialGenState.update {
                it.copy(
                    template = parsed.template,
                    startSerial = parsed.startSerial,
                    scannedParse = parsed.parse,
                    visible = true,
                    preview = null,
                    previewError = null
                )
            }
            null
        }
    }

    /** 预览：服务端展开 + 查重（只读不落库），duplicates 供弹窗红色警告 */
    fun previewSerialRange() {
        val st = _serialGenState.value
        if (st.previewing || st.submitting) return
        if (st.template.isBlank() || st.startSerial.isBlank() || st.endSerial.isBlank()) {
            _serialGenState.update { it.copy(previewError = "请先扫码并填写起止流水号") }
            return
        }
        viewModelScope.launch {
            _serialGenState.update { it.copy(previewing = true, preview = null, previewError = null) }
            repository.expandSerialRange(st.template, st.startSerial, st.endSerial)
                .onSuccess { data -> _serialGenState.update { it.copy(previewing = false, preview = data) } }
                .onFailure { e -> _serialGenState.update { it.copy(previewing = false, previewError = e.message ?: "预览失败") } }
        }
    }

    /** 确认生成（预览成功后调用），三分支路由：
     *  实时录入页·有任务 → append（409 终态自动降级新建）；
     *  实时录入页·无任务 → 新建任务；
     *  批量输入页 → 进本地扫码池（与普通扫码同池，顶部云上传统一触发上传） */
    fun confirmSerialGenerate(onResult: (String?) -> Unit) {
        val st = _serialGenState.value
        val preview = st.preview
        if (preview == null || preview.values.isEmpty() || st.submitting) return
        val spec = com.example.inventorymaster.data.network.SerialRangeSpec(st.template, st.startSerial, st.endSerial)
        viewModelScope.launch {
            _serialGenState.update { it.copy(submitting = true) }
            if (st.fromEntryMode) {
                val activeJobId = _entryState.value.jobId
                if (_entryState.value.active && activeJobId != null) {
                    val appended = repository.appendEntryJob(activeJobId, emptyList(), spec)
                    if (appended.isSuccess) {
                        _entryState.update {
                            it.copy(notice = "已生成 ${preview.count} 条并追加到任务 #$activeJobId")
                        }
                        pollEntryJobOnce()
                        _serialGenState.value = SerialGenUiState()
                        onResult(null)
                        return@launch
                    }
                    if (appended.exceptionOrNull() !is EntryJobTerminalException) {
                        _serialGenState.update { it.copy(submitting = false) }
                        onResult(appended.exceptionOrNull()?.message ?: "追加失败")
                        return@launch
                    }
                    // 任务已终态：降级为新建任务继续
                }
                val title = st.name.ifBlank { "序列生成" }
                val titleBase = title.replace(Regex("-\\d+$"), "")
                repository.submitEntryJob(title, emptyList(), spec)
                    .onSuccess { jobId ->
                        _entryState.value = RealtimeEntryState(
                            active = true,
                            jobId = jobId,
                            titleBase = titleBase,
                            batchSeq = 1,
                            notice = "已生成 ${preview.count} 条，新任务 #$jobId 等待脚本认领"
                        )
                        entryRealtime.setJobId(jobId)
                        entryRealtime.ensureConnected()
                        pollEntryJobOnce()
                        _serialGenState.value = SerialGenUiState()
                        onResult(null)
                    }
                    .onFailure { e ->
                        _serialGenState.update { it.copy(submitting = false) }
                        onResult(e.message ?: "提交失败")
                    }
            } else {
                // 批量输入页：清洗串直接进扫码池（与普通扫码同池，顶部云上传统一触发上传）；
                // 表格用扫码时已解析出的 DI/批号一次性 +N——不逐条解析、不逐条查库，百级数量无感。
                // 上传到 UDI 看板的就是清洗串本身，脚本 cleanUDI 清洗后与预览值逐字节一致
                _rawBarcodeList.update { current -> current + preview.values }

                val parsedStart = st.scannedParse
                val di = parsedStart?.di
                if (di == null) {
                    // 罕见：扫码只有 (21) 没有 (01)，无从匹配表格行，只入池
                    _entryState.update {
                        it.copy(notice = "已生成 ${preview.count} 条加入扫码池（起始码无 DI，未计入表格），点顶部云上传统一上传")
                    }
                } else {
                    val batch = parsedStart.batch ?: "—"
                    viewModelScope.launch {
                        val product = repository.getProductsByDi(di).firstOrNull()
                        val currentRecords = _recordList.value.toMutableList()
                        val newEntries = mutableListOf<InventoryRecord>()
                        applyBarcodeCountToRecords(
                            currentRecords, newEntries, _currentDocuments.value.isNotEmpty(),
                            di, batch, product?.productName ?: "未知商品",
                            parsedStart.prodDate, parsedStart.expiry, preview.count
                        )
                        currentRecords.addAll(newEntries)
                        _recordList.value = currentRecords
                        _entryState.update {
                            it.copy(notice = "已生成 ${preview.count} 条加入扫码池，点顶部云上传按钮统一上传")
                        }
                    }
                }
                _serialGenState.value = SerialGenUiState()
                onResult(null)
            }
        }
    }

    /** 退出录入模式（暂离）：不取消任务（服务端照跑，可重入）；退出前尽力把待追加码发出去。
     *  job 状态保留在 state 中，供普通页角标与"回到当前任务"使用 */
    fun exitEntryMode() {
        appendFlushJob?.cancel()
        appendRetryJob?.cancel()
        val st = _entryState.value
        val items = normalizeEntryItems(st.pendingScans)
        if (st.jobId != null && items.isNotEmpty()) {
            val jobId = st.jobId
            viewModelScope.launch { repository.appendEntryJob(jobId, items) }
        }
        _entryState.update { it.copy(active = false, pendingScans = emptyList()) }
    }

    /** 暂离后"回到当前任务"：恢复录入模式（job 状态由随后轮询刷新）*/
    fun reenterEntryMode() {
        _entryState.update { it.copy(active = true, notice = "已回到任务 #${it.jobId}") }
    }

    /** 录入模式内"结束任务"（用户拍板：不弹确认直接终止）：服务端置终态，
     *  PC 脚本轮询感知后停止录入并解除关联；本地待追加码随终止一并放弃 */
    fun abandonEntryJob() {
        val st = _entryState.value
        val jobId = st.jobId ?: run {
            _entryState.update { it.copy(active = false) }
            return
        }
        viewModelScope.launch {
            repository.abandonEntryJob(jobId)
                .onSuccess {
                    appendFlushJob?.cancel()
                    appendRetryJob?.cancel()
                    _entryState.value = RealtimeEntryState(active = false, notice = "任务 #$jobId 已结束，PC 端将自动停止录入")
                }
                .onFailure { e ->
                    _entryState.update { it.copy(notice = "结束任务失败：${e.message}") }
                }
        }
    }
    /**
     * 核心业务：处理扫描结果，并进行多维度（DI、批号、日期、数量）匹配
     */
    fun processScannerResults(bitmaps: List<Uri>, rawBarcodes: List<String>, barcodes: List<GlobalBarcode> = emptyList()) {
        _photoList.update { current -> current + bitmaps }
        _rawBarcodeList.update { current -> current + rawBarcodes }
        if (bitmaps.isNotEmpty()) {
            _photoScanResults.update { current ->
                current + PhotoScanResult(bitmaps.first(), barcodes)
            }
        }
        matchScannedBarcodes(rawBarcodes)
    }

    /**
     * 批量队列模式入口：照片与各自条码按图分组回传，每组一条 PhotoScanResult（PDF 导出每图一页）；
     * 记录匹配仍按扁平 rawBarcodes 逐条进行，与单张路径完全一致
     */
    fun processScannerResultsGrouped(photoGroups: List<PhotoScanResult>, rawBarcodes: List<String>) {
        _photoList.update { current -> current + photoGroups.map { it.photoUri } }
        _rawBarcodeList.update { current -> current + rawBarcodes }
        if (photoGroups.isNotEmpty()) {
            _photoScanResults.update { current -> current + photoGroups }
        }
        matchScannedBarcodes(rawBarcodes)
    }

    private fun matchScannedBarcodes(rawBarcodes: List<String>) {
        viewModelScope.launch {
            val currentRecords = _recordList.value.toMutableList()
            val newEntries = mutableListOf<InventoryRecord>()
            val hasDoc = _currentDocuments.value.isNotEmpty()

            withContext(Dispatchers.IO) {
                for (raw in rawBarcodes) {
                    val parseResult = Gs1Parser.parse(raw)

                    // --- 无法解析 → 触发 ERROR ---
                    if (!parseResult.isUdi || parseResult.di == null) {
                        newEntries.add(
                            InventoryRecord(
                                di = "未知", productName = "解析失败", batch = "未知", actualQty = 1,
                                diStatus = FieldStatus.ERROR, batchStatus = FieldStatus.ERROR,
                                qtyStatus = FieldStatus.ERROR, errorMessage = "非 UDI 格式"
                            )
                        )
                        continue
                    }

                    val di = parseResult.di!!
                    val batch = parseResult.batch ?: "—"
                    val scannedProdDate = parseResult.prodDate
                    val scannedExpDate = parseResult.expiry

                    // 组合键下同 DI 可能对应多条产品（物料编码不同），取物料编码升序第一条用于展示
                    val product = repository.getProductsByDi(di).firstOrNull()
                    val finalProductName = product?.productName ?: "未知商品"

                    applyBarcodeCountToRecords(
                        currentRecords, newEntries, hasDoc,
                        di, batch, finalProductName, scannedProdDate, scannedExpDate, count = 1
                    )
                }
                currentRecords.addAll(newEntries)
            }
            _recordList.value = currentRecords
        }
    }

    /**
     * 把同一 DI+批号的 count 条码值记入盘点表格——processScannerResults 逐条匹配逻辑的批量版：
     * 有单据匹配行 → 贪心选最佳行一次性 +count；无匹配 → 复用/新建 UNKNOWN 行一次 +count。
     * 序列号批量生成用：不做逐条解析与逐条查库，100+ 条也只有一次匹配、一次加法。
     */
    private fun applyBarcodeCountToRecords(
        currentRecords: MutableList<InventoryRecord>,
        newEntries: MutableList<InventoryRecord>,
        hasDoc: Boolean,
        di: String,
        batch: String,
        productName: String,
        scannedProdDate: String?,
        scannedExpDate: String?,
        count: Int
    ) {
        val matchingRecords = currentRecords.filter {
            (it.di == di || it.candidateDis.contains(di)) && it.batch == batch
        }

        if (matchingRecords.isNotEmpty()) {
            // 【升级版贪心算法】：给候选行打分寻找最佳匹配
            val bestFitRecord = matchingRecords.minByOrNull { record ->
                val isFull = record.actualQty >= record.targetQty
                val prodMatch = evaluateDateStatus(record.productionDate, scannedProdDate) == FieldStatus.MATCHED
                val expMatch = evaluateDateStatus(record.expiryDate, scannedExpDate) == FieldStatus.MATCHED

                var score = if (isFull) 10 else 0
                if (!isFull) {
                    if (!prodMatch) score += 1
                    if (!expMatch) score += 1
                }
                score
            } ?: return
            val index = currentRecords.indexOf(bestFitRecord)
            val newActualQty = bestFitRecord.actualQty + count

            val newQtyStatus = when {
                bestFitRecord.targetQty == 0 -> FieldStatus.MATCHED
                newActualQty == bestFitRecord.targetQty -> FieldStatus.MATCHED
                newActualQty < bestFitRecord.targetQty -> FieldStatus.WARNING
                else -> FieldStatus.ERROR
            }

            currentRecords[index] = bestFitRecord.copy(
                di = di,
                actualDi = di,
                actualQty = newActualQty,
                sources = bestFitRecord.sources + RecordSource.UDI,
                diStatus = FieldStatus.MATCHED,
                batchStatus = FieldStatus.MATCHED,
                prodDateStatus = evaluateDateStatus(bestFitRecord.productionDate, scannedProdDate),
                expDateStatus = evaluateDateStatus(bestFitRecord.expiryDate, scannedExpDate),
                qtyStatus = newQtyStatus
            )
        } else {
            val existingNewEntry = newEntries.find { it.di == di && it.batch == batch && it.documentId == "UNKNOWN" }
            val existingCurrentRecord = currentRecords.find { it.di == di && it.batch == batch && it.documentId == "UNKNOWN" }
            val isNotInDocError = hasDoc
            val globalStatus = if (isNotInDocError) FieldStatus.ERROR else FieldStatus.MATCHED

            if (existingNewEntry != null) {
                val index = newEntries.indexOf(existingNewEntry)
                newEntries[index] = existingNewEntry.copy(
                    actualQty = existingNewEntry.actualQty + count,
                    sources = existingNewEntry.sources + RecordSource.UDI,
                    productionDate = scannedProdDate ?: existingNewEntry.productionDate,
                    expiryDate = scannedExpDate ?: existingNewEntry.expiryDate,
                    prodDateStatus = if (scannedProdDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED,
                    expDateStatus = if (scannedExpDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED
                )
            } else if (existingCurrentRecord != null) {
                val index = currentRecords.indexOf(existingCurrentRecord)
                currentRecords[index] = existingCurrentRecord.copy(
                    actualQty = existingCurrentRecord.actualQty + count,
                    sources = existingCurrentRecord.sources + RecordSource.UDI,
                    productionDate = scannedProdDate ?: existingCurrentRecord.productionDate,
                    expiryDate = scannedExpDate ?: existingCurrentRecord.expiryDate,
                    prodDateStatus = if (scannedProdDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED,
                    expDateStatus = if (scannedExpDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED
                )
            } else {
                newEntries.add(
                    InventoryRecord(
                        documentId = "UNKNOWN", documentName = "未知单据",
                        di = di, productName = productName, batch = batch,
                        actualQty = count, sources = setOf(RecordSource.UDI),
                        productionDate = scannedProdDate,
                        expiryDate = scannedExpDate,
                        diStatus = globalStatus, batchStatus = globalStatus,
                        prodDateStatus = if (scannedProdDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED,
                        expDateStatus = if (scannedExpDate != null) FieldStatus.MATCHED else FieldStatus.NOT_CHECKED,
                        qtyStatus = globalStatus,
                        errorMessage = if (isNotInDocError) "不在单据中" else (if (productName == "未知商品") "库中无记录" else "")
                    )
                )
            }
        }
    }

    /**
     * 处理 OCR 识别结果，进入对比池。
     */
    fun processOcrResults(batch: String, prodDate: String?, expDate: String?) {
        val currentRecords = _recordList.value.toMutableList()
        val hasDoc = _currentDocuments.value.isNotEmpty()

        if (hasDoc) {
            val matchIndex = currentRecords.indexOfFirst {
                OcrFuzzyMatcher.fuzzyMatch(it.batch, batch)
            }
            if (matchIndex != -1) {
                val r = currentRecords[matchIndex]
                val newActualQty = r.actualQty + 1
                currentRecords[matchIndex] = r.copy(
                    actualQty = newActualQty,
                    sources = r.sources + RecordSource.OCR,
                    batchStatus = FieldStatus.MATCHED,
                    prodDateStatus = evaluateDateStatus(r.productionDate, prodDate),
                    expDateStatus = evaluateDateStatus(r.expiryDate, expDate),
                    qtyStatus = computeQtyStatus(newActualQty, r.targetQty)
                )
            } else {
                currentRecords.add(
                    InventoryRecord(
                        documentId = "UNKNOWN", documentName = "OCR未匹配",
                        di = "无DI", productName = "未知产品", batch = batch,
                        actualQty = 1, sources = setOf(RecordSource.OCR),
                        productionDate = prodDate, expiryDate = expDate,
                        diStatus = FieldStatus.ERROR, batchStatus = FieldStatus.ERROR,
                        qtyStatus = FieldStatus.ERROR, errorMessage = "OCR批号未匹配单据"
                    )
                )
            }
        } else {
            val existing = currentRecords.find { it.batch == batch && it.documentId == "UNKNOWN" }
            if (existing != null) {
                val idx = currentRecords.indexOf(existing)
                currentRecords[idx] = existing.copy(
                    actualQty = existing.actualQty + 1,
                    sources = existing.sources + RecordSource.OCR,
                    productionDate = prodDate ?: existing.productionDate,
                    expiryDate = expDate ?: existing.expiryDate
                )
            } else {
                currentRecords.add(
                    InventoryRecord(
                        documentId = "UNKNOWN", documentName = "未知单据",
                        di = "无DI", productName = "未知产品", batch = batch,
                        actualQty = 1, sources = setOf(RecordSource.OCR),
                        productionDate = prodDate, expiryDate = expDate,
                        diStatus = FieldStatus.MATCHED, batchStatus = FieldStatus.MATCHED,
                        qtyStatus = FieldStatus.MATCHED
                    )
                )
            }
        }
        _recordList.value = currentRecords
    }

    fun updateRecordQuantity(recordId: String, newQty: Int) {
        _recordList.update { current ->
            current.map {
                if (it.id == recordId) it.copy(
                    actualQty = newQty,
                    sources = it.sources + RecordSource.MANUAL,
                    qtyStatus = when {
                        it.targetQty == 0 -> FieldStatus.MATCHED
                        newQty == it.targetQty -> FieldStatus.MATCHED
                        newQty < it.targetQty -> FieldStatus.WARNING
                        else -> FieldStatus.ERROR
                    }
                ) else it
            }
        }
    }

    fun removeRecord(recordId: String) {
        _recordList.update { current ->
            if (_currentDocuments.value.isNotEmpty()) {
                current.mapNotNull { record ->
                    if (record.id != recordId) return@mapNotNull record
                    if (record.documentId == "UNKNOWN" || record.documentId.isBlank()) return@mapNotNull null
                    record.copy(
                        actualQty = 0,
                        sources = record.sources - RecordSource.MANUAL,
                        diStatus = FieldStatus.NOT_CHECKED,
                        batchStatus = FieldStatus.NOT_CHECKED,
                        prodDateStatus = FieldStatus.NOT_CHECKED,
                        expDateStatus = FieldStatus.NOT_CHECKED,
                        qtyStatus = if (record.targetQty > 0) FieldStatus.WARNING else FieldStatus.MATCHED
                    )
                }
            } else {
                current.filterNot { it.id == recordId }
            }
        }
    }

    fun getPhotoScanResults(): List<PhotoScanResult> = _photoScanResults.value
    fun getRecordList(): List<InventoryRecord> = _recordList.value
    fun getDocumentsForExport(): List<TargetDocument> = _currentDocuments.value

    fun checkExportReadiness(): DocStatus {
        val records = _recordList.value
        if (records.isEmpty()) return DocStatus.PASS
        if (records.any { it.rowStatus == RowStatus.ERROR }) return DocStatus.ERROR
        if (records.any { it.rowStatus == RowStatus.WARNING }) return DocStatus.WARNING
        return DocStatus.PASS
    }

    fun getTargetDiList(): List<String>? {
        if (_currentDocuments.value.isEmpty()) return null
        return _recordList.value
            .filter { (it.di.isNotEmpty() || it.candidateDis.isNotEmpty()) && it.targetQty > 0 }
            .flatMap { record ->
                val allDis = (listOf(record.di) + record.candidateDis)
                    .filter { it.isNotEmpty() }
                    .distinct()
                val disKey = allDis.joinToString(",")
                List(record.targetQty) { "$disKey|${record.batch}" }
            }
    }

    fun removePhoto(uri: android.net.Uri) {
        _photoList.update { current -> current.filter { it != uri } }
        try {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) file.delete()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importTargetDocumentFromJson(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val contentResolver = app.contentResolver
                    val inputStream = contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader().use { it?.readText() }

                    if (jsonString != null) {
                        val jsonParser = Json { ignoreUnknownKeys = true }
                        val parsedDoc = jsonParser.decodeFromString<TargetDocument>(jsonString)

                        val newRecords = parsedDoc.items.map { item ->
                            // 同物料编码可能对应多个产品（DI 不同），收集全部有效候选 DI
                            val candidates = repository.getProductsByMaterialCode(item.materialCode)
                            val candidateDis = candidates.mapNotNull { it.di?.trim()?.ifBlank { null } }.distinct()
                            val resolvedDi = candidateDis.firstOrNull() ?: ""
                            InventoryRecord(
                                di = resolvedDi,
                                candidateDis = candidateDis,
                                productName = item.productName ?: "-",
                                batch = item.batch ?: "—",
                                productionDate = item.productionDate ?: "-",
                                expiryDate = item.expiryDate ?: "-",
                                targetQty = item.targetQty.toInt(),
                                actualQty = 0,
                                diStatus = FieldStatus.NOT_CHECKED,
                                batchStatus = FieldStatus.NOT_CHECKED,
                                prodDateStatus = FieldStatus.NOT_CHECKED,
                                expDateStatus = FieldStatus.NOT_CHECKED,
                                qtyStatus = if (item.targetQty > 0) FieldStatus.WARNING else FieldStatus.MATCHED
                            )
                        }

                        withContext(Dispatchers.Main) {
                            _currentDocuments.value = listOf(parsedDoc)
                            _recordList.value = newRecords
                            _rawBarcodeList.value = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadMultipleTaskDetails(documentIds: List<String>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val docs = mutableListOf<TargetDocument>()
            val newRecords = mutableListOf<InventoryRecord>()
            var hasError = false

            for (id in documentIds) {
                val result = repository.fetchPythonTaskDetail(id)
                if (result.isSuccess) {
                    val parsedDoc = result.getOrNull() as? TargetDocument
                    if (parsedDoc != null) {
                        docs.add(parsedDoc)
                        newRecords.addAll(parsedDoc.items.map { item ->
                            // 同物料编码可能对应多个产品（DI 不同），收集全部有效候选 DI
                            val candidates = repository.getProductsByMaterialCode(item.materialCode)
                            val candidateDis = candidates.mapNotNull { it.di?.trim()?.ifBlank { null } }.distinct()
                            val resolvedDi = candidateDis.firstOrNull() ?: ""
                            InventoryRecord(
                                documentId = parsedDoc.documentId,
                                documentName = parsedDoc.documentName,
                                di = resolvedDi,
                                candidateDis = candidateDis,
                                productName = item.productName ?: "-",
                                batch = item.batch ?: "无批号",
                                productionDate = item.productionDate ?: "-",
                                expiryDate = item.expiryDate ?: "-",
                                targetQty = item.targetQty.toInt(),
                                actualQty = 0,
                                diStatus = FieldStatus.NOT_CHECKED,
                                batchStatus = FieldStatus.NOT_CHECKED,
                                prodDateStatus = FieldStatus.NOT_CHECKED,
                                expDateStatus = FieldStatus.NOT_CHECKED,
                                qtyStatus = if (item.targetQty > 0) FieldStatus.WARNING else FieldStatus.MATCHED
                            )
                        })
                    }
                } else {
                    hasError = true
                }
            }

            _photoList.value = emptyList()
            _photoScanResults.value = emptyList()
            _currentDocuments.value = docs
            _recordList.value = newRecords
            _rawBarcodeList.value = emptyList()

            _isLoading.value = false
            onResult(if (hasError) "部分单据获取失败，请检查网络" else null)
        }
    }
}
