package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.inventorymaster.data.entity.ChatMessageEntity

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY sortIndex ASC")
    suspend fun getMessages(threadId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)
}
