package com.example.inventorymaster.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.inventorymaster.data.dao.ProductDao
import com.example.inventorymaster.data.dao.SessionDao
import com.example.inventorymaster.data.dao.StockRecordDao
import com.example.inventorymaster.data.entity.InventorySession
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.data.entity.StockRecord

// 1. 声明包含的表和版本号
@Database(
    entities = [ProductBase::class, InventorySession::class, StockRecord::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // 2. 暴露 DAO 给外部使用
    abstract fun productDao(): ProductDao
    abstract fun sessionDao(): SessionDao
    abstract fun stockRecordDao(): StockRecordDao

    // 3. 单例模式 (Singleton) - 确保全应用只有一个数据库实例
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_master_db" // 数据库文件的名字
                )
                    // 开发阶段允许在主线程查询，防止闪退 (正式上线建议去掉)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration() // 如果改了表结构，直接清空重建 (开发阶段用)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}