package com.example.inventorymaster.ai.local

class ChatHistoryManager(
    private val maxContextTokens: Int = 3072,
    private val maxHistoryMessages: Int = 20
) {
    private val messages = mutableListOf<ChatMessage>()
    private var summary: String? = null

    data class ChatMessage(
        val role: String,
        val content: String
    )

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        trimToWindow()
    }

    fun getContext(): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        summary?.let {
            result.add(ChatMessage("system", "对话历史摘要：$it"))
        }
        result.addAll(messages)
        return result
    }

    private fun trimToWindow() {
        val estimatedTokens = estimateTokens()
        if (estimatedTokens <= maxContextTokens && messages.size <= maxHistoryMessages) return

        while (messages.size > maxHistoryMessages / 2) {
            val removed = messages.removeAt(0)
            if (summary == null) {
                summary = "之前的对话涉及：${removed.content.take(100)}..."
            }
        }
    }

    private fun estimateTokens(): Int {
        return messages.sumOf { estimateMessageTokens(it) }
    }

    private fun estimateMessageTokens(msg: ChatMessage): Int {
        val contentLength = msg.content.length
        return (contentLength * 1.2).toInt() + 4
    }

    fun clear() {
        messages.clear()
        summary = null
    }
}
