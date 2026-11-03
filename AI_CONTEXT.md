# AI_CONTEXT.md — AI 编程助手项目上下文入口

> **本文件是所有 AI 编程助手（Claude Code / Codex / Cursor / Gemini 等）在本仓库工作前的必读入口。**
> 内容 = 核心事实 + 行为约束 + 导航地图。细节一律进 docs/，本文不重复。
> 维护规则：任何改动使本文失真时，必须同步更新本文件与对应 docs/*。

## 1. 项目是什么

**InventoryMaster**：医疗器械仓库盘点 Android App（单模块 `:app`，约 2.8 万行 Kotlin）。
用户用手机对医疗器械 UDI 码（GS1）拍照/视频/单码扫描，与 Excel 底账或金蝶 ERP 单据核对，经局域网 Python 服务端多机协作，产出 Excel/PDF 报告。
本仓库**只有 Android 端**；配套服务端在仓库外：`D:\xz\inventory_server`（FastAPI :8080 同步，SQLite inventory.db，Token 认证）与 `D:\xz\pythonUDI`（FastAPI :8000 金蝶单据中转，SQLite tasks.db）。金蝶数据由 Tampermonkey 用户脚本从金蝶云星空页面提取。

## 2. 技术栈（速记）

Kotlin 2.4.0 / AGP 8.12.0 / compileSdk 35 / minSdk 29；Jetpack Compose + Material3；Room 2.7.2（**v12**，含 MIGRATION_5_6 ~ 11_12 完整原位迁移链）；DataStore 1.2.0；Retrofit 2.9 + Gson；CameraX 1.3；ML Kit barcode + 中文 OCR；OpenCV 4.10（Maven）；ONNX Runtime 1.17（YOLO，assets/barcode_detector.onnx）；**libdmtx C 源码 NDK 编译**（cpp/，CMake 4.1.2，NDK 30）；**LiteRT-LM 0.17.1**（本地大模型 Gemma 4 E2B / Qwen3-4B，GPU 优先 + KV 复用）；Multiplatform Markdown Renderer 0.31.0；POI / iText7 / Jieba / ZXing(仅生成二维码)。**无 Hilt**。

## 3. 总体架构（一句话版）

```
UI(Compose) → ViewModel(手动Factory/AndroidViewModel) → InventoryRepositoryImpl(唯一数据入口)
   → Room DAO ×4 | Retrofit(8080同步, 8000任务) | SyncOrchestrator(4策略+Mutex)
扫码 = CameraX → YOLO定位(缩略图) → ROI裁剪 → OpenCV增强 → DecoderChain(MLKit→libdmtx)
     ；YOLO失败降级金字塔切片(600px瓦片,35%重叠,MLKit)；定格多码选择(1080p,亚像素映射,微信绿波纹)
AI   = ChatViewModel → InventoryAiAgent(业务适配) → AiRuntime(通用循环, FC/JSON双协议, ≤5轮, 全程超时/主动取消)
       → 9工具（结构化查询=AiRecordSqlBuilder白名单+绑定参数，LLM不接触SQL；0-Token产品卡片展示；Room持久化）
       → AiService（CloudAiService 云端流式 / LocalAiService LiteRT-LM 本地离线流式）
```

## 4. 核心业务（详见 docs/BUSINESS.md）

1. **盘点**：建任务 → Excel 导底账/云端下载 → 扫码/手填（写库自动打 `syncStatus=1` 脏标记+historyLog）→ 校对/导出 → 推送同步。
2. **金蝶单据核对**：:8000 拉单（物料编码→DI 靠产品字典桥接）→ 扫码贪心匹配行 → 字段级三色状态（MATCHED/WARNING/ERROR，多扫=ERROR 强拦截导出）→ PDF 归档上传 + 原始码串上传。
3. **同步**：全量上下行 + 增量推拉（pull/push 双锚点防倒退）；增量下载冲突=服务端覆盖留痕；全量下载前脏数据必须三选一确认。
4. **AI 助手**：查库存/查产品/异常分析(5类规则)/总结/OCR纠正/UI跳转。

## 5. 核心数据模型（详见 docs/DATABASE.md）

- `product_base`：**PK=productKey**（DI 优先，回退物料编码，`ProductBase.computeProductKey`）；di 只存真 GTIN。二级索引 `(di)` 与 `(materialCode)`。
- `inventory_sessions`：任务（uuid 跨端标识；status 0进行/1归档/2锁定；startedAt 标记服务端开始）。
- `stock_records`：FK→session(CASCADE)、FK→product(**RESTRICT**)；uuid 唯一；**syncStatus(0干净/1脏)、lastUpdateTime、historyLog 为同步字段**；**`is_verified` 为查验状态原生字段（0未查验/1已查验，Room v12 索引化）**；remarks 撤销查验时自动转存备份实盘数据（"原实盘: N"）。
- `chat_messages`：AI 消息持久化（Room v12），threadId 索引（`t<sessionId>_<ts>` 任务绑定 / `g_<ts>` 自由会话），含 `durationMs`、`model`、`cardActionJson`。
- 单据核对模型 `InventoryRecord`（batchscanner/task/models/TaskModels.kt:45）**纯内存不落库**，与 StockRecord 是两套体系。

## 6. 核心模块 → 关键文件

| 模块 | 关键文件（相对 app/src/main/java/com/example/inventorymaster/） |
|------|------|
| 依赖装配 | InventoryApplication.kt、viewmodel/AppViewModelFactory.kt |
| 数据入口 | data/repository/InventoryRepositoryImpl.kt（一切数据操作） |
| 主盘点 | viewmodel/InventoryViewModel.kt（performSearch L115 降级链；prepareRecordForSync L243；查验状态管理） |
| 同步 | data/sync/SyncOrchestrator.kt + 4 策略 + TimestampManager.kt；viewmodel/SyncViewModel.kt |
| 网络 | data/network/InventoryApiService.kt(:8080,Bearer)、TaskApiService.kt(:8000,无鉴权) |
| 扫码引擎 | utils/DecoderChain.kt（**object Decoder 统一解码门面**，全 app 唯一 ML Kit client）、batchscanner/core/util/QRCodeScannerUtil.kt（processBitmap 主入口）、utils/VisionManager.kt、utils/YoloDetector.kt、utils/LibDmtxDecoder.kt、ui/analyzer/multiscan/（多码定格选择）、ui/analyzer/ResizableScanOverlay.kt、cpp/libdmtx_wrapper.cpp |
| UDI | utils/Gs1Parser.kt（aiRules L20）；utils/Hybiome.kt（长光批号特例） |
| 单据核对 | batchscanner/task/viewmodel/InventoryMainViewModel.kt（processScannerResults L141 贪心匹配） |
| AI 核心 | viewmodel/ChatViewModel.kt、ai/InventoryAiAgent.kt（业务适配）、ai/runtime/AiRuntime.kt（通用循环）、ai/agent/strategy/（FC/JSON 协议路由）、ai/tools/（9 工具，含 QueryRecordsTool、QueryVerificationRecordsTool、UpdateRecordVerificationTool 等）、ai/memory/ConversationMemory.kt（轮次原子裁剪）、ai/provider/CloudAiService.kt、ai/local/LocalLlmRuntime.kt（LiteRT-LM GPU/KV 运行环境） |
| 导航 | MainActivity.kt(外层)、ui/home/MainScreenNav.kt(内层+batch_scan_flow 嵌套图)、ui/theme/NavMotion.kt |
| 设置 | data/SettingsRepository.kt（全部 DataStore 键） |

## 7. 模块关系（谁调用谁）

- UI 永远只碰 ViewModel；ViewModel 只碰 Repository；Repository 碰 DAO/网络/同步。**反向依赖不存在**（扫码工具类直接持有 Repository 的例外：InventoryMainViewModel 注入 repository；BatchScannerViewModel 不碰 Repository，结果经回调交 InventoryMainViewModel）。
- `batchscanner` 是**自包含子系统**：自带 ViewModel/模型/OCR，与主业务唯一交汇点是 `InventoryMainViewModel`（经嵌套 NavHost 父作用域共享，MainScreenNav.kt:331,366）。
- AI 工具通过 lambda/接口反向拿数据：`QueryInventoryDataTool { repository.executeDynamicSqlForAi(sql) }`（ChatViewModel.kt:95-97）。

## 8. 重要业务规则（改前必背）

1. UDI 判定 = Gs1Parser 解析出 AI **01**（Gs1Parser.kt:105-108）。
2. productKey = DI ?: 物料编码，两者都空抛异常（ProductBase.kt:41-49）。
3. 本地写库必须走 `prepareRecordForSync` 打脏标记；服务端数据落库必须强制干净（StockRecordDao.saveRemoteRecords）。
4. 产品有库存记录时删不掉（外键 RESTRICT）——用 `migrateProductKey`/`saveProductsOnly` 平移主键。
5. 单据核对：实到>应到=ERROR（多扫拦截）；导出闸门 `checkExportReadiness`（ERROR 禁止/WARNING 确认）。
6. 金蝶单据行只有 materialCode，DI 靠 `getProductByMaterialCode` 反查——字典是桥。
7. 近效期 = 剩余≤设置阈值(默认30天) 或 剩余<总效期25%（InventoryViewModel.kt:204-212）。
8. 同步锚点更新必须防倒退（TimestampManager 取 max）。
9. 查验状态判定由数据库原生字段 `is_verified`（0=未查验, 1=已查验）负责，DAO 与业务层直接过滤；撤销查验时自动转存备份实盘数据至 remarks，防人工数据丢失。

## 9. 高风险区域（⚠️ 改动需最高谨慎）

| 区域 | 原因 | 详见 |
|------|------|------|
| `data/AppDatabase.kt` | fallbackToDestructiveMigration 仍在链尾：未覆盖的版本跳转仍会清库，改 schema 必须补 Migration | docs/DATABASE.md |
| `data/sync/*` 增量下载 | 冲突静默服务端覆盖 | SYNC.md §5 |
| `stock_records` 外键/索引 | RESTRICT 约束、UUID 唯一索引、(sessionId, is_verified) 索引 | DATABASE.md §3 |
| `cpp/` + NDK | 双 ABI 手工 CMake，libdmtx 勿动 | SCANNER.md §3 |
| `ai/local/LocalLlmRuntime.kt` | 本地模型 GPU 显存驻留与 KV 缓存复用，跨线程 cancel 与释放时序极度敏感 | docs/AI.md |

## 10. 不应随意修改的内容

- **Room schema**：任何 entity 字段变更必须 version+1 并在 `AppDatabase` 的 `.addMigrations()` 链中追加 Migration。
- **同步协议 DTO**（data/dto/SyncModels.kt）：与服务端 templates/models 强耦合，字段增删必须两端同步。
- **Gs1Parser.aiRules**：定长/变长长度符合 GS1 标准，改错会全盘解析失败。
- **BarcodeDeduplicator 阈值**（IoU 0.4 / 中心距 0.3）与 **YoloDetector 阈值**（conf 0.7）：实测调优结果。
- **libdmtx C 源码**（app/src/main/cpp/libdmtx/）：上游库，只动 wrapper。
- **TaskModels.targetQty: Double**：金蝶兼容性修复（CHANGELOG 2026-08-14），勿改回 Int。

## 11. 修改某类功能前先读

| 要改什么 | 先读 |
|----------|------|
| 数据库/实体/DAO | docs/DATABASE.md → DEVELOPMENT.md §5.1 |
| 同步/冲突/服务端接口 | docs/SYNC.md + docs/API.md |
| 扫码识别率/新码制 | docs/SCANNER.md + docs/UDI.md |
| 单据核对/金蝶流程 | docs/BUSINESS.md §3.3 + docs/UDI.md §3.3 |
| AI 行为/工具 | docs/AI.md |
| UI/导航 | docs/UI.md |
| 通用流程/构建 | docs/DEVELOPMENT.md |

## 12. 当前已知问题（摘要）

- 历史 P0 问题已全部闭环：P0-1（AI SQL 白名单参数化）、P0-2（Room v5~v12 完整迁移链）、P0-3（签名配置外置 keystore.properties）。
- 增量下载冲突服务端覆盖不提示（P1）；单据核对不落库，崩溃即丢（P1）。
- operator 固定"手机端"，多设备审计区分度不足（P2）。
- 完整清单：docs/TECH_DEBT.md；现象级排查：docs/TROUBLESHOOTING.md。

## 13. 当前技术债务（摘要）

巨型文件群（9 个 760~1158 行）；测试仅 3 类 11 例；透视矫正/滑动窗口方案已设计未落地；旧设置 API 残留；死字段多。详见 docs/TECH_DEBT.md。

## 14. 文档索引

```
README.md              人类入口（简介/构建/功能）
AI_CONTEXT.md          本文件（AI 入口）
docs/
├── ARCHITECTURE.md    架构与调用关系
├── BUSINESS.md        业务域与端到端流程
├── DATABASE.md        Room 全表结构与演变
├── API.md             全部网络接口
├── SCANNER.md         扫码/视觉引擎（重点）
├── UDI.md             UDI 解析与匹配
├── SYNC.md            同步机制
├── AI.md              AI Agent 与工具
├── UI.md              页面/路由/状态来源
├── DEVELOPMENT.md     开发指南与修改速查
├── TROUBLESHOOTING.md 问题排查
├── TECH_DEBT.md       技术债务 P0-P3
└── archive/           历史方案/过程文档归档（索引 archive/README.md）
CHANGELOG.md           变更日志（持续维护）
```

注：金蝶 WebView 适配功能不在本仓库（独立项目 KingdeeUDIApp + Tampermonkey 脚本），故无 KINGDEE_WEBVIEW.md。

## 15. 行为约束（给 AI 的硬规则）

1. **以代码为准**：本文与 docs 是 2026-09 快照，动手前用 grep/读码复核行号与实现。
2. 未经确认不要升级 Room version、不要动 DTO/协议、不要重构 9 个巨型文件（回归风险）。
3. 新增依赖需说明理由（项目已很重）。
4. 遵循现有模式：手动 Factory、Repository 单入口、sealed class + StateFlow、中文注释风格。
5. 改完跑 `.\gradlew.bat compileDebugKotlin`（Windows PowerShell）。
6. 每次功能性改动在 CHANGELOG.md 追加条目。
7. 不确定就标【待确认】问用户，不要编造。
