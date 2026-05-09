package com.example.inventorymaster.batchscanner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryTaskDetailScreen(
    viewModel: InventoryMainViewModel = viewModel(),
    onNavigateToBatchCamera: (inputUri: String?) -> Unit,  // 导航到批量扫码(补拍)，可传外部图片 Uri
    onNavigateToSingleScanner: () -> Unit// 导航到单码扫码
) {
    val photoList by viewModel.photoList.collectAsState()
    val recordList by viewModel.recordList.collectAsState()

    // 创建 PickVisualMediaRequest 实例（在 Composable 作用域中）
    val pickImageRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    // 相册选择器：选择图片后导航到 BatchScannerScreen 进行处理
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // 将选中的图片 Uri 传给 BatchScannerScreen 进行条码解析
            onNavigateToBatchCamera(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("盘点任务执行") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)) // 浅灰背景，让卡片更凸显
        ) {
            // ================= 1. 上半区：图片轮播区 =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable {
                        // 如果没有图片，点击图片区域也可以触发拍照
                        if (photoList.isEmpty()) onNavigateToBatchCamera(null)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoList.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击此处或下方按钮进行批量采集", color = Color.Gray)
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { photoList.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val photoUri = photoList[page]
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 1. 底图：展示照片
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "盘点照片 $page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            // 2. 右上角：删除按钮
                            IconButton(
                                onClick = {
                                    // 调用 ViewModel 执行删除逻辑
                                    viewModel.removePhoto(photoUri)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    // 给图标加一个半透明背景，确保在浅色图片下也能看清
                                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除照片",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    // 简单的页码指示器
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photoList.size}",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ================= 2. 中间区：操作按钮栏（现代化紧凑风格） =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 导入单据
                FilledTonalButton(
                    onClick = { viewModel.importTargetDocument() },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📄 单据", fontSize = 12.sp)
                }
                // 导入图片
                FilledTonalButton(
                    onClick = { galleryLauncher.launch(pickImageRequest) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("图片", fontSize = 12.sp)
                }
                // 批量补拍
                Button(
                    onClick = { onNavigateToBatchCamera(null) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补拍", fontSize = 12.sp)
                }
                // 单码补扫
                FilledTonalButton(
                    onClick = onNavigateToSingleScanner,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("补扫", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()

            // ================= 3. 下半区：校验结果表格（现代化风格） =================
            Text(
                text = "校验结果明细 (${recordList.size}条)",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 判断是否有导入单据（是否显示"目标"列）
            val hasTarget = recordList.any { record -> record.targetQty > 0 }

            // 列权重：产品名 2 | DI 1.5 | 批号 1 | 目标 0.8 | 实际 1 | 状态 0.6 | 删除 0.6
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // -------- 表头（固定） --------
                Row(
                    modifier = Modifier
                        .background(Color(0xFF37474F))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("产品名称", Modifier.weight(2f))
                    VLine(color = Color.White.copy(alpha = 0.3f))
                    TableHeaderCell("DI", Modifier.weight(1.5f))
                    VLine(color = Color.White.copy(alpha = 0.3f))
                    TableHeaderCell("批号", Modifier.weight(1f))
                    if (hasTarget) {
                        VLine(color = Color.White.copy(alpha = 0.3f))
                        TableHeaderCell("目标", Modifier.weight(0.8f), TextAlign.Center)
                    }
                    VLine(color = Color.White.copy(alpha = 0.3f))
                    TableHeaderCell("实际", Modifier.weight(1f), TextAlign.Center)
                    VLine(color = Color.White.copy(alpha = 0.3f))
                    TableHeaderCell("状态", Modifier.weight(0.6f), TextAlign.Center)
                    VLine(color = Color.White.copy(alpha = 0.3f))
                    TableHeaderCell("", Modifier.weight(0.6f))
                }

                // -------- 表体（可垂直滚动） --------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    recordList.forEachIndexed { index, record ->
                        // 斑马纹
                        val bgColor = if (index % 2 == 0) Color.White else Color(0xFFF0F4F8)
                        // 状态行背景色覆盖
                        val rowBg = when {
                            record.isError -> Color(0xFFFFF0F0)
                            record.status == RecordStatus.OVERFLOW -> Color(0xFFFFF4E6)
                            record.status == RecordStatus.MISSING -> Color(0xFFFFFFE0)
                            record.isManualModified -> Color(0xFFE6F4EA)
                            else -> bgColor
                        }

                        Row(
                            modifier = Modifier
                                .background(rowBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 产品名称（过长截断）
                            TableBodyCell(
                                text = record.productName,
                                modifier = Modifier.weight(2f),
                                color = if (record.isError) Color.Red else Color.Black
                            )
                            VLine()
                            // DI（过长截断）
                            TableBodyCell(text = record.di, modifier = Modifier.weight(1.5f), color = Color(0xFF555555))
                            VLine()
                            // 批号
                            TableBodyCell(text = record.batch, modifier = Modifier.weight(1f))
                            if (hasTarget) {
                                VLine()
                                // 目标数量
                                TableBodyCell(
                                    text = record.targetQty.toString(),
                                    modifier = Modifier.weight(0.8f),
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF555555)
                                )
                            }
                            VLine()
                            // 实际数量（可点击编辑）
                            EditableQuantityCell(
                                qty = record.actualQty,
                                modifier = Modifier.weight(1f),
                                onQtyChange = { newQty ->
                                    viewModel.updateRecordQuantity(record.id, newQty)
                                }
                            )
                            VLine()
                            // 状态指示
                            StatusIndicator(status = record.status, modifier = Modifier.weight(0.6f))
                            VLine()
                            // 删除按钮
                            DeleteButton(modifier = Modifier.weight(0.6f), onDelete = {
                                viewModel.removeRecord(record.id)
                            })
                        }

                        // 水平分割线
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }
            }
        }
    }
}

// ===================== 表格子组件（全部使用 Modifier.weight 自适应） =====================

/**
 * 垂直分隔线（用 Box 绘制，避免 Material3 Divider 的方向兼容问题）
 */
@Composable
private fun VLine(color: Color = Color(0xFFCCCCCC)) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(color)
    )
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableBodyCell(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color(0xFF333333)
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.combinedClickable(
            onClick = { /* 普通点击不做处理，但必须声明 onClick 才能捕获长按 */ },
            onLongClick = {
                if (text.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(text))
                    // 给用户一个 Toast 交互反馈
                    Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
                }
            }
        )
    )
}

