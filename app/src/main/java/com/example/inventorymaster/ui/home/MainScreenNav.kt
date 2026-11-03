package com.example.inventorymaster.ui.home

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.scale
import com.example.inventorymaster.ui.theme.NavMotion
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.navigation.navigation
import com.example.inventorymaster.R
import com.example.inventorymaster.batchscanner.core.screen.BatchScannerScreen
import com.example.inventorymaster.batchscanner.task.viewmodel.InventoryMainViewModel
import com.example.inventorymaster.batchscanner.task.screen.InventoryTaskDetailScreen
import com.example.inventorymaster.batchscanner.videostream.screen.VideoStreamScreen
import com.example.inventorymaster.ui.productManager.ProductManagerScreen
import com.example.inventorymaster.utils.pdf.WatermarkSettingsScreen
import com.example.inventorymaster.utils.pdf.SignatureSettingsScreen
import com.example.inventorymaster.ui.home.AiSettingsScreen
import com.example.inventorymaster.viewmodel.AppViewModelFactory
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel
import com.example.inventorymaster.viewmodel.SyncViewModel
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import java.net.URLEncoder

// 记得导入你原本的 HomeScreen，我们稍后会用到它

/**
 * 监听软键盘弹出状态（兼容非 edge-to-edge + adjustResize 场景）
 */
