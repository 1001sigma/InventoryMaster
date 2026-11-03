package com.example.inventorymaster.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.inventorymaster.data.dao.ChatMessageDao
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.entity.ChatMessageEntity
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord

// 1. 声明包含的表和版本号
@Database(
    entities = [ProductBase::class, InventorySession::class, StockRecord::class, ChatMessageEntity::class],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // 2. 暴露 DAO 给外部使用
    abstract fun productDao(): ProductDao
    abstract fun sessionDao(): SessionDao
    abstract fun stockRecordDao(): StockRecordDao
    abstract fun chatMessageDao(): ChatMessageDao

    // 3. 单例模式 (Singleton) - 确保全应用只有一个数据库实例
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v5→v6：inventory_sessions 新增 startedAt 列（服务端任务"待开始"状态机）
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_sessions ADD COLUMN startedAt INTEGER")
            }
        }

        // v6→v7：AI 聊天消息持久化表（按线程隔离，threadId = 任务ID 或 "global"）
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`threadId` TEXT NOT NULL, " +
                        "`sortIndex` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT, " +
                        "`toolCallId` TEXT, " +
                        "`toolCallsJson` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` ON `chat_messages` (`threadId`)")
            }
        }

        // v7→v8：chat_messages 新增 durationMs 列（AI 回答耗时展示，随快照持久化）
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN durationMs INTEGER")
            }
        }

        // v8→v9：chat_messages 新增 model 列（生成该回复的模型 ID，随快照持久化）
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN model TEXT")
            }
        }

        // v9→v10：chat_messages 新增 cardActionJson 列（产品卡片随消息持久化，退出重进可回显）
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN cardActionJson TEXT")
            }
        }

        // v10→v11：product_base 为 di 和 materialCode 新增二级索引，加速反查与去重
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_product_base_di` ON `product_base` (`di`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_product_base_material_code` ON `product_base` (`materialCode`)")
            }
        }

        // v11→v12：stock_records 新增 is_verified 列（正式查验状态字段，0=未查验, 1=已查验），
        // 并将存量备注包含"已查验"的历史数据平滑迁移为已查验状态
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stock_records ADD COLUMN is_verified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE stock_records SET is_verified = 1 WHERE remarks LIKE '%已查验%'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_records_sessionId_is_verified` ON `stock_records` (`sessionId`, `is_verified`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_master_db" // 数据库文件的名字
                )
                    // 开发阶段允许在主线程查询，防止闪退 (正式上线建议去掉)
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12) // 已定义路径走正式迁移，保住用户数据
                    .fallbackToDestructiveMigration() // 如果改了表结构，直接清空重建 (开发阶段用)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}