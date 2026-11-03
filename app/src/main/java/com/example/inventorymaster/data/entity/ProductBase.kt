package com.example.inventorymaster.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * L1 基础产品库 (字典表)
 * productKey 为组合关联键："DI|物料编码"（缺省侧为空串）。
 * 仅当 DI 与物料编码完全相同才视为同一产品；任一字段不同即为两条独立产品。
 */
@Entity(
    tableName = "product_base",
    indices = [
        Index(value = ["di"], name = "idx_product_base_di"),
        Index(value = ["materialCode"], name = "idx_product_base_material_code")
    ]
)
data class ProductBase(
    // --- 核心主键 ---
    @PrimaryKey
    val productKey: String,        // 组合关联键："{di}|{materialCode}"

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
        /** 键分隔符。用户输入中的半角 | 会被替换为全角 ｜，保证拆分无歧义 */
        private const val KEY_SEPARATOR = "|"

        /**
         * 生成组合关联键 "{di}|{materialCode}"（无冲突检测）
         * 仅当 DI 与物料编码同时为空才抛异常
         */
        fun computeProductKey(di: String?, materialCode: String?): String {
            val cleanDi = sanitizeKeyPart(di)
            val cleanCode = sanitizeKeyPart(materialCode)
            if (cleanDi.isEmpty() && cleanCode.isEmpty()) {
                throw IllegalArgumentException("DI 和物料编码不能同时为空")
            }
            return "$cleanDi$KEY_SEPARATOR$cleanCode"
        }

        /**
         * 带冲突检测的组合键解析
         * @return Pair(productKey, shouldSkip) - shouldSkip=true 时应跳过此条数据
         */
        fun resolveProductKey(di: String?, materialCode: String?): Pair<String, Boolean> {
            val cleanDi = sanitizeKeyPart(di)
            val cleanCode = sanitizeKeyPart(materialCode)
            if (cleanDi.isEmpty() && cleanCode.isEmpty()) {
                return Pair("", true)
            }
            return Pair("$cleanDi$KEY_SEPARATOR$cleanCode", false)
        }

        /**
         * 解析组合键，还原 (di, materialCode) 二元组
         * 兼容不含分隔符的裸输入：整体视为 DI（与扫码行为一致）
         */
        fun parseProductKey(productKey: String): Pair<String, String> {
            val parts = productKey.split(KEY_SEPARATOR, limit = 2)
            return when (parts.size) {
                2 -> Pair(parts[0].trim(), parts[1].trim())
                else -> Pair(productKey.trim(), "")
            }
        }

        /** 键片段清洗：trim + 半角竖线替换为全角，防止破坏组合键拆分 */
        private fun sanitizeKeyPart(value: String?): String {
            return value?.trim()?.replace("|", "｜") ?: ""
        }

        fun hasKeyChanged(oldKey: String, newDi: String?, newMaterialCode: String?): Boolean {
            val newKey = computeProductKey(newDi, newMaterialCode)
            return newKey != oldKey
        }
    }
}
