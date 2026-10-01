package com.example.inventorymaster.ai.tools

import com.google.gson.JsonObject

interface AiTool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(arguments: JsonObject): String
}
