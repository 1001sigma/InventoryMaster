package com.example.inventorymaster.batchscanner

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventorymaster.data.AppDatabase
import com.example.inventorymaster.utils.Gs1Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryMainViewModel(application: Application) : AndroidViewModel(application) {

    private val productDao = AppDatabase.getDatabase(application).productDao()

    // 1. 上半区的轮播图片状态
    private val _photoList = MutableStateFlow<List<Uri>>(emptyList())
    val photoList: StateFlow<List<Uri>> = _photoList.asStateFlow()

    // 2. 下半区的结果表单状态
    private val _recordList = MutableStateFlow<List<InventoryRecord>>(emptyList())
    val recordList: StateFlow<List<InventoryRecord>> = _recordList.asStateFlow()

    /**
     * 接收 BatchScanner 传回的最终结果
     * @param bitmaps 本次盘点拍下的所有照片
     * @param rawBarcodes 识别出的所有合规原始字符串
     */
    /**
     * 核心业务：处理扫描结果并匹配产品库
     */
    fun processScannerResults(bitmaps: List<Uri>, rawBarcodes: List<String>) {
        _photoList.update { current -> current + bitmaps }

        viewModelScope.launch {
            val currentRecords = _recordList.value.toMutableList()
            val newEntries = mutableListOf<InventoryRecord>()

            // 切换到 IO 线程进行数据库操作
            withContext(Dispatchers.IO) {
                for (raw in rawBarcodes) {
                    val parseResult = Gs1Parser.parse(raw)

                    if (!parseResult.isUdi || parseResult.di == null) {
                        newEntries.add(
                            InventoryRecord(
                                di = "未知",
                                productName = "解析失败",
                                batch = "未知",
                                actualQty = 1,
                                isError = true,
                                errorMessage = "非 UDI 格式"
                            )
                        )
                        continue
                    }

                    val di = parseResult.di!!
                    val batch = parseResult.batch ?: "无批号"

                    // ================= 接入真实查库逻辑 =================
                    // 使用你提供的 getProductByDi 方法
                    val product = productDao.getProductByDi(di)
// 如果查不到，就给个默认名字，并标记为错误状态
                    val isUnknown = product?.productName == null
                    val finalProductName = product?.productName ?: "未知商品"
                    val errorMessage = if (isUnknown) "库中无记录" else null
                    // ====================================================

                    val existingIndex = currentRecords.indexOfFirst { it.di == di && it.batch == batch }
                    if (existingIndex != -1) {
                        val existingRecord = currentRecords[existingIndex]
                        currentRecords[existingIndex] = existingRecord.copy(
                            actualQty = existingRecord.actualQty + 1
                        )
                    } else {
                        currentRecords.add(
                            InventoryRecord(
                                di = di, productName = finalProductName, batch = batch, actualQty = 1,isError = false,
                                errorMessage = errorMessage ?: ""
                            )
                        )
                    }
                }
                currentRecords.addAll(newEntries)
            }

            // 回到主线程更新 UI 状态
            _recordList.value = currentRecords
        }
    }

    fun updateRecordQuantity(recordId: String, newQty: Int) {
        _recordList.update { current ->
            current.map { if (it.id == recordId) it.copy(actualQty = newQty, isManualModified = true) else it }
        }
    }

    fun removeRecord(recordId: String) {
        _recordList.update { current -> current.filterNot { it.id == recordId } }
    }

    // 导入单据时也可以在这里加入基于 DI 的查库补
    // InventoryMainViewModel.kt

    fun removePhoto(uri: android.net.Uri) {
        _photoList.update { current ->
            // 过滤掉当前选中的图片 Uri
            current.filter { it != uri }
        }

         //(可选) 建议在这里同步处理物理文件的删除，避免占用手机存储空间
         try {
             val file = java.io.File(uri.path ?: "")
             if (file.exists()) file.delete()
         } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 导入单据 (预填充逻辑)
     */
    fun importTargetDocument() {
        // 模拟导入一份预期的采购单/发货单
        val mockTargets = listOf(
            InventoryRecord(di = "08712345678906", productName = "阿司匹林肠溶片", batch = "20231001", targetQty = 50),
            InventoryRecord(di = "06901234567892", productName = "无菌注射器", batch = "20231115", targetQty = 100)
        )
        // 清空当前列表，装载单据目标（实际数量初始化为 0）
        _recordList.value = mockTargets
    }
}