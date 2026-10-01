package com.example.inventorymaster.ai.tools

enum class ToolPermission {
    AUTO,
    CONFIRM,
    BLOCKED
}

object ToolPermissionManager {

    private val confirmRequired = setOf(
        "execute_ui_action"
    )

    private val blockedTools = setOf<String>()

    fun check(toolName: String): ToolPermission = when {
        toolName in blockedTools -> ToolPermission.BLOCKED
        toolName in confirmRequired -> ToolPermission.CONFIRM
        else -> ToolPermission.AUTO
    }
}
