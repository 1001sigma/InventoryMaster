package com.example.inventorymaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.repository.InventoryRepository
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
    // 拉取云端数据到本地
    data class Pull(val sessionId: Long) : SyncIntent()
    // 推送本地未同步数据到云端
    data class Push(val sessionId: Long,val isSilent: Boolean = false) : SyncIntent()
    // 全量上传到电脑端/服务器
    data class UploadToServer(val ip: String, val sessionId: Long) : SyncIntent()
    // 从电脑端/服务器全量下载
    data class DownloadFromPC(val ip: String, val sessionId: Long) : SyncIntent()
}

// =========================================================================
// 2. 定义状态 (State)：ViewModel 把同步的实时进度反馈给 UI
// =========================================================================
data class SyncState(
    val isSyncing: Boolean = false,       // 是否正在转圈圈
    val syncMessage: String? = null,      // 用于弹 Toast 的提示信息 (成功或失败)
    val isSuccess: Boolean = false ,       // 标记最近一次操作是否成功 (UI 可根据这个判断是否需要刷新列表)
    val lastServerIp: String = ""
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
        val savedIp = repository.getServerIp()
        if (savedIp.isNotEmpty()) {
            _uiState.update { it.copy(lastServerIp = savedIp) }
        }
    }

    // 避免重复点击导致多次同步的锁
    private var isSyncRunning = false

    /**
     * 🔥 中央调度器：UI 只能调用这一个方法，并传入指令
     */
    fun handleIntent(intent: SyncIntent) {
        // 如果当前正在同步，直接忽略新的请求 (你也可以根据业务需求改成排队)
        if (isSyncRunning) return

        viewModelScope.launch {
            isSyncRunning = true
            _uiState.update { it.copy(isSyncing = true, syncMessage = null, isSuccess = false) }

            try {
                // 根据 UI 发来的指令，分配给具体的方法去干活
                when (intent) {
                    is SyncIntent.Pull -> performPull(intent.sessionId)
                    is SyncIntent.Push -> performPush(intent.sessionId, intent.isSilent)
                    is SyncIntent.UploadToServer -> performUpload(intent.ip, intent.sessionId)
                    is SyncIntent.DownloadFromPC -> performDownload(intent.ip, intent.sessionId)
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
            _uiState.update { it.copy(syncMessage = "拉取成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(syncMessage = "拉取失败: ${result.exceptionOrNull()?.message}") }
        }
    }
    //增量上传
    private suspend fun performPush(sessionId: Long, isSyncing: Boolean) {
        val result = withContext(Dispatchers.IO) {
            repository.pushUnsyncedData(sessionId)
        }
        if (!isSyncing) {
            if (result.isSuccess) {
                _uiState.update { it.copy(syncMessage = "推送成功", isSuccess = true) }
            } else {
                _uiState.update { it.copy(syncMessage = "推送失败: ${result.exceptionOrNull()?.message}") }
            }
        }
    }
    //全量上传
    private suspend fun performUpload(ip: String, sessionId: Long) {
        val result = withContext(Dispatchers.IO) {
            repository.exportFullSession(ip, sessionId)
        }
        if (result.isSuccess) {
            repository.saveServerIp(ip) // 保存常用 IP
            _uiState.update { it.copy(syncMessage = "上传成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(syncMessage = "上传失败: ${result.exceptionOrNull()?.message}") }
        }
    }
    //全量下载
    private suspend fun performDownload(ip: String, targetSessionId: Long) {
        if (!com.example.inventorymaster.utils.NetworkUtils.isValidIpAddress(ip)) {
            _uiState.update { it.copy(syncMessage = "IP 地址格式错误") }
            return
        }

        val result = withContext(Dispatchers.IO) {
            repository.exportdownloadFromPC(ip, targetSessionId)
        }
        if (result.isSuccess) {
            repository.saveServerIp(ip)
            _uiState.update { it.copy(syncMessage = "下载成功", isSuccess = true) }
        } else {
            _uiState.update { it.copy(syncMessage = "连接失败: ${result.exceptionOrNull()?.message}") }
        }
    }

    /**
     * 清除消息的方法，通常在 UI 弹出 Toast 之后调用，防止旋转屏幕时再次弹出
     */
    fun clearMessage() {
        _uiState.update { it.copy(syncMessage = null, isSuccess = false) }
    }
}