package com.example.inventorymaster.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.inventorymaster.ui.productManager.ProductManagerScreen
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel

// 记得导入你原本的 HomeScreen，我们稍后会用到它

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    onSessionClick: (Long) -> Unit
) {
    val navController = rememberNavController()
    // 获取当前路由，用于控制底部栏高亮和顶部标题变化
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = when (currentRoute) {
                        "home" -> "\uD83D\uDCE6 库存盘点任务"
                        "products" -> "\uD83D\uDCDA 基础产品库"
                        "settings" -> "⚙\uFE0F 系统设置"
                        else -> "库存管家"
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                // 1. 列表页
                NavigationBarItem(
                    icon = { Icon(Icons.Default.ChecklistRtl, contentDescription = "任务") },
                    label = { Text("任务") },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                // 2. 产品字典页 (原悬浮按钮功能)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = "产品库") },
                    label = { Text("产品库") },
                    selected = currentRoute == "products",
                    onClick = {
                        navController.navigate("products") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                // 3. 设置页
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // --- 导航主机：决定中间显示什么 ---
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // 页面 A: 列表 (暂时先用 Text 占位，确认导航通了再搬代码)
            composable("home") {
                // TODO: 稍后在这里调用原来的 HomeScreen
                SessionListScreen(
                    sessionViewModel = sessionViewModel,
                    inventoryViewModel = inventoryViewModel, // 传进去，因为首页的大FAB需要用
                    onSessionClick = onSessionClick
                )
            }

            // 页面 B: 任务中心 (原悬浮按钮内容)
            composable("products") {
                // TODO: 稍后在这里写 TaskCenterScreen
                ProductManagerScreen(
//                    onBack = { }, // 在 Tab 模式下，不需要返回键了，留空即可
                    viewModel = inventoryViewModel
                )
            }

            // 页面 C: 设置
            composable("settings") {
                // TODO: 稍后在这里写 SettingsScreen
                SettingsScreen(viewModel= settingsViewModel)
            }
        }
    }
}

