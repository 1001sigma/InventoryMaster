package com.example.inventorymaster.ai.memory

import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall

/**
 * 轮次（Turn）制对话记忆。
 *
 * 裁剪只在轮边界进行，绝不拆散 assistant(tool_calls) 与 tool 结果的配对——
 * v2 的头部移除裁剪（removeAt(0)）会把配对拦腰截断，导致 OpenAI 兼容接口
 * 直接 400 拒收整段对话（见 docs/AI_V3_REDESIGN.md BUG-1）。
 */
class ConversationMemory {

    /** 一轮 = 用户消息 + 0..N 组(assistant 工具调用 + tool 结果) + 最终 assistant 回复 */
    private class Turn {
        val messages = mutableListOf<AiMessage>()
        var open = false
        fun totalChars(): Int = messages.sumOf { it.content?.length ?: 0 }
    }

    private val turns = ArrayDeque<Turn>()
    private var openTurn: Turn? = null

    /** 开启新一轮并写入用户消息（Agent 每次收到用户输入时首先调用） */
    fun openUserTurn(content: String) {
        val turn = Turn().apply {
            messages.add(AiMessage("user", content))
            open = true
        }
        turns.addLast(turn)
        openTurn = turn
        trimCompletedTurns()
    }

    /** 轮内追加 assistant 工具调用消息 */
    fun appendAssistantToolCalls(content: String?, toolCalls: List<AiToolCall>) {
        val turn = openTurn ?: return
        turn.messages.add(AiMessage(role = "assistant", content = content, toolCalls = toolCalls))
    }

    /** 轮内追加 assistant 纯文本（如 JSON 模式的格式纠正流程） */
    fun appendAssistantMessage(content: String) {
        val turn = openTurn ?: return
        turn.messages.add(AiMessage("assistant", content))
    }

    /** 轮内追加用户消息（如格式纠正提示） */
    fun appendUserMessage(content: String) {
        val turn = openTurn ?: return
        turn.messages.add(AiMessage("user", content))
    }

    /** 轮内追加工具结果；超长结果截断存储并注明 */
    fun appendToolResult(toolCallId: String, toolName: String, result: String) {
        val turn = openTurn ?: return
        val content = if (result.length > MAX_TOOL_RESULT_CHARS) {
            result.take(MAX_TOOL_RESULT_CHARS) + "\n[已截断，原始长度 ${result.length} 字符]"
        } else {
            result
        }
        turn.messages.add(AiMessage(role = "tool", content = content, toolCallId = toolCallId))
    }

    /**
     * 写入最终 assistant 回复并关闭本轮。
     * 也用于异常/取消/超时兜底——即使轮次未走完，也补一条 assistant 说明后关闭，
     * 保证导出的消息序列永远合法。
     */
    fun closeTurn(reply: String) {
        val turn = openTurn
        if (turn != null) {
            turn.messages.add(AiMessage("assistant", reply))
            turn.open = false
            openTurn = null
        } else {
            // 无开启中的轮次（如请求发出前就失败）：独立成一条 assistant 消息
            turns.addLast(Turn().apply { messages.add(AiMessage("assistant", reply)) })
        }
        trimCompletedTurns()
    }

    /** 导出发送给 LLM 的完整消息序列（含进行中的轮次） */
    fun messagesForRequest(): List<AiMessage> = turns.flatMap { it.messages }

    /**
     * 从持久化快照恢复（聊天历史跨进程存活）。
     * 按消息序列重建轮次结构：user 开轮、assistant(tool_calls)/tool 配对追加、
     * assistant 纯文本收轮（与 closeTurn 语义一致，异常兜底文案同样收轮）。
     */
    fun restoreFrom(messages: List<AiMessage>) {
        clear()
        for (m in messages) {
            when {
                m.role == "user" -> openUserTurn(m.content ?: "")
                m.role == "tool" -> appendToolResult(m.toolCallId ?: "", "", m.content ?: "")
                m.role == "assistant" && m.toolCalls != null -> appendAssistantToolCalls(m.content, m.toolCalls)
                m.role == "assistant" -> closeTurn(m.content ?: "")
            }
        }
    }

    fun clear() {
        turns.clear()
        openTurn = null
    }

    private fun trimCompletedTurns() {
        while (turns.size > MAX_TURNS) {
            val oldest = turns.firstOrNull() ?: break
            if (oldest.open) break
            turns.removeFirst()
        }
        while (totalChars() > SOFT_CHAR_BUDGET && turns.size > MIN_TURNS) {
            val oldest = turns.firstOrNull() ?: break
            if (oldest.open) break
            turns.removeFirst()
        }
    }

    private fun totalChars(): Int = turns.sumOf { it.totalChars() }

    companion object {
        private const val MAX_TURNS = 10
        private const val MIN_TURNS = 2
        private const val SOFT_CHAR_BUDGET = 24_000
        // 工具结果单条上限。2000 会把查询结果 JSON 拦腰截断，模型看不到完整数据
        // 会反复重查（实机证据：35 条结果约 9KB 被截成 2019 字符触发连调闸）
        private const val MAX_TOOL_RESULT_CHARS = 8_000
    }
}
