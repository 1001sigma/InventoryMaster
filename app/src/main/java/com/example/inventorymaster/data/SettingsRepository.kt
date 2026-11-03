package com.example.inventorymaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.data.model.RecordCardConfig
import com.example.inventorymaster.data.model.RecordCardSlot
import com.example.inventorymaster.ui.theme.ColorTuple
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class UserSettings(
    val seedColor: Long = 0xFF4CAF50,
    val activeColorTupleSerialized: String = "",
    val customColorTuplesJson: String = "[]",
    val useDynamicColor: Boolean = false,
    val isAmoledMode: Boolean = false,
    val themeMode: Int = 0,
    val isSimpleMode: Boolean = false,
    val showOcrMask: Boolean = true,
    val enableMultiScan: Boolean = false,
    val enableDiValidation: Boolean = false,
    val useOpenCV: Boolean = true,
    // 拍照原图存系统相册（默认关）
    val saveOriginalPhotoToGallery: Boolean = false,
    // 扫描框尺寸记忆（相对预览容器的归一化比例，0 = 未设置，用默认值）
    val scanFrameRatioW: Float = 0f,
    val scanFrameRatioH: Float = 0f,
    val ocrFrameRatioW: Float = 0f,
    val ocrFrameRatioH: Float = 0f,
    val serverIp: String = "",
    val serverToken: String = "",
    // 实时录入长连接推送（pythonUDI /ws/entry）；关闭 = 纯 v1 轮询行为
    val realtimePushEnabled: Boolean = true,
    // 盘库服务端 WS 门铃（8080 /ws）；关闭 = 手动下拉刷新
    val inventoryPushEnabled: Boolean = true,
    // AI 聊天：显示可折叠思维链/工具调用详情
    val aiShowThinking: Boolean = true,
    // M1 协作身份：设备唯一标识（首启生成永久保存）与绑定的操作员姓名
    val deviceUuid: String = "",
    val operatorName: String = "",
    val watermarkText: String = "",
    val watermarkOpacity: Float = 0.3f,
    val watermarkAngle: Float = 45f,
    val watermarkFontSize: Float = 32f,
    val watermarkColor: Long = 0xFFFF0000,
    // 每页水印数量 1..16（网格自动均布，1=居中单枚）
    val watermarkDensity: Int = 1,
    // 电子签名默认摆放参数：签名中心点百分比坐标（percentY 自顶部起算）、宽度占页宽比例、整体不透明度
    val signatureOffsetX: Float = 0.78f,
    val signatureOffsetY: Float = 0.85f,
    val signatureScale: Float = 0.22f,
    val signatureOpacity: Float = 1f,
    // PDF 本地储存位置（SAF 文件夹 tree URI，持久授权）；空 = 每次导出弹系统选位器
    val pdfExportDirUri: String = "",
    val nearExpiryThresholdDays: Int = 30,
    // 快捷计算器：再次打开时是否携带上次计算结果
    val calcRetainLastResult: Boolean = true,
    // 长光模式（智能识别）：自动识别长光华医 21 位条码并提取批号搜索
    val enableHybiomeMode: Boolean = true,
    // 已查验物料自动移至末尾（默认关，即方案 A 严格保持原录入顺序）
    val sinkVerifiedItems: Boolean = false,
    // 空搜索不显示数据（测试项：默认关；开启后搜索框为空时不自动展示全量物料，仅在输入关键字或扫码后展示）
    val emptySearchShowsNothing: Boolean = false,
    // 记录卡片五个信息位的字段映射（设置 → 盘存设置 → 记录卡片样式）
    val recordCardConfig: RecordCardConfig = RecordCardConfig(),
    // AI 设置 - 新版（服务商列表 + 激活 ID）
    val aiProvidersJson: String = "[]",
    val activeProviderId: String = "",
    // AI 设置 - 旧版（兼容迁移）
    val aiProvider: String = "QWEN",
    val qwenApiKey: String = "",
    val qwenModel: String = "qwen-plus",
    val deepseekApiKey: String = "",
    val deepseekModel: String = "deepseek-chat"
) {
    /** 解析服务商列表 */
    fun getAiProviders(): List<AiProviderConfig> {
        return try {
            val type = object : TypeToken<List<AiProviderConfig>>() {}.type
            Gson().fromJson(aiProvidersJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 获取当前激活的服务商 */
    fun getActiveProvider(): AiProviderConfig? {
        val providers = getAiProviders()
        return providers.find { it.id == activeProviderId }
            ?: providers.firstOrNull()
    }

    /** 获取当前生效的色彩方案 (ColorTuple) */
    fun getActiveColorTuple(): ColorTuple {
        ColorTuple.fromSerializedString(activeColorTupleSerialized)?.let { return it }
        // 兼容旧版 seedColor: Long
        val argb = seedColor.toInt()
        val matchPreset = ModernColorSchemes.allSchemes.find { it.primary.toArgb() == argb }
        return matchPreset?.asColorTuple() ?: ColorTuple(Color(argb))
    }

    /** 获取用户自定义保存的调色板列表 */
    fun getCustomColorTuples(): List<ColorTuple> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = Gson().fromJson(customColorTuplesJson, type) ?: emptyList()
            list.mapNotNull { ColorTuple.fromSerializedString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val SEED_COLOR = longPreferencesKey("seed_color")
        val ACTIVE_COLOR_TUPLE = stringPreferencesKey("active_color_tuple")
        val CUSTOM_COLOR_TUPLES = stringPreferencesKey("custom_color_tuples")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val IS_AMOLED = booleanPreferencesKey("is_amoled")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val IS_SIMPLE_MODE = booleanPreferencesKey("is_simple_mode")
        val SHOW_OCR_MASK = booleanPreferencesKey("show_ocr_mask")
        val ENABLE_MULTI_SCAN = booleanPreferencesKey("enable_multi_scan")
        val ENABLE_DI_VALIDATION = booleanPreferencesKey("enable_di_validation")
        val USE_OPENCV = booleanPreferencesKey("use_opencv")
        val SAVE_ORIGINAL_PHOTO_TO_GALLERY = booleanPreferencesKey("save_original_photo_to_gallery")
        val SCAN_FRAME_RATIO_W = floatPreferencesKey("scan_frame_ratio_w")
        val SCAN_FRAME_RATIO_H = floatPreferencesKey("scan_frame_ratio_h")
        val OCR_FRAME_RATIO_W = floatPreferencesKey("ocr_frame_ratio_w")
        val OCR_FRAME_RATIO_H = floatPreferencesKey("ocr_frame_ratio_h")
        val SERVER_IP = stringPreferencesKey("server_ip")
        val SERVER_TOKEN = stringPreferencesKey("server_token")
        val REALTIME_PUSH_ENABLED = booleanPreferencesKey("realtime_push_enabled")
        val INVENTORY_PUSH_ENABLED = booleanPreferencesKey("inventory_push_enabled")
        // AI 聊天：折叠思维链/工具调用详情卡片
        val AI_SHOW_THINKING = booleanPreferencesKey("ai_show_thinking")
        val DEVICE_UUID = stringPreferencesKey("device_uuid")
        val OPERATOR_NAME = stringPreferencesKey("operator_name")
        val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
        val WATERMARK_OPACITY = floatPreferencesKey("watermark_opacity")
        val WATERMARK_ANGLE = floatPreferencesKey("watermark_angle")
        val WATERMARK_FONT_SIZE = floatPreferencesKey("watermark_font_size")
        val WATERMARK_COLOR = longPreferencesKey("watermark_color")
        val WATERMARK_DENSITY = intPreferencesKey("watermark_density")
        val SIGNATURE_OFFSET_X = floatPreferencesKey("signature_offset_x")
        val SIGNATURE_OFFSET_Y = floatPreferencesKey("signature_offset_y")
        val SIGNATURE_SCALE = floatPreferencesKey("signature_scale")
        val SIGNATURE_OPACITY = floatPreferencesKey("signature_opacity")
        // PDF 本地储存位置（SAF 文件夹 tree URI，持久授权）
        val PDF_EXPORT_DIR_URI = stringPreferencesKey("pdf_export_dir_uri")
        val NEAR_EXPIRY_THRESHOLD = intPreferencesKey("near_expiry_threshold_days")
        val CALC_RETAIN_LAST_RESULT = booleanPreferencesKey("calc_retain_last_result")
        val ENABLE_HYBIOME_MODE = booleanPreferencesKey("enable_hybiome_mode")
        val SINK_VERIFIED_ITEMS = booleanPreferencesKey("sink_verified_items")
        val EMPTY_SEARCH_SHOWS_NOTHING = booleanPreferencesKey("empty_search_shows_nothing")
        // 记录卡片字段配置（五个信息位各自显示的字段 id）
        val CARD_FIELD_UDI = stringPreferencesKey("card_field_udi")
        val CARD_FIELD_BATCH = stringPreferencesKey("card_field_batch")
        val CARD_FIELD_EXPIRY = stringPreferencesKey("card_field_expiry")
        val CARD_FIELD_LOCATION = stringPreferencesKey("card_field_location")
        val CARD_FIELD_MANUFACTURER = stringPreferencesKey("card_field_manufacturer")
        // 旧版 AI Keys（保留用于迁移）
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val QWEN_API_KEY = stringPreferencesKey("qwen_api_key")
        val QWEN_MODEL = stringPreferencesKey("qwen_model")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val DEEPSEEK_MODEL = stringPreferencesKey("deepseek_model")
        // 新版 AI Keys
        val AI_PROVIDERS_JSON = stringPreferencesKey("ai_providers_json")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .map { preferences ->
            UserSettings(
                seedColor = preferences[Keys.SEED_COLOR] ?: 0xFF4CAF50,
                activeColorTupleSerialized = preferences[Keys.ACTIVE_COLOR_TUPLE] ?: "",
                customColorTuplesJson = preferences[Keys.CUSTOM_COLOR_TUPLES] ?: "[]",
                useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: false,
                isAmoledMode = preferences[Keys.IS_AMOLED] ?: false,
                themeMode = preferences[Keys.THEME_MODE] ?: 0,
                isSimpleMode = preferences[Keys.IS_SIMPLE_MODE] ?: false,
                showOcrMask = preferences[Keys.SHOW_OCR_MASK] ?: true,
                enableMultiScan = preferences[Keys.ENABLE_MULTI_SCAN] ?: false,
                enableDiValidation = preferences[Keys.ENABLE_DI_VALIDATION] ?: false,
                useOpenCV = preferences[Keys.USE_OPENCV] ?: true,
                saveOriginalPhotoToGallery = preferences[Keys.SAVE_ORIGINAL_PHOTO_TO_GALLERY] ?: false,
                scanFrameRatioW = preferences[Keys.SCAN_FRAME_RATIO_W] ?: 0f,
                scanFrameRatioH = preferences[Keys.SCAN_FRAME_RATIO_H] ?: 0f,
                ocrFrameRatioW = preferences[Keys.OCR_FRAME_RATIO_W] ?: 0f,
                ocrFrameRatioH = preferences[Keys.OCR_FRAME_RATIO_H] ?: 0f,
                serverIp = preferences[Keys.SERVER_IP] ?: "",
                serverToken = preferences[Keys.SERVER_TOKEN] ?: "",
                realtimePushEnabled = preferences[Keys.REALTIME_PUSH_ENABLED] ?: true,
                inventoryPushEnabled = preferences[Keys.INVENTORY_PUSH_ENABLED] ?: true,
                aiShowThinking = preferences[Keys.AI_SHOW_THINKING] ?: true,
                deviceUuid = preferences[Keys.DEVICE_UUID] ?: "",
                operatorName = preferences[Keys.OPERATOR_NAME] ?: "",
                watermarkText = preferences[Keys.WATERMARK_TEXT] ?: "",
                watermarkOpacity = preferences[Keys.WATERMARK_OPACITY] ?: 0.3f,
                watermarkAngle = preferences[Keys.WATERMARK_ANGLE] ?: 45f,
                watermarkFontSize = preferences[Keys.WATERMARK_FONT_SIZE] ?: 32f,
                watermarkColor = preferences[Keys.WATERMARK_COLOR] ?: 0xFFFF0000,
                watermarkDensity = preferences[Keys.WATERMARK_DENSITY] ?: 1,
                signatureOffsetX = preferences[Keys.SIGNATURE_OFFSET_X] ?: 0.78f,
                signatureOffsetY = preferences[Keys.SIGNATURE_OFFSET_Y] ?: 0.85f,
                signatureScale = preferences[Keys.SIGNATURE_SCALE] ?: 0.22f,
                signatureOpacity = preferences[Keys.SIGNATURE_OPACITY] ?: 1f,
                pdfExportDirUri = preferences[Keys.PDF_EXPORT_DIR_URI] ?: "",
                nearExpiryThresholdDays = preferences[Keys.NEAR_EXPIRY_THRESHOLD] ?: 30,
                calcRetainLastResult = preferences[Keys.CALC_RETAIN_LAST_RESULT] ?: true,
                enableHybiomeMode = preferences[Keys.ENABLE_HYBIOME_MODE] ?: true,
                sinkVerifiedItems = preferences[Keys.SINK_VERIFIED_ITEMS] ?: false,
                emptySearchShowsNothing = preferences[Keys.EMPTY_SEARCH_SHOWS_NOTHING] ?: false,
                recordCardConfig = RecordCardConfig(
                    udiField = preferences[Keys.CARD_FIELD_UDI] ?: RecordCardSlot.UDI.defaultField.id,
                    batchField = preferences[Keys.CARD_FIELD_BATCH] ?: RecordCardSlot.BATCH.defaultField.id,
                    expiryField = preferences[Keys.CARD_FIELD_EXPIRY] ?: RecordCardSlot.EXPIRY.defaultField.id,
                    locationField = preferences[Keys.CARD_FIELD_LOCATION] ?: RecordCardSlot.LOCATION.defaultField.id,
                    manufacturerField = preferences[Keys.CARD_FIELD_MANUFACTURER] ?: RecordCardSlot.MANUFACTURER.defaultField.id
                ),
                aiProvidersJson = preferences[Keys.AI_PROVIDERS_JSON] ?: "[]",
                activeProviderId = preferences[Keys.ACTIVE_PROVIDER_ID] ?: "",
                aiProvider = preferences[Keys.AI_PROVIDER] ?: "QWEN",
                qwenApiKey = preferences[Keys.QWEN_API_KEY] ?: "",
                qwenModel = preferences[Keys.QWEN_MODEL] ?: "qwen-plus",
                deepseekApiKey = preferences[Keys.DEEPSEEK_API_KEY] ?: "",
                deepseekModel = preferences[Keys.DEEPSEEK_MODEL] ?: "deepseek-chat"
            )
        }

    /** 首次启动时将旧版 AI 设置迁移到新版 */
    suspend fun migrateOldProviderSettings() {
        context.dataStore.data.first().let { preferences ->
            val providersJson = preferences[Keys.AI_PROVIDERS_JSON]
            if (!providersJson.isNullOrBlank() && providersJson != "[]") return

            val oldProvider = preferences[Keys.AI_PROVIDER] ?: return
            val providers = mutableListOf<AiProviderConfig>()

            when (oldProvider) {
                "QWEN" -> {
                    val apiKey = preferences[Keys.QWEN_API_KEY] ?: ""
                    val model = preferences[Keys.QWEN_MODEL] ?: "qwen-plus"
                    if (apiKey.isNotBlank()) {
                        val config = AiProviderConfig(
                            name = "通义千问",
                            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                            model = model,
                            apiKey = apiKey,
                            modelsEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/models"
                        )
                        providers.add(config)
                        context.dataStore.edit {
                            it[Keys.ACTIVE_PROVIDER_ID] = config.id
                        }
                    }
                }
                "DEEPSEEK" -> {
                    val apiKey = preferences[Keys.DEEPSEEK_API_KEY] ?: ""
                    val model = preferences[Keys.DEEPSEEK_MODEL] ?: "deepseek-chat"
                    if (apiKey.isNotBlank()) {
                        val config = AiProviderConfig(
                            name = "DeepSeek",
                            baseUrl = "https://api.deepseek.com/v1",
                            model = model,
                            apiKey = apiKey,
                            modelsEndpoint = "https://api.deepseek.com/v1/models"
                        )
                        providers.add(config)
                        context.dataStore.edit {
                            it[Keys.ACTIVE_PROVIDER_ID] = config.id
                        }
                    }
                }
            }

            if (providers.isNotEmpty()) {
                context.dataStore.edit {
                    it[Keys.AI_PROVIDERS_JSON] = gson.toJson(providers)
                }
            }
        }
    }

    // ============================================================
    // 服务商管理方法
    // ============================================================

    suspend fun getAiProviders(): List<AiProviderConfig> {
        return context.dataStore.data.first().let { preferences ->
            val json = preferences[Keys.AI_PROVIDERS_JSON] ?: "[]"
            try {
                val type = object : TypeToken<List<AiProviderConfig>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveAiProviders(providers: List<AiProviderConfig>) {
        context.dataStore.edit {
            it[Keys.AI_PROVIDERS_JSON] = gson.toJson(providers)
        }
    }

    suspend fun setActiveProvider(id: String) {
        context.dataStore.edit {
            it[Keys.ACTIVE_PROVIDER_ID] = id
        }
    }

    suspend fun addAiProvider(provider: AiProviderConfig) {
        val providers = getAiProviders().toMutableList()
        providers.add(provider)
        saveAiProviders(providers)
    }

    suspend fun updateAiProvider(provider: AiProviderConfig) {
        val providers = getAiProviders().toMutableList()
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            providers[index] = provider
            saveAiProviders(providers)
        }
    }

    suspend fun deleteAiProvider(id: String) {
        val providers = getAiProviders().toMutableList()
        providers.removeAll { it.id == id }
        saveAiProviders(providers)
        val activeId = context.dataStore.data.first()[Keys.ACTIVE_PROVIDER_ID]
        if (activeId == id) {
            context.dataStore.edit {
                it[Keys.ACTIVE_PROVIDER_ID] = providers.firstOrNull()?.id ?: ""
            }
        }
    }

    suspend fun getActiveProviderId(): String {
        return context.dataStore.data.first()[Keys.ACTIVE_PROVIDER_ID] ?: ""
    }

    // ============================================================
    // 通用设置写入方法
    // ============================================================

    suspend fun updateColorTuple(tuple: ColorTuple) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ACTIVE_COLOR_TUPLE] = tuple.toSerializedString()
            preferences[Keys.SEED_COLOR] = tuple.primary.toArgb().toLong()
        }
    }

    suspend fun getCustomColorTuples(): List<ColorTuple> {
        val json = context.dataStore.data.first()[Keys.CUSTOM_COLOR_TUPLES] ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, type) ?: emptyList()
            list.mapNotNull { ColorTuple.fromSerializedString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addCustomColorTuple(tuple: ColorTuple) {
        val current = getCustomColorTuples().toMutableList()
        val serialized = tuple.toSerializedString()
        if (!current.any { it.toSerializedString() == serialized }) {
            current.add(tuple)
            val json = gson.toJson(current.map { it.toSerializedString() })
            context.dataStore.edit { it[Keys.CUSTOM_COLOR_TUPLES] = json }
        }
    }

    suspend fun removeCustomColorTuple(tuple: ColorTuple) {
        val serialized = tuple.toSerializedString()
        val current = getCustomColorTuples().filterNot { it.toSerializedString() == serialized }
        val json = gson.toJson(current.map { it.toSerializedString() })
        context.dataStore.edit { it[Keys.CUSTOM_COLOR_TUPLES] = json }
    }

    suspend fun updateSeedColor(color: Long) {
        context.dataStore.edit { it[Keys.SEED_COLOR] = color }
    }

    suspend fun updateDynamicColor(useDynamic: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = useDynamic }
    }

    suspend fun updateAmoledMode(isAmoled: Boolean) {
        context.dataStore.edit { it[Keys.IS_AMOLED] = isAmoled }
    }

    suspend fun updateThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun updateSimpleMode(isSimple: Boolean) {
        context.dataStore.edit { it[Keys.IS_SIMPLE_MODE] = isSimple }
    }

    suspend fun updateOcrMask(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_OCR_MASK] = show }
    }

    suspend fun updateMultiScan(enable: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_MULTI_SCAN] = enable }
    }

    suspend fun updateDiValidation(enable: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_DI_VALIDATION] = enable }
    }

    suspend fun updateUseOpenCV(use: Boolean) {
        context.dataStore.edit { it[Keys.USE_OPENCV] = use }
    }

    suspend fun updateSaveOriginalPhotoToGallery(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_ORIGINAL_PHOTO_TO_GALLERY] = enabled }
    }

    suspend fun updateScanFrameRatio(w: Float, h: Float) {
        context.dataStore.edit {
            it[Keys.SCAN_FRAME_RATIO_W] = w
            it[Keys.SCAN_FRAME_RATIO_H] = h
        }
    }

    suspend fun updateOcrFrameRatio(w: Float, h: Float) {
        context.dataStore.edit {
            it[Keys.OCR_FRAME_RATIO_W] = w
            it[Keys.OCR_FRAME_RATIO_H] = h
        }
    }

    suspend fun updateServerIp(ip: String) {
        context.dataStore.edit { it[Keys.SERVER_IP] = ip }
    }

    suspend fun saveServerIp(ip: String) {
        context.dataStore.edit { it[Keys.SERVER_IP] = ip }
    }

    suspend fun getServerIp(): String {
        return context.dataStore.data.map { it[Keys.SERVER_IP] ?: "" }.first()
    }

    suspend fun saveServerToken(token: String) {
        context.dataStore.edit { it[Keys.SERVER_TOKEN] = token }
    }

    suspend fun updateRealtimePushEnabled(enable: Boolean) {
        context.dataStore.edit { it[Keys.REALTIME_PUSH_ENABLED] = enable }
    }

    suspend fun updateInventoryPushEnabled(enable: Boolean) {
        context.dataStore.edit { it[Keys.INVENTORY_PUSH_ENABLED] = enable }
    }

    suspend fun updateAiShowThinking(enable: Boolean) {
        context.dataStore.edit { it[Keys.AI_SHOW_THINKING] = enable }
    }

    /** M1：首次启动生成设备唯一标识，永久保存（doc §2 设备身份）*/
    suspend fun ensureDeviceUuid(): String {
        val existing = context.dataStore.data.map { it[Keys.DEVICE_UUID] ?: "" }.first()
        if (existing.isNotBlank()) return existing
        val uuid = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_UUID] = uuid }
        return uuid
    }

    suspend fun getDeviceUuid(): String {
        return context.dataStore.data.map { it[Keys.DEVICE_UUID] ?: "" }.first()
    }

    suspend fun saveOperatorName(name: String) {
        context.dataStore.edit { it[Keys.OPERATOR_NAME] = name }
    }

    suspend fun getOperatorName(): String {
        return context.dataStore.data.map { it[Keys.OPERATOR_NAME] ?: "" }.first()
    }

    suspend fun getServerToken(): String {
        return context.dataStore.data.map { it[Keys.SERVER_TOKEN] ?: "" }.first()
    }

    suspend fun updateWatermarkText(text: String) {
        context.dataStore.edit { it[Keys.WATERMARK_TEXT] = text }
    }
    suspend fun updateWatermarkOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.WATERMARK_OPACITY] = opacity }
    }
    suspend fun updateWatermarkAngle(angle: Float) {
        context.dataStore.edit { it[Keys.WATERMARK_ANGLE] = angle }
    }
    suspend fun updateWatermarkFontSize(size: Float) {
        context.dataStore.edit { it[Keys.WATERMARK_FONT_SIZE] = size }
    }
    suspend fun updateWatermarkColor(color: Long) {
        context.dataStore.edit { it[Keys.WATERMARK_COLOR] = color }
    }
    suspend fun updateWatermarkDensity(density: Int) {
        context.dataStore.edit { it[Keys.WATERMARK_DENSITY] = density.coerceIn(1, 16) }
    }

    suspend fun updateSignatureOffsetX(x: Float) {
        context.dataStore.edit { it[Keys.SIGNATURE_OFFSET_X] = x.coerceIn(0f, 1f) }
    }
    suspend fun updateSignatureOffsetY(y: Float) {
        context.dataStore.edit { it[Keys.SIGNATURE_OFFSET_Y] = y.coerceIn(0f, 1f) }
    }
    suspend fun updateSignatureScale(scale: Float) {
        context.dataStore.edit { it[Keys.SIGNATURE_SCALE] = scale.coerceIn(0.05f, 0.9f) }
    }
    suspend fun updateSignatureOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.SIGNATURE_OPACITY] = opacity.coerceIn(0.1f, 1f) }
    }

    /** PDF 本地储存位置：写入 SAF tree URI；空字符串 = 清除（恢复每次弹系统选位器） */
    suspend fun updatePdfExportDirUri(uri: String) {
        context.dataStore.edit { it[Keys.PDF_EXPORT_DIR_URI] = uri }
    }

    suspend fun updateNearExpiryThreshold(days: Int) {
        context.dataStore.edit { it[Keys.NEAR_EXPIRY_THRESHOLD] = days }
    }

    suspend fun updateCalcRetainLastResult(retain: Boolean) {
        context.dataStore.edit { it[Keys.CALC_RETAIN_LAST_RESULT] = retain }
    }

    suspend fun updateHybiomeMode(enable: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_HYBIOME_MODE] = enable }
    }

    suspend fun updateSinkVerifiedItems(enable: Boolean) {
        context.dataStore.edit { it[Keys.SINK_VERIFIED_ITEMS] = enable }
    }

    suspend fun updateEmptySearchShowsNothing(enable: Boolean) {
        context.dataStore.edit { it[Keys.EMPTY_SEARCH_SHOWS_NOTHING] = enable }
    }

    suspend fun updateRecordCardConfig(config: RecordCardConfig) {
        context.dataStore.edit {
            it[Keys.CARD_FIELD_UDI] = config.udiField
            it[Keys.CARD_FIELD_BATCH] = config.batchField
            it[Keys.CARD_FIELD_EXPIRY] = config.expiryField
            it[Keys.CARD_FIELD_LOCATION] = config.locationField
            it[Keys.CARD_FIELD_MANUFACTURER] = config.manufacturerField
        }
    }

    // ---- M5：归档上报重试队列（离线缓存，App 启动/同步成功后补报）----
    private val PENDING_REPORTS = stringSetPreferencesKey("pending_task_reports")

    suspend fun addPendingReport(sessionUuid: String) {
        context.dataStore.edit { it[PENDING_REPORTS] = (it[PENDING_REPORTS] ?: emptySet()) + sessionUuid }
    }

    suspend fun getPendingReports(): Set<String> {
        return context.dataStore.data.map { it[PENDING_REPORTS] ?: emptySet() }.first()
    }

    suspend fun removePendingReport(sessionUuid: String) {
        context.dataStore.edit { it[PENDING_REPORTS] = (it[PENDING_REPORTS] ?: emptySet()) - sessionUuid }
    }
}
