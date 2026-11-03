# UI — 界面结构与状态来源

> 全部 Jetpack Compose + Material3（无 XML 布局）。状态来源：各 Screen 收集对应 ViewModel 的 `StateFlow`，事件经 lambda 回调上行。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。

## 1. 路由总表

| 路由 | Screen | 所在文件:行 | ViewModel |
|------|--------|-------------|-----------|
| 外层 `home` | MainScreen（内层容器） | MainActivity.kt:85 | 复用 5 个 VM 下传 |
| 外层 `inventory/{sessionId}` | InventoryScreen | MainActivity.kt:106 | InventoryViewModel + SessionViewModel + SyncViewModel + ChatViewModel |
| 外层 `ai_chat?sessionId={sessionId}` | ChatScreen | MainActivity.kt:130 | ChatViewModel（外层路由，避免跨层找不到路由闪退） |
| 内层 `home` | HomeScreen | ui/home/MainScreenNav.kt:252 | （入口卡片 + 底部 AI 快捷入口） |
| 内层 `session_list` | SessionListScreenNav | MainScreenNav.kt:269 | SessionViewModel + SyncViewModel |
| 内层 `products` | ProductManagerScreenNav | MainScreenNav.kt:280 | InventoryViewModel |
| 内层 `settings` | SettingScreenNav | MainScreenNav.kt:292 | SettingsViewModel + InventoryViewModel |
| `watermark_settings` | WatermarkSettingsScreen | MainScreenNav.kt:308 | （直接读写 SettingsRepository） |
| `signature_settings` | SignatureSettingsScreen | MainScreenNav.kt:316 | 同上 |
| `ai_settings` | AiSettingsScreen | MainScreenNav.kt:324 | SettingsViewModel |
| `batch_scan_flow/taskDetail` | InventoryTaskDetailScreen | MainScreenNav.kt:339 | InventoryMainViewModel（父作用域共享） |
| `batch_scan_flow/batchCamera?inputUri=` | BatchScannerScreen | MainScreenNav.kt:365 | BatchScannerViewModel + InventoryMainViewModel |
| `batch_scan_flow/videoStream` | VideoStreamScreen | MainScreenNav.kt:408 | VideoStreamViewModel + InventoryMainViewModel |

全屏隐藏顶/底栏：`taskDetail / batchCamera / singleCamera / videoStream / session_list / watermark_settings / signature_settings / ai_settings`（MainScreenNav.kt）。
底部三 Tab：主页 / 基础产品库 / 系统设置。

### 转场动效与容器几何稳定性（2026-10 优化）
- **统一转场动效 (`NavMotion.kt`)**：彻底剔除旧版 `0.4f` 残影淡入淡出。二级推栈与返回采用带贝塞尔曲线（`CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)`）的水平平移加全透明度淡入淡出（右进右出、镜像退场）；一级 Tab 切换采用瞬时切换（`EnterTransition.None / ExitTransition.None`），杜绝黏滞感。
- **消除全屏切页的容器 Padding 突变**：`MainScreenNav.kt` 中 `NavHost` 的尺寸恒定为 `Modifier.fillMaxSize()`，绝不在切入全屏页面时动态增减 `innerPadding`。脚手架的 `innerPadding` 仅独立注入于一级 Tab 宿主（`home`、`products`、`settings`），全屏推栈页面保持满屏绝对恒定尺寸转场，彻底杜绝切页第 0 帧容器高度跳变引发的同步重排（Synchronous Relayout）与视觉闪动。
- **键盘与沉浸式治理（Edge-to-Edge）**：`MainActivity` 全面开启 `enableEdgeToEdge()`，`MainScreenNav.kt` 外层导航栏通过 `rememberIsKeyboardOpen()` 在软键盘弹起时自适应隐藏，彻底杜绝底部导航栏被输入法顶起；页面内容区（产品库、AI 聊天页等）显式通过 `Modifier.imePadding()` 与系统 `WindowInsets.ime/statusBars/navigationBars` 联动弹性伸缩。

