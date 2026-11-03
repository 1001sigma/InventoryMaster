package com.example.inventorymaster.ocr

import android.content.Context
import android.net.Uri

interface DictionaryManager {
    suspend fun init(context: Context)
    suspend fun updateDict(context: Context, uri: Uri)
}
