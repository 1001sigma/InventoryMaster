# Python 任务系统 API — 涉及代码文件与调用链

## 概览

Python 任务系统（端口 8000）提供三个核心接口：**获取任务列表**、**获取任务明细**、**PDF 上传**。
共涉及 **7 个代码文件**，分 3 层。

---

## 文件清单

### 第 1 层：网络/API 定义

| 文件 | 关键内容 |
|------|----------|
| `app/src/main/java/com/example/inventorymaster/data/network/TaskApiService.kt` | Retrofit 接口 + 响应数据模型 |
| `app/src/main/java/com/example/inventorymaster/batchscanner/task/models/TaskModels.kt` | `TargetDocument` 模型（API 反序列化目标） |

**TaskApiService 定义的三个接口：**

```kotlin
// 1. 获取任务列表
@GET("api/task/list")
suspend fun getTaskList(): Response<TaskSummaryResponse>

// 2. 获取单个任务明细
@GET("api/task/{documentId}")
suspend fun getTaskDetail(@Path("documentId") documentId: String): Response<TaskDetailResponse>

// 3. 上传 PDF 报告
@Multipart
@POST("api/task/upload")
suspend fun uploadPdfResult(
    @Part("documentIds") documentIds: RequestBody,
    @Part file: MultipartBody.Part
): Response<UploadResponse>
```

**响应数据模型（TaskApiService.kt 内定义）：**

```kotlin
data class TaskSummaryResponse(val status: String, val data: List<TaskSummary>)
data class TaskSummary(
    val documentId: String, val documentName: String,
    val documentDate: String, val documentNumber: Int, val status: Int
)
data class TaskDetailResponse(val status: String, val data: TargetDocument)
data class UploadResponse(val status: String, val message: String?)
```

**TargetDocument（TaskModels.kt）：**

```kotlin
data class TargetDocument(
    val documentId: String, val documentName: String, val documentDate: String,
    val items: List<TargetItem>
)
data class TargetItem(
    val materialCode: String, val batch: String, val productName: String,
    val targetQty: Int, val productionDate: String?, val expiryDate: String?
)
```

---

### 第 2 层：Repository

| 文件 | 方法 |
|------|------|
| `app/src/main/java/com/example/inventorymaster/data/repository/InventoryRepository.kt` | 接口声明 |
| `app/src/main/java/com/example/inventorymaster/data/repository/InventoryRepositoryImpl.kt` | 实现（L426-469） |

```kotlin
// InventoryRepository.kt
suspend fun fetchPythonTaskList(): Result<List<TaskSummary>>
suspend fun fetchPythonTaskDetail(documentId: String): Result<TargetDocument>
suspend fun uploadPythonTaskPdf(documentIds: String, file: File): Result<String>
```

实现逻辑：从 `SettingsRepository` 获取 IP → 调用 `TaskApiService.create(ip)` → 发起 Retrofit 调用 → 统一包装为 `Result`。

---

### 第 3 层：ViewModel + UI

| 文件 | 用途 |
|------|------|
| `batchscanner/task/viewmodel/InventoryMainViewModel.kt` | 调度中心 |
| `batchscanner/task/screen/InventoryTaskDetailScreen.kt` | 用户触发入口 |
| `batchscanner/task/util/PdfExportUtils.kt` | PDF 生成工具 |

---

## 完整调用链

### 流程 1：获取任务列表并加载明细

```
InventoryTaskDetailScreen
  └─ "单据"按钮 → 弹窗展示可选择的远程任务列表
      └─ viewModel.fetchTaskListFromNetwork { errorMsg -> ... }
          └─ repository.fetchPythonTaskList()
              └─ TaskApiService.create(ip).getTaskList()
                  └─ GET http://<ip>:8000/api/task/list
                      └─ 返回 TaskSummary[]（status=0 表示待处理）

InventoryTaskDetailScreen
  └─ 用户勾选任务后确认
      └─ viewModel.loadMultipleTaskDetails(selectedIds) { resultMsg -> ... }
          └─ repository.fetchPythonTaskDetail(id)  [循环每个 id]
              └─ TaskApiService.create(ip).getTaskDetail(documentId)
                  └─ GET http://<ip>:8000/api/task/{documentId}
                      └─ 返回 TargetDocument
                          ├─ documentId, documentName, documentDate
                          └─ items[]
                              ├─ 通过 materialCode 解析 DI
                              └─ 创建 InventoryRecord 列表
      └─ 更新 _currentDocuments + _recordList
```

### 流程 2：导出 PDF 并上传

```
InventoryTaskDetailScreen
  └─ "导出PDF" 按钮
      └─ ActivityResultContracts.CreateDocument → 用户选择保存路径
          └─ uri → viewModel.uploadPdfResultToNetwork(context, uri, docIds) { resultMsg -> ... }
              ├─ PdfExportUtils.generatePdf(context, outputStream, documents, photoResults, recordList)
              │   ├─ Page 1: 单据摘要（单据信息 + 明细项表格）
              │   ├─ Page 2+: 盘点结果明细（分组表格 + 状态颜色）
              │   └─ Page 3+: 照片页（原图 + 条码明细表格）
              │
              ├─ uri → 临时文件（FileConvertUtils.uriToTempFile）
              │
              └─ repository.uploadPythonTaskPdf(documentId, tempFile)
                  └─ TaskApiService.create(ip).uploadPdfResult(idBody, filePart)
                      └─ POST http://<ip>:8000/api/task/upload (Multipart)
                          ├─ Part "documentIds": 文本
                          └─ Part "file": PDF 文件
```

---

## 数据传输模型对照

| 概念 | Android 端模型 | JSON 字段 | API 路径 |
|------|---------------|-----------|----------|
| 任务概要 | `TaskSummary` | documentId, documentName, documentDate, status | `GET /api/task/list` |
| 单据头 | `TargetDocument` | documentId, documentName, documentDate | `GET /api/task/{id}` |
| 单据行 | `TargetItem` | materialCode, batch, productName, targetQty, dates | 同上（嵌套在 data.items） |
| 上传响应 | `UploadResponse` | status, message | `POST /api/task/upload` |

---

## 文件完整路径

```
D:\xz\InventoryMaster\
├── app\src\main\java\com\example\inventorymaster\
│   ├── data\network\
│   │   └── TaskApiService.kt
│   ├── data\repository\
│   │   ├── InventoryRepository.kt
│   │   └── InventoryRepositoryImpl.kt
│   └── batchscanner\task\
│       ├── models\TaskModels.kt
│       ├── viewmodel\InventoryMainViewModel.kt
│       ├── screen\InventoryTaskDetailScreen.kt
│       └── util\PdfExportUtils.kt
```
