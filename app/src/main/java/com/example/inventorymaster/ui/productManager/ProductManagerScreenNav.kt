package com.example.inventorymaster.ui.productManager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventorymaster.data.entity.ProductBase
import com.example.inventorymaster.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagerScreen(
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductBase?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 1. 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.searchProducts(it)
            },
            label = { Text("搜索名称/厂家/编码/DI") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.productList.isEmpty() && searchQuery.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请输入关键词进行搜索", color = Color.Gray)
            }
        } else {
            // 2. 列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.productList, key = { it.di }) { product ->
                    ProductItem(product = product, onClick = { editingProduct = product })
                }
            }
        }
    }

    // 3. 编辑弹窗
    if (editingProduct != null) {
        EditProductDialog(
            product = editingProduct!!,
            onDismiss = { editingProduct = null },
            onConfirm = { updatedProduct ->
                viewModel.updateProductBase(updatedProduct)
                editingProduct = null
            }
        )
    }
}

@Composable
fun ProductItem(product: ProductBase, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = product.productName, style = MaterialTheme.typography.titleMedium)
            Text(text = "规格: ${product.specification ?: "-"}  型号: ${product.model ?: "-"}", style = MaterialTheme.typography.bodySmall)
            Text(text = "厂家: ${product.manufacturer}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray.copy(alpha = 0.5f))
            Text(text = "DI: ${product.di}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (!product.materialCode.isNullOrBlank()) {
                Text(text = "物料编码: ${product.materialCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EditProductDialog(
    product: ProductBase,
    onDismiss: () -> Unit,
    onConfirm: (ProductBase) -> Unit
) {
    var name by remember { mutableStateOf(product.productName) }
    var spec by remember { mutableStateOf(product.specification ?: "") }
    var mfr by remember { mutableStateOf(product.manufacturer) }
    var regCert by remember { mutableStateOf(product.registrationCert ?: "" ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑产品信息") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // DI 是主键，禁止修改，只读显示
                Text("DI: ${product.di}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("物料编码: ${product.materialCode ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("产品名称") })
                OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("规格") })
                OutlinedTextField(value = regCert, onValueChange = { regCert = it }, label = { Text("注册证号") })
                OutlinedTextField(value = mfr, onValueChange = { mfr = it }, label = { Text("生产厂家") })


            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(product.copy(
                    productName = name,
                    specification = spec,
                    manufacturer = mfr,
                    registrationCert = regCert
                ))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}