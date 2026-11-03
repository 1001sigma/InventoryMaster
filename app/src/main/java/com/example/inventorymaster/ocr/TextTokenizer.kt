package com.example.inventorymaster.ocr

interface TextTokenizer {
    suspend fun cut(text: String, simple: Boolean): List<String>
}
