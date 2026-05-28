package com.example.inventorymaster.ui.productManager

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.viewmodel.InventoryViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagerScreen(
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductBase?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 用户消息提示
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importProductsFromFile(context, it) }
    }

    // 导出 Excel
    val exportExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportProductsToExcelFile(context, it) }
    }

    // 导出 JSON
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportProductsToJsonFile(context, it) }
    }

    // 导入冲突弹窗
    if (uiState.conflictList.isNotEmpty()) {
        ProductConflictScreen(
            conflicts = uiState.conflictList,
            onToggle = { viewModel.toggleConflictAction(it) },
            onSelectAll = { viewModel.setAllConflictsAction(it) },
            onConfirm = { viewModel.confirmProductsResolution() },
            onCancel = { viewModel.cancelProductsImport() }
        )
    }

    // 导出格式选择
    if (showExportFormatDialog) {
        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showExportFormatDialog = false },
            title = { Text("选择导出格式", fontWeight = FontWeight.Bold) },
            text = { Text("请选择导出文件格式") },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        showExportFormatDialog = false
                        exportExcelLauncher.launch("产品库.xlsx")
                    }
                ) { Text("Excel (.xlsx)") }
            },
            dismissButton = {
                TextButton(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        showExportFormatDialog = false
                        exportJsonLauncher.launch("产品库.json")
                    }
                ) { Text("JSON (.json)") }
            }
        )
    }

    // 添加产品弹窗
    if (showAddDialog) {
        ProductAddEditDialog(
            existingProduct = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { newProduct ->
                viewModel.addProduct(newProduct)
                showAddDialog = false
            }
        )
    }

    // 编辑产品弹窗
    if (editingProduct != null) {
        ProductAddEditDialog(
            existingProduct = editingProduct!!,
            onDismiss = { editingProduct = null },
            onConfirm = { updated ->
                viewModel.updateProductBase(updated)
                editingProduct = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (uiState.isProductDeleteMode) {
                    TopAppBar(
                        title = { Text("已选 ${uiState.selectedProductsForDelete.size} 项") },
                        navigationIcon = {
                            TextButton(onClick = { viewModel.toggleProductDeleteMode() }) {
                                Text("取消")
                            }
                        },
                        actions = {
                            Button(
                                onClick = { viewModel.deleteSelectedProducts() },
                                enabled = uiState.selectedProductsForDelete.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("删除(${uiState.selectedProductsForDelete.size})")
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                MinimalistTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchProducts(it)
                    },
                    label = "搜索名称 / 厂家 / 编码 / DI",
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.productList.isEmpty() && searchQuery.isBlank()) {
                    // 优化后的空数据状态
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Empty",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "产品库空空如也",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请点击右下角按钮添加或导入数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.productList, key = { it.di }) { product ->
                            SwipeableProductCard(
                                product = product,
                                isDeleteMode = uiState.isProductDeleteMode,
                                isSelected = uiState.selectedProductsForDelete.contains(product.di),
                                onToggleSelect = { viewModel.toggleProductSelection(product.di) },
                                onClick = {
                                    if (uiState.isProductDeleteMode) {
                                        viewModel.toggleProductSelection(product.di)
                                    } else {
                                        editingProduct = product
                                    }
                                },
                                onDelete = { viewModel.deleteSingleProduct(product.di) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) } // 底部留白给 FAB
                    }
                }
            }
        }

        // Speed Dial overlay
        SpeedDialFAB(
            onImport = { importLauncher.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/json"
            )) },
            onExport = { showExportFormatDialog = true },
            onAdd = { showAddDialog = true },
            onDelete = { viewModel.toggleProductDeleteMode() }
        )
    }
}

// =========================================================================
// Custom UI Components
// =========================================================================

@Composable
fun MinimalistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = leadingIcon,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        )
    )
}

// =========================================================================
// Speed Dial FAB
// =========================================================================

@Composable
fun SpeedDialFAB(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)) // 增加淡淡的遮罩层
                    .clickable { expanded = false }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpeedDialItem(icon = Icons.Default.Delete, label = "删除产品", onClick = {
                        expanded = false
                        onDelete()
                    })
                    SpeedDialItem(icon = Icons.Default.Add, label = "手动添加", onClick = {
                        expanded = false
                        onAdd()
                    })
                    SpeedDialItem(icon = Icons.Default.FileDownload, label = "导出数据", onClick = {
                        expanded = false
                        onExport()
                    })
                    SpeedDialItem(icon = Icons.Default.FileUpload, label = "导入文件", onClick = {
                        expanded = false
                        onImport()
                    })
                }
            }

            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = if (expanded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (expanded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp) // 更圆润的 FAB
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (expanded) "关闭" else "展开"
                )
            }
        }
    }
}

