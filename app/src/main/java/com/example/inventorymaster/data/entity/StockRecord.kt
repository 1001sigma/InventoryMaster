package com.example.inventorymaster.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * L2 库存实物记录表 (账本表)
 * 只存储“变动”的数据 (批号、数量、位置)，静态数据通过 di 查 ProductBase
 */
@Entity(
    tableName = "stock_records",
    foreignKeys = [
        // 级联删除：删任务 -> 删记录
        ForeignKey(
            entity = InventorySession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        // 级联删除：删产品 -> 删记录 (慎用，通常产品库不删)
        // 注意：如果你希望删了 ProductBase 里的某个产品，连带把盘点记录删了，就保留这个。
        // 如果希望保留历史记录，这里可以改为 NO_ACTION 或 SET_NULL (需要 di 可空)
        ForeignKey(
            entity = ProductBase::class,
            parentColumns = ["di"],
            childColumns = ["di"],
            onDelete = ForeignKey.RESTRICT // 建议改成 RESTRICT，防止误删字典导致账本坏死
        )
    ],
    // 联合索引：加速查询，同时防止同一位置重复录入完全一样的数据
    indices = [
        Index(value = ["sessionId", "di", "batchNumber", "location"], unique = false),
        Index(value = ["di"]),
        Index(value = ["uuid"], unique = true)],

)
data class StockRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),

    val sessionId: Long,      // 所属盘点任务
    val di: String,           // 关联产品库

    val batchNumber: String,  // 批号 (10)
    val expiryDate: Long,     // 效期 (17)
    val productionDate: Long? = null, // 生产日期 (11) - NMPA 有时会有

    val quantity: Double,     // 数量
    val location: String,     // 库位

    val actualQuantity: Double? = null,     //实际数量（初始为null）
    val remarks: String? = null, // 备注 (存“已查验”等状态)
    val sourceType: Int = 0,    // 0=手动, 1=Excel导入, 2=扫码

    // 1. 操作人 (必填，用于区分是谁改的)
    @ColumnInfo(name = "operator")
    var operator: String = "",

    // 2. 最后修改时间 (必填，用于版本对比)
    @ColumnInfo(name = "last_update_time")
    var lastUpdateTime: Long = System.currentTimeMillis(),

    // 3. 同步状态 (必填，用于断网续传) 0=已同步, 1=待上传
    @ColumnInfo(name = "sync_status")
    var syncStatus: Int = 0,

    // 4. 修改历史记录 (选填，用于本地展示详情)
    @ColumnInfo(name = "history_log")
    var historyLog: String? = ""
)