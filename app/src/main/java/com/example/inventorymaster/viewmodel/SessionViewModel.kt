package com.example.inventorymaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.dto.SessionDto
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.SessionWithProgress
import com.example.inventorymaster.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. 定义专属的小状态：只管任务列表
data class SessionUiState(
    val sessions: List<SessionWithProgress> = emptyList(), // 本地任务
    val cloudSessions: List<SessionDto> = emptyList(),  // 云端任务
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

class SessionViewModel(private val repository: InventoryRepository) : ViewModel() {

    // 2. 初始化状态
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        // 3. 启动时自动监听数据库里的任务列表
        viewModelScope.launch {
            repository.getAllSessionsWithProgress().collect { sessionList ->
                _uiState.value = _uiState.value.copy(sessions = sessionList)
            }
        }
    }

    // ... 下一步我们往这里填函数 ...
    // --- Session 操作 ---
    //新建任务
    fun createNewSession(name: String) {
        viewModelScope.launch { repository.createSession(name) }
    }

    //尝试归档
    suspend fun tryFinishSession(sessionId: Long): Int {
        val unverifiedCount = repository.getUnverifiedCount(sessionId)
        if (unverifiedCount == 0) {
            repository.updateSessionStatus(sessionId, 1)
            return -1
        }
        return unverifiedCount
    }

    //锁定任务
    fun toggleLockSession(session: InventorySession) {
        viewModelScope.launch {
            val newStatus = if (session.status == 2) 1 else 2
            repository.updateSessionStatus(session.id, newStatus)
        }
    }
    //删除任务
    fun deleteSession(session: InventorySession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    //获取云端任务
    fun fetchCloudTasks(ip: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.fetchCloudSessions(ip)
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    cloudSessions = list,
                    isLoading = false
                )
                if (list.isEmpty()) {
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

