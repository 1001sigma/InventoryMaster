package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.entity.StockRecordCombined
import kotlinx.coroutines.flow.Flow

@Dao
interface StockRecordDao {

    // --- 写入操作 (只负责存 ID 和 数量) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: StockRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<StockRecord>)

    @Update
    suspend fun updateRecord(record: StockRecord)

    @Delete
    suspend fun deleteRecord(record: StockRecord)

    @Query("DELETE FROM stock_records WHERE sessionId = :sessionId")
    suspend fun deleteRecordsBySession(sessionId: Long)

    // 🔥 新增：根据 UUID 查找本地记录
    // 如果找到了，返回的 StockRecord 里就包含本地的 ID
    @Query("SELECT * FROM stock_records WHERE uuid = :uuid LIMIT 1")
    suspend fun getRecordByUuid(uuid: String): StockRecord?


    // --- 事务操作 ---

    // Excel 导入覆盖逻辑：先清空该 Session 的记录，再插入新的
    @Transaction
    suspend fun overwriteSessionData(sessionId: Long, newRecords: List<StockRecord>) {
        deleteRecordsBySession(sessionId)
        insertRecords(newRecords)
    }

    // --- 查询操作 (核心升级) ---

    // 1. 获取列表：返回 Combined 对象，包含产品详情
    // Room 会自动根据 StockRecordCombined 中的 @Relation 去抓取 ProductBase
    @Transaction // 涉及多表查询，必须加 @Transaction
    @Query("SELECT * FROM stock_records WHERE sessionId = :sessionId ORDER BY id DESC")
    fun getRecordsBySession(sessionId: Long): Flow<List<StockRecordCombined>>

    // 2. 联合模糊搜索 (强力过滤)
    // 逻辑：连接两张表，只要库存表里的 DI/批号/库位 匹配，或者 产品表里的 名称/厂家/规格 匹配，都找出来
    @Transaction
    @Query("""
        SELECT stock_records.* FROM stock_records 
        LEFT JOIN product_base ON stock_records.di = product_base.di
        WHERE stock_records.sessionId = :sessionId 
        AND (
            stock_records.di LIKE '%' || :query || '%'          
            OR stock_records.batchNumber LIKE '%' || :query || '%'
            OR stock_records.location LIKE '%' || :query || '%'
            OR stock_records.remarks LIKE '%' || :query || '%'
            OR product_base.productName LIKE '%' || :query || '%' 
            OR product_base.manufacturer LIKE '%' || :query || '%'
            OR product_base.specification LIKE '%' || :query || '%'
            OR product_base.model LIKE '%' || :query || '%'
        )
    """)
    suspend fun searchRecordsByQuery(sessionId: Long, query: String): List<StockRecordCombined>

    // 3. UDI 精准搜索 (用于扫码枪)
    @Transaction
    @Query("""
        SELECT * FROM stock_records 
        WHERE sessionId = :sessionId 
        AND di = :di 
        AND batchNumber = :batch
    """)
    suspend fun getRecordsByUdi(sessionId: Long, di: String, batch: String): List<StockRecordCombined>

    //临时过度无UDI码 批号or效期
    @Transaction
    @Query("""
        SELECT * FROM stock_records 
        WHERE sessionId = :sessionId
        AND batchNumber = :batch
    """)
    suspend fun getRecordsBybatchorexpiryDate(sessionId: Long,batch: String): List<StockRecordCombined>

    // 4. DI 精准搜索 (用于只扫了码，没扫批号的情况)
    @Transaction
    @Query("""
        SELECT * FROM stock_records 
        WHERE sessionId = :sessionId 
        AND di = :di
    """)
    suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined>

    // 5. 统计未查验数量 (逻辑不变，但为了安全加个 Transaction)
    @Query("""
        SELECT COUNT(*) FROM stock_records 
        WHERE sessionId = :sessionId 
        AND (remarks IS NULL OR remarks NOT LIKE '%已查验%')
    """)
    suspend fun getUnverifiedCount(sessionId: Long): Int

    // 6. 导出专用 (和 getRecordsBySession 其实一样，只是为了命名语义区分)
    @Transaction
    @Query("SELECT * FROM stock_records WHERE sessionId = :sessionId")
    fun getExportData(sessionId: Long): List<StockRecordCombined>

    //[新增] 查重：根据 DI+批号+库位 找记录
    @Query("""
        SELECT * FROM stock_records 
        WHERE sessionId = :sessionId 
        AND di = :di 
        AND batchNumber = :batch 
        AND location = :location 
        LIMIT 1
    """)
    suspend fun findExistingRecord(sessionId: Long, di: String, batch: String, location: String): StockRecord?

    // 🔥 [新增] 1. 找出所有“待上传”的记录
    // 注意：这里用的是 sessionId (驼峰命名)，对应你表里的字段
    @Query("SELECT * FROM stock_records WHERE sessionId = :sessionId AND sync_status = 1")
    suspend fun getDirtyRecords(sessionId: Long): List<StockRecord>

    // 🔥 [新增] 2. 批量更新同步状态 (上传成功后调用)
    // 把指定 ID 的记录标记为“已同步 (0)”
    @Query("UPDATE stock_records SET sync_status = 0 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    // 3. 保存从服务器下载的数据 (防止死循环的关键！)
    // 逻辑：保存数据的同时，强制把 syncStatus 设为 0
    @Transaction
    suspend fun saveRemoteRecords(records: List<StockRecord>) {
        // 这一步在内存中操作：先把所有记录标记为“干净”，再存入数据库
        val cleanRecords = records.map { it.copy(syncStatus = 0) }
        insertRecords(cleanRecords)
    }

}
