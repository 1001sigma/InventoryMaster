package com.example.inventorymaster.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ai.agent.ExpiredProductCardData
import com.example.inventorymaster.ai.agent.UiAction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 现代产品卡片列表组件（v3.5 UI 升级）。
 * 视觉融入对话气泡流，呈现任务标签、仓库库位、查验状态、生产厂家、效期预警等全维信息。
 */
@Composable
fun ProductCardListWidget(
    cardPayload: UiAction.ShowProductCardList,
    onCardProductClick: (ExpiredProductCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = cardPayload.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${cardPayload.products.size} 条物资",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cardPayload.products.forEach { item ->
                    ProductInteractiveCard(
                        product = item,
                        onClick = { onCardProductClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductInteractiveCard(
    product: ExpiredProductCardData,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 顶部标签栏（任务归属、库位、查验状态、效期倒计时）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val taskLabel = product.sessionName.ifBlank {
                        if (product.sessionId.isNotBlank()) "任务 #${product.sessionId}" else ""
                    }
                    if (taskLabel.isNotBlank()) {
                        StatusTag(
                            text = taskLabel,
                            bgColor = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (product.location.isNotBlank()) {
                        StatusTag(
                            text = product.location,
                            bgColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (product.isVerified) {
                        StatusTag(
                            text = "已查验",
                            bgColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF2E7D32)
                        )
                    } else {
                        StatusTag(
                            text = "未查验",
                            bgColor = Color(0xFFFFF3E0),
                            textColor = Color(0xFFE65100)
                        )
                    }
                }

                // 效期倒计时警示徽章
                val expiryStatus = getExpiryStatus(product.expiryDateStr)
                if (expiryStatus != null) {
                    val (label, level) = expiryStatus
                    StatusTag(
                        text = label,
                        bgColor = if (level == ExpiryLevel.EXPIRED) MaterialTheme.colorScheme.errorContainer else Color(0xFFFFECB3),
                        textColor = if (level == ExpiryLevel.EXPIRED) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFE65100),
                        isBold = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 产品名称与展开箭头
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp)
                )
            }

            // 规格与生产厂家
            val specAndMfr = listOfNotNull(
                product.specification.takeIf { it.isNotBlank() },
                product.manufacturer.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (specAndMfr.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = specAndMfr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部：批号与数量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "批号: ${product.batchNumber.ifBlank { "无" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "数量: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatQuantity(product.quantity)} ${product.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusTag(
    text: String,
    bgColor: Color,
    textColor: Color,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private enum class ExpiryLevel { EXPIRED, WARNING }

private fun getExpiryStatus(expiryStr: String): Pair<String, ExpiryLevel>? {
    if (expiryStr.isBlank() || expiryStr == "未标明") return null
    return try {
        val expiry = LocalDate.parse(expiryStr.trim())
        val days = ChronoUnit.DAYS.between(LocalDate.now(), expiry)
        when {
            days < 0 -> "已过期 ${-days}天" to ExpiryLevel.EXPIRED
            days == 0L -> "今日到期" to ExpiryLevel.EXPIRED
            days <= 90 -> "剩 ${days}天" to ExpiryLevel.WARNING
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatQuantity(qty: Double): String {
    return if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)
}
