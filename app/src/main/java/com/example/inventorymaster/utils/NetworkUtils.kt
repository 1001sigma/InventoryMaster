package com.example.inventorymaster.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.regex.Pattern

object NetworkUtils {

    /**
     * 检查当前是否连接了 WiFi 或 热点 (即局域网环境)
     * 返回 false 如果是移动数据或无网
     */
    fun isWifiOrEthernetConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        // 注意：Android 中连接热点通常也被算作 WIFI 类型
    }

    /**
     * 简单的 IP 格式校验 (IPv4)
     */
    fun isValidIpAddress(ip: String): Boolean {
        val ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        return Pattern.matches(ipv4Pattern, ip)
    }
}