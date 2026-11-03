# ARCHITECTURE — 系统架构

> 本文档描述 InventoryMaster 的整体架构与分层调用关系。所有结论以当前代码为准，代码位置格式：`文件:行号`（相对 `app/src/main/java/com/example/inventorymaster/`）。

## 1. 系统全景

InventoryMaster 是**医疗器械仓库盘点 Android App**（单模块 `:app`），本身不包含服务端代码。它通过局域网 HTTP 与两个本机/局域网 Python 服务协作：

```
┌─────────────────────────── Android App（本仓库）───────────────────────────┐
│  UI (Compose) → ViewModel → Repository → DAO → Room (inventory_master_db)  │
│                                    │                                        │
│  扫码子系统 (batchscanner/ + utils/) ← CameraX/YOLO/OpenCV/MLKit/libdmtx   │
│  AI 助手 (ai/) ← OkHttp → OpenAI 兼容云端 API                               │
└──────────┬──────────────────────────────┬──────────────────────────────────┘
           │ Retrofit :8080               │ Retrofit :8000
           ▼                              ▼
   inventory_server（仓库外）        pythonUDI 任务系统（仓库外）
   FastAPI + SQLModel/SQLite        FastAPI + SQLite(tasks.db)
   数据同步/聚合服务                 金蝶单据中转（数据源：金蝶云星空
                                    + Tampermonkey 提取脚本）
```

- 同步服务端：`D:\xz\inventory_server\main.py`（FastAPI，标题 `InventoryMaster Server`，Token 认证中间件）。
- 任务服务端：`D:\xz\pythonUDI\main.py`（FastAPI，`uvicorn.run(app, host="0.0.0.0", port=8000)`）。
- 金蝶云星空是最终业务数据源，通过浏览器用户脚本《金蝶提取工具-自动巡航7.2》（`D:\xz\pythonUDI\金蝶提取工具-自动巡航7.2.txt`，`@match http://116.63.160.136/*`）定时提取单据推入 pythonUDI。【待确认】金蝶服务器地址 116.63.160.136 为现场环境固定地址。
- 另有独立 Android 项目 `D:\xz\KingdeeUDIApp`（金蝶 WebView 适配器），与本仓库**无代码共享**，仅共享扫码相关技术选型。

## 2. Android 分层架构（实际代码）

手动 MVVM，**无 Hilt/Dagger**。依赖装配链：

```
InventoryApplication (InventoryApplication.kt:11)
 ├─ database        = AppDatabase.getDatabase()            (L17)
 ├─ settingsRepository = SettingsRepository(DataStore)     (L20)
 ├─ timestampManager   = TimestampManager                  (L23)
 └─ repository      = InventoryRepositoryImpl(productDao, sessionDao,
                        stockRecordDao, settingsRepository,
                        timestampManager, database)          (L27-36)

AppViewModelFactory (viewmodel/AppViewModelFactory.kt:16-35)
 └─ 手工创建 6 个 ViewModel：SessionViewModel / InventoryViewModel /
    SettingsViewModel / SyncViewModel / InventoryMainViewModel / ChatViewModel
```

调用方向（严格单向向下）：

```
UI (Compose Screen)
  ↓ StateFlow 收集 / 方法调用
ViewModel (viewmodel/ + batchscanner/*/viewmodel/)
  ↓
InventoryRepository (接口 data/repository/InventoryRepository.kt:12)
  ↓ 唯一实现
InventoryRepositoryImpl (data/repository/InventoryRepositoryImpl.kt:44)
  ↓                        ↓                     ↓
Room DAO ×3            Retrofit ×2          SyncOrchestrator
(data/dao/)            (data/network/)      (data/sync/SyncOrchestrator.kt:14)
  ↓                        ↓                     ↓
SQLite                  :8080 / :8000       4 种 SyncStrategy
```

