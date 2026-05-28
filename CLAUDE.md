# CLAUDE.md
<!-- # CLAUDE.md — 本文件为 Claude Code 在此仓库中工作时提供指导 -->

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
<!-- 本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。 -->

## Build Commands
<!-- ## 构建命令 -->

```bash
# Build debug APK
<!-- 构建调试版 APK -->
./gradlew assembleDebug

# Build release APK
<!-- 构建发布版 APK -->
./gradlew assembleRelease

# Run all tests
<!-- 运行所有测试 -->
./gradlew test

# Run a single test class
<!-- 运行单个测试类 -->
./gradlew test --tests com.example.inventorymaster.CalcTest

# Clean build
<!-- 清理构建 -->
./gradlew clean assembleDebug
```

## Architecture Overview
<!-- ## 架构概览 -->

**InventoryMaster** is a medical device warehouse inventory Android app. It uses **Jetpack Compose** with a manual MVVM architecture (no Hilt/Dagger — see `AppViewModelFactory` for DI).
<!-- **InventoryMaster** 是一款医疗器械仓库盘存 Android 应用。它使用 **Jetpack Compose** 并采用手动 MVVM 架构（不使用 Hilt/Dagger，依赖注入参见 `AppViewModelFactory`）。 -->

### Layers
<!-- ### 分层结构 -->

- **data/entity** — Room entities: `ProductBase` (product dictionary, PK=`di`), `InventorySession` (inventory task), `StockRecord` (individual scan records, FK→session + FK→product)
<!-- - **data/entity** — Room 实体：`ProductBase`（产品字典，主键=`di`）、`InventorySession`（盘存任务）、`StockRecord`（单条扫描记录，外键→session + 外键→product） -->
- **data/dao** — Room DAOs with Flow-returning queries for reactive UI
<!-- - **data/dao** — Room DAO，返回 Flow 类型的查询，用于响应式 UI -->
- **data/repository** — `InventoryRepository` interface + `InventoryRepositoryImpl`. Single source of truth for all data ops including Excel import/export, LAN sync push/pull, and conflict resolution
<!-- - **data/repository** — `InventoryRepository` 接口 + `InventoryRepositoryImpl`。所有数据操作的唯一数据源，包括 Excel 导入/导出、局域网同步推送/拉取以及冲突解决 -->
- **viewmodel/** — Four ViewModels: `InventoryViewModel` (main search/CRUD/import), `SessionViewModel` (task list), `SyncViewModel` (LAN sync intent-based), `SettingsViewModel` (theme/scanner prefs)
<!-- - **viewmodel/** — 四个 ViewModel：`InventoryViewModel`（主搜索/增删改查/导入）、`SessionViewModel`（任务列表）、`SyncViewModel`（基于 Intent 的局域网同步）、`SettingsViewModel`（主题/扫描仪偏好设置） -->
- **ui/** — Compose screens organized by feature: `home/`, `inventory/`, `analyzer/`, `productManager/`
<!-- - **ui/** — 按功能组织的 Compose 页面：`home/`、`inventory/`、`analyzer/`、`productManager/` -->
- **batchscanner/** — Self-contained batch-scanning feature with its own `InventoryMainViewModel` (extends `AndroidViewModel` directly, not via factory)
<!-- - **batchscanner/** — 独立的批量扫描功能，拥有自己的 `InventoryMainViewModel`（直接继承 `AndroidViewModel`，不通过工厂创建） -->
- **utils/** — GS1 barcode parser, Excel I/O (Apache POI), Jieba Chinese tokenizer, ML Kit OCR/QR helpers, PDF export
<!-- - **utils/** — GS1 条码解析器、Excel 读写（Apache POI）、结巴中文分词器、ML Kit OCR/二维码辅助工具、PDF 导出 -->

### Navigation
<!-- ### 导航 -->

Two NavHost levels:
<!-- 两级 NavHost： -->
1. **Outer** (`MainActivity`): `"home"` ↔ `"inventory/{sessionId}"` — task list ↔ detail
<!-- 1. **外层** (`MainActivity`)：`"home"` ↔ `"inventory/{sessionId}"` — 任务列表 ↔ 详情 -->
2. **Inner** (`MainScreen`): bottom tabs for `"home"` (task list), `"products"` (product dictionary), `"settings"` + nested `"batch_scan_flow"` (taskDetail → batchCamera/singleCamera)
<!-- 2. **内层** (`MainScreen`)：底部标签页包括 `"home"`（任务列表）、`"products"`（产品字典）、`"settings"`（设置）+ 嵌套的 `"batch_scan_flow"`（任务详情 → 批量相机/单扫相机） -->

### Database
<!-- ### 数据库 -->

Room DB version 3, singleton via `AppDatabase.getDatabase()`. Uses `fallbackToDestructiveMigration()` and `allowMainThreadQueries()` — both are dev conveniences that should be removed for production. Three tables with foreign key constraints: `product_base`, `inventory_sessions`, `stock_records`.
<!-- Room 数据库版本 3，通过 `AppDatabase.getDatabase()` 实现单例。使用了 `fallbackToDestructiveMigration()` 和 `allowMainThreadQueries()` — 两者均为开发便利项，生产环境应移除。三张表包含外键约束：`product_base`、`inventory_sessions`、`stock_records`。 -->

### Key Design Decisions
<!-- ### 关键设计决策 -->

- **No DI framework** — `AppViewModelFactory` manually constructs ViewModels. `InventoryApplication` holds lazy singletons for DB, `SettingsRepository` (DataStore), and `InventoryRepository`
<!-- - **无 DI 框架** — `AppViewModelFactory` 手动构造 ViewModel。`InventoryApplication` 持有延迟加载的单例：数据库、`SettingsRepository`（DataStore）和 `InventoryRepository` -->
- **LAN sync, not cloud** — Retrofit talks to a local PC server via user-provided IP. Supports full upload/download and incremental push/pull. No authentication layer
<!-- - **局域网同步，而非云端** — Retrofit 通过用户提供的 IP 地址与本地 PC 服务器通信。支持完整上传/下载和增量推送/拉取。无身份验证层 -->
- **Dual scanner system** — Barcode mode scans GS1 UDI codes; OCR mode captures text from the camera frame's center crop region. Settings control scan frame sizes, multi-barcode selection, and Jieba tokenization mode
<!-- - **双扫描系统** — 条码模式扫描 GS1 UDI 码；OCR 模式从相机帧的中央裁剪区域捕获文字。设置项控制扫描框大小、多条码选择和结巴分词模式 -->
- **Excel import with conflict resolution** — When importing Excel, if a DI already exists in the product dictionary with different fields, the UI shows a conflict resolution dialog
<!-- - **带冲突解决的 Excel 导入** — 导入 Excel 时，如果产品字典中已存在相同 DI 但字段不同的记录，UI 会显示冲突解决对话框 -->


