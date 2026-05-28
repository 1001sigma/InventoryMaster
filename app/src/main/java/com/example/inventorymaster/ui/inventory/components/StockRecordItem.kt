package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.R
import com.example.inventorymaster.data.entity.ExpiryState
import com.example.inventorymaster.data.entity.HighlightField
import com.example.inventorymaster.data.entity.StockRecordUiModel
import com.example.inventorymaster.ui.theme.HighlightField as HighlightFieldColor
import com.example.inventorymaster.ui.theme.StatusInfo
import com.example.inventorymaster.ui.theme.StatusSuccess
import com.example.inventorymaster.ui.theme.StatusWarning

@Composable
fun StockRecordItem(uiModel: StockRecordUiModel) {
    val combined = uiModel.combined
    val record = combined.record
    val product = combined.product
    val highlightField = uiModel.highlightField
    val expiryState = uiModel.expiryState

    val highlightBg = HighlightFieldColor

    val bookQty = record.quantity
    val actQty = record.actualQuantity
    val isVerified = actQty != null
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product?.productName ?: "未录入产品 (${record.di})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(250.dp)
                            .background(if (highlightField == HighlightField.PRODUCT_NAME) highlightBg else Color.Transparent)
                    )

                    if (product != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_udi),
                                contentDescription = "UDI",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${product.di} ${product.model ?: ""}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.background(if (highlightField == HighlightField.DI) highlightBg else Color.Transparent)
                            )
                        }
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_batch), contentDescription = "批号", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "批号: ${record.batchNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.background(if (highlightField == HighlightField.BATCH_NUMBER) highlightBg else Color.Transparent)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.use_bydate), contentDescription = "效期", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "效期: ${record.expiryDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = expiryTextColor,
                            fontWeight = if (expiryState != ExpiryState.NORMAL) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "仓库名称: ${record.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.background(if (highlightField == HighlightField.LOCATION) highlightBg else Color.Transparent)
                    )

                    val mfr = (product?.manufacturer) ?: "未知厂家"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_manufacturer2), contentDescription = "厂家", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (mfr.length > 10) mfr.take(10) + "..." else mfr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