## 2. 主要页面结构

### InventoryScreen（ui/inventory/InventoryScreen.kt，约 1000 行）
盘点详情主页。组成：
- `SearchBarArea`（components/SearchBarArea.kt）：搜索框+扫码按钮——扫码回调 `performSearch(query)`（InventoryViewModel.kt:115 降级链）。
- 「长光模式」开关行：智能批号处理开关 + **搜索结果数量合计**（`合计: N`，只加 `quantity`，随搜索实时变化；M9 2026-09-25 新增）。
- ~~`ModeSwitcher`~~（**M9 已移除**，components/ModeSwitcher.kt 已删除）：盘库/查询模式不再手动切换——`isInventoryMode = !isReadOnly` 派生（可编辑任务恒为盘库模式，只读任务恒为查询视角，点击记录看详情）。
- 记录列表 `StockRecordItem`（components/StockRecordItem.kt）：渲染 `StockRecordUiModel`（高亮字段 HighlightField + 效期三色 ExpiryState，计算逻辑 InventoryViewModel.kt:166-225）。
- 弹窗群 `InventoryDialogs.kt`：编辑记录、新建任务等；`SyncDialog`/`DownloadConflictDialog`（ui/sync/）由本页触发（InventoryScreen.kt 对 SyncViewModel 的 8 处调用）。
- ~~Excel 导入~~（**M9 已迁出**）：导入入口移至「新建盘点任务」弹窗（SessionListScreenNav，可选选文件），模板选择/列映射/导入确认流水线随迁；详情页更多菜单不再有「导入Excel」。
- 计算器：`InventoryCalcDialog`（浮层计算器，CalcEngine 支持优先级/连续运算）。
- **极速模式（`isQuickCheckMode`）**：右上角菜单开关开启。未查验物料右侧显示 84dp 蓝色「查验」按钮（点击一键核验）；已查验物料右侧切换为 84dp 浅绿背景「已查验（长按撤销）」框（长按调用 `cancelVerification` 安全备份实盘数据至备注）。长按卡片弹出编辑/操作弹窗。**坑点警示**：右侧状态框与查验按钮为严格互斥的 `if (isChecked) { ... } else { ... }` 结构，严禁漏掉 `else` 分支导致双按钮并排撑爆 168dp 挤压左侧文本。

### InventoryTaskDetailScreen（batchscanner/task/screen/，约 1158 行）
单据核对主页（业务流程见 docs/BUSINESS.md §3.3）。上半区照片轮播（photoList）、下半区记录表单（recordList，行内五字段状态染色）、顶栏（单据选择/上传解码/导出 PDF）。三个扫码入口按钮分别导航 `batchCamera`（可带相册图 inputUri）/`videoStream`。

### BatchScannerScreen（batchscanner/core/screen/，约 739 行）
CameraX 取景 + 拍照；状态机驱动四态界面（Capture/Cropping/Processing/Review，ScannerUiState.kt:5-9）：
- Cropping：`ModernCropView`（core/overlay/，拖拽裁剪框 CropDragTarget L27）。
- Review：`ReviewCanvasOverlay`（core/overlay/，绘制绿/红框结果）+ 点按补扫（handleImageTapForRescan）+ 增量扫描。
- OCR 支线：`OcrCropOverlay` + `OcrResultDialog`（batchscanner/ocr/）。
- 内嵌 `SimpleFloatingScanner`（ui/analyzer/）作单码实时补扫。

### VideoStreamScreen（batchscanner/videostream/screen/，约 380 行）
实时取景 + `VideoStreamOverlay`（绿/红框+网格）+ 统计条（ScanStatistics）+ 录像按钮（VideoRecordHelper）+ 暂停/恢复/确认。

### ProductManagerScreenNav（ui/productManager/，约 927 行）
产品字典：搜索（searchProducts 4 字段模糊）、多选批量删除（RESTRICT 失败提示）、Excel/JSON 导入、`ProductConflictScreen`（DI 冲突决策）、`ImportDiagnosticsDialog`（跳过行/重复组报告，data/model/ImportDiagnostics.kt）。

