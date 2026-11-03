package com.example.inventorymaster.utils

import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV 初始化管理器
 * 负责 C++ 底层库的异步加载与状态机管理
 */
object OpenCVInit {

    private const val TAG = "OpenCVInit"

    enum class State {
        LOADING,
        LOADED,
        FAILED
    }

    @Volatile
    private var state: State = State.LOADING

    /**
     * 初始化 OpenCV，线程安全
     * @return true if initialized successfully
     */
    fun init(): Boolean {
        if (state == State.LOADED) return true
        if (state == State.LOADING) {
            // 等待其他线程完成初始化
            synchronized(this) {
                if (state == State.LOADED) return true
                if (state == State.FAILED) return false
            }
        }

        synchronized(this) {
            if (state == State.LOADED) return true
            state = State.LOADING
            return try {
                val success = OpenCVLoader.initLocal()
                state = if (success) State.LOADED else State.FAILED
                if (success) {
                    Log.i(TAG, "OpenCV loaded successfully")
                } else {
                    Log.e(TAG, "OpenCV initialization failed")
                }
                success
            } catch (e: Exception) {
                state = State.FAILED
                Log.e(TAG, "OpenCV initialization exception", e)
                false
            }
        }
    }

    fun isInitialized(): Boolean = state == State.LOADED

    fun getState(): State = state
}
