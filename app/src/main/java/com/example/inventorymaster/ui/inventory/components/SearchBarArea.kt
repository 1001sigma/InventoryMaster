package com.example.inventorymaster.ui.inventory.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.inventorymaster.ui.components.AppTextField

@Composable
fun SearchBarArea(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isInventoryMode: Boolean,
    onScanClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }

    AppTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .onFocusChanged { isFocused = it.isFocused },
        label = if (isInventoryMode) "扫描或输入 UDI / 批号" else "搜索名称、厂家、库位...",
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 附加逻辑：当聚焦搜索栏且有内容时才显示清空按钮，其余情况均不出现
                if (isFocused && query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        onClear?.invoke()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空"
                        )
                    }
                }
                IconButton(onClick = {
                    // 扫码层是 InventoryScreen 内的覆盖层而非路由跳转，
                    // 搜索框仍留在组合树中，必须先释放焦点收起键盘再进入扫码
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onScanClick()
                }) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "扫码"
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}