@Composable
fun SpeedDialItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

// =========================================================================
// Swipeable Product Card
// =========================================================================

@Composable
fun SwipeableProductCard(
    product: ProductBase,
    isDeleteMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f

    if (showDeleteConfirm) {
        AlertDialog(
            shape = RoundedCornerShape(24.dp),
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除产品「${product.productName}」吗？\nDI: ${product.di}") },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(shape = RoundedCornerShape(12.dp), onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 滑动露出的删除背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.error)
                .clickable { showDeleteConfirm = true },
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.White,
                modifier = Modifier.padding(end = 24.dp)
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp), // 极简轻微阴影
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(isDeleteMode) {
                    if (isDeleteMode) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -swipeThreshold) {
                                showDeleteConfirm = true
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-swipeThreshold * 2, 0f)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDeleteMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.productName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "规格: ${product.specification ?: "-"}   型号: ${product.model ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "厂家: ${product.manufacturer}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 底部标识信息采用微标签风格
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "DI: ${product.di}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (!product.materialCode.isNullOrBlank()) {
                            Text(
                                text = "编码: ${product.materialCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!isDeleteMode) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// Full Add / Edit Dialog
// =========================================================================

@Composable
fun ProductAddEditDialog(
    existingProduct: ProductBase?,
    onDismiss: () -> Unit,
    onConfirm: (ProductBase) -> Unit
) {
    val isEdit = existingProduct != null
    var di by remember { mutableStateOf(existingProduct?.di ?: "") }
    var name by remember { mutableStateOf(existingProduct?.productName ?: "") }
    var spec by remember { mutableStateOf(existingProduct?.specification ?: "") }
    var model by remember { mutableStateOf(existingProduct?.model ?: "") }
    var mfr by remember { mutableStateOf(existingProduct?.manufacturer ?: "") }
    var regCert by remember { mutableStateOf(existingProduct?.registrationCert ?: "") }
    var matCode by remember { mutableStateOf(existingProduct?.materialCode ?: "") }
    var unit by remember { mutableStateOf(existingProduct?.unit ?: "") }
    var catCode by remember { mutableStateOf(existingProduct?.categoryCode ?: "") }
    var diError by remember { mutableStateOf(false) }

    AlertDialog(
        shape = RoundedCornerShape(24.dp),
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑产品" else "添加产品", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (isEdit) {
                        Text("DI (条码): ${existingProduct!!.di}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        MinimalistTextField(
                            value = di,
                            onValueChange = { di = it; diError = false },
                            label = "DI (条码) *",
                            isError = diError,
                            supportingText = if (diError) {{ Text("DI 不能为空") }} else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item { MinimalistTextField(value = name, onValueChange = { name = it }, label = "产品名称 *", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = spec, onValueChange = { spec = it }, label = "规格", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = model, onValueChange = { model = it }, label = "型号", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = mfr, onValueChange = { mfr = it }, label = "生产厂家 *", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = regCert, onValueChange = { regCert = it }, label = "注册证号", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = matCode, onValueChange = { matCode = it }, label = "物料编码", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = unit, onValueChange = { unit = it }, label = "单位", modifier = Modifier.fillMaxWidth()) }
                item { MinimalistTextField(value = catCode, onValueChange = { catCode = it }, label = "分类编码", modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    if (!isEdit && di.isBlank()) {
                        diError = true
                        return@Button
                    }
                    if (name.isBlank()) name = "未命名产品"
                    if (mfr.isBlank()) mfr = "未知厂家"
                    onConfirm(
                        ProductBase(
                            di = if (isEdit) existingProduct!!.di else di.trim(),
                            productName = name,
                            specification = spec.ifBlank { null },
                            model = model.ifBlank { null },
                            manufacturer = mfr,
                            registrationCert = regCert.ifBlank { null },
                            materialCode = matCode.ifBlank { null },
                            unit = unit.ifBlank { null },
                            categoryCode = catCode.ifBlank { null },
                            source = if (isEdit) existingProduct!!.source else "local"
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(shape = RoundedCornerShape(12.dp), onClick = onDismiss) { Text("取消") }
        }
    )
}
