package com.example.inventorymaster.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.ai.provider.ProviderPresets
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val providers by viewModel.aiProviders.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 服务商管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加服务商")
            }
        }
    ) { padding ->
        AiProviderSettingsContent(
            providers = providers,
            activeProviderId = activeProviderId,
            onAdd = { viewModel.addAiProvider(it) },
            onActivate = { viewModel.setActiveProvider(it) },
            onEdit = { viewModel.updateAiProvider(it) },
            onDelete = { viewModel.deleteAiProvider(it) },
            modifier = Modifier.padding(padding)
        )
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { provider ->
                viewModel.addAiProvider(provider)
                showAddDialog = false
            }
        )
    }

    editingProvider?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onConfirm = { updated ->
                viewModel.updateAiProvider(updated)
                editingProvider = null
            }
        )
    }
}

/*
// ========== 版本 1（已注释，保留用于测试） ==========
@Composable
fun AiProviderSettingsContent(
    providers: List<AiProviderConfig>,
    activeProviderId: String,
    onAdd: (AiProviderConfig) -> Unit,
    onActivate: (String) -> Unit,
    onEdit: (AiProviderConfig) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无服务商，请点击添加",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        isActive = provider.id == activeProviderId,
                        onActivate = { onActivate(provider.id) },
                        onEdit = { editingProvider = provider },
                        onDelete = { onDelete(provider.id) }
                    )
                }
            }
        }

        TextButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加服务商")
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { provider ->
                onAdd(provider)
                showAddDialog = false
            }
        )
    }

    editingProvider?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onConfirm = { updated ->
                onEdit(updated)
                editingProvider = null
            }
        )
    }
}
*/

/*
@Composable
private fun ProviderCard(
    provider: AiProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (provider.baseUrl.isNotBlank()) {
                        Text(
                            text = provider.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "模型: ${provider.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (provider.isLocal) {
                        Text(
                            text = "本地模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row {
                    if (!isActive) {
                        IconButton(onClick = onActivate) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "启用",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
*/

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (AiProviderConfig) -> Unit
) {
    var showPresets by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isLocal by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(true) }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择预设模板") },
            text = {
                LazyColumn {
                    items(ProviderPresets.presets) { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            modifier = Modifier.clickable {
                                name = preset.config.name
                                baseUrl = preset.config.baseUrl
                                model = preset.config.model
                                isLocal = preset.config.isLocal
                                showPresets = false
                            }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("自定义") },
                            modifier = Modifier.clickable {
                                showPresets = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("添加服务商") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLocal
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = modelList.isNotEmpty(),
                        trailingIcon = {
                            if (modelList.isNotEmpty()) {
                                IconButton(onClick = { modelExpanded = true }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "展开"
                                    )
                                }
                            }
                        }
                    )
                    if (modelExpanded && modelList.isNotEmpty()) {
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            modelList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        model = m
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLocal
                    )
                    if (!isLocal && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                isFetchingModels = true
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val models = withContext(Dispatchers.IO) {
                                            val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
                                            val service = CloudAiService(
                                                AiProviderConfig(
                                                    name = name,
                                                    baseUrl = baseUrl,
                                                    model = model,
                                                    apiKey = apiKey,
                                                    modelsEndpoint = endpoint
                                                )
                                            )
                                            service.fetchModels()
                                        }
                                        modelList = models
                                    } catch (e: Exception) {
                                        modelList = emptyList()
                                    } finally {
                                        isFetchingModels = false
                                    }
                                }
                            },
                            enabled = !isFetchingModels
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(if (isFetchingModels) "获取中..." else "获取模型列表")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isLocal,
                            onCheckedChange = { isLocal = it }
                        )
                        Text("本地模型")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            AiProviderConfig(
                                name = name,
                                baseUrl = baseUrl,
                                model = model,
                                apiKey = apiKey,
                                isLocal = isLocal
                            )
                        )
                    },
                    enabled = name.isNotBlank() && model.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}
*/

/*
@Composable
private fun EditProviderDialog(
    provider: AiProviderConfig,
    onDismiss: () -> Unit,
    onConfirm: (AiProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var model by remember { mutableStateOf(provider.model) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !provider.isLocal
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = modelList.isNotEmpty(),
                    trailingIcon = {
                        if (modelList.isNotEmpty()) {
                            IconButton(onClick = { modelExpanded = true }) {
                                Icon(Icons.Default.Add, contentDescription = "展开")
                            }
                        }
                    }
                )
                if (modelExpanded && modelList.isNotEmpty()) {
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        modelList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    model = m
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !provider.isLocal
                )
                if (!provider.isLocal && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            isFetchingModels = true
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val service = com.example.inventorymaster.ai.provider.CloudAiService(
                                        AiProviderConfig(
                                            name = name,
                                            baseUrl = baseUrl,
                                            model = model,
                                            apiKey = apiKey
                                        )
                                    )
                                    val models = service.fetchModels()
                                    modelList = models
                                } catch (_: Exception) {
                                    modelList = emptyList()
                                } finally {
                                    isFetchingModels = false
                                }
                            }
                        },
                        enabled = !isFetchingModels
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(if (isFetchingModels) "获取中..." else "获取模型列表")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        provider.copy(
                            name = name,
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey
                        )
                    )
                },
                enabled = name.isNotBlank() && model.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
*/

