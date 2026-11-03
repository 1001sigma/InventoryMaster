package com.example.inventorymaster.batchscanner.videostream.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * IMU 运动检测器 — 滞回算法 + 降频休眠
 *
 * 设计原则：
 * - Moving 状态：15 FPS（66ms 间隔）
 * - Stationary 状态：3 FPS（333ms 间隔）
 * - 不停死，保证静止对焦时仍能识别
 * - 滞回阈值：0.8/0.3，防止临界值频跳
 */
class ImuMotionDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // 滑动窗口（最近 5 次读数）
    private val readings = mutableListOf<FloatArray>()
    private val windowSize = 5

    // 状态机
    private var isMoving = true  // 默认 Moving，避免初始静止时完全跳过

    // 滞回阈值
    private val startThreshold = 0.8f   // 进入 Moving 的阈值
    private val stopThreshold = 0.3f    // 进入 Stationary 的阈值
    private val stopDurationMs = 500L   // 静止持续时间

    private var belowThresholdSince = 0L

    // 帧间隔
    private val _frameIntervalMs = MutableStateFlow(66L)  // 默认 15 FPS
    val frameIntervalMs: StateFlow<Long> = _frameIntervalMs.asStateFlow()

    // 运动状态
    private val _isDeviceMoving = MutableStateFlow(true)
    val isDeviceMoving: StateFlow<Boolean> = _isDeviceMoving.asStateFlow()

    /**
     * 开始监听
     */
    fun start() {
        linearAccelerationSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    /**
     * 停止监听
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val values = event.values
        val reading = floatArrayOf(values[0], values[1], values[2])
        readings.add(reading)
        if (readings.size > windowSize) readings.removeAt(0)

        if (readings.size < 3) return

        // 计算方差
        val variance = calculateVariance()
        val now = System.currentTimeMillis()

        // 滞回状态机
        when {
            variance > startThreshold -> {
                // 超过启动阈值 → Moving
                isMoving = true
                belowThresholdSince = 0L
                _isDeviceMoving.value = true
                _frameIntervalMs.value = 66L  // 15 FPS
            }
            variance < stopThreshold -> {
                // 低于停止阈值
                if (belowThresholdSince == 0L) {
                    belowThresholdSince = now
                } else if (now - belowThresholdSince > stopDurationMs) {
                    // 持续低于阈值超过 500ms → Stationary
                    isMoving = false
                    _isDeviceMoving.value = false
                    _frameIntervalMs.value = 333L  // 3 FPS
                }
            }
            else -> {
                // 在滞回区间内，保持当前状态
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 计算读数的方差
     */
    private fun calculateVariance(): Float {
        if (readings.isEmpty()) return 0f

        val magnitudes = readings.map { sqrt((it[0] * it[0] + it[1] * it[1] + it[2] * it[2]).toDouble()).toFloat() }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        return variance
    }
}
