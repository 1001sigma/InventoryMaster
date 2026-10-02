package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 聊天消息持久化（v3 二期，聊天历史跨进程存活）。
 *
 * 线程隔离：`threadId` = 任务ID字符串（任务内对话）或 "global"（无任务全局对话）；
 * 每个线程保存 [com.example.inventorymaster.ai.memory.ConversationMemory.messagesForRequest]
 * 的完整快照（含 assistant(tool_calls) 与 tool 结果的配对），每轮结束整线程重写。
 */
@Entity(tableName = "chat_messages", indices = [Index("threadId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val sortIndex: Int,
    val role: String,
    val content: String?,
    val toolCallId: String?,
    val toolCallsJson: String?,
    val createdAt: Long
)
