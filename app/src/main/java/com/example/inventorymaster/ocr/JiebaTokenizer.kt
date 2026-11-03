package com.example.inventorymaster.ocr

import com.huaban.analysis.jieba.JiebaSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JiebaTokenizer : TextTokenizer {
    private val segmenter by lazy { JiebaSegmenter() }
    private val SIMPLE_SPLIT_REGEX = Regex("""[\s,，.。;；:：!！?？、|()\[\]{}<>"'`~]+""")

    override suspend fun cut(text: String, simple: Boolean): List<String> = withContext(Dispatchers.Default) {
        if (simple) {
            text.split(SIMPLE_SPLIT_REGEX)
                .filter { it.isNotBlank() }
                .map { it.trim() }
        } else {
            try {
                segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
                    .map { it.word.trim() }
                    .filter { it.length > 1 }
                    .distinct()
            } catch (e: Exception) {
                e.printStackTrace()
                text.split(" ").filter { it.isNotBlank() }
            }
        }
    }
}
