package com.example.inventorymaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.repository.InventoryRepository
import com.example.inventorymaster.data.network.InventoryApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =========================================================================
// 1. 定义意图 (Intent)：UI 只能通过发送这些指令来让 ViewModel 工作
// =========================================================================
sealed class SyncIntent {
    data class Pull(val sessionId: Long) : SyncIntent()
    data class Push(val sessionId: Long, val isSilent: Boolean = false) : SyncIntent()
    data class UploadToServer(val ip: String, val token: String, val sessionId: Long) : SyncIntent()
    data class DownloadFromPC(val ip: String, val token: String, val sessionId: Long) : SyncIntent()
    data class ConfirmForceDownload(val ip: String, val sessionId: Long) : SyncIntent()
    data class PushThenDownload(val ip: String, val sessionId: Long) : SyncIntent()
    data class SaveToken(val token: String) : SyncIntent()
}

data class DownloadConflictInfo(
    val ip: String,
    val sessionId: Long,
    val dirtyCount: Int
)

// =========================================================================
// 2. 定义状态 (State)：ViewModel 把同步的实时进度反馈给 UI
// =========================================================================
data class SyncState(
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val isSuccess: Boolean = false,
    val lastServerIp: String = "",
    val lastServerToken: String = "",
    val pendingDownloadConflict: DownloadConflictInfo? = null
)

// =========================================================================
// 3. 核心 ViewModel (中央调度室)
// =========================================================================
class SyncViewModel(private val repository: InventoryRepository) : ViewModel() {
    // 确保你的 SyncState 里已经加上了