@Composable
private fun EditableQuantityCell(
    qty: Int,
    modifier: Modifier,
    onQtyChange: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    if (isEditing) {
        BasicTextField(
            value = textValue,
            onValueChange = { newVal ->
                if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                    textValue = newVal
                }
            },
            modifier = modifier
                .height(32.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val newQty = textValue.toIntOrNull()
                    if (newQty != null && newQty >= 0) {
                        onQtyChange(newQty)
                    } else {
                        textValue = qty.toString()
                    }
                    isEditing = false
                }
            ),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    innerTextField()
                }
            }
        )
    } else {
        Box(
            modifier = modifier.clickable {
                isEditing = true
                textValue = ""
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = qty.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: RecordStatus, modifier: Modifier) {
    val (color, label) = when (status) {
        RecordStatus.NORMAL -> Color(0xFF4CAF50) to "✔"
        RecordStatus.MATCHED -> Color(0xFF4CAF50) to "✔"
        RecordStatus.MISSING -> Color(0xFFFFC107) to "⚠"
        RecordStatus.OVERFLOW -> Color(0xFFFF9800) to "⚠"
        RecordStatus.ERROR -> Color(0xFFF44336) to "✘"
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = label, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteButton(modifier: Modifier, onDelete: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color(0xFF999999),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
