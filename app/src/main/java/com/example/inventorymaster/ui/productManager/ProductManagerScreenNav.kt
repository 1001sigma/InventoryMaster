package com.example.inventorymaster.ui.productManager

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar
import com.example.inventorymaster.ui.components.ModernDialogTextField
import com.example.inventorymaster.viewmodel.InventoryViewModel
import kotlinx.coroutines.delay
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

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
    uiState.importSummary?.let { summaryText ->
        val diagnostics = uiState.importDiagnostics
        if (diagnostics != null && diagnostics.hasIssues) {
            ImportDiagnosticsDialog(
                diagnostics = diagnostics,
                onConfirm = { viewModel.confirmImport() },
                onDismiss = { viewModel.cancelImport() }
            )
        } else {
            ModernAlertDialog(
                onDismiss = { viewModel.cancelImport() },
                title = "导入确认",
                content = {
                    Text(
                        text = summaryText + "\n\n以导入文件为准，将覆盖本地数据，是否继续？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    ModernDialogButtonBar(
                        onConfirm = { viewModel.confirmImport() },
                        onDismiss = { viewModel.confirmImport() },
                        confirmText = "确认导入",
                        dismissText = "取消"
                    )
                }
            )
        }
    }

    // 导出格式选择
    if (showExportFormatDialog) {
        ModernAlertDialog(
            onDismiss = { showExportFormatDialog = false },
            title = "选择导出格式",
            content = {
                Text("请选择导出文件格式")
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        showExportFormatDialog = false
                        exportExcelLauncher.launch("产品库.xlsx")
                    },
                    onDismiss = {
                        showExportFormatDialog = false
                        exportJsonLauncher.launch("产品库.json")
                    },
                    confirmText = "Excel (.xlsx)",
                    dismissText = "JSON (.json)"
                )
            }
        )
    }

    // 服务器 Token 输入弹窗（未配置或 401 时触发）
    if (uiState.showTokenPrompt) {
        var inputToken by remember { mutableStateOf("") }
        ModernAlertDialog(
            onDismiss = { viewModel.dismissTokenPrompt() },
            title = "配置服务端认证 Token",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.tokenPromptMessage ?: "请输入服务端 Token（可在服务端启动控制台查看）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModernDialogTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = "认证 Token",
                        placeholder = "输入 Token 后自动保存并同步",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        if (inputToken.isNotBlank()) {
                            viewModel.saveTokenAndSync(inputToken)
                        }
                    },
                    onDismiss = { viewModel.dismissTokenPrompt() },
                    confirmText = "保存并同步",
                    dismissText = "取消"
                )
            }
        )
    }

    // 添加产品弹窗
    if (showAddDialog) {
        ProductAddEditDialog(
            existingProduct = null,
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showAddDialog = false
            },
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
            onDismiss = {
                keyboardController?.hide()
                focusManager.clearFocus()
                editingProduct = null
            },
            onConfirm = { updated ->
                viewModel.updateProductBase(editingProduct!!.productKey, updated)
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
                            TextButton(onClick = { viewModel.selectAllProducts() }) {
                                Text(
                                    text = if (uiState.selectedProductsForDelete.isNotEmpty() && uiState.selectedProductsForDelete.size == uiState.productList.size) "取消全选" else "全选"
                                )
                            }
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
                AppTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchProducts(it)
                    },
                    label = "搜索名称 / 厂家 / 编码 / DI",
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.productList.isEmpty() && searchQuery.isBlank()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.productCount == 0) {
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
                        } else {
                            Text(
                                text = "共 ${uiState.productCount} 款产品",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请输入关键字搜索",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.productList, key = { it.productKey }) { product ->
                            SwipeableProductCard(
                                product = product,
                                isDeleteMode = uiState.isProductDeleteMode,
                                isSelected = uiState.selectedProductsForDelete.contains(product.productKey),
                                onToggleSelect = { viewModel.toggleProductSelection(product.productKey) },
                                onClick = {
                                    if (uiState.isProductDeleteMode) {
                                        viewModel.toggleProductSelection(product.productKey)
                                    } else {
                                        editingProduct = product
                                    }
                                },
                                onDelete = { viewModel.deleteSingleProduct(product.productKey) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) } // 底部留白给 FAB
                    }
                }
            }
        }

        // Speed Dial overlay
        SpeedDialFAB(
            onSync = { viewModel.syncProductLibrary() },
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
// Speed Dial FAB
// =========================================================================

@Composable
fun SpeedDialFAB(
    onSync: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // ===== 新增：FAB旋转动画 =====
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "fab_rotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()

                    // ===== 修改：遮罩层更明显 =====
                    .background(Color.Black.copy(alpha = 0.35f))

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

                // ===== 修改：动画更自然 =====
                enter = fadeIn() +
                        expandVertically(expandFrom = Alignment.Bottom),

                exit = fadeOut() +
                        shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // ===== 修改：按重要性排序 =====

                    SpeedDialItem(
                        icon = Icons.Default.CloudDownload,
                        label = "同步产品库",
                        onClick = {
                            expanded = false
                            onSync()
                        }
                    )

                    SpeedDialItem(
                        icon = Icons.Default.Add,
                        label = "新增产品",
                        onClick = {
                            expanded = false
                            onAdd()
                        }
                    )

                    SpeedDialItem(
                        icon = Icons.Default.FileUpload,
                        label = "导入数据",
                        onClick = {
                            expanded = false
                            onImport()
                        }
                    )

                    SpeedDialItem(
                        icon = Icons.Default.FileDownload,
                        label = "导出数据",
                        onClick = {
                            expanded = false
                            onExport()
                        }
                    )

                    SpeedDialItem(
                        icon = Icons.Default.Delete,
                        label = "批量删除",
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    expanded = !expanded
                },

                // ===== 修改：使用主色 =====
                containerColor = MaterialTheme.colorScheme.primary,

                contentColor = MaterialTheme.colorScheme.onPrimary,

                shape = RoundedCornerShape(18.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.Add,

                    // ===== 新增：旋转动画 =====
                    modifier = Modifier.rotate(rotation),

                    contentDescription = "操作菜单"
                )
            }
        }
    }
}


