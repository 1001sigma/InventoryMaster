package com.example.inventorymaster.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ai.local.HardwareConfig
import com.example.inventorymaster.ai.local.LocalModelCatalog
import com.example.inventorymaster.ai.provider.AiProviderConfig
import com.example.inventorymaster.ai.provider.CloudAiService
import com.example.inventorymaster.ai.provider.ProviderPresets
import com.example.inventorymaster.ui.components.AppTextField
import com.example.inventorymaster.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            viewModel = viewModel,
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
            viewModel = viewModel,
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
            viewModel = viewModel,
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
    viewModel: SettingsViewModel,
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
    viewModel: SettingsViewModel,
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
    viewModel: SettingsViewModel,
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
    viewModel: SettingsViewModel,
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
                        onUpdateProvider = { onEdit(it) },
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
            viewModel = viewModel,
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
            viewModel = viewModel,
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
    onUpdateProvider: (AiProviderConfig) -> Unit,
    onDelete: () -> Unit
) {
    var showModelMenu by remember { mutableStateOf(false) }

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
                    Box(modifier = Modifier.padding(top = 2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .then(
                                    if (provider.cachedModels.isNotEmpty()) {
                                        Modifier.clickable { showModelMenu = true }
                                    } else Modifier
                                )
                        ) {
                            Text(
                                text = "模型: ${provider.model}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (provider.cachedModels.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "切换模型",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (showModelMenu && provider.cachedModels.isNotEmpty()) {
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                provider.cachedModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = m,
                                                fontWeight = if (m == provider.model) FontWeight.Bold else FontWeight.Normal,
                                                color = if (m == provider.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            showModelMenu = false
                                            if (m != provider.model) {
                                                onUpdateProvider(provider.copy(model = m))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val paramTags = buildList {
                        if (provider.temperatureEnabled) add("T:${provider.temperature}")
                        if (provider.topPEnabled) add("P:${provider.topP}")
                        if (provider.maxTokensEnabled) add("Max:${provider.maxTokens}")
                    }
                    if (paramTags.isNotEmpty()) {
                        Text(
                            text = "参数: " + paramTags.joinToString(" | "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

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
    viewModel: SettingsViewModel,
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
    var fetchError by remember { mutableStateOf<String?>(null) }
    var modelExpanded by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(true) }

    var temperatureEnabled by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topPEnabled by remember { mutableStateOf(false) }
    var topP by remember { mutableFloatStateOf(1.0f) }
    var maxTokensEnabled by remember { mutableStateOf(false) }
    var maxTokens by remember { mutableIntStateOf(4096) }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择预设模板") },
            text = {
                LazyColumn {
                    items(ProviderPresets.presets) { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            supportingContent = {
                                Text(
                                    preset.config.baseUrl.ifBlank {
                                        if (preset.config.isLocal) "本地模型" else "预设端点"
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                name = preset.config.name
                                baseUrl = preset.config.baseUrl
                                model = preset.config.model
                                isLocal = preset.config.isLocal
                                supportsTools = preset.config.supportsTools
                                temperatureEnabled = preset.config.temperatureEnabled
                                temperature = preset.config.temperature
                                topPEnabled = preset.config.topPEnabled
                                topP = preset.config.topP
                                maxTokensEnabled = preset.config.maxTokensEnabled
                                maxTokens = preset.config.maxTokens
                                showPresets = false
                            }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("自定义服务商") },
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = model,
                            onValueChange = {
                                model = it
                                fetchError = null
                            },
                            label = "模型",
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (modelList.isNotEmpty()) {
                                    IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "展开模型列表")
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
                    }

                    if (!isLocal && baseUrl.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    isFetchingModels = true
                                    fetchError = null
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val result = withContext(Dispatchers.IO) {
                                            val tempConfig = AiProviderConfig(
                                                name = name,
                                                baseUrl = baseUrl,
                                                model = model,
                                                apiKey = apiKey
                                            )
                                            val resolved = tempConfig.resolveModelsEndpoint()
                                            val service = CloudAiService(tempConfig.copy(modelsEndpoint = resolved))
                                            service.fetchModelsResult()
                                        }
                                        isFetchingModels = false
                                        result.onSuccess { models ->
                                            modelList = models
                                            if (models.isNotEmpty()) {
                                                if (model.isBlank()) {
                                                    model = models.first()
                                                }
                                                modelExpanded = true
                                            }
                                        }.onFailure { err ->
                                            fetchError = err.message ?: "获取模型列表失败"
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
                        if (fetchError != null) {
                            Text(
                                text = fetchError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isLocal,
                            onCheckedChange = { isLocal = it }
                        )
                        Text("本地模型")
                    }
                    if (isLocal) {
                        LocalModelFileSection(modelId = model, viewModel = viewModel)
                    }

                    if (!isLocal) {
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

                        Spacer(modifier = Modifier.height(4.dp))

                        AdvancedModelParametersSection(
                            temperatureEnabled = temperatureEnabled,
                            onTemperatureEnabledChange = { temperatureEnabled = it },
                            temperature = temperature,
                            onTemperatureChange = { temperature = it },
                            topPEnabled = topPEnabled,
                            onTopPEnabledChange = { topPEnabled = it },
                            topP = topP,
                            onTopPChange = { topP = it },
                            maxTokensEnabled = maxTokensEnabled,
                            onMaxTokensEnabledChange = { maxTokensEnabled = it },
                            maxTokens = maxTokens,
                            onMaxTokensChange = { maxTokens = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val baseConfig = AiProviderConfig(
                            name = name,
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey
                        )
                        onConfirm(
                            baseConfig.copy(
                                isLocal = isLocal,
                                supportsTools = supportsTools,
                                modelsEndpoint = baseConfig.resolveModelsEndpoint(),
                                cachedModels = if (modelList.isNotEmpty()) modelList else emptyList(),
                                temperatureEnabled = temperatureEnabled,
                                temperature = temperature,
                                topPEnabled = topPEnabled,
                                topP = topP,
                                maxTokensEnabled = maxTokensEnabled,
                                maxTokens = maxTokens,
                                localModelPath = if (isLocal && viewModel.isLocalModelAvailable(model)) {
                                    viewModel.localModelPathFor(model)
                                } else {
                                    null
                                }
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
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (AiProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var model by remember { mutableStateOf(provider.model) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var modelList by remember { mutableStateOf(provider.cachedModels) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var modelExpanded by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(provider.supportsTools) }

    var temperatureEnabled by remember { mutableStateOf(provider.temperatureEnabled) }
    var temperature by remember { mutableFloatStateOf(provider.temperature) }
    var topPEnabled by remember { mutableStateOf(provider.topPEnabled) }
    var topP by remember { mutableFloatStateOf(provider.topP) }
    var maxTokensEnabled by remember { mutableStateOf(provider.maxTokensEnabled) }
    var maxTokens by remember { mutableIntStateOf(provider.maxTokens) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务商") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            fetchError = null
                        },
                        label = "模型",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (modelList.isNotEmpty()) {
                                IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "展开模型列表")
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
                }

                if (!provider.isLocal && baseUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                isFetchingModels = true
                                fetchError = null
                                CoroutineScope(Dispatchers.Main).launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val tempConfig = AiProviderConfig(
                                            name = name,
                                            baseUrl = baseUrl,
                                            model = model,
                                            apiKey = apiKey,
                                            modelsEndpoint = provider.modelsEndpoint
                                        )
                                        val resolved = tempConfig.resolveModelsEndpoint()
                                        val service = CloudAiService(tempConfig.copy(modelsEndpoint = resolved))
                                        service.fetchModelsResult()
                                    }
                                    isFetchingModels = false
                                    result.onSuccess { models ->
                                        modelList = models
                                        if (models.isNotEmpty()) {
                                            modelExpanded = true
                                        }
                                    }.onFailure { err ->
                                        fetchError = err.message ?: "获取模型列表失败"
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
                    if (fetchError != null) {
                        Text(
                            text = fetchError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
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

                    Spacer(modifier = Modifier.height(4.dp))

                    AdvancedModelParametersSection(
                        temperatureEnabled = temperatureEnabled,
                        onTemperatureEnabledChange = { temperatureEnabled = it },
                        temperature = temperature,
                        onTemperatureChange = { temperature = it },
                        topPEnabled = topPEnabled,
                        onTopPEnabledChange = { topPEnabled = it },
                        topP = topP,
                        onTopPChange = { topP = it },
                        maxTokensEnabled = maxTokensEnabled,
                        onMaxTokensEnabledChange = { maxTokensEnabled = it },
                        maxTokens = maxTokens,
                        onMaxTokensChange = { maxTokens = it }
                    )
                }

                if (provider.isLocal) {
                    LocalModelFileSection(modelId = model, viewModel = viewModel)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resolvedEndpoint = if (provider.modelsEndpoint.isNotBlank()) {
                        provider.modelsEndpoint
                    } else {
                        AiProviderConfig(name = name, baseUrl = baseUrl, model = model, apiKey = apiKey).resolveModelsEndpoint()
                    }
                    onConfirm(
                        provider.copy(
                            name = name,
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey,
                            supportsTools = supportsTools,
                            modelsEndpoint = resolvedEndpoint,
                            cachedModels = if (modelList.isNotEmpty()) modelList else provider.cachedModels,
                            temperatureEnabled = temperatureEnabled,
                            temperature = temperature,
                            topPEnabled = topPEnabled,
                            topP = topP,
                            maxTokensEnabled = maxTokensEnabled,
                            maxTokens = maxTokens,
                            localModelPath = when {
                                !provider.isLocal -> provider.localModelPath
                                viewModel.isLocalModelAvailable(model) -> viewModel.localModelPathFor(model)
                                else -> null
                            }
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

@Composable
private fun AdvancedModelParametersSection(
    temperatureEnabled: Boolean,
    onTemperatureEnabledChange: (Boolean) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    topPEnabled: Boolean,
    onTopPEnabledChange: (Boolean) -> Unit,
    topP: Float,
    onTopPChange: (Float) -> Unit,
    maxTokensEnabled: Boolean,
    onMaxTokensEnabledChange: (Boolean) -> Unit,
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "高级模型超参数",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "温度 (Temperature)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (temperatureEnabled) String.format("%.2f", temperature) else "默认 (未开启)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (temperatureEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = temperatureEnabled,
                        onCheckedChange = onTemperatureEnabledChange
                    )
                }
                if (temperatureEnabled) {
                    Slider(
                        value = temperature,
                        onValueChange = { onTemperatureChange((it * 20).roundToInt() / 20f) },
                        valueRange = 0.0f..2.0f,
                        steps = 39,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "数值越低回答越严谨确定，数值越高回答更具创造性。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 2. Top_P
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "核采样 (Top P)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (topPEnabled) String.format("%.2f", topP) else "默认 (未开启)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (topPEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = topPEnabled,
                        onCheckedChange = onTopPEnabledChange
                    )
                }
                if (topPEnabled) {
                    Slider(
                        value = topP,
                        onValueChange = { onTopPChange((it * 20).roundToInt() / 20f) },
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "概率采样阈值，通常建议与温度二选一进行调节。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 3. Max Tokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最大输出 (Max Tokens)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (maxTokensEnabled) "$maxTokens" else "默认 (厂商无限制或预设)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (maxTokensEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = maxTokensEnabled,
                        onCheckedChange = onMaxTokensEnabledChange
                    )
                }
                if (maxTokensEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1024, 2048, 4096, 8192).forEach { tokens ->
                            FilterChip(
                                selected = maxTokens == tokens,
                                onClick = { onMaxTokensChange(tokens) },
                                label = { Text("$tokens", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AppTextField(
                        value = if (maxTokens > 0) maxTokens.toString() else "",
                        onValueChange = { str ->
                            val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                            onMaxTokensChange(num)
                        },
                        label = "自定义 Token 上限",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                }
                Text(
                    text = "单次对话模型生成的最大 Token 数量限制。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 本地模型文件区块（Add/Edit 对话框 isLocal 时显示）：
 * 目录收录模型（LocalModelCatalog）→ 显示体积 + 服务器下载（断点续传 + SHA-256 校验）；
 * 未收录模型 → 仅允许 SAF 手动导入 .litertlm 文件。
 * 模型文件是否就绪决定对话框保存时的 localModelPath（LocalAiService.isAvailable 依据）。
 */
@Composable
private fun LocalModelFileSection(
    modelId: String,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val state by viewModel.localModelState.collectAsState()
    val entry = LocalModelCatalog.byId(modelId)
    var available by remember(modelId) { mutableStateOf(viewModel.isLocalModelAvailable(modelId)) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            available = viewModel.isLocalModelAvailable(modelId)
            if (state.isSuccess) delay(4000)
            viewModel.clearLocalModelMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importLocalModel(modelId, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text("模型文件（.litertlm）", style = MaterialTheme.typography.labelLarge)
        Text(
            text = when {
                available -> "已就绪：${entry?.displayName ?: modelId}"
                entry != null -> "未下载（${entry.sizeBytes / (1024 * 1024)}MB）"
                else -> "未导入（$modelId 未收录于模型目录，仅支持手动导入）"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (entry != null && !available &&
            HardwareConfig.getDeviceRamMb(context) < entry.minRamGb * 1024
        ) {
            Text(
                "⚠ 设备内存低于 ${entry.minRamGb}GB，运行该模型可能卡顿或崩溃",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.busyModelId == modelId) {
            if (state.downloadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry != null) {
                TextButton(
                    onClick = { viewModel.downloadLocalModel(modelId) },
                    enabled = state.busyModelId == null
                ) {
                    Text(if (state.busyModelId == modelId) "下载中..." else "从服务器下载")
                }
            }
            TextButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = state.busyModelId == null
            ) {
                Text("从文件导入")
            }
        }
        state.message?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isSuccess) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}