特例（实际代码为准，不是所有 ViewModel 都走 Factory）：
- `batchscanner` 的 `InventoryMainViewModel`（`batchscanner/task/viewmodel/InventoryMainViewModel.kt:27`）虽在 Factory 注册（`AppViewModelFactory.kt:29-31`），但在 `batch_scan_flow` 嵌套导航中通过 `navController.getBackStackEntry("batch_scan_flow")` 以父作用域共享同一实例（`ui/home/MainScreenNav.kt:331,366`）。
- `BatchScannerViewModel`、`VideoStreamViewModel` 为模块内部 ViewModel，由各自 Screen 自行创建。

## 3. 导航架构（两级 NavHost）

外层（`MainActivity.kt:71`，startDestination = `"home"`）：

| 路由 | 目的地 | 说明 |
|------|--------|------|
| `home` | `MainScreen`（内层 NavHost 容器） | MainActivity.kt:74 |
| `inventory/{sessionId}` | `InventoryScreen` 全屏盘点详情 | MainActivity.kt:97；进入时调 `inventoryViewModel.selectSession(sessionId)` |

内层（`ui/home/MainScreenNav.kt:222`，startDestination = `"home"`，底部 Tab：主页/基础产品库/系统设置 L88-91）：

| 路由 | 目的地 | 位置 |
|------|--------|------|
| `home` | HomeScreen | MainScreenNav.kt:228 |
| `session_list` | SessionListScreenNav 任务列表 | MainScreenNav.kt:247 |
| `products` | ProductManagerScreenNav 产品字典 | MainScreenNav.kt:262 |
| `settings` | SettingScreenNav 设置 | MainScreenNav.kt:274 |
| `watermark_settings` | 水印设置 | MainScreenNav.kt:288 |
| `signature_settings` | 签名设置 | MainScreenNav.kt:293 |
| `ai_chat?sessionId={sessionId}` | ChatScreen AI 助手 | MainScreenNav.kt:299 |
| `batch_scan_flow`（嵌套图） | startDestination = `taskDetail` | MainScreenNav.kt:322 |
| ├ `taskDetail` | InventoryTaskDetailScreen | MainScreenNav.kt:326 |
| ├ `batchCamera?inputUri={inputUri}` | BatchScannerScreen 拍照批量扫码 | MainScreenNav.kt:343,355 |
| └ `videoStream` | VideoStreamScreen 视频流扫码 | MainScreenNav.kt:349,399 |

全屏控制：`isFullScreen`（MainScreenNav.kt:75-79）对 `taskDetail/batchCamera/singleCamera/videoStream/session_list/ai_chat*` 隐藏顶部/底部栏。

## 4. 各层职责

### UI 层（`ui/`）
- 全 Jetpack Compose + Material3，无 XML 布局（res/ 下只有 drawable/主题资源）。
- 状态唯一来源是对应 ViewModel 的 `StateFlow`；事件回传通过 lambda 回调（如 `onScanResult: (String) -> Unit`，`ui/analyzer/ScanScreen.kt:121`）。
- 通用组件：`ui/components/ModernDialog.kt`（统一弹窗）、`ui/components/SharedComponents.kt`（Badge/卡片等）。

### ViewModel 层（`viewmodel/` + `batchscanner/*/viewmodel/`）
- `InventoryViewModel`（viewmodel/InventoryViewModel.kt:68）：搜索/CRUD/Excel 导入导出/产品字典管理，`prepareRecordForSync`（L243）统一为写库记录打脏标记。
- `SessionViewModel`：任务列表与归档。
- `SyncViewModel`（viewmodel/SyncViewModel.kt:49）：Intent 驱动的同步调度（详见 docs/SYNC.md）。
- `SettingsViewModel`：DataStore 设置读写 + AI 服务商管理。
- `ChatViewModel`（viewmodel/ChatViewModel.kt:48）：AI 对话，负责注册 AI 工具（L92-104）。
- `InventoryMainViewModel`：金蝶单据核对业务核心（详见 docs/BUSINESS.md）。

### Repository 层
- `InventoryRepositoryImpl` 是**所有数据操作的唯一入口**（本地库、两个网络服务、同步编排都在这里），约 550 行。构造时 `runBlocking` 预载 Token（L53-61），并注册 4 种同步策略（L64-73）。

