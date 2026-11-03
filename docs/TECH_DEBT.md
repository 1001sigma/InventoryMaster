# TECH_DEBT — 技术债务清单

> 原则：只列从代码确认的问题，不制造"看起来专业"的伪问题。P0=可能致数据错误/系统不可用；P1=高；P2=中；P3=低。
> 每条含证据位置（相对 `app/src/main/java/com/example/inventorymaster/`）。

## P0（严重）

| # | 问题 | 证据 | 影响 |
|---|------|------|------|
| P0-1 | ~~**AI 动态 SQL 无白名单**~~ **已解决（2026-09-19，v3 一期）**：动态 SQL 工具与 `executeDynamicSqlForAi` 已删除，替换为 `QueryRecordsTool` + `AiRecordSqlBuilder`（白名单列 + 全绑定参数，LLM 不接触 SQL） | data/repository/AiRecordQuery.kt；ai/tools/QueryRecordsTool.kt | 注入面归零；测试 AiRecordSqlBuilderTest 覆盖 |
| P0-2 | ~~**Room `fallbackToDestructiveMigration()` 导致清库**~~ **已解决（2026-10，已实现 MIGRATION_5_6 ~ MIGRATION_11_12 完整迁移链）**：从任务 startedAt、chat_messages 建立、durationMs、model、cardActionJson 到 is_verified 均已编写原地 SQL 迁移脚本并在双检锁单例中注册 | AppDatabase.kt:41-118；当前 DB version=12 | 正常发版与升级保留所有本地数据；只有低于 v5 或未定义的非连续版本跳跃才会走到兜底清库 |
| P0-3 | ~~**签名密钥明文入库**~~ **已解决（2026-10）**：签名凭据已从 `app/build.gradle.kts` 剥离，改为通过外部 `keystore.properties`（已加入 .gitignore）读取，源码中不再暴露硬编码密码 | app/build.gradle.kts:18-31；.gitignore | 仓库代码安全，防止凭据意外泄漏 |

## P1（高）

| # | 问题 | 证据 | 影响 |
|---|------|------|------|
| P1-1 | **增量下载冲突永远服务端覆盖**：本地 `syncStatus=1` 未推送修改被静默覆盖，仅 historyLog 留痕；ConflictItem 只打日志不上抛 UI；服务端 `conflictSummary` 字段客户端从不消费 | IncrementalDownloadStrategy.kt:88-135；SyncModels.kt:62-70 | 多端并发编辑场景用户数据无声丢失 |
| P1-2 | **单线程布尔锁做同步防重**：`SyncViewModel.isSyncRunning` 非原子，且 `performDownload` 中途置 false 再走 `executeDownload` 重置 true 的窗口期 | SyncViewModel.kt:66,72,169,202 | 竞态可双发请求（有 Orchestrator Mutex 兜底，故降为 P1 而非 P0） |
| P1-3 | **主线程数据库访问**：`allowMainThreadQueries()` 常驻 | AppDatabase.kt:40 | 大库（数万记录 Excel 导入/搜索 9 字段 LIKE）卡 UI/ANR |
| P1-4 | ~~**AI 文本兜底拦截器硬编码中文别名**~~ **已解决（2026-09-19，v3 一期）**：拦截器已删除，弱模型场景改由 `JsonToolCallStrategy` 正式协议接管（解析失败按普通回答/纠正重试，不再伪造调用） | ai/agent/strategy/ToolCallStrategy.kt | — |
| P1-5 | **金蝶单据核对结果不落库**：`InventoryRecord` 全内存，App 崩溃/退出即丢，仅 PDF/码串已上传的部分留存 | InventoryMainViewModel.kt:40,141-266 | 长时间核对中途崩溃需全部重扫 |
| P1-6 | **大文件类**：InventoryTaskDetailScreen 1158 行、AiProviderSettingsScreen 1004、InventoryScreen 1000、SettingScreenNav 960、SessionListScreenNav 930、ProductManagerScreenNav 927、InventoryViewModel 860、ScanScreen 830、ExcelUtils 770 | wc -l 统计 | 维护成本高、回归风险集中（改动热点，见 .mimocode/skills 统计） |
| P1-7 | **测试严重不足**：v3 一期新增 14 个 AI 用例（ConversationMemoryTest 7 + AiRecordSqlBuilderTest 7）后共 40 个；同步、DAO、扫码、冲突仍零覆盖。⚠️ 两个环境/历史问题：① 系统默认 JDK 24 超出 Gradle 8.11.1 支持范围，`testDebugUnitTest` 任务无法实例化，需 `-Dorg.gradle.java.home=D:/Android/Android Studio/jbr`（JBR 21）运行；② 测试源集曾因 `CalcEngine` private 编译不过（已改 internal），解冻后暴露 4 个历史失败：Gs1ParserTest 效期格式期望 8 位（解析器输出 GS1 规范的 6 位 YYMMDD）、BatchCodeProcessorTest 非法输入边界 2 个——**期望行为待产品确认后修复** | app/src/test/；Gs1ParserTest.xml | 回归防护网仍不完整 |

## P2（中）

