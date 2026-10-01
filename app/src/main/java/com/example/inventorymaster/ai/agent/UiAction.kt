package com.example.inventorymaster.ai.agent

sealed class UiAction {
    data class NavigateToRecord(val sessionId: Long, val recordId: Long) : UiAction()
    data class HighlightField(val recordId: Long, val field: String) : UiAction()
    data class OpenEditDialog(val recordId: Long) : UiAction()
    data class ShowAnomalies(val sessionId: Long, val anomalyType: String?) : UiAction()
    data class ShowSummary(val sessionId: Long) : UiAction()
    data class NavigateToProduct(val productKey: String) : UiAction()
    data class ShowProductCardList(
        val title: String,
        val products: List<ExpiredProductCardData>
    ) : UiAction()

    fun toDisplayString(): String = when (this) {
        is NavigateToRecord -> "跳转到记录 #$recordId"
        is HighlightField -> "高亮字段: $field"
        is OpenEditDialog -> "编辑记录 #$recordId"
        is ShowAnomalies -> "查看异常列表" + (anomalyType?.let { " ($it)" } ?: "")
        is ShowSummary -> "查看盘点总结"
        is NavigateToProduct -> "查看产品: $productKey"
        is ShowProductCardList -> "显示产品卡片列表 (${products.size}个产品)"
    }
}

data class ExpiredProductCardData(
    val productKey: String,
    val productName: String,
    val batchNumber: String,
    val expiryDateStr: String,
    val quantity: Double,
    val sessionId: String,
    /** 来源记录 ID（点击卡片打开编辑对话框用，v3 修复 recordId=0 TODO） */
    val recordId: Long = 0
)
