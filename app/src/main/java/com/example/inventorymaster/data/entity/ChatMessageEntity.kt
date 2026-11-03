package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 聊天消息持久化（v3 二期，聊天历史跨进程存活）。
 *
 * 线程隔离：`threadId` = `t<任务ID>_<时间戳>`（任务会话，一个任务可多条）或 `g_<时间戳>`（自由会话；
 * 旧 "global" 与纯数字任务ID为历史格式，按自由会话/任务会话兼容解析，不迁移）；
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
    val createdAt: Long,
    /** assistant 最终回复的整轮耗时（毫秒，UI 操作条展示）；null=未统计/旧数据 */
    val durationMs: Long? = null,
    /** 生成该回复的模型 ID（UI 操作条展示）；null=未统计/旧数据 */
    val model: String? = null,
    /** 产品卡片持久化（编解码见 ai/agent/ProductCardListCodec）；null=无卡片/旧数据。仅供退出重进后回显，绝不进模型提示词 */
    val cardActionJson: String? = null
)
