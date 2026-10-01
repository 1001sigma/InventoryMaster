package com.example.inventorymaster.ai.local

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ModelManager(private val context: Context) {

    private val engines = mutableMapOf<String, ModelEngine>()
    private val lastUsed = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    suspend fun getOrCreateEngine(
        modelId: String,
        config: ModelConfig
    ): ModelEngine = mutex.withLock {
        engines[modelId]?.let {
            lastUsed[modelId] = System.currentTimeMillis()
            return it
        }

        val engine = when (config.engineType) {
            EngineType.ONNX -> OnnxEngine()
            EngineType.LLM -> LlmEngine()
        }

        val availableMb = getAvailableMemoryMb()
        if (config.maxMemoryMb > availableMb * 0.8) {
            releaseLeastRecentlyUsed()
        }

        engine.initialize(config).getOrElse { e ->
            throw ModelLoadException("Failed to load $modelId: ${e.message}", e)
        }

        engines[modelId] = engine
        lastUsed[modelId] = System.currentTimeMillis()
        engine
    }

    suspend fun onTrimMemory(level: Int) = mutex.withLock {
        when {
            level >= TRIM_MEMORY_MODERATE -> {
                engines.filterValues { it.engineType == EngineType.LLM }
                    .forEach { (id, engine) ->
                        (engine as? LlmEngine)?.releaseTemporarily()
                        engines.remove(id)
                    }
                Log.i("ModelManager", "Released LLM engines due to memory pressure (level=$level)")
            }
            level >= TRIM_MEMORY_RUNNING_LOW -> {
                releaseLeastRecentlyUsed()
            }
        }
    }

    fun release(modelId: String) {
        engines.remove(modelId)?.release()
        lastUsed.remove(modelId)
    }

    fun releaseAll() {
        engines.values.forEach { it.release() }
        engines.clear()
        lastUsed.clear()
    }

    private fun releaseLeastRecentlyUsed() {
        val oldest = lastUsed.entries.minByOrNull { it.value }?.key ?: return
        engines.remove(oldest)?.release()
        lastUsed.remove(oldest)
        Log.i("ModelManager", "Released LRU model: $oldest")
    }

    private fun getAvailableMemoryMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    companion object {
        const val TRIM_MEMORY_RUNNING_LOW = 15
        const val TRIM_MEMORY_MODERATE = 5
    }
}