@Composable
fun SpeedDialItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var showLabel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showLabel = true
        delay(2500)
        showLabel = false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            LabelBubble(label)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(52.dp)
                .clickable { onClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label
                )
            }
        }
    }
}


@Composable
private fun LabelBubble(
    text: String
) {
    Surface(
        color = Color.White,

        shape = RoundedCornerShape(12.dp),

        shadowElevation = 8.dp
    ) {
        Text(
            text = text,

            color = Color.Black,

            style = MaterialTheme.typography.titleMedium,

            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 10.dp
            )
        )
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
        ModernAlertDialog(
            onDismiss = { showDeleteConfirm = false },
            title = "确认删除",
            content = {
                Text("确定要删除产品\n「${product.productName}」吗？\nmaterialCode: ${product.materialCode}" + (product.di?.let { "\nDI: $it" } ?: ""))
            },
            confirmButton = {
                ModernDialogButtonBar(
                    onConfirm = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    onDismiss = { showDeleteConfirm = false },
                    confirmText = "删除",
                    confirmColor = MaterialTheme.colorScheme.error
                )
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
                        text = "注册证编号: ${product.registrationCert ?: "-"} ",
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
                                text = "物料编码: ${product.materialCode}",
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
    // 基础必填/核心信息
    var di by remember { mutableStateOf(existingProduct?.di ?: "") }
    var matCode by remember { mutableStateOf(existingProduct?.materialCode ?: "") }
    var name by remember { mutableStateOf(existingProduct?.productName ?: "") }
    var mfr by remember { mutableStateOf(existingProduct?.manufacturer ?: "") }

    // 选填信息
    var spec by remember { mutableStateOf(existingProduct?.specification ?: "") }
    var model by remember { mutableStateOf(existingProduct?.model ?: "") }
    var regCert by remember { mutableStateOf(existingProduct?.registrationCert ?: "") }
    var unit by remember { mutableStateOf(existingProduct?.unit ?: "") }
    var catCode by remember { mutableStateOf(existingProduct?.categoryCode ?: "") }

    // 状态控制
    var keyError by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) } // 控制选填项展开

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = if (isEdit) "编辑产品" else "添加产品",
        showContentCard = false,
        content = {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp) // 限制最高高度，留出外围通透感
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEdit) {
                    val computedKey = ProductBase.computeProductKey(di.ifBlank { null }, matCode.ifBlank { null })
                    val keyChanged = computedKey != existingProduct!!.productKey

                    Text(
                        text = "Key: ${existingProduct.productKey}" + (existingProduct.di?.let { " | DI: $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, // 用强调色代替默认灰
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                    if (keyChanged) {
                        Text(
                            text = "关联键将变更为: $computedKey",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                ModernDialogTextField(
                    value = di,
                    onValueChange = { di = it; keyError = false },
                    label = "DI码",
                    isError = keyError && di.isBlank() && matCode.isBlank(),
                    supportingText = if (keyError) { { Text("DI与物料编码至少填一项") } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = matCode,
                    onValueChange = { matCode = it; keyError = false },
                    label = "物料编码*",
                    isError = keyError && di.isBlank() && matCode.isBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "产品名称 *",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ModernDialogTextField(
                    value = mfr,
                    onValueChange = { mfr = it },
                    label = "生产厂家",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // --- 展开/收起 触发器 ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAdvanced) "收起选填项" else "展开更多选填项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // --- 选填折叠区 ---
                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModernDialogTextField(
                            value = spec,
                            onValueChange = { spec = it },
                            label = "规格",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        ModernDialogTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = "型号",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        ModernDialogTextField(
                            value = regCert,
                            onValueChange = { regCert = it },
                            label = "注册证号",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        ModernDialogTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = "单位",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        ModernDialogTextField(
                            value = catCode,
                            onValueChange = { catCode = it },
                            label = "分类编码",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done) // 最后一个是 Done
                        )
                        Spacer(modifier = Modifier.height(8.dp)) // 底部防贴边
                    }
                }
            }
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    if (di.isBlank() && matCode.isBlank()) {
                        keyError = true
                        return@ModernDialogButtonBar
                    }
                    val finalName = name.ifBlank { "未命名产品" }
                    val finalMfr = mfr.ifBlank { "未知厂家" }
                    val safeProductKey = ProductBase.computeProductKey(di.ifBlank { null }, matCode.ifBlank { null })

                    onConfirm(
                        ProductBase(
                            productKey = safeProductKey,
                            di = di.ifBlank { null },
                            productName = finalName,
                            specification = spec.ifBlank { null },
                            model = model.ifBlank { null },
                            manufacturer = finalMfr,
                            registrationCert = regCert.ifBlank { null },
                            materialCode = matCode.ifBlank { null },
                            unit = unit.ifBlank { null },
                            categoryCode = catCode.ifBlank { null },
                            lastSyncTime = if (isEdit) existingProduct!!.lastSyncTime else 0,
                            source = if (isEdit) existingProduct!!.source else "local_gen"
                        )
                    )
                },
                onDismiss = onDismiss,
                confirmText = "保存"
            )
        }
    )
}