| # | 问题 | 证据 | 影响 |
|---|------|------|------|
| P2-1 | **operator 字段语义失效**：Entity 默认 Build.MODEL，但写库一律覆盖为 "手机端"，多设备区分度丢失（服务端操作人审计失效） | StockRecord.kt:56-57；InventoryViewModel.kt:245,260 | 无法追溯哪台设备改的数据 |
| P2-2 | **ProductDto 丢字段**：DTO 无 `lastSyncTime`/`source`，同步往返后重置为默认值 | SyncModels.kt:24-35 vs ProductBase.kt:31-32 | 产品来源标记在同步后失真 |
| P2-3 | **旧设置 API 残留**：`SettingsRepository.saveLastSyncTime/getLastSyncTime`（旧 string 键）与 TimestampManager 并存 | SettingsRepository.kt:343-351 | 误用会造成锚点双源不一致 |
| P2-4 | **两个全屏控制器并存**：InventorySession.status(0/1/2) 与 isLocked 冗余 | InventorySession.kt:17-19 | 状态不一致风险 |
| P2-5 | **`sessionSource`/`remoteId`/`totalItems`/`scannedItems`/`extJson` 多数无写入点**（remoteId 全库无写） | InventorySession.kt:21-30 | 死字段误导后来者 |
| P2-6 | **透视矫正未接线**：`ImagePreprocessor.perspectiveCorrect` 无调用方 | ImagePreprocessor.kt:73 | 倾斜/遮挡码识别率损失（方案文档《yolo方案优化.md》的滑动窗口亦未落地） |
| P2-7 | ~~**Magic String `"已查验"` 状态判定**~~ **已解决（2026-10-30，DB v12）**：`stock_records` 正式新增 `is_verified` 数据库列与 `(sessionId, is_verified)` 索引，DAO 层与业务层全量改为原生布尔判定；存量数据通过 `MIGRATION_11_12` 平滑刷洗，撤销查验转存备注仅作为防丢备份 | StockRecord.kt:81；SessionDao.kt:47；StockRecordDao.kt:151；AppDatabase.kt:105 | 消除脆弱字符串匹配，大幅提升查验筛选与统计性能 |
| P2-8 | **Excel 导入整段跑 IO 的协程粒度粗**：无分批/进度取消（importExcelFile→analyzeProductsImport 链） | InventoryViewModel.kt:356-420 | 大文件 UI 冻结、无取消 |
| P2-9 | **OkHttp 未设超时**：同步服务 client 未显式 timeout（OkHttp 默认 10s 读，大 SyncData 传输可能中断重试放大） | InventoryApiService.kt:65-74 | 弱网下同步体验差 |
| P2-10 | **AI_PROVIDER 旧键迁移只在首次**：`migrateOldProviderSettings` 条件 providersJson 为空才跑 | SettingsRepository.kt:138-189 | 迁移失败后无重试路径 |

## P3（低）

| # | 问题 | 证据 |
|---|------|------|
| P3-1 | ~~根目录堆积 14+ 份中文方案 md，未归档~~ **已解决（2026-09）**：13 份方案 md 已归档至 docs/archive/（索引见其 README.md）；`__dream_query*.py` 临时脚本仍在根目录待清理 | docs/archive/README.md |
| P3-2 | `countObjects()` TODO 直接崩溃（调用即抛） | VisionManager.kt:202-204 |
| P3-3 | ~~`ChatViewModel.handleCardActionClick` 点击产品卡片无响应~~ **已解决（2026-10-15）**：卡片携带 recordId 点击触发现代 `CardDetailDialog` 弹窗，完整呈现任务、库位、批号、效期、物料编码等 10 个全量业务字段，完成单条消息交互闭环 | ChatMessageItem.kt；CardDetailDialog.kt | 交互完整 |
| P3-4 | codegraph 库内部分 Kotlin→dmtxencode.c 调用边为误匹配噪音（如 InventoryScreen→dmtxencode.c） | .codegraph/codegraph.db edges |
| P3-5 | git 历史仅 12 commit、信息简略，多数设计动机不可考 | git log |
| P3-6 | `Hybiome.kt`（批号处理器）文件名与内容（BatchCodeProcessor）不对应 | utils/Hybiome.kt:3 |

## 已核实"不是问题"的项（避免误改）

- 同步死循环已有防护：`saveRemoteRecords` 强制 syncStatus=0（StockRecordDao.kt:141-146）。
- AI 工具死循环防护 v3 已增强：完全相同签名 + 同工具连续 3 轮双检测（InventoryAiAgent.kt，先检测后写 memory，不留孤立 tool_calls）。
- 大图切片有 OOM/回收保护（QRCodeScannerUtil.kt:231-239）。
- 产品删除有外键 RESTRICT + try-catch 双保护（StockRecord.kt:24-29、InventoryRepositoryImpl.kt:209-222）。
- AI 工具调用上限 5 轮（v3：另加单轮工具数≤4、单工具 10s 超时、LLM 90s 超时；v2 的 500ms 轮间 delay 已删除）。

## 修复优先级建议（供排期参考，非本次执行）

1. ~~P0-1：SQL 白名单~~ **已完成（2026-09-19 v3 一期，结构化查询 + 绑定参数，优于白名单方案）**。
2. ~~P0-2：补 Migration~~ **已完成（2026-10，建立 MIGRATION_5_6 ~ 11_12 链式迁移）**。
3. ~~P0-3：签名安全~~ **已完成（2026-10，外置 keystore.properties）**。
4. P1-1：增量下载冲突上抛 UI（复用 DownloadConflictDialog 模式）。
5. P1-5：单据核对会话持久化（Room 新表或 JSON 快照）。
