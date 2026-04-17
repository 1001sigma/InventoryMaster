package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.SessionWithProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    // 创建一个新的盘点任务，返回新生成的 ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InventorySession): Long

    // 查询所有盘点任务，按时间倒序排列 (最新的在最上面)
    // 注意：这里返回 Flow，意味着如果数据库变了，UI 会自动更新
    @Query("SELECT * FROM inventory_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<InventorySession>>

    // 根据 ID 获取单个任务
    @Query("SELECT * FROM inventory_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): InventorySession?

    // SessionDao.kt
    @Query("SELECT * FROM inventory_sessions WHERE uuid = :uuid LIMIT 1")
    suspend fun getSessionByUuid(uuid: String): InventorySession?

    // 更新任务状态 (例如锁定)
    @Update
    suspend fun updateSession(session: InventorySession)

    //[新增] 更新状态 (归档/锁定/解锁)
    @Query("UPDATE inventory_sessions SET status = :newStatus WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: Long, newStatus: Int)

    @Query("""
    SELECT 
        s.*,
        (SELECT COUNT(*) FROM stock_records WHERE sessionId = s.id) as totalCount,
        (SELECT COUNT(*) FROM stock_records WHERE sessionId = s.id AND remarks LIKE '%已查验%') as verifiedCount
    FROM inventory_sessions s
    ORDER BY s.date DESC
""")
    fun getAllSessionsWithProgress(): Flow<List<SessionWithProgress>>

    //[新增] 删除任务 (级联删除会在 Entity 定义 ForeignKey 时处理，或者手动删)
    @androidx.room.Delete
    suspend fun deleteSession(session: InventorySession)
}