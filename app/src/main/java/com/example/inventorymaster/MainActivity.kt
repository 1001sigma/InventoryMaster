package com.example.inventorymaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inventorymaster.ui.HomeScreen
import com.example.inventorymaster.ui.InventoryScreen
import com.example.inventorymaster.ui.theme.InventoryMasterTheme
import com.example.inventorymaster.viewmodel.InventoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InventoryMasterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 1. 创建导航控制器 (相当于司机)
                    val navController = rememberNavController()

                    // 2. 获取 ViewModel (在这里获取，为了在不同页面间共享数据)
                    val inventoryViewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)

                    // 3. 定义导航路线图 (NavHost)
                    NavHost(navController = navController, startDestination = "home") {

                        // 路线 A: 首页
                        composable("home") {
                            HomeScreen(
                                viewModel = inventoryViewModel,
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
                                viewModel = inventoryViewModel,
                                onBackClick = { navController.popBackStack() } // 返回上一页
                            )
                        }
                    }
                }
            }
        }
    }
}