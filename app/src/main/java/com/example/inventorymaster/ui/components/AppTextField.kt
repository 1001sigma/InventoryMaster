package com.example.inventorymaster.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** 全项目统一的输入框圆角，与卡片/按钮主流的 12dp 对齐 */
private val FieldShape = RoundedCornerShape(12.dp)

/**
 * 全项目统一的填充式输入框（无边框、浅底、圆角）。
 *
 * 颜色全部取自 MaterialTheme，容器色默认 [MaterialTheme.colorScheme.surfaceVariant]；
 * 玻璃拟态对话框内使用时可通过 [containerColor] 覆盖为半透明 token。
 *
 * [onFocusLost] 在输入框"曾获得焦点且真正失焦"时回调一次，用于失焦保存场景；
 * 组合初始的未聚焦状态不会触发。触发失焦的路径有三种：焦点移到别处、按"完成"键、
 * 键盘被手动收起（下滑/返回键，此时自动清焦点）。
 *
 * 默认行为：未自定义 [keyboardActions] 且 IME 动作为 Done 时，按"完成"键会自动清除焦点，
 * 使失焦保存与软键盘收起同时生效。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    containerColor: Color? = null,
    onFocusLost: (() -> Unit)? = null
) {
    // 记录是否获得过焦点：onFocusChanged 在组合初期就会以未聚焦状态回调一次，需跳过
    var hadFocus by remember { mutableStateOf(false) }
    var isFocusedNow by remember { mutableStateOf(false) }
    val focusModifier = if (onFocusLost != null) {
        Modifier.onFocusChanged { state ->
            isFocusedNow = state.isFocused
            if (state.isFocused) {
                hadFocus = true
            } else if (hadFocus) {
                hadFocus = false
                onFocusLost()
            }
        }
    } else {
        Modifier
    }

    val container = containerColor ?: MaterialTheme.colorScheme.surfaceVariant

    // Done 键默认清焦点：保证失焦保存（onFocusLost）在用户按"完成"时同样触发
    val focusManager = LocalFocusManager.current

    // 键盘被手动收起（下滑/返回键）时 Compose 不会自动清焦点，
    // 这里监听 IME 插入高度归零，对仍聚焦的字段补一次清焦点，统一走 onFocusLost。
    // 仅在启用了失焦回调的字段上激活，避免影响搜索框等常驻输入场景。
    if (onFocusLost != null) {
        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(imeBottom) {
            if (imeBottom <= 0 && isFocusedNow) {
                focusManager.clearFocus()
            }
        }
    }

    val effectiveKeyboardActions =
        if (keyboardActions == KeyboardActions.Default && keyboardOptions.imeAction == ImeAction.Done) {
            KeyboardActions(onDone = { focusManager.clearFocus() })
        } else {
            keyboardActions
        }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = focusModifier
            .then(modifier)
            .fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = effectiveKeyboardActions,
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            errorContainerColor = container,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = MaterialTheme.colorScheme.error
        )
    )
}
