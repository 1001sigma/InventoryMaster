package com.example.inventorymaster.batchscanner.ocr

import com.example.inventorymaster.ai.provider.AiService
import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.batchscanner.ocr.models.ExtractSource
import com.example.inventorymaster.batchscanner.ocr.models.OcrExtractedData
import com.example.inventorymaster.utils.JsonExtractor
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OCR 文本中转处理器：提取批号和日期。
 * Phase 1: 规则引擎（关键字上下文匹配 + 退化顺序）
 * Phase 2: AI 降级（可选，withTimeout 8s）
 * 最后经 OcrNormalizer 标准化。
 */
object OcrInfoExtractor {

    // ---- 批号匹配：List<Regex>，按优先级依次尝试 ----
    private val batchPatterns = listOf(
        Regex("""(?:LOT|Lot|批号|批|生产批号|LOT\s*NO\.?|Lot\s*No\.?)[：:\s]*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:SN|Serial\s*No)[：:\s]*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:REF|Cat\.?\s*No)[：:\s]*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE),
    )

    // ---- 生产日期：关键字上下文匹配 ----
    private val prodDatePatterns = listOf(
        Regex("""(?:MFG|生产日期|制造日期|Manufacture\s*Date|MFD|MFR|生产|制造)[：:\s]*(\d{4}[-/\.]\d{1,2}[-/\.]\d{1,2}|\d{6,8})""", RegexOption.IGNORE_CASE),
    )

    // ---- 有效期：关键字上下文匹配 ----
    private val expDatePatterns = listOf(
        Regex("""(?:EXP|有效期|效期|Use\s*By|Expiry|EXPIRY|Best\s*Before|有效|过期)[：:\s]*(\d{4}[-/\.]\d{1,2}[-/\.]\d{1,2}|\d{6,8})""", RegexOption.IGNORE_CASE),
    )

    // ---- 通用日期：退化用 ----
    private val dateRegex = Regex("""(\d{4}[-/\.]\d{1,2}[-/\.]\d{1,2}|\d{6,8})""")

    /** 主入口：规则引擎优先，失败降级 AI，最后标准化 */
    suspend fun extract(
        ocrText: String,
        aiService: AiService? = null
    ): OcrExtractedData {
        val ruleResult = extractByRules(ocrText)

        // AI 降级：规则引擎未提取到批号或日期，且 AI 可用
        if (aiService != null && (ruleResult.batch.isBlank() || ruleResult.prodDate == null || ruleResult.expDate == null)) {
            val aiResult = extractByAi(ocrText, aiService)
            val merged = OcrExtractedData(
                batch = ruleResult.batch.ifBlank { aiResult.batch },
                prodDate = ruleResult.prodDate ?: aiResult.prodDate,
                expDate = ruleResult.expDate ?: aiResult.expDate,
                rawText = ocrText,
                confidence = if (aiResult.batch.isNotBlank() || aiResult.prodDate != null) 0.6f else 0.5f,
                source = if (aiResult.batch.isNotBlank() || aiResult.prodDate != null) ExtractSource.AI else ExtractSource.RULE
            )
            return normalize(merged)
        }

        return normalize(ruleResult)
    }

    /** 规则引擎提取：关键字上下文优先，退化为顺序取值 */
    private fun extractByRules(text: String): OcrExtractedData {
        // 1. 批号：List<Regex> 依次匹配
        val batch = batchPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)
        } ?: ""

        // 2. 生产日期：关键字上下文匹配
        val prodDateRaw = prodDatePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)
        }

        // 3. 有效期：关键字上下文匹配
        val expDateRaw = expDatePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)
        }

        // 4. 退化逻辑：找不到关键字时按顺序取
        val fallbackDates = dateRegex.findAll(text).map { it.value }.toList()
        val prodDate = prodDateRaw ?: fallbackDates.getOrNull(0)
        val expDate = expDateRaw ?: fallbackDates.getOrNull(1)

        return OcrExtractedData(
            batch = batch,
            prodDate = prodDate,
            expDate = expDate,
            rawText = text,
            confidence = 0.5f,
            source = ExtractSource.RULE
        )
    }

    /** AI 智能抽取：withTimeout 8s，失败返回空结果 */
    private suspend fun extractByAi(text: String, aiService: AiService): OcrExtractedData {
        val prompt = buildString {
            appendLine("你是医疗器械行业数据提取专家。")
            appendLine("请从以下 OCR 文本中提取结构化数据。")
            appendLine()
            appendLine("OCR 文本：$text")
            appendLine()
            appendLine("请提取以下字段（没有则留空）：")
            appendLine("- batch: 批号")
            appendLine("- prodDate: 生产日期")
            appendLine("- expDate: 有效期/效期")
            appendLine()
            appendLine("请以 JSON 格式返回：")
            appendLine("{\"batch\":\"\",\"prodDate\":\"\",\"expDate\":\"\"}")
        }

        return withTimeoutOrNull(8000L) {
            try {
                val response = aiService.chat(
                    messages = listOf(AiMessage("user", prompt)),
                    systemPrompt = "你是医疗器械数据提取专家，只输出JSON。",
                    tools = null
                )
                val json = JsonExtractor.extractJsonObject(response.content)
                if (json != null) {
                    OcrExtractedData(
                        batch = json.get("batch")?.asString ?: "",
                        prodDate = json.get("prodDate")?.asString,
                        expDate = json.get("expDate")?.asString,
                        rawText = text,
                        confidence = 0.6f,
                        source = ExtractSource.AI
                    )
                } else {
                    OcrExtractedData(rawText = text, confidence = 0f)
                }
            } catch (e: Exception) {
                OcrExtractedData(rawText = text, confidence = 0f)
            }
        } ?: OcrExtractedData(rawText = text, confidence = 0f)
    }

    /** 标准化：日期 → yyyy-MM-dd，批号 → 去空格/全角转半角 */
    private fun normalize(data: OcrExtractedData): OcrExtractedData {
        return data.copy(
            batch = OcrNormalizer.normalizeBatch(data.batch) ?: data.batch,
            prodDate = OcrNormalizer.normalizeDate(data.prodDate),
            expDate = OcrNormalizer.normalizeDate(data.expDate)
        )
    }
}
