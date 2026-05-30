package com.example.inventorymaster.data.sync

import kotlinx.coroutines.delay

/**
 * 局域网同步重试策略（Phase 4）
 *
 * 仅对网络层面的瞬时异常进行指数退避重试；
 * 业务层错误（HTTP 4xx/5xx）不重试，直接向上抛出。
 *
 * @param maxRetries       最大重试次数（默认 3）
 * @param baseDelayMs      初始退避延迟毫秒（默认 500）
 * @param maxDelayMs       退避延迟上限毫秒（默认 5000）
 * @param multiplier       退避乘数（默认 2.0）
 */
class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 500L,
    val maxDelayMs: Long = 5000L,
    val multiplier: Double = 2.0,
) {
    /**
     * 执行带重试的阻塞操作。
     * 仅捕获网络层异常重试；其他异常直接抛出。
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        var delayMs = baseDelayMs

        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isRetryable(e) || ++attempt >= maxRetries) {
                    throw e
                }
                delay(delayMs)
                delayMs = (delayMs * multiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }
    }

    /**
     * 判断异常是否属于可重试的网络瞬时故障。
     */
    private fun isRetryable(e: Exception): Boolean {
        val name = e.javaClass.name
        return name.contains("SocketTimeoutException")
                || name.contains("ConnectException")
                || name.contains("UnknownHostException")
                || name.contains("SocketException")
                || name.contains("ProtocolException")
                || name.contains("SSLException")
    }
}
