package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.inventorymaster.data.entity.StockRecord
import com.example.inventorymaster.data.model.StockRecordCombined
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

    @Query("SELECT * FROM stock_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): StockRecord?


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

    // 2. 联合模糊搜索
    @Transaction
    @Query("""
        SELECT stock_records.* FROM stock_records
        LEFT JOIN product_base ON stock_records.productKey = product_base.productKey
        WHERE stock_records.sessionId = :sessionId
        AND (
            product_base.di LIKE '%' || :query || '%'
            OR stock_records.batchNumber LIKE '%' || :query || '%'
            OR stock_records.location LIKE '%' || :query || '%'
            OR stock_records.remarks LIKE '%' || :query || '%'
            OR product_base.productName LIKE '%' || :query || '%'
            OR product_base.manufacturer LIKE '%' || :query || '%'
            OR product_base.specification LIKE '%' || :query || '%'
            OR product_base.model LIKE '%' || :query || '%'
            OR product_base.materialCode LIKE '%' || :query || '%'
        )
        ORDER BY stock_records.id ASC
    """)
    suspend fun searchRecordsByQuery(sessionId: Long, query: String): List<StockRecordCombined>

    // 3. productKey + 批号精准搜索 (用于扫码枪)
    @Transaction
    @Query("""
        SELECT * FROM stock_records
        WHERE sessionId = :sessionId
        AND productKey = :productKey
        AND batchNumber = :batch
        ORDER BY id ASC
    """)
    suspend fun getRecordsByUdi(sessionId: Long, productKey: String, batch: String): List<StockRecordCombined>

    //临时过度无UDI码 批号or效期
    @Transaction
    @Query("""
        SELECT * FROM stock_records
        WHERE sessionId = :sessionId
        AND batchNumber = :batch
        ORDER BY id ASC
    """)
    suspend fun getRecordsBybatchorexpiryDate(sessionId: Long,batch: String): List<StockRecordCombined>

    // 4. productKey 精准搜索 (只扫了码，没扫批号)
    @Transaction
    @Query("""
        SELECT * FROM stock_records
        WHERE sessionId = :sessionId
        AND productKey = :productKey
        ORDER BY id ASC
    """)
    suspend fun getRecordsByKey(sessionId: Long, productKey: String): List<StockRecordCombined>

    // 4a. 按 DI 搜索（组合键下同 DI 可能对应多个产品，JOIN 产品表把它们的记录全部查出）
    @Transaction
    @Query("""
        SELECT stock_records.* FROM stock_records
        INNER JOIN product_base ON stock_records.productKey = product_base.productKey
        WHERE stock_records.sessionId = :sessionId
        AND product_base.di = :di
        ORDER BY stock_records.id ASC
    """)
    suspend fun getRecordsByDi(sessionId: Long, di: String): List<StockRecordCombined>

    // 4b. 按 DI + 批号搜索（扫码带批号场景）
    @Transaction
    @Query("""
        SELECT stock_records.* FROM stock_records
        INNER JOIN product_base ON stock_records.productKey = product_base.productKey
        WHERE stock_records.sessionId = :sessionId
        AND product_base.di = :di
        AND stock_records.batchNumber = :batch
        ORDER BY stock_records.id ASC
    """)
    suspend fun getRecordsByDiAndBatch(sessionId: Long, di: String, batch: String): List<StockRecordCombined>

    // 4c. 非扫码搜索：组合键 / DI / 物料编码 任一精确命中
    @Transaction
    @Query("""
        SELECT stock_records.* FROM stock_records
        LEFT JOIN product_base ON stock_records.productKey = product_base.productKey
        WHERE stock_records.sessionId = :sessionId
        AND (
            stock_records.productKey = :query
            OR product_base.di = :query
            OR product_base.materialCode = :query
        )
        ORDER BY stock_records.id ASC
    """)
    suspend fun getRecordsByKeyOrDiOrMat(sessionId: Long, query: String): List<StockRecordCombined>

    // 跨任务引用计数（删除产品保护用）
    @Query("SELECT COUNT(*) FROM stock_records WHERE productKey = :productKey")
    suspend fun countRecordsByKey(productKey: String): Int

    // 5. 统计未查验数量 (逻辑不变，但为了安全加个 Transaction)
    @Query("""
        SELECT COUNT(*) FROM stock_records 
        WHERE sessionId = :sessionId 
        AND (is_verified = 0 AND actualQuantity IS NULL)
    """)
    suspend fun getUnverifiedCount(sessionId: Long): Int

    // 6. 导出专用 (和 getRecordsBySession 其实一样，只是为了命名语义区分)
    @Transaction
    @Query("SELECT * FROM stock_records WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getExportData(sessionId: Long): List<StockRecordCombined>

    // 查重：根据 productKey+批号+库位 找记录
    @Query("""
        SELECT * FROM stock_records
        WHERE sessionId = :sessionId
        AND productKey = :productKey
        AND batchNumber = :batch
        AND location = :location
        LIMIT 1
    """)
    suspend fun findExistingRecord(sessionId: Long, productKey: String, batch: String, location: String): StockRecord?

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

    @Query("UPDATE stock_records SET productKey = :newKey WHERE productKey = :oldKey")
    suspend fun updateProductKeyForRecords(oldKey: String, newKey: String)

    @Query("DELETE FROM stock_records WHERE uuid IN (:uuids)")
    suspend fun deleteRecordsByUuids(uuids: List<String>)

    @Query("SELECT uuid FROM stock_records WHERE sessionId = :sessionId")
    suspend fun getRecordUuidsBySession(sessionId: Long): List<String>

}
