package com.example.inventorymaster.ai.local

import android.content.Context

object HardwareConfig {

    fun detectAvailableAcceleration(context: Context): Set<HardwareAccel> {
        val result = mutableSetOf(HardwareAccel.CPU)

        if (hasNnApiSupport()) {
            result.add(HardwareAccel.NPU)
        }

        if (hasGpuSupport()) {
            result.add(HardwareAccel.GPU)
        }

        return result
    }

    fun getDeviceRamMb(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    fun getRecommendedModel(context: Context): String {
        val ramMb = getDeviceRamMb(context)
        return when {
            ramMb >= 12 * 1024 -> "gemma-4-e4b"
            ramMb >= 6 * 1024 -> "gemma-4-e2b"
            else -> "gemma-4-e2b"
        }
    }

    private fun hasNnApiSupport(): Boolean {
        return try {
            Class.forName("android.hardware.neuralnetworks") != null
        } catch (_: Exception) {
            false
        }
    }

    private fun hasGpuSupport(): Boolean {
        return true
    }
}
