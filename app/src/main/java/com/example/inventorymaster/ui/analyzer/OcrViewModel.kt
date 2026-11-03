package com.example.inventorymaster.ui.analyzer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.SettingsRepository
import com.example.inventorymaster.ocr.OcrProvider
import com.example.inventorymaster.ocr.OcrResult
import com.example.inventorymaster.utils.downsample
import com.example.inventorymaster.utils.toBitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OcrViewModel(application: Application) : AndroidViewModel(application) {
    private val recognizer get() = OcrProvider.textRecognizer
    private val tokenizer get() = OcrProvider.tokenizer
    private val dictManager get() = OcrProvider.dictionaryManager

    private val _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocrResult

    private val _tokenizedWords = MutableStateFlow<List<String>>(emptyList())
    val tokenizedWords: StateFlow<List<String>> = _tokenizedWords

    fun recognizeFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val safeBitmap = bitmap.downsample()
            val result = recognizer.recognize(safeBitmap)
            _ocrResult.value = result
            if (result is OcrResult.Success) {
                tokenize(result.ocrText.text)
            }
        }
    }

    fun recognizeFromUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val bitmap = uri.toBitmap(context)?.downsample()
            if (bitmap == null) {
                _ocrResult.value = OcrResult.Error("无法读取图片")
                return@launch
            }
            val result = recognizer.recognize(bitmap)
            _ocrResult.value = result
            if (result is OcrResult.Success) {
                tokenize(result.ocrText.text)
            }
        }
    }

    private suspend fun tokenize(text: String) {
        val simple = SettingsRepository(getApplication()).settingsFlow.first().isSimpleMode
        val words = tokenizer.cut(text, simple)
        _tokenizedWords.value = words
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>(replay = 0)
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    sealed class UiEvent {
        data object DictImportSuccess : UiEvent()
        data class DictImportError(val message: String) : UiEvent()
    }

    fun importDictionary(uri: Uri) {
        viewModelScope.launch {
            try {
                dictManager.updateDict(getApplication(), uri)
                _uiEvent.emit(UiEvent.DictImportSuccess)
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.DictImportError(e.message ?: "导入失败"))
            }
        }
    }

    fun clearResult() {
        _ocrResult.value = null
        _tokenizedWords.value = emptyList()
    }
}