    // 内部可变状态
    private val _uiState = MutableStateFlow(SyncState())
    // 暴露给 UI 的只读状态
    val uiState: StateFlow<SyncState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedIp = repository.getServerIp()
            val savedToken = repository.getServerToken()
            _uiState.update { it.copy(lastServerIp = savedIp, lastServerToken = savedToken) }
        }
    }

    // 避免重复点击导致多次同步的锁
    private var isSyncRunning = false

    /**
     * 🔥 中央调度器：UI 只能调用这一个方法，并传入指令
     */
    fun handleIntent(intent: SyncIntent) {
        if (isSyncRunning) return

        viewModelScope.launch {
            isSyncRunning = true
            _uiState.update { it.copy(isSyncing = true, syncMessage = null, isSuccess = false) }

            try {
                when (intent) {
                    is SyncIntent.Pull -> performPull(intent.sessionId)
                    is SyncIntent.Push -> performPush(intent.sessionId, intent.isSilent)
                    is SyncIntent.UploadToServer -> performUpload(intent.ip, intent.token, intent.sessionId)
                    is SyncIntent.DownloadFromPC -> performDownload(intent.ip, intent.token, intent.sessionId)
                    is SyncIntent.ConfirmForceDownload -> performForceDownload(intent.ip, intent.sessionId)
                    is SyncIntent.PushThenDownload -> performPushThenDownload(intent.ip, intent.sessionId)
                    is SyncIntent.SaveToken -> {
                        repository.saveToken(intent.token)
                        InventoryApiService.setToken(intent.token)
                        _uiState.update { it.copy(lastServerToken = intent.token) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(syncMessage = "同步发生异常: ${e.message}") }
            } finally {
                // 不管成功还是失败，最后都要解除锁定并停止转圈圈
                isSyncRunning = false
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    // --- 以下是具体的搬运过来的业务逻辑，被 private 保护起来，不给 UI 直接调用 ---
    //增量下载
    private suspend fun performPull(sessionId: Long) {
        val result = withContext(Dispatchers.IO) {
            repository.pullNewData(sessionId)
        }
        if (result.isSuccess) {
            _uiState.update { it.copy(syncMessage = result.getOrNull() ?: "拉取成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(syncMessage = "拉取失败: ${result.exceptionOrNull()?.message}") }
        }
    }
    //增量上传（isSilent=true 为后台自动推送：连接失败静默，不弹服务端搜索弹窗）
    private suspend fun performPush(sessionId: Long, isSilent: Boolean) {
        val result = withContext(Dispatchers.IO) {
            repository.pushUnsyncedData(sessionId, userInitiated = !isSilent)
        }
        if (!isSilent) {
            if (result.isSuccess) {
                _uiState.update { it.copy(syncMessage = "推送成功", isSuccess = true) }
            } else {
                _uiState.update { it.copy(syncMessage = "推送失败: ${result.exceptionOrNull()?.message}") }
            }
        }
    }
    //全量上传
    private suspend fun performUpload(ip: String, token: String, sessionId: Long) {
        // 先验证 token
        val verifyResult = withContext(Dispatchers.IO) {
            repository.verifyAndSaveToken(ip, token)
        }
        if (verifyResult.isFailure) {
            _uiState.update { it.copy(syncMessage = "认证失败: ${verifyResult.exceptionOrNull()?.message}") }
            return
        }

        val result = withContext(Dispatchers.IO) {
            repository.exportFullSession(ip, sessionId)
        }
        if (result.isSuccess) {
            repository.saveServerIp(ip)
            _uiState.update { it.copy(syncMessage = "上传成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(syncMessage = "上传失败: ${result.exceptionOrNull()?.message}") }
        }
    }
    private suspend fun performDownload(ip: String, token: String, targetSessionId: Long) {
        if (!com.example.inventorymaster.utils.NetworkUtils.isValidIpAddress(ip)) {
            _uiState.update { it.copy(syncMessage = "IP 地址格式错误") }
            return
        }

        // 先验证 token
        val verifyResult = withContext(Dispatchers.IO) {
            repository.verifyAndSaveToken(ip, token)
        }
        if (verifyResult.isFailure) {
            _uiState.update { it.copy(syncMessage = "认证失败: ${verifyResult.exceptionOrNull()?.message}") }
            return
        }

        // M4：加入任务管控（R3）——大厅/扫码/手动/SyncDialog 全部下载入口都经此处
        val joinResult = repository.joinTask(targetSessionId)
        joinResult.onFailure { e ->
            val code = (e as? com.example.inventorymaster.data.network.CollabApiException)?.code
            // 任务不存在等情况不在此拦截，交给后续下载自然报错
            if (code in setOf("JOIN_CLOSED", "TASK_ARCHIVED", "DEVICE_NOT_BOUND", "OPERATOR_INACTIVE", "DEVICE_NOT_APPROVED", "NAME_EXISTS")) {
                val msg = when (code) {
                    "JOIN_CLOSED" -> "任务已开始盘点，禁止加入"
                    "TASK_ARCHIVED" -> "任务已归档"
                    "DEVICE_NOT_APPROVED" -> "等待管理员在电脑端批准加入"
                    "NAME_EXISTS" -> "任务中已存在同名操作员，请在设置中修改昵称"
                    else -> "无法加入任务: $code"
                }
                _uiState.update { it.copy(syncMessage = msg) }
                return
            }
        }

        val dirtyCount = withContext(Dispatchers.IO) {
            repository.getDirtyRecordsCount(targetSessionId)
        }

        if (dirtyCount > 0) {
            isSyncRunning = false
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    pendingDownloadConflict = DownloadConflictInfo(ip, targetSessionId, dirtyCount)
                )
            }
            return
        }

        executeDownload(ip, targetSessionId)
    }

    private suspend fun performForceDownload(ip: String, sessionId: Long) {
        _uiState.update { it.copy(pendingDownloadConflict = null) }
        executeDownload(ip, sessionId)
    }

    private suspend fun performPushThenDownload(ip: String, sessionId: Long) {
        _uiState.update { it.copy(pendingDownloadConflict = null) }

        val pushResult = withContext(Dispatchers.IO) {
            repository.pushUnsyncedData(sessionId, userInitiated = true)
        }

        if (pushResult.isSuccess) {
            executeDownload(ip, sessionId)
        } else {
            _uiState.update { it.copy(syncMessage = "推送失败: ${pushResult.exceptionOrNull()?.message}，下载已取消") }
        }
    }

    private suspend fun executeDownload(ip: String, targetSessionId: Long) {
        isSyncRunning = true
        _uiState.update { it.copy(isSyncing = true) }

        val result = withContext(Dispatchers.IO) {
            repository.exportdownloadFromPC(ip, targetSessionId)
        }

        isSyncRunning = false
        if (result.isSuccess) {
            repository.saveServerIp(ip)
            _uiState.update { it.copy(isSyncing = false, syncMessage = "下载成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(isSyncing = false, syncMessage = "连接失败: ${result.exceptionOrNull()?.message}") }
        }
    }

    /**
     * 清除消息的方法，通常在 UI 弹出 Toast 之后调用，防止旋转屏幕时再次弹出
     */
    fun clearMessage() {
        _uiState.update { it.copy(syncMessage = null, isSuccess = false) }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(pendingDownloadConflict = null) }
    }

    suspend fun requestJoinTask(ip: String, sessionId: Long, operatorName: String): Result<com.example.inventorymaster.data.dto.JoinStatusResponse> {
        return repository.requestJoinTask(ip, sessionId, operatorName)
    }

    suspend fun checkJoinStatus(ip: String, sessionId: Long): Result<com.example.inventorymaster.data.dto.JoinStatusResponse> {
        return repository.checkJoinStatus(ip, sessionId)
    }

    fun saveTokenDirect(token: String) {
        // 同步设置 token 到 API 服务（立即生效）
        InventoryApiService.setToken(token)
        // 异步保存到持久化存储
        viewModelScope.launch {
            repository.saveToken(token)
            _uiState.update { it.copy(lastServerToken = token) }
        }
    }
}