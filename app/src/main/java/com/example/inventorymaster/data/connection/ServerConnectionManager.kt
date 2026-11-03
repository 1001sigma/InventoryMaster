package com.example.inventorymaster.data.connection

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.utils.NetworkUtils
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 服务端不可达（探测失败、未配置 IP、用户取消搜索等），由调用方 catch 后包装为 Result.failure */
class ServerUnreachableException(message: String) : Exception(message)

/** UDP 发现应答解析出的一个服务端实例 */
data class DiscoveredServer(
    val ip: String,
    val httpPort: Int,
    val name: String
)

/** 全局「搜索服务端」弹窗的状态机，由 MainActivity 根部收集渲染 */
sealed interface ServerDiscoveryState {
    object Hidden : ServerDiscoveryState
    data class Discovering(val isManual: Boolean) : ServerDiscoveryState
    data class Results(val servers: List<DiscoveredServer>, val isManual: Boolean) : ServerDiscoveryState
    data class NotFound(val isManual: Boolean, val message: String? = null) : ServerDiscoveryState
}

/**
 * 服务端连接管理器（App 级单例）
 *
 * 职责：统一解析「该连哪台服务器」，供所有服务端调用走 [withServer]：
 * 1. 优先使用调用方新输入的 [ServerConnectionManager.withServer] preferredIp，其次 DataStore 缓存 IP；
 * 2. 每个 IP 先做快速 TCP 探测（30s 内探测通过的 IP 直接复用，避免轮询反复探测）；
 * 3. 探测失败且为用户主动操作时，弹出全局「搜索服务端」弹窗（UDP 广播发现），
 *    用户选定/输入新 IP 后当前调用自动重试；后台轮询保持静默失败。
 * 服务端侧应答协议见 D:\xz\pythonUDI\main.py 与 D:\xz\inventory_server\main.py 的 _discovery_responder()。
 */
