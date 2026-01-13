package com.example.inventorymaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.inventorymaster.data.entity.ProductBase

@Dao
interface ProductDao {
    // 1. 插入或更新产品资料 (如果 DI 已存在，则覆盖更新)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductBase)

    // 2. 批量插入 (用于 Excel 导入)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductBase>)

    // 3. 根据 DI 查找产品 (扫码时用)
    @Query("SELECT * FROM product_base WHERE di = :di")
    suspend fun getProductByDi(di: String): ProductBase?



    // 4. 模糊搜索产品库 (比如你想手动查找某个产品)
    @Query("""
        SELECT * FROM product_base 
        WHERE productName LIKE '%' || :query || '%' 
        OR manufacturer LIKE '%' || :query || '%' 
        OR di LIKE '%' || :query || '%'
    """)
    suspend fun searchProducts(query: String): List<ProductBase>

//    1. [新增] 用于比对：根据 DI 查单个产品
//    @Query("SELECT * FROM product_base WHERE di = :di LIMIT 1")
//    suspend fun getProductByDione(di: String): ProductBase?

    // 2. [新增] 安全更新：用于覆盖旧产品
    // ⚠️ 绝对不能用 REPLACE，否则有盘点记录时会闪退！
    @Update
    suspend fun updateProduct(product: ProductBase)
}