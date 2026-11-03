package com.example.inventorymaster

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inventorymaster.ui.connection.ServerDiscoveryDialog
import com.example.inventorymaster.ui.ai.ChatScreen
import com.example.inventorymaster.ui.home.MainScreen
import com.example.inventorymaster.ui.inventory.InventoryScreen
import com.example.inventorymaster.ui.theme.InventoryMasterTheme
import com.example.inventorymaster.ui.theme.NavMotion
import com.example.inventorymaster.viewmodel.AppViewModelFactory
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import com.example.inventorymaster.ui.theme.ModernColorSchemes
import com.example.inventorymaster.viewmodel.SyncViewModel
import com.example.inventorymaster.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as InventoryApplication
            // 1. 先拿到通用的工厂
            val appFactory = AppViewModelFactory(
                application = application,
                repository = app.repository,
                settingsRepository = app.settingsRepository
            )
            // 2. 分别获取两个 ViewModel
            val sessionViewModel: SessionViewModel = viewModel(factory = appFactory)
            val inventoryViewModel: InventoryViewModel = viewModel(factory = appFactory)
            val syncViewModel: SyncViewModel = viewModel(factory = appFactory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = appFactory) // 🔥 获取 ViewModel
            val chatViewModel: ChatViewModel = viewModel(factory = appFactory)
            val settingsState by settingsViewModel.uiState.collectAsState()
            val isSettingsLoaded by settingsViewModel.isSettingsLoaded.collectAsState()
            val isDarkTheme = when (settingsState.themeMode) {
                1 -> false // 强制浅色
                2 -> true  // 强制深色
                else -> isSystemInDarkTheme() // 跟随系统 (默认)
            }
            val activeTuple = settingsState.getActiveColorTuple()
            InventoryMasterTheme(
                colorTuple = activeTuple,
                useDynamicColor = settingsState.useDynamicColor,
                darkTheme = isDarkTheme,
                isAmoledMode = settingsState.isAmoledMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isSettingsLoaded && settingsState.operatorName.isBlank()) {
                        com.example.inventorymaster.ui.home.NicknameSetupScreen(
                            onNameConfirmed = { name ->
                                settingsViewModel.setOperatorName(name)
                            }
                        )
                    } else {
                        // 1. 创建导航控制器 (相当于司机)
                        val navController = rememberNavController()
                    // 3. 定义导航路线图 (NavHost)
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = NavMotion.slideEnter,
                        exitTransition = NavMotion.slideExit,
                        popEnterTransition = NavMotion.slidePopEnter,
                        popExitTransition = NavMotion.slidePopExit
                    ) {

                        // 路线 A: 首页
                        composable(route = "home") {
                            MainScreen(
                                inventoryViewModel= inventoryViewModel,
                                sessionViewModel= sessionViewModel,
                                settingsViewModel = settingsViewModel,
                                syncViewModel = syncViewModel,
                                appViewModelFactory = appFactory,
                                onSessionClick = { sessionId ->
                                    // 点击列表时，命令司机开车去 inventory 页面，并带上 id
                                    navController.navigate("inventory/$sessionId")
                                },
                                onNavigateToSession = { sessionId ->
                                    navController.navigate("inventory/$sessionId")
                                },
                                onNavigateToAiChat = {
                                    navController.navigate("ai_chat?sessionId=0")
                                }
                            )
                        }

                        // 路线 B: 盘点详情页
                        composable(
                            route = "inventory/{sessionId}",
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            // 取出传递过来的 id
                            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L

                            // 显示详情页（selectSession 已移入 InventoryScreen 内部 LaunchedEffect）
                            InventoryScreen(
                                sessionId = sessionId,
                                inventoryViewModel = inventoryViewModel,
                                sessionViewModel = sessionViewModel,
                                syncViewModel = syncViewModel,
                                settingsViewModel = settingsViewModel,
                                chatViewModel = chatViewModel,
                                onBackClick = { navController.popBackStack() }, // 返回上一页
                                onNavigateToAiChat = { id ->
                                    navController.navigate("ai_chat?sessionId=$id")
                                }
                            )
                        }

                        // 路线 C: AI 聊天（外层路由——主页与任务详情页统一从外层导航至此；
                        // 曾漏放内层图导致盘库页入口 navigate 找不到目的地而闪退）
                        composable(
                            route = "ai_chat?sessionId={sessionId}",
                            arguments = listOf(
                                navArgument("sessionId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }
                            )
                        ) { backStackEntry ->
                            val chatSessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                            LaunchedEffect(chatSessionId) {
                                // sessionId>0 = 任务绑定会话（落到该任务最近会话）；0 = 主页自由会话（绝不自动绑任务）
                                chatViewModel.enterFromSession(chatSessionId)
                            }
                            ChatScreen(
                                viewModel = chatViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSession = { id ->
                                    navController.navigate("inventory/$id")
                                }
                            )
                        }
                    }

                    // 全局：服务端自动发现弹窗（挂在 NavHost 之上，覆盖「home」各标签页与盘存详情页）
                    val discoveryState by app.connectionManager.discoveryState.collectAsState()
                    ServerDiscoveryDialog(
                        state = discoveryState,
                        onServerSelected = app.connectionManager::onServerSelected,
                        onManualInput = app.connectionManager::onManualInputRequested,
                        onManualIpSubmitted = app.connectionManager::onManualIpSubmitted,
                        onDismiss = app.connectionManager::onDismissDiscovery
                    )
                    }
                }
            }
        }
    }
}