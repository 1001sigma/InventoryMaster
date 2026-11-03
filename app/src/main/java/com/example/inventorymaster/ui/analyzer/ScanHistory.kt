package com.example.inventorymaster.ui.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 识别记录类型
 */
enum class ScanRecordType(val label: String) {
    BARCODE("条码"),
    OCR("文字")
}

/**
 * 单条识别历史数据模型
 */
data class ScanHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: ScanRecordType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 识别历史数据管理器 (单例轻量持久化)
 */
object ScanHistoryManager {
    private const val PREFS_NAME = "scan_history_prefs"
    private const val KEY_HISTORY = "scan_history_records"
    private const val MAX_RECORDS = 50

    private val _historyFlow = MutableStateFlow<List<ScanHistoryRecord>>(emptyList())
    val historyFlow: StateFlow<List<ScanHistoryRecord>> = _historyFlow.asStateFlow()

    private val gson = Gson()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<ScanHistoryRecord>>() {}.type
                val list: List<ScanHistoryRecord> = gson.fromJson(json, type) ?: emptyList()
                _historyFlow.value = list
            } catch (e: Exception) {
                _historyFlow.value = emptyList()
            }
        }
        isInitialized = true
    }

    fun addRecord(context: Context, content: String, type: ScanRecordType) {
        val clean = content.trim()
        if (clean.isBlank()) return
        init(context)

        val current = _historyFlow.value.toMutableList()
        // 去重：若已存在相同内容，先移除旧的，再推到最前面
        current.removeAll { it.content == clean }
        val newRecord = ScanHistoryRecord(
            content = clean,
            type = type,
            timestamp = System.currentTimeMillis()
        )
        current.add(0, newRecord)
        val trimmed = if (current.size > MAX_RECORDS) current.take(MAX_RECORDS) else current
        _historyFlow.value = trimmed
        persist(context, trimmed)
    }

    fun deleteRecord(context: Context, id: String) {
        init(context)
        val current = _historyFlow.value.toMutableList()
        current.removeAll { it.id == id }
        _historyFlow.value = current
        persist(context, current)
    }

    fun clearAll(context: Context) {
        _historyFlow.value = emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun persist(context: Context, list: List<ScanHistoryRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }
}

/**
 * 识别历史记录展示面板 (ModalBottomSheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistorySheet(
    onDismiss: () -> Unit,
    onSelectRecord: (String) -> Unit
) {
    val context = LocalContext.current
    val historyList by ScanHistoryManager.historyFlow.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // 标题栏与清空按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "识别历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        Text(
                            text = " (${historyList.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (historyList.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清空",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "清空",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = "点击记录可直接填入并应用，或点击右侧复制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 列表或空视图
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无识别历史",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "扫码或OCR文字识别后将自动记录于此",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = historyList, key = { it.id }) { record ->
                        ScanHistoryItemCard(
                            record = record,
                            onSelect = {
                                onSelectRecord(record.content)
                                onDismiss()
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ScanResult", record.content))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                ScanHistoryManager.deleteRecord(context, record.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // 清空确认弹窗
    if (showClearConfirmDialog) {
        ModernAlertDialog(
            onDismiss = { showClearConfirmDialog = false },
            title = "清空历史记录",
            content = {
                Text("确定要清空全部识别历史记录吗？该操作无法撤销。")
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        ScanHistoryManager.clearAll(context)
                        showClearConfirmDialog = false
                    },
                    confirmText = "清空",
                    confirmColor = MaterialTheme.colorScheme.error,
                    dismissText = "取消",
                    onDismiss = { showClearConfirmDialog = false }
                )
            }
        )
    }
}

/**
 * 历史记录单项卡片
 */
@Composable
private fun ScanHistoryItemCard(
    record: ScanHistoryRecord,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    // 根据条码内容与类型分配标签
    val (badgeText, badgeBg, badgeTextColor) = when (record.type) {
        ScanRecordType.BARCODE -> {
            if (record.content.startsWith("(01)") || record.content.startsWith("01")) {
                Triple("UDI条码", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Triple("条码", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        ScanRecordType.OCR -> {
            Triple("OCR文字", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = record.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (record.type == ScanRecordType.BARCODE) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 复制与单条删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
