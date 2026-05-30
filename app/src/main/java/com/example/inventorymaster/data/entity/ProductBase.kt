package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * L1 基础产品库 (字典表)
 * productKey 为统一关联键：有 DI 时 = DI，无 DI 时 = "MCODE:" + materialCode
 * di 字段只存真实 GTIN，不再被污染。
 */
@Entity(tableName = "product_base")
data class ProductBase(
    // --- 核心主键 ---
    @PrimaryKey
    val productKey: String,        // 统一关联键：di 不为空时=di，否则="MCODE:"+materialCode

    // --- NMPA 标准字段 ---
    val di: String? = null,        // GTIN (01) 全球贸易项目代码，仅存真实 DI，可为空
    val productName: String,       // 通用名称 (对应 NMPA: CPMC)
    val specification: String?,    // 规格 (对应 NMPA: GGXH - 规格)
    val model: String?,            // 型号 (对应 NMPA: GGXH - 型号)
    val manufacturer: String,      // 生产企业/注册人名称 (对应 NMPA: QYMC)
    val registrationCert: String?, // 注册证编号 (对应 NMPA: ZCZ/BAZ)

    // --- 辅助业务字段 ---
    val materialCode: String?,     // 院内/企业内部物料编码 (用于对接 ERP)
    val unit: String?,             // 最小销售单元 (如：盒、支)
    val categoryCode: String?,     // 分类编码 (如 68xx)

    // --- 数据维护字段 ---
    val lastSyncTime: Long = 0,     // 上次同步/更新时间
    val source: String = "local"    // 数据来源: "local"(手动/Excel导入), "nmpa"(国家库), "json"(json导入)
) {
    companion object {
        /**
         * 生成产品统一关联键
         * @param di GTIN 码，可为空
         * @param materialCode 物料编码，可为空
         * @return 关联键：有 DI 返回 DI，否则返回 "MCODE:" + materialCode
         */
        fun computeProductKey(di: String?, materialCode: String?): String {
            val trimmedDi = di?.trim()
            val trimmedCode = materialCode?.trim()
            return when {
                !trimmedDi.isNullOrBlank()          -> trimmedDi
                !trimmedCode.isNullOrBlank()        -> "MCODE:$trimmedCode"
                else -> throw IllegalArgumentException("DI 和物料编码不能同时为空")
            }
        }
    }
}