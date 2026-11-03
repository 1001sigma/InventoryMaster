package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.example.inventorymaster.data.entity.ChatMessageEntity

/** 历史会话摘要（历史会话栏用） */
data class ChatThreadSummary(val threadId: String, val lastTime: Long)

/** 各线程首条用户消息（会话标题命名用） */
data class ChatThreadFirstMessage(val threadId: String, val content: String?)

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY sortIndex ASC")
    suspend fun getMessages(threadId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT threadId, MAX(createdAt) AS lastTime FROM chat_messages GROUP BY threadId ORDER BY lastTime DESC")
    suspend fun getThreadSummaries(): List<ChatThreadSummary>

    // SQLite 裸列约定：GROUP BY + MIN() 聚合时，content 取自命中 MIN(sortIndex) 的那一行
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT threadId, content, MIN(sortIndex) FROM chat_messages WHERE role = 'user' GROUP BY threadId")
    suspend fun getFirstUserMessages(): List<ChatThreadFirstMessage>
}
