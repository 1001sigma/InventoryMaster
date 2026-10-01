package com.example.inventorymaster.ai.tools

import com.example.inventorymaster.data.repository.InventoryRepository
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 列出盘存任务（v3 新增）：替代 v2 把全量任务列表塞进系统提示词的做法（§4.6），
 * 由模型按需调用，省 token 且不随任务数增长。
 */
class ListSessionsTool(
    private val repository: InventoryRepository
) : AiTool {

    override val name = "list_sessions"

    override val description =
        "列出系统中的盘存任务（任务ID/名称/创建日期/状态）。当用户提到的任务不是当前任务时，先用本工具获取任务ID，再带 session_id 查询。"

    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject())
    }

    override suspend fun execute(arguments: JsonObject): String {
        val sessions = repository.getAllSessionsOnce()
        if (sessions.isEmpty()) return "系统中暂无盘存任务。"

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sessions.take(MAX_SESSIONS).joinToString("\n") { session ->
            "任务ID: ${session.id} | 名称: ${session.name} | 创建日期: ${formatter.format(Date(session.date))} | 状态: ${statusText(session.status)}"
        } + if (sessions.size > MAX_SESSIONS) "\n[仅显示最近 $MAX_SESSIONS 个，共 ${sessions.size} 个]" else ""
    }

    private fun statusText(status: Int) = when (status) {
        0 -> "进行中"
        1 -> "已归档"
        2 -> "已锁定"
        else -> "未知"
    }

    companion object {
        private const val MAX_SESSIONS = 20
    }
}
