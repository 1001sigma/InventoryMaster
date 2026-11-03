package com.example.inventorymaster.ai.memory

import com.example.inventorymaster.ai.provider.AiMessage
import com.example.inventorymaster.ai.provider.AiToolCall
import com.example.inventorymaster.data.entity.ChatMessageEntity
import com.google.gson.Gson

/**
 * 聊天线程编解码（纯函数，可单测）：memory 的消息序列 ↔ Room 行。
 * toolCalls 以 JSON 序列化存进 toolCallsJson，恢复时原样还原（配对不丢）。
 */
object ChatThreadCodec {

    private val gson = Gson()

    fun toRows(threadId: String, messages: List<AiMessage>, now: Long = System.currentTimeMillis()): List<ChatMessageEntity> =
        messages.mapIndexed { index, m ->
            ChatMessageEntity(
                threadId = threadId,
                sortIndex = index,
                role = m.role,
                content = m.content,
                toolCallId = m.toolCallId,
                toolCallsJson = m.toolCalls?.let { gson.toJson(it) },
                createdAt = now,
                durationMs = m.durationMs,
                model = m.model,
                cardActionJson = m.cardActionJson
            )
        }

    fun toMessages(rows: List<ChatMessageEntity>): List<AiMessage> = rows.map { row ->
        AiMessage(
            role = row.role,
            content = row.content,
            toolCalls = row.toolCallsJson?.let { json ->
                gson.fromJson(json, Array<AiToolCall>::class.java).toList().takeIf { it.isNotEmpty() }
            },
            toolCallId = row.toolCallId,
            durationMs = row.durationMs,
            model = row.model,
            cardActionJson = row.cardActionJson
        )
    }
}
