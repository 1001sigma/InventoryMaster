package com.example.inventorymaster.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.ai.AiProviderSettingsContent
import com.example.inventorymaster.viewmodel.SettingsViewModel

/**
 * AI 助手设置页（全屏页面：自带返回头部，顶栏底栏由 MainScreenNav 隐藏）。
 * 卡片风格与系统设置页一致（24dp 圆角、surface、elevation 2dp）。
 */
@Composable
fun AiSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
                text = "AI 助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            AiSettingsContent(viewModel = viewModel)
        }
    }
}

/**
 * AI 助手设置内容：思维链显示开关 + AI 服务商管理（含本地模型下载/导入）。
 * 原挂载于设置页「AI 助手」分组，2026-10-06 随独立设置页整体迁入。
 */
@Composable
fun AiSettingsContent(viewModel: SettingsViewModel) {
    val providers by viewModel.aiProviders.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val activeProvider = providers.find { it.id == activeProviderId }

        // 思维链开关（DeepSeek 风格折叠卡片；关闭=只显示一行工具提示）
        SettingsSwitch(
            title = "显示思考过程",
            subtitle = "聊天中展示 AI 的思考文本与每次工具调用的参数、结果详情（可折叠）",
            icon = Icons.Default.AutoAwesome,
            iconTint = MaterialTheme.colorScheme.primary,
            checked = uiState.aiShowThinking,
            onCheckedChange = { viewModel.setAiShowThinking(it) }
        )

        if (activeProvider != null) {
            Text(
                text = "当前服务商: ${activeProvider.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "模型: ${activeProvider.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "未配置 AI 服务商",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        AiProviderSettingsContent(
            viewModel = viewModel,
            providers = providers,
            activeProviderId = activeProviderId,
            onAdd = { viewModel.addAiProvider(it) },
            onActivate = { viewModel.setActiveProvider(it) },
            onEdit = { viewModel.updateAiProvider(it) },
            onDelete = { viewModel.deleteAiProvider(it) }
        )
    }
}