### SettingScreenNav（ui/home/，约 960 行）
分组：主题（seedColor/动态色/AMOLED/深浅模式/简洁模式）、扫码（扫描框宽高、多码、DI 校验、OpenCV 开关、OCR 遮罩）、服务器（IP+Token 验证）、AI（进入 AiProviderSettingsScreen）、水印/签名入口、近效期阈值、结巴分词模式。
状态源：`SettingsViewModel` ← `SettingsRepository.settingsFlow`（DataStore）。

### ChatScreen（ui/ai/，约 222 行）
消息列表（ChatMessageItem：Markdown 渲染 MarkdownText.kt）+ 输入框 + 工具调用气泡 + `ProductCardListWidget`（AI 推送的过期/近效期产品卡片，点击进编辑——当前 TODO，ChatViewModel.kt:266-275）。`pendingActions` 驱动导航/弹窗。

### HomeScreen / SessionListScreenNav
HomeScreen：入口卡片（新建任务、批量扫描、云任务）。SessionListScreenNav（约 930 行）：任务列表（SessionWithProgress 进度条：已查验/总数，SessionDao.kt:39-47）、ExpandableActionCard 操作面板（同步/归档/锁定/删除/导出）、云任务对话框、二维码分享。

## 3. 主题系统（ui/theme/）
- `Theme.kt`：Material3 主题入口；`Color.kt`/`ExtendedColors.kt`（ModernColorSchemes L9 / ThemeColors L76）/`Spacing.kt`。
- 支持：seed 色自定义（默认 0xFF4CAF50 绿）、Android 12 动态取色（默认关）、AMOLED 纯黑、深浅模式（UserSettings，SettingsRepository.kt:16-47）。

## 4. 通用组件（ui/components/）
- `ModernDialog.kt`（约 378 行）：全 App 统一弹窗基座（DialogTokens L48）——被 ProductManager/SessionList/TaskDetail/Inventory 等大量复用。
- `SharedComponents.kt`：Badge（BadgeSeverity L33-37：Success/Warning/Error/Custom）、StandardCard 等。

## 5. 液态玻璃材质系统（ui/liquidglass/）
- `LiquidGlass.kt`：高性能纯 Compose 实现的物理折射液态玻璃组件（五层光学模型：外软阴影、锐利外高光边、物理折射暗缝、内倒角漫反射高光、主体与凸面流光）。
- 具备同心圆角几何内缩（`Concentric RoundRect Inset`，避免非等比缩放将胶囊拉扯为椭圆）与轻量无重组按压交互。
- 材质预设（`LiquidGlassStyle.kt`）：
  - `DarkGlass` / `DarkStrong`：深色烟熏玻璃面板（用于计算器浮层 `InventoryCalcDialog`）；
  - `CrystalClear`：晶透白液态玻璃（用于高透圆形浮钮、现代化胶囊操作栏）。
- 详见完整设计文档 [LIQUID_GLASS.md](LIQUID_GLASS.md)。

## 6. UI 状态来源速查

| 界面状态 | 来源链 |
|----------|--------|
| 任务列表+进度 | Room Flow：SessionDao.getAllSessionsWithProgress → SessionViewModel |
| 盘点记录列表 | Room Flow：StockRecordDao.getRecordsBySession(StockRecordCombined) → InventoryViewModel.uiState（searchResults 为搜索时快照） |
| 单据核对表格 | 内存 StateFlow：InventoryMainViewModel.recordList（不落库） |
| 扫码界面 | BatchScannerViewModel.uiState（状态机）+ 本地 mutableStateOf |
| 主题/扫码设置 | DataStore：SettingsRepository.settingsFlow → SettingsViewModel |
| 同步状态 | SyncViewModel.uiState（isSyncing/syncMessage/pendingDownloadConflict） |
| AI 对话 | ChatViewModel.uiState（messages/pendingActions/isProcessing） |
| 视频/拍照进度 | VideoStreamViewModel 会话状态 + ScanStatistics |
