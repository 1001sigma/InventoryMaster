package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * L1 基础产品库 (字典表)
 * 结构对齐 NMPA UDI 数据库标准，用于存储产品的静态属性。
 * 全局共享，不随盘点任务删除而删除。
 */
@Entity(tableName = "product_base")
data class ProductBase(
    // --- 核心主键 ---
    @PrimaryKey
    val di: String, // GTIN (01) 全球贸易项目代码，作为唯一身份证

    // --- NMPA 标准字段 (用于后续校对) ---
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
    val source: String = "local"    // 数据来源: "local"(手动/Excel), "nmpa"(国家库), "scan"(扫码)
)