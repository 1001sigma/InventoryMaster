package com.example.inventorymaster.ocr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OcrProvider {
    lateinit var textRecognizer: TextRecognizer
        private set
    lateinit var tokenizer: TextTokenizer
        private set
    lateinit var dictionaryManager: DictionaryManager
        private set

    fun init(context: Context) {
        textRecognizer = MlKitTextRecognizer()
        tokenizer = JiebaTokenizer()
        dictionaryManager = JiebaDictionaryManager()
        CoroutineScope(Dispatchers.IO).launch {
            dictionaryManager.init(context)
        }
    }
}
