# DEVELOPMENT — 开发指南

> 面向接手本项目的开发者（人类或 AI）。所有路径以实际仓库为准；`{base}` = `app/src/main/java/com/example/inventorymaster/`。

## 1. 环境

| 项 | 值 | 来源 |
|----|-----|------|
| Android Studio | 任意支持 AGP 8.9 的版本 | gradle/libs.versions.toml |
| AGP / Kotlin | 8.9.1 / 2.0.21 | libs.versions.toml |
| JDK | 11（source/targetCompatibility） | app/build.gradle.kts:54-60 |
| compileSdk / minSdk / targetSdk | 35 / 29 / 34 | app/build.gradle.kts:11,25-26 |
| NDK | 30.0.14904198（libdmtx 编译必需） | app/build.gradle.kts:12 |
| CMake | 4.1.2 | app/build.gradle.kts:68 |
| ABI | arm64-v8a、armeabi-v7a | app/build.gradle.kts:32-34 |
| 签名 | `app/inventorymaster.jks`（密码硬编码 build.gradle.kts:14-20 ⚠️） | |

依赖：Room 2.6.1(KSP)、Compose BOM 2024.09.00、CameraX 1.3.0、ML Kit barcode 17.2.0 + text-recognition-chinese 16.0.1、OpenCV 4.10.0、onnxruntime-android 1.17.0、Retrofit 2.9.0、POI 5.2.3、iText7 7.2.5、DataStore、Coil、Jieba、ZXing 3.5.2（完整列表 app/build.gradle.kts:73-150）。

## 2. 构建与运行

```bash
# Windows（项目根目录）
.\gradlew.bat assembleDebug          # 调试包
.\gradlew.bat assembleRelease        # 发布包（minify 开启）
.\gradlew.bat compileDebugKotlin     # 只编译校验（快）
.\gradlew.bat test                   # 全部单元测试
.\gradlew.bat test --tests com.example.inventorymaster.CalcTest   # 单测类
.\gradlew.bat clean assembleDebug
```

- 只看编译错误：`.\gradlew.bat compileDebugKotlin 2>&1 | Select-String "^e:"`（PowerShell）。
- 首次构建需 NDK/CMake 下完成 native 编译（`app/.cxx/` 产物）。
- 运行需真机（CameraX/NDK）；`usesCleartextTraffic=true` 已允许 http。
- 配套服务端（可选，功能联调用）：`python uvicorn main.py:app --port 8080`（D:\xz\inventory_server）与 `python main.py`（D:\xz\pythonUDI，自带 :8000 启动）。

## 3. 调试要点

- 同步日志：Logcat 过滤 `SYNC` / `SYNC_ERROR`（sync 策略类）。
- 扫码日志：`VisionManager` / `DecoderChain` / `QRCodeScannerUtil` / `YoloDetector`（含 [timing] 耗时）。
- 导入日志：`DEBUG_SAVE` / `DEBUG_STOCK`（InventoryRepositoryImpl）。
- Room schema：`exportSchema=true`；数据库文件 `inventory_master_db`（Device Explorer → databases）。
- AI 调试：Logcat `InventoryAiAgent`（兜底拦截/死循环告警）。

## 4. 常见修改 → 对应文件（速查表）

