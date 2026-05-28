package com.example.inventorymaster.ui.home

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import java.net.URLEncoder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.inventorymaster.viewmodel.AppViewModelFactory
import androidx.navigation.navigation
import com.example.inventorymaster.batchscanner.BatchScannerScreen
import com.example.inventorymaster.batchscanner.InventoryTaskDetailScreen
import com.example.inventorymaster.ui.analyzer.ScanScreen
import com.example.inventorymaster.R

// 记得导入你原本的 HomeScreen，我们稍后会用到它

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    syncViewModel: SyncViewModel,
    appViewModelFactory: AppViewModelFactory,
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
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                            "home" -> " 库存盘点任务"
                            "products" -> " 基础产品库"
                            "settings" -> " 系统设置"
                            else -> "库存管家"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!isInBatchScanFlow) {
                NavigationBar (
                    containerColor = MaterialTheme.colorScheme.surface,
                    // 优化4：将 tonalElevation 设为 0.dp 或极小值，去掉默认的淡淡灰色蒙层，极致通透
                    tonalElevation = 0.dp
                ) {
                    val isHomeSelected = currentRoute == "home"
                    // 1. 列表页
                    NavigationBarItem(
                        selected = isHomeSelected,
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            // 🌟 预留位：在这里替换你的图标
                            Icon(
                                painter = if (isHomeSelected) {
                                    painterResource(id = R.drawable.todo_fill) // TODO: 替换为你下载的【面性/实心】任务图标
                                } else {
                                    painterResource(id = R.drawable.todo_line) // TODO: 替换为你下载的【线性/空心】任务图标
                                },
                                contentDescription = "任务"
                            )
                        },
                        label = { Text("任务") },
                        // 优化5：精细控制各个状态的颜色对比度
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer, // 选中的药丸底色
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, // 选中时的图标颜色（深色）
                            selectedTextColor = MaterialTheme.colorScheme.onSurface, // 选中时的文字颜色（加深）
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, // 未选中的图标颜色（淡灰）
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant // 未选中的文字颜色（淡灰）
                        )
                    )

                    // 2. 产品字典页 (原悬浮按钮功能)
                    val isProductsSelected = currentRoute == "products"
                    NavigationBarItem(
                        selected = isProductsSelected,
                        onClick = {
                            navController.navigate("products") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            // 🌟 预留位：在这里替换你的图标
                            Icon(
                                painter = if (isProductsSelected) {
                                    painterResource(id = R.drawable.database_2_fill) // TODO: 替换为你下载的【面性/实心】任务图标
                                } else {
                                    painterResource(id = R.drawable.database_2_line) // TODO: 替换为你下载的【线性/空心】任务图标
                                },
                                contentDescription = "产品库"
                            )
                        },
                        label = { Text("产品库") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    // 3. 设置页
                    val isSettingsSelected = currentRoute == "settings"
                    NavigationBarItem(
                        selected = isSettingsSelected,
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            // 🌟 预留位：在这里替换你的图标
                            Icon(
                                painter = if (isSettingsSelected) {
                                    painterResource(id = R.drawable.settings_3_fill) // TODO: 替换为你下载的【面性/实心】任务图标
                                } else {
                                    painterResource(id = R.drawable.settings_3_line) // TODO: 替换为你下载的【线性/空心】任务图标
                                },
                                contentDescription = "设置"
                            )
                        },
                        label = { Text("设置") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            composable(
                route = "home",
                enterTransition = { fadeIn(initialAlpha = 0.4f) },
                exitTransition = { fadeOut(targetAlpha = 0.4f) }
            ) {
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
            composable(
                route = "products",
                enterTransition = { fadeIn(initialAlpha = 0.4f) },
                exitTransition = { fadeOut(targetAlpha = 0.4f) }
            ) {
                ProductManagerScreen( //
                    viewModel = inventoryViewModel
                )
            }


            // 页面 C: 设置
            composable(
                route = "settings",
                enterTransition = { fadeIn(initialAlpha = 0.4f) },
                exitTransition = { fadeOut(targetAlpha = 0.4f) }
            ) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            // 使用 navigation 将这三个页面打包。进入这个 flow 默认先显示 "taskDetail"
            navigation(route = "batch_scan_flow", startDestination = "taskDetail") {

                // 页面 A: 查验详情UI (大表单)
                composable(
                    route = "taskDetail",
                    enterTransition = { fadeIn(initialAlpha = 0.5f) },
                    exitTransition = { fadeOut(targetAlpha = 0.5f) }
                ) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)

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
                    ),
                    enterTransition = { fadeIn(initialAlpha = 0.5f) },
                    exitTransition = { fadeOut(targetAlpha = 0.5f) }
                ) { backStackEntry ->
                    // 获取跟 taskDetail 同一个 ViewModel 实例
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)

                    // 从导航参数中解析传入的图片 Uri（可选）
                    val inputUriStr = backStackEntry.arguments?.getString("inputUri")
                    val inputUri = inputUriStr?.let { Uri.parse(it) }

                    // 从当前单据提取 DI 列表，供扫码器核对（不在单据内的码打叉）
                    val targetDiList = mainVM.getTargetDiList()

                    BatchScannerScreen(
                        inputUri = inputUri,
                        targetList = targetDiList,
                        onComplete = { results, bitmap, barcodes ->
                            mainVM.processScannerResults(listOf(bitmap), results, barcodes)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }

                // 页面 C: 单码相机工具 (ScanScreen)
                composable(
                    route = "singleCamera",
                    enterTransition = { fadeIn(initialAlpha = 0.5f) },
                    exitTransition = { fadeOut(targetAlpha = 0.5f) }
                ) { backStackEntry ->
                    // 1. 获取共享的 ViewModel
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)

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