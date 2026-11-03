package com.example.inventorymaster.utils.pdf

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 电子签名库设置页（全屏页面：自带返回头部，顶栏底栏由 MainScreenNav 隐藏）：
 * 管理手绘签名素材（绘制入库 / 预览 / 删除），并配置导出 PDF 时的默认盖章摆放参数。
 * 卡片风格与系统设置页一致（24dp 圆角、surface、elevation 2dp）。
 */
@Composable
fun SignatureSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = SignatureStore.get(context)

    val uiState by viewModel.uiState.collectAsState()
    val signatures by store.signatures.collectAsState()

    var showPadDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // —— 页内返回头部 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "电子签名库",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // —— 绘制入口 ——
            SectionCard {
                Text(
                    text = "绘制新签名",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "手写签名将以透明底 PNG 存入本机签名库（最多保留 20 份，超出自动淘汰最旧），供导出 PDF 时盖章使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showPadDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✍ 打开手写板")
                }
            }

            // —— 签名库 ——
            SectionCard {
                Text(
                    text = "我的签名（${signatures.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (signatures.isEmpty()) {
                    Text(
                        text = "签名库还是空的，先绘制一个签名吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    signatures.chunked(3).forEach { rowFiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFiles.forEach { file ->
                                SignatureCell(
                                    file = file,
                                    modifier = Modifier.weight(1f),
                                    onClick = { deleteTarget = file }
                                )
                            }
                            repeat(3 - rowFiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        text = "点击签名可预览并删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // —— 盖章默认摆放参数 ——
            SectionCard {
                Text(
                    text = "导出盖章默认参数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "控制签名在 PDF 首页的摆放位置与样式，导出弹窗无需每次调整",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 拖动过程只改本地状态，松手才写 DataStore：
                // 若每个刻度都直接落盘，滑杆值要等 DataStore 流回吐才更新，拇指会滞后于手指产生"惯性"感
                var offsetX by remember(uiState.signatureOffsetX) { mutableStateOf(uiState.signatureOffsetX) }
                var offsetY by remember(uiState.signatureOffsetY) { mutableStateOf(uiState.signatureOffsetY) }
                var scale by remember(uiState.signatureScale) { mutableStateOf(uiState.signatureScale) }
                var opacity by remember(uiState.signatureOpacity) { mutableStateOf(uiState.signatureOpacity) }

                // —— 盖章效果预览：模拟 A4 纸 + 最新一枚签名，坐标/钳制算法与 PdfEngine.addSignature 一致 ——
                val previewFile = signatures.firstOrNull()
                var previewSignature by remember(previewFile?.absolutePath) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(previewFile?.absolutePath) {
                    previewSignature = previewFile?.let { file ->
                        withContext(Dispatchers.IO) { store.decodeThumbnail(file, 600) }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.414f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                ) {
                    val density = LocalDensity.current
                    val wPx = with(density) { maxWidth.toPx() }
                    val hPx = with(density) { maxHeight.toPx() }

                    // 假单据灰色占位线条
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth(0.4f).height(8.dp).background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    }

                    val currentPreview = previewSignature
                    if (currentPreview == null) {
                        Text(
                            text = "签名库还没有签名，绘制后即可在这里预览盖章效果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    } else {
                        val aspect = currentPreview.width.toFloat() / currentPreview.height.toFloat().coerceAtLeast(1f)
                        val targetWidth = wPx * scale
                        val targetHeight = targetWidth / aspect
                        val x = (wPx * offsetX - targetWidth / 2f).coerceIn(0f, (wPx - targetWidth).coerceAtLeast(0f))
                        val y = (hPx * offsetY - targetHeight / 2f).coerceIn(0f, (hPx - targetHeight).coerceAtLeast(0f))

                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                                .width(with(density) { targetWidth.toDp() })
                                .aspectRatio(aspect)
                                .graphicsLayer { alpha = opacity }
                        )
                    }
                }
                Text(
                    text = "预览使用最新一枚签名，拖动下方滑杆实时生效（导出 PDF 位置与此一致）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("水平位置", style = MaterialTheme.typography.bodyMedium)
                        Text("${(offsetX * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = offsetX,
                        onValueChange = { offsetX = it },
                        onValueChangeFinished = { viewModel.setSignatureOffsetX(offsetX) },
                        valueRange = 0f..1f
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("垂直位置（自顶部起）", style = MaterialTheme.typography.bodyMedium)
                        Text("${(offsetY * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = offsetY,
                        onValueChange = { offsetY = it },
                        onValueChangeFinished = { viewModel.setSignatureOffsetY(offsetY) },
                        valueRange = 0f..1f
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("签名大小", style = MaterialTheme.typography.bodyMedium)
                        Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        onValueChangeFinished = { viewModel.setSignatureScale(scale) },
                        valueRange = 0.05f..0.6f
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("不透明度", style = MaterialTheme.typography.bodyMedium)
                        Text("${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        onValueChangeFinished = { viewModel.setSignatureOpacity(opacity) },
                        valueRange = 0.2f..1f
                    )
                }
            }
        }
    }

    // —— 手写板 ——
    if (showPadDialog) {
        SignaturePadDialog(
            onDismiss = { showPadDialog = false },
            onConfirm = { bitmap ->
                showPadDialog = false
                scope.launch {
                    val saved = store.saveSignature(bitmap)
                    Toast.makeText(
                        context,
                        if (saved != null) "签名已存入签名库" else "签名保存失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // —— 删除确认 ——
    deleteTarget?.let { target ->
        var preview by remember(target.absolutePath) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(target.absolutePath) {
            preview = withContext(Dispatchers.IO) {
                store.decodeThumbnail(target, 600)
            }
        }
        ModernAlertDialog(
            onDismiss = { deleteTarget = null },
            title = "删除签名",
            showContentCard = true,
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = (preview as Bitmap).asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        )
                    }
                    Text(
                        "删除后无法恢复，确定删除这份签名吗？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        deleteTarget = null
                        scope.launch { store.deleteSignature(target) }
                    },
                    onDismiss = { deleteTarget = null },
                    confirmText = "删除"
                )
            }
        )
    }
}

/** 设置页同款分区卡片：24dp 圆角、surface 底、2dp 阴影 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

/** 签名库网格单元：白底透明签名缩略图（IO 线程异步解码，避免滚动掉帧） */
@Composable
private fun SignatureCell(
    file: File,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            SignatureStore.get(context).decodeThumbnail(file, 200)
        }
    }

    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = (bitmap as Bitmap).asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        } else {
            Text(
                "无法读取",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