| 我要改… | 先看/改这些（相对 {base}） |
|---------|---------------------------|
| 库存搜索逻辑/降级链 | viewmodel/InventoryViewModel.kt:115 `performSearch`；data/dao/StockRecordDao.kt:58-103 三个查询 |
| 新增/修改记录的字段 | data/entity/StockRecord.kt（DB v+1！）+ data/dto/SyncModels.kt:38 StockRecordDto + viewmodel/InventoryViewModel.kt:243 `prepareRecordForSync` |
| Excel 导入列映射 | utils/ExcelUtils.kt:282 `parseExcelWithMapping`、621 `identifyFieldType`；模板 utils/MappingTemplateManager.kt；UI ui/inventory/components/MappingConfigDialog.kt |
| UDI 解析规则（AI 表） | utils/Gs1Parser.kt:20-39 `aiRules` |
| DI 校验 | utils/Gs1Parser.kt:118 `isValidDI`；开关 SettingsRepository(enableDiValidation) |
| 特殊批号规则 | utils/Hybiome.kt `BatchCodeProcessor` |
| 扫码管线（识别率） | utils/VisionManager.kt:62 `scanImage`（YOLO 路径）；utils/YoloDetector.kt（模型/阈值）；batchscanner/core/util/QRCodeScannerUtil.kt:121 `pyramidScan`（兜底切片）；utils/DecoderChain.kt（引擎顺序） |
| 新增解码引擎 | 实现 `BarcodeDecoder`（utils/DecoderChain.kt:26）→ 加入 VisionManager.kt:34-39 的链 |
| 图像增强 | utils/ImagePreprocessor.kt:22 `enhanceForBarcode` |
| DataMatrix JNI | app/src/main/cpp/libdmtx_wrapper.cpp + utils/LibDmtxNative.kt / LibDmtxDecoder.kt |
| 单据核对判定 | batchscanner/task/viewmodel/InventoryMainViewModel.kt:62-84（evaluateDateStatus/computeQtyStatus）、141 `processScannerResults`；状态模型 batchscanner/task/models/TaskModels.kt |
| 金蝶单据接口 | data/network/TaskApiService.kt + data/repository/InventoryRepositoryImpl.kt:426-501 |
| 同步策略/冲突 | data/sync/*.kt（四策略）+ viewmodel/SyncViewModel.kt:71 `handleIntent` |
| 服务端 API 客户端 | data/network/InventoryApiService.kt（:8080）/ TaskApiService.kt（:8000） |
| 导航/新页面 | ui/home/MainScreenNav.kt:222 内层图；MainActivity.kt:71 外层图；全屏判定 MainScreenNav.kt:75-79 |
| 设置项 | data/SettingsRepository.kt:70 Keys + 103 settingsFlow + update 方法；UI ui/home/SettingScreenNav.kt |
| AI 工具 | ai/tools/*.kt（实现 AiTool）→ 注册 viewmodel/ChatViewModel.kt:92 `registerTools`；权限 ai/tools/ToolPermission.kt |
| AI 提示词 | ai/prompt/PromptBuilder.kt `build` |
| AI 动态 SQL | ai/tools/QueryInventoryDataTool.kt（参数 schema）+ InventoryRepositoryImpl.kt:526 `executeDynamicSqlForAi` |
| 异常检测规则 | ai/tools/anomaly/AnomalyDetector.kt:55+ |
| 主题/颜色 | ui/theme/（ExtendedColors.kt） |
| 弹窗样式 | ui/components/ModernDialog.kt |
| PDF 导出 | batchscanner/task/util/PdfExportUtils.kt（单据报告）；utils/PdfEngine.kt（通用）；水印 utils/pdf/ |
| OCR 提取规则 | batchscanner/ocr/OcrInfoExtractor.kt:18-36 正则组 |
| 词典/分词 | ocr/JiebaDictionaryManager.kt、ocr/JiebaTokenizer.kt |

## 5. How-to（高频操作）

### 5.1 修改数据库（⚠️ 破坏性）
1. 改 `data/entity/*.kt`；2. `AppDatabase.kt:17` version+1；3. **当前无 Migration → 升级即清库**（fallbackToDestructiveMigration，AppDatabase.kt:41）。要保数据必须写 `Migration` 并 `addMigrations`。4. 若字段进同步链，同步改 `SyncModels.kt` DTO 与服务端（D:\xz\inventory_server templates/models/models.py）。

### 5.2 新增 API
见 docs/API.md §6（接口 + DTO + Repository + 策略/ViewModel 四步）。

### 5.3 新增扫码类型（如 PDF417）
1. `MlKitDecoder` 格式表加 `Barcode.FORMAT_PDF417`（DecoderChain.kt:38-47）；2. 如 ML Kit 弱，按 LibDmtxDecoder 模式加 JNI 引擎；3. 需要时在 `Gs1Parser` 增 AI 规则。

### 5.4 修改 UDI 规则
docs/UDI.md §5 速查表。

### 5.5 修改 WebView/金蝶页面
**本仓库无 WebView 代码。** 金蝶侧页面自动化在 `D:\xz\pythonUDI\金蝶提取工具-自动巡航7.2.txt`（Tampermonkey 用户脚本）与独立项目 `D:\xz\KingdeeUDIApp`——去那里改。

### 5.6 修改 UI
Compose 组件树即代码；新页面：建 Screen → MainScreenNav.kt 加 composable → 需要全屏则更新 L75-79 列表 → ViewModel 走 AppViewModelFactory.kt 注册。

## 6. 测试现状

`app/src/test/`（JVM 单测，无 instrumented 业务测试）：
- `CalcTest.kt`：计算器引擎（优先级/除法/连续运算）。
- `ExampleUnitTest.kt` 内 `Gs1ParserTest`：括号格式/裸格式/仅 DI/普通码 4 例。
- `hybiome.kt` 内 `BatchCodeProcessorTest`：长光批号 4 例。
无：同步、DAO、扫码管线、AI 测试。

## 7. 提交规范现状

git 历史为中文短句（如"优化了数据库，新增关联键"），无分支策略（单 main）。CHANGELOG.md 手工维护——**每次改动请追加条目**（格式见该文件 2026-08-14 条目）。
