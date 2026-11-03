# BUSINESS — 业务视角说明

> 面向业务理解：谁在用、解决什么问题、核心业务流"用户操作 → 系统处理 → 数据变化 → UI 结果"。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。用户角色从代码/界面文案推断处标【推测】。

## 1. 项目解决的问题

医疗器械仓库（医院/经销商库房）的**库存盘点与单据核对**。行业痛点：
1. 医疗器械 UDI（DI+批号+效期）码制多、密集、易破损——需要强扫码能力（YOLO 定位+多引擎解码+切片兜底）。
2. 账面（Excel 底账 / 金蝶 ERP 单据）与实物需要核对——需要高效批量扫码 + 自动比对。
3. 院内多设备协作盘点——需要局域网同步（无公网依赖）。
4. 金蝶云星空 ERP 的出入库单据要下发现场核对、结果回传归档——通过 pythonUDI 中转实现非侵入对接。

## 2. 用户角色【推测，依据界面文案与权限模型缺失】

- **库房管理员/盘点员**（唯一角色，App 无登录/权限体系）：操作全部功能。
- **PC 端数据管理员**【推测】：维护 inventory_server 的网页端（Jinja2 页面，服务端仓库）与金蝶侧数据。
- 操作人标识：记录级 `operator` 字段（当前固定写 "手机端"，InventoryViewModel.kt:245——**尚无真正用户体系**）。

## 3. 核心业务域

### 3.1 产品字典（L1 基础库）
- 页面：`products` Tab（ui/productManager/ProductManagerScreenNav.kt）。
- 数据：`product_base` 表（DI/GTIN、名称、规格型号、厂家、注册证、物料编码、单位、分类）。
- 来源：Excel 导入（MappingTemplate 列映射）、JSON 导入（ProductJsonUtils）、手动新增、扫码自动补占位（"未知产品(扫码)"，InventoryRepositoryImpl.kt:117-125）、服务端下载。
- 业务意义：**物料编码 ↔ DI 的映射桥梁**（金蝶单据只有物料编码，扫码只有 DI，靠字典对接，InventoryMainViewModel.kt:407,451）。

### 3.2 盘点任务（InventorySession）与库存账本（StockRecord）
- 建任务：主页 → `inventory/{sessionId}` 全屏详情（MainActivity.kt:97）。
- 账面导入：Excel（`importExcelFile`，InventoryViewModel.kt:356 / `importExcelFileWithMapping` L393）——表头自动映射或模板映射（MappingTemplateManager），冲突走 `ProductConflict` 对话框。
- 扫码盘点：单码（浮窗实时扫）/ 批量拍照 / 视频流（见 §4）。
- 手工修正：编辑对话框、`quickCheck` 一键核验（L313，置"已查验"备注）。
- 校验：DI 合法性检查（`validateCurrentTaskDIs`，L834，GS1 校验位）。
- 导出：Excel（`exportExcelFile` L459）、PDF（PdfEngine + 水印设置）。
- 数据变化：每次写库 → `prepareRecordForSync` 打脏标记+操作日志（L243-263）→ 待同步推送。

### 3.3 金蝶单据核对（batchscanner/task，核心增值业务）
入口：主页 → `taskDetail`（batch_scan_flow）。

**流程（用户操作 → 处理 → 数据 → UI）：**
1. 「单据」按钮 → 拉取 pythonUDI 任务列表（只显示 status=0 待处理）→ 勾选多单 → `loadMultipleTaskDetails`（InventoryMainViewModel.kt:437-484）逐单下载明细 → items 按物料编码解析 DI → 生成 `InventoryRecord` 内存行（targetQty=单据应到数量）。
2. 扫码核对：
   - 批量拍照：`batchCamera` → `BatchScannerScreen`（YOLO/金字塔识别多码，绿框=在单据，红框=不在）→ `onComplete` 回传。
   - 视频流：`videoStream` 连续扫（网格去重、停留判定）。
   - OCR 补录：无码标签 → 框选 → ML Kit 中文识别 → 规则/AI 提取批号效期 → `onOcrConfirmed`。