### DAO 层（`data/dao/`）
- `ProductDao` / `SessionDao` / `StockRecordDao`。列表查询返回 `Flow`（Room 响应式），其余 `suspend`。跨表组合查询用 `@Transaction` + `StockRecordCombined`（`data/model/StockRecordCombined.kt:8`，`@Relation` 抓取产品）。

### 网络层（`data/network/`）
- `InventoryApiService`（:8080，Bearer Token 拦截器，InventoryApiService.kt:62-82）与 `TaskApiService`（:8000，无鉴权，TaskApiService.kt:57-67）。DTO 在 `data/dto/SyncModels.kt` 与 `data/network/TaskApiService.kt:17-36`。详见 docs/API.md。

### 数据库层
- Room v4（`data/AppDatabase.kt:17`），`inventory_master_db`，3 表。**开发期开关**：`allowMainThreadQueries()`（L40）、`fallbackToDestructiveMigration()`（L41）。详见 docs/DATABASE.md。

### 同步层（`data/sync/`）
- `SyncOrchestrator` 用 `Mutex` 串行化全部同步（SyncOrchestrator.kt:30-38）；策略模式 4 实现 + `RetryPolicy` 指数退避 + `TimestampManager` 双锚点。详见 docs/SYNC.md。

### 扫码层（`batchscanner/` + `utils/` + `app/src/main/cpp/`）
- 双路径管线：YOLO 智能定位 → 降级金字塔暴力切片；解码链 ML Kit → libdmtx(JNI)。详见 docs/SCANNER.md。

### AI 层（`ai/`）
- Agent 循环 + 工具调用（function calling），云端 OpenAI 兼容 API。详见 docs/AI.md。

## 5. 关键横切事实

- **双数据模型并存**：Room 的 `StockRecord`（持久化账本）与 `batchscanner/task/models/TaskModels.kt:45` 的 `InventoryRecord`（单据核对内存模型）是两套模型，后者不落库，导出即 PDF/上传。两者通过 DI/批号语义关联。
- **Token 生命周期**：DataStore 持久化（`server_token`）→ `InventoryRepositoryImpl.init` 启动时 `runBlocking` 载入 `InventoryApiService.setToken`（静态缓存）→ OkHttp 拦截器附加 Bearer 头。
- **明文 HTTP**：`AndroidManifest.xml:21` `usesCleartextTraffic="true"`，局域网两服务均为 http。
- **OcrProvider** 在 Application 启动即初始化（`InventoryApplication.kt:14` → `ocr/OcrProvider.kt:8`），ML Kit 中文识别器全局单例。

## 6. 设计决策记录（可从代码/文档确认的部分）

| 决策 | 依据 | 备注 |
|------|------|------|
| 无 DI 框架，手工 Factory | AppViewModelFactory.kt 全文 | 项目小，避免 Hilt 学习成本【推测-动机】 |
| Repository 单例聚合一切数据操作 | InventoryRepositoryImpl 职责范围 | 便于 UI 单点调用，但已是巨型类（见 TECH_DEBT） |
| 同步用策略模式 + Mutex 串行 | data/sync/ 注释 "Phase 3/Phase 4" | 替代早期 ViewModel 内 isSyncRunning 锁（SyncViewModel.kt:66 仍保留了外层布尔锁） |
| productKey 统一关联键（DI 优先，回退物料编码） | ProductBase.kt:6-9 注释 + git 31f5229（2026-05-30 "优化了数据库，新增关联键"） | 引入原因：早期 di 字段被物料编码污染【推测-细节，见 DATABASE.md §5】 |
| libdmtx 以 C 源码 NDK 编译而非第三方 AAR | app/src/main/cpp/ + libdmtx集成方案.md | 设计文档《libdmtx集成方案.md》明确"JNI/NDK + 责任链"选型 |
| YOLO 用 ONNX Runtime 直集成（onnxruntime-android 1.17.0） | app/build.gradle.kts:146 + utils/YoloDetector.kt | 模型 barcode_detector.onnx 打包在 assets |
