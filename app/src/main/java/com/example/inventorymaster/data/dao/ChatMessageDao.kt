package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.inventorymaster.data.entity.ChatMessageEntity

/** 历史会话摘要（历史会话栏用） */
data class ChatThreadSummary(val threadId: String, val lastTime: Long)

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
}
