# TROUBLESHOOTING — 已知问题与排查

> 来源：代码分析可确认原因的，写原因与定位；不能确认的标【待确认】。相对路径基 `app/src/main/java/com/example/inventorymaster/`。

## 1. 编译/构建

| 症状 | 原因（代码证据） | 处理 |
|------|------------------|------|
| `TaskApiService` 相关编译错误：`targetQty` 类型不匹配 | 2026-08-14 由 Int 改 Double（CHANGELOG.md；TaskModels.kt:107），旧代码 `.toInt()` 缺失 | 对照 CHANGELOG §一 修复；`(item.targetQty.toInt())` |
| NDK/CMake 阶段失败 | 需 NDK 30.0.14904198 + CMake 4.1.2（build.gradle.kts:12,68），本地缺组件 | SDK Manager 安装对应版本 |
| Release 包崩溃（JSON/OCR） | minifyEnabled=true（build.gradle.kts:46），proguard-rules.pro 只有 `-dontwarn`（POI 生成），**未见 keep 规则**【待确认-运行期是否实际出错】 | 必要时为 Gson 反序列化模型（data/dto/、TaskModels）加 `-keepclassmembers` 或 @SerializedName 已有注解验证 |
| POI 相关 `javax.*` 警告刷屏 | proguard-rules.pro:15-50 大量 `-dontwarn java.awt/javax.xml` | 无害，忽略 |

## 2. 数据库

| 症状 | 原因 | 处理/规避 |
|------|------|-----------|
| **升级后本地数据全没** | `fallbackToDestructiveMigration()`（AppDatabase.kt:41），无 Migration | 已知设计（见 TECH_DEBT P0-2）；升级前先推送/导出 |
| 删产品报"仍有库存记录引用" | 外键 RESTRICT（StockRecord.kt:24-29）；`deleteProductByKey` 捕获后仅 Log.w（InventoryRepositoryImpl.kt:209-215） | 先删/迁移该产品库存记录，或改关联键（migrateProductKey L166） |
| productKey 变更后记录"跟丢" | 主键裂变（补 DI 后 key 从物料编码变 DI）需走 `migrateProductKey`/`saveProductsOnly` 平移（InventoryRepositoryImpl.kt:183-208） | 导入产品走正规导入流程（自动平移），勿直接 insertProduct |
| 主线程查库卡顿/ANR | allowMainThreadQueries（AppDatabase.kt:40）+ 大库 LIKE 全表扫（StockRecordDao.kt:58-75） | 数据量大时迁移到 IO 协程 |

## 3. 同步/网络

| 症状 | 原因 | 处理 |
|------|------|------|
| 全量上传报"服务器拒绝" | 服务端先删后插事务失败或数据体过大（FullUploadStrategy.kt:71-79 未细分错误码） | 看 Logcat `SYNC_ERROR` 与服务端日志；检查 inventory.db 落盘空间 |
| 上传后部分记录仍显示待同步 | 增量上传逐条结果处理：服务端返回 failed 的保持脏（IncrementalUploadStrategy.kt:77-92） | 按响应 reason 排查；重试推送 |
| 拉取后本地改动消失 | 增量下载冲突策略=服务端覆盖（IncrementalDownloadStrategy.kt:115-135，historyLog 有 "server_overwrite" 痕迹） | 设计如此（TECH_DEBT P1-1）；重要修改先推送 |
| 下载按钮弹出"存在未同步数据"对话框 | 下载前 dirty 检查（SyncViewModel.kt:164-177） | 三选一：先推后下/强制下载/取消 |
| 连不上 :8080 | Token 未验证通过（verifyAndSaveToken 失败即不保存 IP，InventoryRepositoryImpl.kt:503-518）；或 IP 带 http:// 前缀格式问题（InventoryApiService.kt:63 已兼容） | 设置页重新验证；确认服务端 AuthMiddleware 白名单 |
| :8000 任务列表为空 | TaskApiService 强制 8000 端口（TaskApiService.kt:58-60）；服务端只回 status==0 的任务（InventoryMainViewModel.kt:92） | 确认 pythonUDI 运行、单据未被脚本重复推送成已完成 |
| 弱网同步失败重试间隔突兀 | RetryPolicy 500ms×2^ 最多 3 次仅网络类异常（RetryPolicy.kt:26-54）；HTTP 4xx/5xx 不重试 | 属预期；失败信息在 SyncState.syncMessage |

## 4. 扫码

