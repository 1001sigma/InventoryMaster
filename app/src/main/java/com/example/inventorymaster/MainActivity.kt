package com.example.inventorymaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inventorymaster.ui.home.MainScreen
import com.example.inventorymaster.ui.inventory.InventoryScreen
import com.example.inventorymaster.ui.theme.InventoryMasterTheme
import com.example.inventorymaster.viewmodel.AppViewModelFactory
import com.example.inventorymaster.viewmodel.InventoryViewModel
import com.example.inventorymaster.viewmodel.SessionViewModel
import com.example.inventorymaster.viewmodel.SettingsViewModel
import androidx.compose.runtime.getValue
import com.example.inventorymaster.viewmodel.SyncViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as InventoryApplication
            // 1. 先拿到通用的工厂
            val appFactory = AppViewModelFactory(
                repository = app.repository,
                settingsRepository = app.settingsRepository
            )
            // 2. 分别获取两个 ViewModel
            val sessionViewModel: SessionViewModel = viewModel(factory = appFactory)
            val inventoryViewModel: InventoryViewModel = viewModel(factory = appFactory)
            val syncViewModel: SyncViewModel = viewModel(factory = appFactory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = appFactory) // 🔥 获取 ViewModel
            val settingsState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsState.themeMode) {
                1 -> false // 强制浅色
                2 -> true  // 强制深色
                else -> isSystemInDarkTheme() // 跟随系统 (默认)
            }
            InventoryMasterTheme(
                seedColor = androidx.compose.ui.graphics.Color(settingsState.seedColor), // 1. 传入当前选中的颜色
                useDynamicColor = settingsState.useDynamicColor, // 2. 传入动态颜色开关
                darkTheme = isDarkTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 1. 创建导航控制器 (相当于司机)
                    val navController = rememberNavController()
                    // 3. 定义导航路线图 (NavHost)
                    NavHost(navController = navController, startDestination = "home") {

                        // 路线 A: 首页
                        composable("home") {
                            MainScreen(
                                inventoryViewModel= inventoryViewModel,
                                sessionViewModel= sessionViewModel,
                                settingsViewModel = settingsViewModel,
                                syncViewModel = syncViewModel,
                                onSessionClick = { sessionId ->
                                    // 点击列表时，命令司机开车去 inventory 页面，并带上 id
                                    navController.navigate("inventory/$sessionId")
                                }
                            )
                        }

                        // 路线 B: 盘点详情页
                        // 格式: "inventory/{sessionId}" 表示这个路径接受一个参数
                        composable(
                            route = "inventory/{sessionId}",
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            // 取出传递过来的 id
                            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L

                            // 通知 ViewModel 选中了这个月 (用于后续加载数据)
                            inventoryViewModel.selectSession(sessionId)

                            // 显示详情页
                            InventoryScreen(
                                sessionId = sessionId,
                                inventoryViewModel= inventoryViewModel,
                                sessionViewModel= sessionViewModel,
                                syncViewModel = syncViewModel,
                                onBackClick = { navController.popBackStack() } // 返回上一页
                            )
                        }
                    }
                }
            }
        }
    }
}