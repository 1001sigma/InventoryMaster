package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.R
import com.example.inventorymaster.data.entity.ExpiryState
import com.example.inventorymaster.data.entity.HighlightField
import com.example.inventorymaster.data.entity.StockRecordUiModel
import com.example.inventorymaster.data.model.RecordCardConfig
import com.example.inventorymaster.data.model.RecordCardField
import com.example.inventorymaster.data.model.RecordCardSlot
import com.example.inventorymaster.data.model.matchesHighlight
import com.example.inventorymaster.data.model.resolveFieldValue
import com.example.inventorymaster.ui.components.readableOn
import com.example.inventorymaster.ui.theme.HighlightField as HighlightFieldColor
import com.example.inventorymaster.ui.theme.StatusInfo
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning

/**
 * 记录卡片。五个信息位（UDI/批号/效期/仓库名称/生产厂家）按 cardConfig 显示对应字段，
 * 字段全卡唯一；配置为「不显示」的信息位整行隐藏。
 * onSlotClick 非空时（设置页预览模式）点击信息位回调对应位置。
 */
@Composable
fun StockRecordItem(
    uiModel: StockRecordUiModel,
    cardConfig: RecordCardConfig = RecordCardConfig(),
    onSlotClick: ((RecordCardSlot) -> Unit)? = null
) {
    val combined = uiModel.combined
    val record = combined.record
    val product = combined.product
    val highlightField = uiModel.highlightField
    val expiryState = uiModel.expiryState

    val highlightRing = HighlightFieldColor.copy(alpha = 0.5f)

    val bookQty = record.quantity
    val actQty = record.actualQuantity
    val isVerified = record.isVerified || actQty != null || record.remarks?.contains("已查验") == true
    val isError = isVerified && (actQty != bookQty)

    val statusColor = when {
        !isVerified -> StatusInfo
        isError -> MaterialTheme.colorScheme.error
        else -> StatusSuccess
    }

    val expiryTextColor = when (expiryState) {
        ExpiryState.EXPIRED -> MaterialTheme.colorScheme.error
        ExpiryState.NEAR_EXPIRY -> StatusWarning
        ExpiryState.NORMAL -> MaterialTheme.typography.bodyMedium.color
    }

    val udiField = cardConfig.fieldFor(RecordCardSlot.UDI)
    val batchField = cardConfig.fieldFor(RecordCardSlot.BATCH)
    val expiryField = cardConfig.fieldFor(RecordCardSlot.EXPIRY)
    val locationField = cardConfig.fieldFor(RecordCardSlot.LOCATION)
    val manufacturerField = cardConfig.fieldFor(RecordCardSlot.MANUFACTURER)

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product?.productName ?: "未录入产品 (${record.productKey})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.searchHighlightRing(
                            highlighted = highlightField == HighlightField.PRODUCT_NAME,
                            ringColor = highlightRing
                        )
                    )

                    // UDI 位（图标 + 值，无前缀）
                    val udiValue = resolveFieldValue(udiField, record, product)
                    if (udiField != RecordCardField.NONE) {
                        SlotRow(
                            slot = RecordCardSlot.UDI,
                            field = udiField,
                            value = udiValue ?: "",
                            showLabel = false,
                            onSlotClick = onSlotClick,
                            highlighted = udiField.matchesHighlight(highlightField),
                            ringColor = highlightRing,
                            textStyle = MaterialTheme.typography.bodySmall,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val mainQty = if (isVerified) actQty?.toInt() else bookQty.toInt()
                    Text(
                        text = mainQty.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = when {
                            !isVerified -> "待盘点"
                            isError -> "差异: ${(actQty ?: 0.0) - bookQty}"
                            else -> "已核对"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(5.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1.05f)) {
                    // 批号位
                    val batchValue = resolveFieldValue(batchField, record, product)
                    if (batchField != RecordCardField.NONE) {
                        SlotRow(
                            slot = RecordCardSlot.BATCH,
                            field = batchField,
                            value = batchValue ?: "",
                            showLabel = true,
                            onSlotClick = onSlotClick,
                            highlighted = batchField.matchesHighlight(highlightField),
                            ringColor = highlightRing,
                            fillValueArea = true,
                            maxLines = if (batchField == RecordCardField.BATCH) Int.MAX_VALUE else 1
                        )
                    }

                    // 效期位（效期值任何位置显示都应用近效期/过期配色）
                    val expiryValue = resolveFieldValue(expiryField, record, product)
                    if (expiryField != RecordCardField.NONE) {
                        val isExpiryValue = expiryField == RecordCardField.EXPIRY
                        SlotRow(
                            slot = RecordCardSlot.EXPIRY,
                            field = expiryField,
                            value = expiryValue ?: "",
                            showLabel = true,
                            onSlotClick = onSlotClick,
                            highlighted = expiryField.matchesHighlight(highlightField),
                            ringColor = highlightRing,
                            fillValueArea = true,
                            textColor = if (isExpiryValue) expiryTextColor else Color.Unspecified,
                            fontWeight = if (isExpiryValue && expiryState != ExpiryState.NORMAL) FontWeight.Bold else null
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // 仓库名称位（固定区域内左对齐，起点不随内容浮动）
                    val locationValue = resolveFieldValue(locationField, record, product)
                    if (locationField != RecordCardField.NONE) {
                        SlotRow(
                            slot = RecordCardSlot.LOCATION,
                            field = locationField,
                            value = locationValue ?: "",
                            showLabel = false,
                            onSlotClick = onSlotClick,
                            highlighted = locationField.matchesHighlight(highlightField),
                            ringColor = highlightRing,
                            fillValueArea = true
                        )
                    }

                    // 生产厂家位（右列窄，沿用 10 字截断）
                    val manufacturerValue = resolveFieldValue(manufacturerField, record, product)
                    if (manufacturerField != RecordCardField.NONE) {
                        SlotRow(
                            slot = RecordCardSlot.MANUFACTURER,
                            field = manufacturerField,
                            value = manufacturerValue ?: "",
                            showLabel = false,
                            onSlotClick = onSlotClick,
                            highlighted = false,
                            ringColor = highlightRing,
                            fillValueArea = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (expiryState != ExpiryState.NORMAL) {
            val badgeColor = if (expiryState == ExpiryState.EXPIRED) MaterialTheme.colorScheme.error else StatusWarning
            val badgeText = if (expiryState == ExpiryState.EXPIRED) "已过期" else "近效期"

            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = badgeText,
                    color = readableOn(badgeColor),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * 记录卡片单个信息位：图标 + 文本。showLabel 时渲染「label: 值」。
 * 设置页预览模式下整行可点（onSlotClick 非空）。
 */
@Composable
private fun SlotRow(
    slot: RecordCardSlot,
    field: RecordCardField,
    value: String,
    showLabel: Boolean,
    onSlotClick: ((RecordCardSlot) -> Unit)?,
    highlighted: Boolean,
    ringColor: Color,
    fillValueArea: Boolean = false,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    iconSize: Dp = 20.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    truncateAt: Int? = null,
    maxLines: Int = if (field == RecordCardField.BATCH) Int.MAX_VALUE else 1
) {
    // 缺失占位：空值显示「-」占住固定区域；占位须在 truncateAt 之前判断，避免空串拼成「...」
    val displayValue = value.ifBlank { "-" }.let {
        if (truncateAt != null && it.length > truncateAt) it.take(truncateAt) + "..." else it
    }
    val isMultiLine = maxLines > 1
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            // 仅在预览模式（onSlotClick 非空）挂 clickable：禁用态 clickable 也会消费 DOWN，
            // 会吞掉外层卡片的点击（信息位铺满下半卡后曾导致整块区域点按失灵）
            .then(if (onSlotClick != null) Modifier.clickable { onSlotClick?.invoke(slot) } else Modifier),
        verticalAlignment = if (isMultiLine) Alignment.Top else Alignment.CenterVertically
    ) {
        if (showIcon) {
            RecordFieldIcon(field = field, modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (showLabel) {
            Text(
                text = "${field.label}:",
                style = textStyle,
                color = textColor,
                fontWeight = fontWeight
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        val valueText: @Composable (Modifier) -> Unit = { extra ->
            Text(
                text = displayValue,
                style = textStyle,
                color = textColor,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = if (isMultiLine) TextOverflow.Clip else TextOverflow.Ellipsis,
                softWrap = true,
                modifier = extra.searchHighlightRing(highlighted, ringColor)
            )
        }
        if (fillValueArea) {
            // 固定区域模式：值区占满剩余宽度（内容在区内左对齐、超长省略），描边圈仍贴实际字符
            Box(modifier = Modifier.weight(1f)) {
                valueText(Modifier)
            }
        } else {
            valueText(Modifier)
        }
    }
}

/**
 * 搜索命中标记：空心描边圈，固定 12dp 圆角。单行文本行高约 20dp，12dp 圆角被收敛到半高、
 * 呈胶囊观感；产品名两行块（约 40dp 高）则为规整圆角矩形。
 */
private fun Modifier.searchHighlightRing(highlighted: Boolean, ringColor: Color): Modifier =
    if (!highlighted) this
    else this
        .border(1.dp, ringColor, RoundedCornerShape(12.dp))
        .padding(horizontal = 6.dp)

/** 字段图标：统一的 24dp 单色描边 drawable，随主题 tint 着色 */
@Composable
fun RecordFieldIcon(field: RecordCardField, modifier: Modifier = Modifier) {
    field.iconRes?.let {
        Icon(
            painter = painterResource(id = it),
            contentDescription = field.label,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