| 症状 | 原因 | 处理 |
|------|------|------|
| 小码/密集码漏扫 | YOLO 在 ≤1920 缩略图上检测（VisionManager.kt:46 THUMBNAIL_MAX_WIDTH），小码特征被抹平 | 依赖金字塔切片兜底（QRCodeScannerUtil.kt:161-168）；仍失败用局部补扫（点按 rescan） |
| DataMatrix 破损码识别率低 | ML Kit 失败后 libdmtx 才介入（DecoderChain 顺序） | libdmtx 对旋转/部分遮挡更鲁棒但慢；确认 OpenCV 增强已开（useOpenCV 设置） |
| OpenCV 路径偶尔整体失效 | `OpenCVInit.init()` 失败直接走金字塔（QRCodeScannerUtil.kt:86-103 有降级，消息提示"OpenCV 未加载"） | 看 Logcat OpenCV 初始化错误；初始化状态 OpenCVInit.State |
| 补扫点按无反应 | `currentReviewBitmap` 为空（BatchScannerViewModel.kt:110-111 直接 return） | 必须先进入 Review 态（处理完成）再点按 |
| 识别出码但记录成"非 UDI 格式" | Gs1Parser 只认 aiRules 表内 AI，且必须含 01（Gs1Parser.kt:105-108） | 码面是纯批号文本等非 GS1 码属预期；需支持新 AI 见 docs/UDI.md §5 |
| YOLO 无检出日志 | 模型加载失败静默返回空（YoloDetector.kt:38-52,58-62） | Logcat 搜 "Failed to load YOLO"；确认 assets/barcode_detector.onnx 在包内 |
| 视频流重复计数 | 网格/跟踪器依赖 setDimensions（VideoStreamViewModel.kt:74），预览尺寸未初始化时网格失效【待确认-具体复现条件】 | 确认先进入画面再开始流 |

## 5. OCR

| 症状 | 原因 | 处理 |
|------|------|------|
| 批号/效期提取失败 | 正则组未命中（OcrInfoExtractor.kt:18-36）且 AI 未配置或不可用 | 配置 AI 服务商后重试（走 extractByAi 兜底 L95） |
| OCR 批号对不上单据 | fuzzyMatch 字符映射表覆盖有限（OcrFuzzyMatcher.kt:26） | 手工修正；必要时扩 charMap |
| 中文识别不准 | 用的是 text-recognition-chinese 16.0.1（build.gradle.kts:124） | 属模型能力；重要字段走扫码 |

## 6. AI 模块

| 症状 | 原因 | 处理 |
|------|------|------|
| 提示"请先在设置中配置 AI 服务" | activeProvider 为空（ChatViewModel.kt:62-77,121-123） | 设置→AI 服务商配置 |
| AI 说查了数据但结果离谱 / 危险 SQL | 动态 SQL 无白名单（TECH_DEBT P0-1，InventoryRepositoryImpl.kt:526） | 尽快落白名单；当前靠 prompt 约束 |
| 工具被反复调用后突然道歉 | 死循环签名检测截断（InventoryAiAgent.kt:119-127） | 换问法；检查工具参数 schema |
| 本地模型选项无效果 | LocalAiService 占位未实现推理（ai/provider/LocalAiService.kt） | 待 llama.cpp/onnx 接入（ai/local/ 基建已有） |
| AI 回复里出现英文碎片 "Let's call that" | 上轮数后兜底清洗只覆盖两种短语（InventoryAiAgent.kt:174-177） | 可扩清洗词表 |

## 7. UI/其它

| 症状 | 原因 | 处理 |
|------|------|------|
| 聊天页点产品卡片无反应 | handleCardActionClick TODO（ChatViewModel.kt:266-275，recordId=0） | 待实现 recordId 回查 |
| 极速模式已查验项双按钮并排、批号严重挤压竖排；待查验项右侧空白无查验按钮 | `InventoryScreen.kt:825-886` 处 `if (isChecked)` 与 `Surface` 之间的 `} else {` 被自动化编辑误删，导致 `Surface` 漏进 `if (isChecked)` 块内并发渲染（84dp+84dp=168dp 挤扁左侧信息列），且 `isChecked==false` 时跳过查验按钮；伴生卡片点击/长按的 `} else {` 也被同一批次清空 | 恢复 `if (isChecked) { Box(...) } else { Surface(...) }` 与弹窗 `if (sourceType == 0) { ... } else { ... }` 分支。严防使用通用短行（如单独一行 `} else {`）作为无上下文的 `edit_file` Target 进行全局替换 |
| 金蝶单据行 di 为空、扫码全红 | 产品字典缺该物料编码→DI 映射（InventoryMainViewModel.kt:407,451 反查失败） | 先在产品库导入含物料编码+DI 的字典 |
| 计算器精度问题 | Double 运算（CalcEngine，ui/InventoryCalcDialog.kt:323）【待确认-是否有复现 case】 | 如需金额精度改 BigDecimal |
| 水印不生效 | 水印仅作用于 PDF 导出链路（utils/pdf/ + PdfExportUtils），不作用于预览 | 属设计 |
