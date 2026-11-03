package com.example.inventorymaster.data.connection

import com.example.inventorymaster.data.SettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** 盘库门铃连接状态（驱动 UI 与自动刷新开关） */
sealed class DoorbellConnState {
    data object Disconnected : DoorbellConnState()
    data object Connecting : DoorbellConnState()
    data class Connected(val serverIp: String) : DoorbellConnState()
}

/** 服务端门铃事件：通知非真相，收到后走既有 REST 增量拉取 */
sealed class DoorbellEvent {
    data class DataChanged(val sessionUuid: String, val seq: Long) : DoorbellEvent()
    data class SessionChanged(val sessionUuid: String) : DoorbellEvent()
}

/**
 * 盘库服务端（8080）WS 门铃客户端（M0 基建，协议见设计文档附录 A）
 *
 * - 地址经 [ServerConnectionManager] 解析（复用探测/缓存；静默失败不弹发现弹窗），
 *   每次重连前重新解析，服务端换 IP 自动跟随；
 * - hello 携带 token（服务端握手内校验）；协议层心跳 pingInterval(20s) 对应服务端
 *   uvicorn ws_ping_interval=20；指数退避 1s→2s→5s→10s 封顶；
 * - 任何时刻断开，调用方行为退化为手动下拉刷新，功能无损。
 */
class InventoryDoorbellClient(
    private val connectionManager: ServerConnectionManager,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private val BACKOFF_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L)
        private const val OPEN_TIMEOUT_MS = 8_000L
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connState = MutableStateFlow<DoorbellConnState>(DoorbellConnState.Disconnected)
    val connState: StateFlow<DoorbellConnState> = _connState.asStateFlow()

    private val _events = MutableSharedFlow<DoorbellEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DoorbellEvent> = _events.asSharedFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var enabled = true        // 设置页「盘库实时刷新」开关
    @Volatile private var wantConnected = false // 生命周期开关（VM 控）
    private var connectJob: Job? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closedSignal: CompletableDeferred<Unit>? = null

    /** 设置开关切换；关闭即断开，完全回到手动刷新行为 */
    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) shutdown()
    }

    /** 进入盘库详情页（有当前任务）时调用；幂等，已连接时无副作用 */
    fun ensureConnected() {
        if (!enabled) return
        wantConnected = true
        launchConnectLoop()
    }

    /** 离开盘库详情页 / VM 销毁时调用 */
    fun close() = shutdown()

    private fun shutdown() {
        wantConnected = false
        connectJob?.cancel()
        connectJob = null
        closedSignal?.complete(Unit)
        socket?.close(1000, "client shutdown")
        socket = null
        _connState.value = DoorbellConnState.Disconnected
    }

    private fun launchConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            var attempt = 0
            while (isActive && wantConnected && enabled && _connState.value !is DoorbellConnState.Connected) {
                if (tryConnect()) {
                    attempt = 0
                    closedSignal?.await() // 挂起直到连接断开，再走退避重连
                } else {
                    _connState.value = DoorbellConnState.Disconnected
                }
                if (!wantConnected || !enabled) break
                delay(BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)])
                attempt++
            }
            if (_connState.value !is DoorbellConnState.Connected) {
                _connState.value = DoorbellConnState.Disconnected
            }
        }
    }

    private suspend fun tryConnect(): Boolean {
        _connState.value = DoorbellConnState.Connecting
        val serverToken = settingsRepository.getServerToken()
        val ip = try {
            connectionManager.withServer(userInitiated = false) { it }
        } catch (e: Exception) {
            return false
        }
        val closed = CompletableDeferred<Unit>()
        closedSignal = closed
        val opened = CompletableDeferred<Boolean>()
        val request = Request.Builder().url("ws://$ip:8080/ws").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(
                    gson.toJson(
                        mapOf(
                            "type" to "hello",
                            "role" to "mobile",
                            "protocol" to 1,
                            "token" to serverToken,
                            "deviceId" to null // R1 预留：设备身份接入后填充
                        )
                    )
                )
                _connState.value = DoorbellConnState.Connected(ip)
                opened.complete(true)
            }

            override fun onMessage(ws: WebSocket, text: String) = dispatch(text)

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connState.value = DoorbellConnState.Disconnected
                closed.complete(Unit)
                opened.complete(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connState.value = DoorbellConnState.Disconnected
                closed.complete(Unit)
                opened.complete(false)
            }
        })
        return try {
            withTimeoutOrNull(OPEN_TIMEOUT_MS) { opened.await() } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun dispatch(text: String) {
        val obj = try {
            gson.fromJson(text, Map::class.java) as? Map<*, *>
        } catch (e: Exception) {
            return
        }
        val type = obj?.get("type") as? String ?: return
        when (type) {
            "data_changed" -> {
                val uuid = obj["sessionUuid"] as? String ?: return
                val seq = (obj["seq"] as? Number)?.toLong() ?: 0L
                _events.tryEmit(DoorbellEvent.DataChanged(uuid, seq))
            }
            "session_changed" -> {
                val uuid = obj["sessionUuid"] as? String ?: return
                _events.tryEmit(DoorbellEvent.SessionChanged(uuid))
            }
            // hello_ack / pong 与未知 type 静默忽略（前向兼容）
        }
    }
}