@Composable
private fun rememberIsKeyboardOpen(): Boolean {
    val view = LocalView.current
    var isKeyboardOpen by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val isOpen = keypadHeight > screenHeight * 0.15
            if (isKeyboardOpen != isOpen) {
                isKeyboardOpen = isOpen
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return isKeyboardOpen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel,
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    syncViewModel: SyncViewModel,
    appViewModelFactory: AppViewModelFactory,
    onSessionClick: (Long) -> Unit,
    onNavigateToSession: (Long) -> Unit = {},
    onNavigateToAiChat: () -> Unit = {}
) {
    val navController = rememberNavController()
    // 获取当前路由，用于控制底部栏高亮和顶部标题变化
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 判断当前软键盘是否弹起（弹起时隐藏底部导航栏，避免被顶在键盘上方挤压内容区）
    val isKeyboardOpen = rememberIsKeyboardOpen()

    // 判断当前是否处于"批量盘点"流程中，隐藏顶部和底部导航栏
    val isInBatchScanFlow = currentRoute in listOf("taskDetail", "batchCamera", "singleCamera", "videoStream") ||
            currentRoute.startsWith("batchCamera?")

    // 判断当前是否处于全屏页面（隐藏顶部和底部导航栏）
    // 水印/签名/AI 设置页走页内返回头部，同样全屏（遮住底部导航栏）
    val isFullScreen = isInBatchScanFlow || currentRoute == "session_list" ||
            currentRoute in listOf("watermark_settings", "signature_settings", "ai_settings")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isFullScreen) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                            "home" -> " 主页"
                            "session_list" -> " 库存任务"
                            "products" -> " 基础产品库"
                            "settings" -> " 系统设置"
                            "watermark_settings" -> "水印设置"
                            "signature_settings" -> "签名设置"
                            else -> ""
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
            if (!isFullScreen && !isKeyboardOpen) {
                NavigationBar (
                    containerColor = MaterialTheme.colorScheme.surface,
                    // 优化4：将 tonalElevation 设为 0.dp 或极小值，去掉默认的淡淡灰色蒙层，极致通透
                    tonalElevation = 0.dp
                ) {
                    val isHomeSelected = currentRoute == "home"
                    val homeScale by animateFloatAsState(
                        targetValue = if (isHomeSelected) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "homeIconScale"
                    )
                    // 1. 列表页
                    NavigationBarItem(
                        selected = isHomeSelected,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = if (isHomeSelected) {
                                    painterResource(id = R.drawable.todo_fill)
                                } else {
                                    painterResource(id = R.drawable.todo_line)
                                },
                                contentDescription = "任务",
                                modifier = Modifier.scale(homeScale)
                            )
                        },
                        label = { Text("任务") },
                        // 优化5：精细控制各个状态的颜色对比度
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // 2. 产品字典页 (原悬浮按钮功能)
                    val isProductsSelected = currentRoute == "products"
                    val productsScale by animateFloatAsState(
                        targetValue = if (isProductsSelected) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "productsIconScale"
                    )
                    NavigationBarItem(
                        selected = isProductsSelected,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            navController.navigate("products") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = if (isProductsSelected) {
                                    painterResource(id = R.drawable.database_2_fill)
                                } else {
                                    painterResource(id = R.drawable.database_2_line)
                                },
                                contentDescription = "产品库",
                                modifier = Modifier.scale(productsScale)
                            )
                        },
                        label = { Text("产品库") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    // 3. 设置页
                    val isSettingsSelected = currentRoute == "settings"
                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsSelected) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "settingsIconScale"
                    )
                    NavigationBarItem(
                        selected = isSettingsSelected,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = if (isSettingsSelected) {
                                    painterResource(id = R.drawable.settings_3_fill)
                                } else {
                                    painterResource(id = R.drawable.settings_3_line)
                                },
                                contentDescription = "设置",
                                modifier = Modifier.scale(settingsScale)
                            )
                        },
                        label = { Text("设置") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // --- 导航主机：尺寸恒定铺满屏幕，消除全屏切页时的容器尺寸突变与重排抖动 ---
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = NavMotion.slideEnter,
            exitTransition = NavMotion.slideExit,
            popEnterTransition = NavMotion.slidePopEnter,
            popExitTransition = NavMotion.slidePopExit,
            modifier = Modifier.fillMaxSize()
        ) {
            // 页面 A: 主页入口 (一级 Tab，应用脚手架内边距)
            composable(
                route = "home",
                enterTransition = NavMotion.tabEnter,
                exitTransition = NavMotion.tabExit
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    HomeScreen(
                        onNavigateToSessionList = {
                            navController.navigate("session_list")
                        },
                        onNavigateToTaskDetail = {
                            navController.navigate("taskDetail")
                        },
                        onNavigateToAiChat = onNavigateToAiChat
                    )
                }
            }

            // 页面 A1: 任务列表 (二级推栈页，自带全屏 Scaffold 与 TopAppBar)
            composable(route = "session_list") {
                SessionListScreen(
                    sessionViewModel = sessionViewModel,
                    inventoryViewModel = inventoryViewModel,
                    syncViewModel = syncViewModel,
                    settingsViewModel = settingsViewModel,
                    onSessionClick = onSessionClick,
                    onBack = { navController.popBackStack() }
                )
            }

            // 页面 B: 产品库 (一级 Tab，应用脚手架内边距)
            composable(
                route = "products",
                enterTransition = NavMotion.tabEnter,
                exitTransition = NavMotion.tabExit
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ProductManagerScreen(
                        viewModel = inventoryViewModel
                    )
                }
            }


            // 页面 C: 系统设置 (一级 Tab，应用脚手架内边距)
            composable(
                route = "settings",
                enterTransition = NavMotion.tabEnter,
                exitTransition = NavMotion.tabExit
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        inventoryViewModel = inventoryViewModel,
                        onNavigateToWatermark = { navController.navigate("watermark_settings") },
                        onNavigateToSignature = { navController.navigate("signature_settings") },
                        onNavigateToAiSettings = { navController.navigate("ai_settings") }
                    )
                }

            }

            // 👇 水印设置页面的路由（全屏，页内返回头部）
            composable("watermark_settings") {
                WatermarkSettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 电子签名库页面（全屏，页内返回头部；管理手绘签名素材与导出盖章默认参数）
            composable("signature_settings") {
                SignatureSettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // AI 助手设置页（全屏，页内返回头部；服务商管理 + 本地模型 + 思维链显示）
            composable("ai_settings") {
                AiSettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // AI 聊天页已迁至外层 NavHost（MainActivity）——任务详情页与主页都从外层导航至此，
            // 内层图路由不跨层可见（ai_chat 曾漏放内层导致盘库页入口闪退）


            // 使用 navigation 将这三个页面打包。进入这个 flow 默认先显示 "taskDetail"
            navigation(route = "batch_scan_flow", startDestination = "taskDetail") {

                // 页面 A: 查验详情UI (大表单)
                composable(route = "taskDetail") { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)

                    InventoryTaskDetailScreen(
                        viewModel = mainVM,
                        settingsViewModel = settingsViewModel,
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
                        onNavigateToVideoStream = {
                            navController.navigate("videoStream")
                        }
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
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)

                    // 从导航参数中解析传入的图片 Uri（可选）
                    val inputUriStr = backStackEntry.arguments?.getString("inputUri")
                    val inputUri = inputUriStr?.let { Uri.parse(it) }

                    // 从当前单据提取 DI 列表，供扫码器核对（不在单据内的码打叉）
                    val targetDiList = mainVM.getTargetDiList()

                    BatchScannerScreen(
                        inputUri = inputUri,
                        targetList = targetDiList,
                        onComplete = { results, photoGroups, _ ->
                            // 照片与条码按图分组回传：轮播区逐张展示，PDF 导出每图一页
                            mainVM.processScannerResultsGrouped(photoGroups, results)
                            navController.popBackStack()
                        },
                        onOcrConfirmed = { ocrData ->
                            mainVM.processOcrResults(
                                batch = ocrData.batch,
                                prodDate = ocrData.prodDate,
                                expDate = ocrData.expDate
                            )
                        },
                        onClose = { navController.popBackStack() }
                    )
                }

                // 页面 C: 视频流采集
                composable(route = "videoStream") { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("batch_scan_flow")
                    }
                    val mainVM: InventoryMainViewModel = viewModel(parentEntry, factory = appViewModelFactory)
                    val targetDiList = mainVM.getTargetDiList()

                    VideoStreamScreen(
                        targetList = targetDiList,
                        onComplete = { results, videoUri, barcodes ->
                            mainVM.processScannerResults(listOf(videoUri), results, barcodes)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }

            }
        }
    }
}