3. 每个码：Gs1Parser 解析 → 贪心匹配最该填的行 → actualQty+1 → 字段状态（DI/批号/日期/数量）逐格染色（MATCHED 绿 / WARNING 黄 / ERROR 红）。
4. 行状态聚合 = "短板效应"（TaskModels.kt:71-80 rowStatus）；单据级 `checkExportReadiness`（L371-377）：有 ERROR→强拦截导出，有 WARNING→二次确认弹窗，全绿→直接导出。
5. 导出 PDF（PdfExportUtils：摘要页+明细页+照片页）→ `uploadPdfResultToNetwork` 上传 pythonUDI 归档（L101-119）。
6. 「上传解码」：把本次扫描的原始码串（_rawBarcodeList）提交 `api/custom-data/submit`（L121-137），供后台分析识别率。
7. 支持无单据自由扫模式（全部绿框）与 JSON 导入单据（`importTargetDocumentFromJson`，L394-435）。

### 3.4 局域网同步
- 场景：多台手机协作 / PC 端汇总。设置页配 IP+Token（`/api/auth/verify`）。
- 操作：任务列表/详情页 → 推送（脏数据上传）/ 拉取（增量下载）/ 全量上传 / 全量下载（有脏数据时弹冲突三选一：强制下载/先推后下/取消）。
- 详见 docs/SYNC.md。

### 3.5 云端任务下载
`CloudTaskDialog`（ui/session/CloudTaskDialog.kt）：`fetchCloudSessions`（GET :8080/api/sessions）→ 选择服务端任务 → 全量下载到本地（sessionSource=1 服务器下发）。

### 3.6 AI 助手
`ai_chat` 页：自然语言查库存/查产品/异常分析/盘点总结（如"看近效期""分析异常"）→ AI 可驱动 UI 跳转、高亮、弹产品卡片。详见 docs/AI.md。

### 3.7 辅助工具
- 库存计算器（`ui/InventoryCalcDialog.kt` + CalcEngine L323，含优先级/除法/连算测试 CalcTest）。
- 水印设置（水印文字/透明度/角度/字号/颜色，SettingsRepository）+ 签名设置（路由 `signature_settings`）→ PDF 导出用。
- 二维码生成（utils/QRCodeUtil.kt，ZXing；任务列表页分享入口 ui/home/SessionListScreenNav.kt:使用 QRCodeUtil）。

## 4. 关键业务规则汇总（技术实现见各自文档）

| 规则 | 值/逻辑 | 代码 |
|------|---------|------|
| UDI 判定 | 解析出 AI=01 即有效 | Gs1Parser.kt:105-108 |
| productKey 优先级 | DI > 物料编码，不可都空 | ProductBase.kt:41-49 |
| 已查验判定 | remarks 含 "已查验" | StockRecordDao.kt:106-111 |
| 近效期 | 剩余天数 ≤ 设置阈值(默认30) 或 剩余 < 总效期 25% | InventoryViewModel.kt:204-212 |
| 单据数量状态 | 实到=应到 MATCHED；少于 WARNING；**多于 ERROR（多扫拦截）** | InventoryMainViewModel.kt:201-206 |
| 导出闸门 | ERROR 行 → 禁止导出；WARNING → 二次确认 | checkExportReadiness L371 |
| 全量下载冲突 | 本地有脏数据 → 必须三选一确认 | SyncViewModel.kt:164-177 |
| 同步冲突裁决 | 增量下载服务端覆盖本地（留痕） | IncrementalDownloadStrategy.kt:115-135 |
| Excel 重复导入 | (productKey,批号,库位) 相同 → 覆盖数量/效期/备注 | InventoryRepositoryImpl.kt:337-351 |
| 产品删除保护 | 外键 RESTRICT，有库存记录的产品删不掉（UI 提示） | StockRecord.kt:24-29 + deleteProductByKey L209-215 |

## 5. 端到端示例：一次完整盘点

```
1. 管理员在 PC 把金蝶采购入库单导出 → pythonUDI 收到任务
2. 手机 App「单据」→ 拉取明细（物料编码→DI 换算，缺字典的行 di 为空）
3. 到货架拍照批量扫码 → 红绿框即时反馈 → 回到 taskDetail 看每行状态
4. 补扫/OCR 无码标签 → 手工修正数量
5. 全绿（或确认警告）→ 导出 PDF（含水印）→ 上传归档
6. 账面数据如需入本地账本 → 走盘点任务的 Excel 导入 + 同步推送
```

【待确认】单据核对结果（InventoryRecord）与本地库存账本（StockRecord）**没有打通**：单据核对在内存完成、上传后即弃；是否需要落库存属业务决策。
