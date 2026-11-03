package com.example.inventorymaster.data.model

data class ImportDiagnostics(
    val skippedRows: List<SkippedRow> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList()
) {
    val hasIssues get() = skippedRows.isNotEmpty() || duplicateGroups.isNotEmpty()
}

data class SkippedRow(
    val rowNumber: Int,
    val productName: String,
    val di: String?,
    val materialCode: String?,
    val reason: String
)

data class DuplicateGroup(
    // 组合键标签 "DI|物料编码"——只有 (di, materialCode) 完全相同的行才算重复
    val keyLabel: String,
    val entries: List<DuplicateEntry>
)

data class DuplicateEntry(
    val rowNumber: Int,
    val productName: String,
    val manufacturer: String
)
