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
    version = 7,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_master_db" // 数据库文件的名字
                )
                    // 开发阶段允许在主线程查询，防止闪退 (正式上线建议去掉)
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7) // 已定义路径走正式迁移，保住用户数据
                    .fallbackToDestructiveMigration() // 如果改了表结构，直接清空重建 (开发阶段用)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}