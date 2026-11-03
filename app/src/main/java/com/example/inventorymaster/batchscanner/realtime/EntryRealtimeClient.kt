package com.example.inventorymaster.batchscanner.realtime

import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.data.connection.ServerConnectionManager
import com.example.inventorymaster.data.network.EntryJobDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** WS 连接状态（驱动 UI 圆点与轮询节奏：断开 → 退回 2.5s 轮询，连接 → 放宽 15s 兜底） */
sealed interface RealtimeConnState {
    data object Disconnected : RealtimeConnState
    data object Connecting : RealtimeConnState
    data class Connected(val serverIp: String) : RealtimeConnState
}

/** 服务端推送事件。铁律：通知非真相——Progress 镜像仅刷 UI，Claimed/Appended/Finished 触发 REST 复核 */
sealed interface EntryPushEvent {
    data class Claimed(val jobId: Long) : EntryPushEvent
    data class Progress(val jobId: Long, val job: EntryJobDto) : EntryPushEvent
    data class Appended(val jobId: Long, val newTotal: Int?) : EntryPushEvent
    data class Finished(val jobId: Long) : EntryPushEvent
}

/**
 * 实时录入 WS 客户端（pythonUDI /ws/entry，协议见 WebSocket长连接方案.md §3/§4.1）
 *
 * - 地址经 [ServerConnectionManager] 解析（复用探测/缓存，静默失败不弹发现弹窗），
 *   每次重连前重新解析，服务端换 IP 自动跟随；
 * - 重连指数退避 1s→2s→5s→10s 封顶；协议层心跳 pingInterval(20s) 对应服务端 ws_ping_interval=20
 *   （移动端非浏览器，无需文档 §3.2 的应用层 ping，已记录为对文档的偏离）；
 * - 任何时刻断开，调用方行为退化为纯轮询，功能无损。
 */
class EntryRealtimeClient(
    private val connectionManager: ServerConnectionManager,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private val BACKOFF_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L)
        private const val OPEN_TIMEOUT_MS = 8_000L
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connState = MutableStateFlow<RealtimeConnState>(RealtimeConnState.Disconnected)
    val connState: StateFlow<RealtimeConnState> = _connState.asStateFlow()

    private val _events = MutableSharedFlow<EntryPushEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<EntryPushEvent> = _events.asSharedFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var enabled = true       // 设置页「实时推送」开关
    @Volatile private var wantConnected = false // 生命周期开关（VM 控）
    @Volatile private var jobId: Long? = null   // 当前订阅的录入任务
    private var connectJob: Job? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closedSignal: CompletableDeferred<Unit>? = null

    /** 设置开关切换；关闭即断开，完全回到 v1 纯轮询行为 */
    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) shutdown()
    }

    /** 进入录入场景时调用：标记需要连接并启动连接循环（幂等，已连接时无副作用） */
    fun ensureConnected() {
        if (!enabled) return
        wantConnected = true
        launchConnectLoop()
    }

    /** 当前任务变化时调用：连接中即时补订阅新 job */
    fun setJobId(id: Long?) {
        jobId = id
        if (id != null && _connState.value is RealtimeConnState.Connected) {
            socket?.send("""{"type":"subscribe","jobId":$id}""")
        }
    }

    /** VM.onCleared 调用：断开并停止重连 */
    fun close() = shutdown()

    private fun shutdown() {
        wantConnected = false
        connectJob?.cancel()
        connectJob = null
        closedSignal?.complete(Unit)
        socket?.close(1000, "client shutdown")
        socket = null
        _connState.value = RealtimeConnState.Disconnected
    }

    private fun launchConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            var attempt = 0
            while (isActive && wantConnected && enabled && _connState.value !is RealtimeConnState.Connected) {
                if (tryConnect()) {
                    attempt = 0
                    closedSignal?.await() // 挂起直到连接断开，再走退避重连
                } else {
                    _connState.value = RealtimeConnState.Disconnected
                }
                if (!wantConnected || !enabled) break
                delay(BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)])
                attempt++
            }
            if (_connState.value !is RealtimeConnState.Connected) {
                _connState.value = RealtimeConnState.Disconnected
            }
        }
    }

    private suspend fun tryConnect(): Boolean {
        _connState.value = RealtimeConnState.Connecting
        val ip = try {
            connectionManager.withServer(userInitiated = false) { it }
        } catch (e: Exception) {
            return false
        }
        val closed = CompletableDeferred<Unit>()
        closedSignal = closed
        val opened = CompletableDeferred<Boolean>()
        val request = Request.Builder().url("ws://$ip:8000/ws/entry").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("""{"type":"hello","role":"mobile","protocol":1}""")
                jobId?.let { ws.send("""{"type":"subscribe","jobId":$it}""") }
                _connState.value = RealtimeConnState.Connected(ip)
                opened.complete(true)
            }

            override fun onMessage(ws: WebSocket, text: String) = dispatch(text)

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connState.value = RealtimeConnState.Disconnected
                closed.complete(Unit)
                opened.complete(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connState.value = RealtimeConnState.Disconnected
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
            gson.fromJson(text, JsonObject::class.java)
        } catch (e: Exception) {
            return
        }
        val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        val id = obj.get("jobId")?.takeIf { it.isJsonPrimitive }?.asLong
        when (type) {
            "job.claimed" -> emit(EntryPushEvent.Claimed(id ?: return))
            "job.progress" -> {
                val data = obj.get("data") ?: return
                val job = try {
                    gson.fromJson(data, EntryJobDto::class.java)
                } catch (e: Exception) {
                    return
                }
                emit(EntryPushEvent.Progress(id ?: job.jobId, job))
            }
            "job.appended" -> emit(
                EntryPushEvent.Appended(id ?: return, obj.get("newTotal")?.takeIf { it.isJsonPrimitive }?.asInt)
            )
            "job.finished" -> emit(EntryPushEvent.Finished(id ?: return))
            // hello_ack / pong 与未知 type 静默忽略（前向兼容）
        }
    }

    private fun emit(e: EntryPushEvent) {
        _events.tryEmit(e)
    }
}
