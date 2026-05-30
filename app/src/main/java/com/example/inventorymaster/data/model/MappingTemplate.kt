package com.example.inventorymaster.data.model

import com.example.inventorymaster.utils.ExcelUtils

data class MappingTemplate(
    val id: String,
    val name: String,
    val mappings: Map<String, String>,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toFieldMap(): Map<Int, ExcelUtils.FieldType> {
        val result = mutableMapOf<Int, ExcelUtils.FieldType>()
        for ((colIdxStr, fieldName) in mappings) {
            val colIdx = colIdxStr.toIntOrNull() ?: continue
            val fieldType = try {
                ExcelUtils.FieldType.valueOf(fieldName)
            } catch (_: Exception) {
                continue
            }
            result[colIdx] = fieldType
        }
        return result
    }

    companion object {
        fun fromFieldMap(
            id: String,
            name: String,
            fieldMap: Map<Int, ExcelUtils.FieldType>,
            createdAt: Long,
            updatedAt: Long
        ): MappingTemplate {
            return MappingTemplate(
                id = id,
                name = name,
                mappings = fieldMap.mapKeys { it.key.toString() }.mapValues { it.value.name },
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