class ServerConnectionManager(
    context: Context,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        const val DISCOVERY_PORT = 38080
        const val SERVICE_HTTP_PORT = 8000
        const val INVENTORY_HTTP_PORT = 8080

        private const val PROBE_TIMEOUT_MS = 1500
        private const val SCAN_DURATION_MS = 3000L
        private const val SOCKET_TIMEOUT_MS = 400
        /** 用户主动取消搜索后，自动触发弹窗的冷却时间 */
        private const val AUTO_TRIGGER_COOLDOWN_MS = 60_000L
        /** 探测通过的 IP 在该时间内直接复用，不再重复探测 */
        private const val VERIFIED_CACHE_MS = 30_000L
        /** 弹窗等待用户选择的最长时间，超时视为取消 */
        private const val DISCOVERY_IDLE_TIMEOUT_MS = 5 * 60_000L

        val DISCOVERY_MAGIC = "INVENTORYMASTER_DISCOVERY_V1".toByteArray(Charsets.UTF_8)
        private const val DISCOVERY_APP_ID = "inventorymaster"
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveryState = MutableStateFlow<ServerDiscoveryState>(ServerDiscoveryState.Hidden)
    val discoveryState: StateFlow<ServerDiscoveryState> = _discoveryState.asStateFlow()

    private var verifiedIp: String? = null
    private var verifiedAt = 0L

    /** 最近一次发现会话取消的时间（成功选定则归零），作为自动触发冷却依据 */
    @Volatile
    private var lastDiscoveryCancelAt = 0L

    /** 当前进行中的发现会话（多个并发调用共享同一弹窗），guarded by this */
    private var activeDiscovery: CompletableDeferred<String?>? = null

    // ============================================================
    // 核心入口
    // ============================================================

    /**
     * 解析服务端地址并执行 [block]。
     * @param userInitiated 是否用户主动操作：true 时探测失败可弹出发现弹窗并自动重试
     * @param preferredIp 调用方显式提供的地址（如同步弹窗里新输入的 IP），优先于缓存
     * @throws ServerUnreachableException 无法解析出可用地址时
     */
    suspend fun <T> withServer(
        userInitiated: Boolean,
        preferredIp: String? = null,
        block: suspend (ip: String) -> T
    ): T {
        val ip = resolveServerIp(userInitiated, preferredIp)
        return block(ip)
    }

    private suspend fun resolveServerIp(userInitiated: Boolean, preferredIp: String?): String {
        val candidates = mutableListOf<String>()
        preferredIp?.let { raw ->
            val normalized = normalizeIp(raw)
            if (normalized.isNotBlank()) candidates.add(normalized)
        }
        val cached = normalizeIp(settingsRepository.getServerIp())
        if (cached.isNotBlank() && !candidates.contains(cached)) candidates.add(cached)

        for (candidate in candidates) {
            if (isVerifiedFresh(candidate)) return candidate
            if (probe(candidate)) {
                markVerified(candidate)
                if (candidate != cached) settingsRepository.saveServerIp(candidate)
                return candidate
            }
        }

        if (!userInitiated) {
            throw ServerUnreachableException(
                if (candidates.isEmpty()) "未设置服务器 IP，请先在设置中配置服务器地址"
                else "无法连接服务端 ${candidates.first()}"
            )
        }

        // 自动触发的冷却：用户刚主动取消过搜索时不重复打扰
        if (System.currentTimeMillis() - lastDiscoveryCancelAt < AUTO_TRIGGER_COOLDOWN_MS) {
            throw ServerUnreachableException("无法连接服务端（自动搜索冷却中，可在设置中手动搜索）")
        }

        return joinOrStartDiscovery(isManual = false)
            ?: throw ServerUnreachableException("未连接到服务端")
    }

    // ============================================================
    // 发现会话（弹窗驱动）
    // ============================================================

    /** 设置页「搜索服务端」按钮入口，不受冷却限制 */
    fun startManualDiscovery() {
        scope.launch { joinOrStartDiscovery(isManual = true) }
    }

    private suspend fun joinOrStartDiscovery(isManual: Boolean): String? {
        val deferred: CompletableDeferred<String?>
        var isOwner = false
        synchronized(this) {
            val existing = activeDiscovery
            if (existing != null) {
                deferred = existing
            } else {
                deferred = CompletableDeferred()
                activeDiscovery = deferred
                isOwner = true
            }
        }

        if (isOwner) {
            try {
                runDiscoverySession(deferred, isManual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "服务发现异常", e)
                completeDiscovery(deferred, null)
            }
            // 搜索结束但弹窗仍在等待用户（多结果/未找到），带超时等待用户决定
            val chosen = try {
                withTimeoutOrNull(DISCOVERY_IDLE_TIMEOUT_MS) { deferred.await() }
            } catch (e: CancellationException) {
                throw e
            }
            if (chosen == null && deferred.isActive) {
                completeDiscovery(deferred, null)
                return null
            }
            return chosen
        }
        return deferred.await()
    }

    private suspend fun runDiscoverySession(deferred: CompletableDeferred<String?>, isManual: Boolean) {
        _discoveryState.value = ServerDiscoveryState.Discovering(isManual)
        val servers = scanForServers()
        when {
            servers.size == 1 -> {
                val server = servers.first()
                settingsRepository.saveServerIp(server.ip)
                markVerified(server.ip)
                completeDiscovery(deferred, server.ip)
            }
            servers.isEmpty() -> _discoveryState.value = ServerDiscoveryState.NotFound(isManual)
            else -> _discoveryState.value = ServerDiscoveryState.Results(servers, isManual)
        }
    }

    private fun completeDiscovery(deferred: CompletableDeferred<String?>, ip: String?) {
        synchronized(this) {
            if (activeDiscovery === deferred) activeDiscovery = null
        }
        lastDiscoveryCancelAt = if (ip == null) System.currentTimeMillis() else 0L
        _discoveryState.value = ServerDiscoveryState.Hidden
        deferred.complete(ip)
    }

    // ============================================================
    // 弹窗回调（MainActivity / 设置页）
    // ============================================================

    fun onServerSelected(server: DiscoveredServer) {
        scope.launch {
            settingsRepository.saveServerIp(server.ip)
            markVerified(server.ip)
            finishActiveDiscovery(server.ip)
        }
    }

    fun onManualInputRequested() {
        val current = _discoveryState.value
        if (current is ServerDiscoveryState.Results) {
            _discoveryState.value = ServerDiscoveryState.NotFound(current.isManual)
        }
    }

    fun onManualIpSubmitted(rawIp: String) {
        val current = _discoveryState.value as? ServerDiscoveryState.NotFound ?: return
        scope.launch {
            val ip = normalizeIp(rawIp)
            if (!NetworkUtils.isValidIpAddress(ip)) {
                _discoveryState.value = ServerDiscoveryState.NotFound(current.isManual, "IP 格式不正确")
                return@launch
            }
            if (!probe(ip)) {
                _discoveryState.value = ServerDiscoveryState.NotFound(
                    current.isManual, "无法连接 $ip（端口 $SERVICE_HTTP_PORT/$INVENTORY_HTTP_PORT 均未响应），请确认服务端已启动"
                )
                return@launch
            }
            settingsRepository.saveServerIp(ip)
            markVerified(ip)
            finishActiveDiscovery(ip)
        }
    }

    fun onDismissDiscovery() {
        finishActiveDiscovery(null)
    }

    private fun finishActiveDiscovery(ip: String?) {
        val deferred = synchronized(this) { activeDiscovery }
        if (deferred != null) completeDiscovery(deferred, ip)
    }

    // ============================================================
    // UDP 广播扫描
    // ============================================================

    private suspend fun scanForServers(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try {
            wifiManager?.createMulticastLock("inventory_discovery")?.apply { acquire() }
        } catch (e: Exception) {
            null
        }

        val found = linkedMapOf<String, DiscoveredServer>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val targets = broadcastAddresses(wifiManager)
                val sendProbe: () -> Unit = {
                    for (target in targets) {
                        runCatching {
                            socket.send(DatagramPacket(DISCOVERY_MAGIC, DISCOVERY_MAGIC.size, target, DISCOVERY_PORT))
                        }
                    }
                }
                sendProbe()
                val deadline = System.currentTimeMillis() + SCAN_DURATION_MS
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(1024)
                    val recv = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(recv)
                    } catch (e: SocketTimeoutException) {
                        sendProbe() // 周期性重发，弥补丢包
                        continue
                    } catch (e: IOException) {
                        break // 网络环境突变，提前结束
                    }
                    parseDiscoveryReply(recv)?.let { found[it.ip] = it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP 扫描失败", e)
        } finally {
            try {
                lock?.release()
            } catch (_: Exception) {
            }
        }
        found.values.toList()
    }

    /** 本网段定向广播地址 + 受限广播兜底 */
    private fun broadcastAddresses(wifiManager: WifiManager?): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val netmask = if (dhcp.netmask != 0) dhcp.netmask else 0xFFFFFF00.toInt() // 默认按 /24
                val broadcastInt = dhcp.ipAddress or netmask.inv()
                val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(broadcastInt).array()
                result.add(InetAddress.getByAddress(bytes))
            }
        } catch (_: Exception) {
        }
        result.add(InetAddress.getByName("255.255.255.255"))
        return result
    }

    private fun parseDiscoveryReply(packet: DatagramPacket): DiscoveredServer? {
        return try {
            val json = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            val reply = Gson().fromJson(json, DiscoveryReply::class.java) ?: return null
            if (reply.app != DISCOVERY_APP_ID) return null
            val ip = packet.address?.hostAddress ?: return null
            DiscoveredServer(
                ip = ip,
                httpPort = reply.httpPort ?: SERVICE_HTTP_PORT,
                name = reply.name?.takeIf { it.isNotBlank() } ?: "服务端"
            )
        } catch (e: Exception) {
            null
        }
    }

    private data class DiscoveryReply(
        val app: String? = null,
        val version: Int = 0,
        @SerializedName("http_port") val httpPort: Int? = null,
        val name: String? = null
    )

    // ============================================================
    // 探测与工具
    // ============================================================

    /** 快速 TCP 探测服务端端口（pythonUDI 网关 8000 或盘点服务端 8080），任一通过即视为可达 */
    private suspend fun probe(ip: String): Boolean = withContext(Dispatchers.IO) {
        listOf(SERVICE_HTTP_PORT, INVENTORY_HTTP_PORT).any { port ->
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun markVerified(ip: String) {
        verifiedIp = ip
        verifiedAt = System.currentTimeMillis()
    }

    private fun isVerifiedFresh(ip: String): Boolean =
        verifiedIp == ip && System.currentTimeMillis() - verifiedAt < VERIFIED_CACHE_MS

    private fun normalizeIp(raw: String): String =
        raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")

    private val TAG = "ServerConnMgr"
}
