package com.example.inventorymaster.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.padding
import java.net.URLEncoder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inventorymaster.batchscanner.InventoryMainViewModel
import com.example.inventorymaster.ui.productManager.ProductManagerScreen
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel
import com.example.inventorymaster.viewmodel.SyncViewModel
import androidx.navigation.navigation
import com.example.inventorymaster.batchscanner.BatchScannerScreen
import com.example.inventorymaster.batchscanner.InventoryTaskDetailScreen
import com.example.inventorymaster.ui.analyzer.ScanScreen

// 记得导入你原本的 HomeScreen，我们稍后会用到它

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    syncViewModel: SyncViewModel,
    onSessionClick: (Long) -> Unit
) {
    val navController = rememberNavController()
    // 获取当前路由，用于控制底部栏高亮和顶部标题变化
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    // 判断当前是否处于"批量盘点"流程中，隐藏顶部和底部导航栏
    val isInBatchScanFlow = currentRoute in listOf("taskDetail", "batchCamera", "singleCamera") ||
            currentRoute.startsWith("batchCamera?")

    Scaffold(
        topBar = {
            if (!isInBatchScanFlow) {
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
            }
        },
        bottomBar = {
            if (!isInBatchScanFlow) {
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
        }
    ) { innerPadding ->
        // --- 导航主机：决定中间显示什么 ---
        NavHost(
            navController = navController,
            startDestination = "home",
            // 在批量盘点流程中不再添加 padding，让内容真正填满屏幕
            modifier = if (isInBatchScanFlow) Modifier else Modifier.padding(innerPadding)
        ) {
            // 页面 A: 列表 (暂时先用 Text 占位，确认导航通了再搬代码)
            composable("home") {
                // TODO: 稍后在这里调用原来的 HomeScreen
                SessionListScreen(
                    sessionViewModel = sessionViewModel,
                    inventoryViewModel = inventoryViewModel, // 传进去，因为首页的大FAB需要用
                    syncViewModel = syncViewModel,
                    onSessionClick = onSessionClick,
                    onCreateNewTask = {
                        navController.navigate("taskDetail")
                    }
                )
            }

            // 页面 B: 任务中心 (原悬浮按钮内容)
            composable("products") {
                // TODO: 稍后在这里写 TaskCenterScreen
                ProductManagerScreen( //
                    viewModel = inventoryViewModel
                )
            }


            // 页面 C: 设置
            composable("settings") {
                // TODO: 稍后在这里写 SettingsScreen
                SettingsScreen(viewModel = settingsViewModel)
            }

            // 使用 navigation 将这三个页面打包。进入这个 flow 默认先显示 "taskDetail"
            navigation(route = "batch_scan_flow", startDestination = "taskDetail") {

                // 页面 A: 查验详情UI (大表单)
                composable("taskDetail") { backStackEntry ->
                    // 核心魔法：把 ViewModel 的生命周期绑定到 "batch_scan_flow" 这个流程上
                    // 只要你不退出这个大流程，相机和表单之间怎么来回跳，数据都在！退回主页则自动销毁。
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry)

                    InventoryTaskDetailScreen(
                        viewModel = mainVM,
                        onNavigateToBatchCamera = { inputUri ->
                            // 如果传入了外部图片 Uri，通过导航参数传给 BatchScannerScreen
                            if (inputUri != null) {
                                // URL编码确保 Uri 中的特殊字符（如 : / #）不破坏路由
                                val encodedUri = URLEncoder.encode(inputUri, "UTF-8")
                                navController.navigate("batchCamera?inputUri=$encodedUri")
                            } else {
                                navController.navigate("batchCamera")
                            }
                        },
                        onNavigateToSingleScanner = {
                            navController.navigate("singleCamera")
                        },
//                        onClose = {
//                            // 退出整个查验流程，回到主页
//                            navController.popBackStack()
//                        }
                    )
                }

                // 页面 B: 批量相机工具（支持通过 inputUri 传入外部图片）
                composable(
                    route = "batchCamera?inputUri={inputUri}",
                    arguments = listOf(
                        navArgument("inputUri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    // 获取跟 taskDetail 同一个 ViewModel 实例
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry)

                    // 从导航参数中解析传入的图片 Uri（可选）
                    val inputUriStr = backStackEntry.arguments?.getString("inputUri")
                    val inputUri = inputUriStr?.let { Uri.parse(it) }

                    BatchScannerScreen(
                        inputUri = inputUri,
                        targetList = null,
                        onComplete = { results, bitmap ->
                            // 1. 把相机拍到的单张图片包装成 List，连同条码丢给 ViewModel 解析
                            mainVM.processScannerResults(listOf(bitmap), results)

                            // 2. 数据处理完，安全退回表单详情页
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }

                // 页面 C: 单码相机工具
                // 页面 C: 单码相机工具 (ScanScreen)
                composable("singleCamera") { backStackEntry ->
                    // 1. 获取共享的 ViewModel
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry)

                    // 2. 调用你的单码扫码屏幕
                    // 注意这里可能需要 import com.example.inventorymaster.ui.analyzer.ScanScreen
                    ScanScreen(
                        onScanResult = { barcodeString ->
                            // 【核心接通】单码扫完了，把字符串包成 List 传给 ViewModel 解析。
                            // 由于单码模式不传/不需要保存全景照片，Bitmap 传 emptyList() 即可。
                            mainVM.processScannerResults(
                                bitmaps = emptyList(),
                                rawBarcodes = listOf(barcodeString)
                            )

                            // 数据处理完，安全退回表单详情页
                            navController.popBackStack()
                        },
                        onClose = {
                            // 点击关闭/返回，直接退回表单页
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}