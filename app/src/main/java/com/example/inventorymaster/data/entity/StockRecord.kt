package com.example.inventorymaster.data.entity

import android.os.Build
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.inventorymaster.data.model.StockRecordCombined

/**
 * L2 库存实物记录表 (账本表)
 * 只存储"变动"的数据 (批号、数量、位置)，静态数据通过 productKey 查 ProductBase
 */
@Entity(
    tableName = "stock_records",
    foreignKeys = [
        ForeignKey(
            entity = InventorySession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductBase::class,
            parentColumns = ["productKey"],
            childColumns = ["productKey"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sessionId", "productKey", "batchNumber", "location"], unique = false),
        Index(value = ["productKey"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class StockRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),

    val sessionId: Long,       // 所属盘点任务
    val productKey: String,    // 关联产品库 (替代旧 di 字段)

    val batchNumber: String,   // 批号 (10)
    val expiryDate: Long,      // 效期 (17)
    val productionDate: Long? = null, // 生产日期 (11)

    val quantity: Double,      // 数量
    val location: String,      // 库位

    val actualQuantity: Double? = null,     // 实际数量（初始为null）
    val remarks: String? = null,            // 备注 (存"已查验"等状态)
    val sourceType: Int = 1,    // 0=手动, 1=Excel导入, 2=云端下载

    @ColumnInfo(name = "operator")
    var operator: String = Build.MODEL,

    @ColumnInfo(name = "last_update_time")
    var lastUpdateTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    var syncStatus: Int = 0,

    @ColumnInfo(name = "history_log")
    var historyLog: String? = ""
)

// 1. 定义哪些字段需要高亮
enum class HighlightField {
    NONE, PRODUCT_NAME, DI, BATCH_NUMBER, LOCATION
}

// 2. 定义效期的三种状态
enum class ExpiryState {
    NORMAL, NEAR_EXPIRY, EXPIRED
}

// 3. 打包给 UI 用的最终数据模型
data class StockRecordUiModel(
    val combined: StockRecordCombined, // 原来的数据库组合数据
    val highlightField: HighlightField = HighlightField.NONE,
    val expiryState: ExpiryState = ExpiryState.NORMAL
)
