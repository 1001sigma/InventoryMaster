package com.example.inventorymaster.utils.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.inventorymaster.ui.components.ModernAlertDialog
import com.example.inventorymaster.ui.components.ModernDialogButtonBar

/**
 * 业务权限统一定义与文案收敛枚举
 */
enum class AppPermission(
    val permission: String,
    val title: String,
    val rationale: String,
    val deniedToast: String
) {
    CAMERA(
        Manifest.permission.CAMERA,
        "相机权限",
        "盘存扫码及文字识别需要使用相机权限",
        "需要相机权限才能扫码"
    )
}

/**
 * 安全从 Compose Context 中递归获取 Activity
 */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 统一权限 Hook：声明权限与授权成功后的动作，返回一个可随时调用的执行函数
 *
 * 特性：
 * 1. 已授权：静默直达 [onGranted]，不产生任何多余操作；
 * 2. 首次/临时拒绝：展示友好的 Toast 提示；
 * 3. 永久拒绝（勾选了不再提示）：自动弹出 [PermissionSettingsDialog]，引导用户一键前往系统设置页开启。
 *
 * 用法：
 * ```kotlin
 * val requestCamera = rememberPermissionAction(AppPermission.CAMERA) {
 *     showScanner = true
 * }
 * Button(onClick = { requestCamera() }) { Text("扫码") }
 * ```
 */
@Composable
fun rememberPermissionAction(
    permission: AppPermission,
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showGoSettingsDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            // 判断是否永久拒绝（用户选择了“不再询问”并拒绝）
            val shouldRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, permission.permission)
            } ?: true

            if (!shouldRationale) {
                showGoSettingsDialog = true
            } else {
                Toast.makeText(context, permission.deniedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showGoSettingsDialog) {
        PermissionSettingsDialog(
            permission = permission,
            onDismiss = { showGoSettingsDialog = false }
        )
    }

    return {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, permission.permission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
        } else {
            launcher.launch(permission.permission)
        }
    }
}

/**
 * 引导用户前往系统应用详情设置页开启权限的通用对话框
 */
@Composable
fun PermissionSettingsDialog(
    permission: AppPermission,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModernAlertDialog(
        onDismiss = onDismiss,
        title = "需要开启${permission.title}",
        content = {
            Text("${permission.rationale}。您之前拒绝了该权限，请前往系统设置中开启。")
        },
        confirmButton = {
            ModernDialogButtonBar(
                onConfirm = {
                    onDismiss()
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开系统设置页面", Toast.LENGTH_SHORT).show()
                    }
                },
                confirmText = "去设置",
                dismissText = "取消",
                onDismiss = onDismiss
            )
        }
    )
}