// ========== 版本 2（已修复） ==========
@Composable
fun AiProviderSettingsContent(
    providers: List<AiProviderConfig>,
    activeProviderId: String,
    onAdd: (AiProviderConfig) -> Unit,
    onActivate: (String) -> Unit,
    onEdit: (AiProviderConfig) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无服务商，请点击下方按钮添加",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                providers.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        isActive = provider.id == activeProviderId,
                        onActivate = { onActivate(provider.id) },
                        onEdit = { editingProvider = provider },
                        onDelete = { onDelete(provider.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加服务商")
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { provider ->
                onAdd(provider)
                showAddDialog = false
            }
        )
    }

    editingProvider?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onConfirm = { updated ->
                onEdit(updated)
                editingProvider = null
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (provider.baseUrl.isNotBlank()) {
                        Text(
                            text = provider.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "模型: ${provider.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (provider.isLocal) {
                        Text(
                            text = "本地模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row {
                    if (!isActive) {
                        IconButton(onClick = onActivate) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "启用",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (AiProviderConfig) -> Unit
) {
    var showPresets by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isLocal by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(true) }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择预设模板") },
            text = {
                LazyColumn {
                    items(ProviderPresets.presets) { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            modifier = Modifier.clickable {
                                name = preset.config.name
                                baseUrl = preset.config.baseUrl
                                model = preset.config.model
                                isLocal = preset.config.isLocal
                                showPresets = false
                            }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("自定义") },
                            modifier = Modifier.clickable {
                                showPresets = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("添加服务商") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "名称",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    AppTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = "Base URL",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLocal,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                    )
                    AppTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "API Key",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLocal,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    AppTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = "模型",
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = modelList.isNotEmpty(),
                        trailingIcon = {
                            if (modelList.isNotEmpty()) {
                                IconButton(onClick = { modelExpanded = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "展开模型列表")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    if (modelExpanded && modelList.isNotEmpty()) {
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            modelList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        model = m
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (!isLocal && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                isFetchingModels = true
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val models = withContext(Dispatchers.IO) {
                                            val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
                                            val service = CloudAiService(
                                                AiProviderConfig(
                                                    name = name,
                                                    baseUrl = baseUrl,
                                                    model = model,
                                                    apiKey = apiKey,
                                                    modelsEndpoint = endpoint
                                                )
                                            )
                                            service.fetchModels()
                                        }
                                        modelList = models
                                    } catch (e: Exception) {
                                        modelList = emptyList()
                                    } finally {
                                        isFetchingModels = false
                                    }
                                }
                            },
                            enabled = !isFetchingModels
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(if (isFetchingModels) "获取中..." else "获取模型列表")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isLocal,
                            onCheckedChange = { isLocal = it }
                        )
                        Text("本地模型")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = supportsTools,
                            onCheckedChange = { supportsTools = it }
                        )
                        Text("支持工具调用（Function Calling）")
                    }
                    Text(
                        "模型不支持工具调用时请取消勾选，AI 将改用 JSON 文本协议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            AiProviderConfig(
                                name = name,
                                baseUrl = baseUrl,
                                model = model,
                                apiKey = apiKey,
                                isLocal = isLocal,
                                supportsTools = supportsTools
                            )
                        )
                    },
                    enabled = name.isNotBlank() && model.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProviderDialog(
    provider: AiProviderConfig,
    onDismiss: () -> Unit,
    onConfirm: (AiProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var model by remember { mutableStateOf(provider.model) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(provider.supportsTools) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = "Base URL",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !provider.isLocal,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )
                AppTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !provider.isLocal,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = "模型",
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = modelList.isNotEmpty(),
                    trailingIcon = {
                        if (modelList.isNotEmpty()) {
                            IconButton(onClick = { modelExpanded = true }) {
                                Icon(Icons.Default.Add, contentDescription = "展开模型列表")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                if (modelExpanded && modelList.isNotEmpty()) {
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        modelList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    model = m
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
                if (!provider.isLocal && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            isFetchingModels = true
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val service = com.example.inventorymaster.ai.provider.CloudAiService(
                                        AiProviderConfig(
                                            name = name,
                                            baseUrl = baseUrl,
                                            model = model,
                                            apiKey = apiKey
                                        )
                                    )
                                    val models = service.fetchModels()
                                    modelList = models
                                } catch (_: Exception) {
                                    modelList = emptyList()
                                } finally {
                                    isFetchingModels = false
                                }
                            }
                        },
                        enabled = !isFetchingModels
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(if (isFetchingModels) "获取中..." else "获取模型列表")
                    }
                }
                if (!provider.isLocal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = supportsTools,
                            onCheckedChange = { supportsTools = it }
                        )
                        Text("支持工具调用（Function Calling）")
                    }
                    Text(
                        "模型不支持工具调用时请取消勾选，AI 将改用 JSON 文本协议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        provider.copy(
                            name = name,
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey,
                            supportsTools = supportsTools
                        )
                    )
                },
                enabled = name.isNotBlank() && model.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
