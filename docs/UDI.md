# UDI — UDI 解析与产品/库存匹配

> UDI（Unique Device Identification，医疗器械唯一标识）= **DI（产品标识，GTIN）+ PI（生产标识：批号/效期/生产日期/序列号）**。本项目通过 GS1 条码承载 UDI。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。

## 1. 核心解析器 Gs1Parser（utils/Gs1Parser.kt:6，object）

### 1.1 支持的 AI（应用标识符）规则表（L20-39）

| AI | 含义 | 定长/变长 | 长度/上限 |
|----|------|-----------|-----------|
| 00 | SSCC（系列货运包装箱码） | 定长 | 18 |
| **01** | **GTIN / DI（产品标识）** | 定长 | 14 |
| 02 | 内容 GTIN | 定长 | 14 |
| **10** | **批号 Batch/Lot** | 变长 | ≤20 |
| 11 | 生产日期 | 定长 | 6（YYMMDD） |
| 13 | 包装日期 | 定长 | 6 |
| 15 | 保质期 | 定长 | 6 |
| **17** | **失效日期（效期）** | 定长 | 6 |
| 21 | 序列号 | 变长 | ≤20 |
| 22 | CPV | 变长 | ≤20 |
| 30 / 37 | 数量 | 变长 | ≤8 |
| 91 | 内部码 | 变长 | ≤30 |

> 未在此表的 AI 不被识别（findAI L181-190 只查本表）。**新增 UDI 规则 = 改这张 map**。

### 1.2 解析流程 `parse(input)`（L57-109）
1. `preprocess`（L148-178）：
   - 剥离扫码引擎 symbology 前缀 `]C1`（GS1-128）/`]Q1`（GS1 QR）/`]d2`（GS1 DataMatrix）/`]e0`（DataBar）/`]L1`（L157-162）。
   - FNC1/GS 分隔符统一为 `0x1D`；另兼容 `0xE8` 映射（部分扫码枪，L165-166）。
   - 人类可读括号格式 `(10)ABC(17)YYMMDD` → 机读 `10<GS>ABC17<GS>YYMMDD`（L170-175）。
2. 循环：最长匹配 2~4 位 AI（L181）→ 按定长/变长切数据（变长以 GS 分隔或到串尾，超长截断保护，L193-226）。
3. 容错：中途异常保留已解析部分（L100-103）。
4. **判定标准：解析出 AI=01 才算 `isUdi=true`**（L105-108）。

结果 `ParseResult{isUdi, rawValue, items: Map<AI,Value>}`（L42-54），快捷属性 `di/batch/prodDate/expiry`。

### 1.3 辅助校验
- `isValidDI(di)`（L118-142）：GS1 Modulo-10 校验位算法；纯数字且长度 ∈ {8,12,13,14}。设置项 `enableDiValidation`（SettingsRepository.kt:24，InventoryViewModel.kt:95 开关）控制入库时是否强校验。
- `normalizeDateToYYMMDD`（L232-244）：`2026-05-20`/`20260520` → `260520`。

### 1.4 特例：长光批号 BatchCodeProcessor（utils/Hybiome.kt:3）
`process("065006200810024022612")` → 取第 7-10 位 + 第 14-17 位 → `"20082402"`（长度<17 原样返回）。用于"智能批号"搜索模式（`isBatchProcessorEnabled`，InventoryViewModel.kt:102 开关、L129-132 使用）。测试见 `app/src/test/.../hybiome.kt`（BatchCodeProcessorTest，4 例）。

## 2. 码制覆盖

| 码制 | 解码引擎 | 说明 |
|------|----------|------|
| QR Code | ML Kit（DecoderChain.kt:40） | 含 GS1 QR（]Q1 前缀剥离） |
| GS1-128 / CODE-128 | ML Kit（L41） | 主流 UDI 一维码 |
| DataMatrix | ML Kit + **libdmtx 二次尝试**（DecoderChain 顺序） | 医疗器械 UDI 最常见码制 |
| CODE-39 / CODE-93 / EAN-13 / EAN-8 | ML Kit（L42-46） | |

## 3. UDI → 产品匹配链路

### 3.1 产品字典匹配（持久层）
产品表主键 `productKey` = **DI 非空 ? DI : materialCode**（`data/entity/ProductBase.kt:41-49 computeProductKey`）。因此扫出的 DI 可直接 `getProductByKey(di)`（`InventoryMainViewModel.kt:176`、Repository L161）。

冲突时的 key 决策 `resolveProductKey`（ProductBase.kt:58-92）：
- 同 DI + 同物料编码 → 覆盖（key 仍为 DI）。
- 同 DI + 不同物料编码 → **新 key = 物料编码**（同 DI 双产品并存）。
- 新数据无物料编码且 DI 撞车 → 跳过。

### 3.2 库存记录匹配（InventoryViewModel.performSearch，viewmodel/InventoryViewModel.kt:115-160）

用户在盘点详情页扫码/输入后，搜索降级链：
1. 开启"智能批号" → `getRecordsBybatchorexpiryDate(BatchCodeProcessor 处理后的串)`。
2. `Gs1Parser.parse` 为 UDI 且有 DI：
   - 有批号 → `getRecordsByUdi(productKey=DI, batch)`（StockRecordDao.kt:78-85）
   - 该批号无结果 → 回退按批号查（L138-139）
   - 无批号 → `getRecordsByKey(DI)`（L96-103）
3. 非 UDI → 9 字段模糊 `searchRecordsByQuery`（StockRecordDao.kt:58-75）。

### 3.3 单据核对匹配（InventoryMainViewModel.processScannerResults，L141-266）

每个扫码值 → Gs1Parser 解析：
- **解析失败/无 DI** → 生成 ERROR 行（"非 UDI 格式"，L160-168）。
- 有 DI+批号 → 在现有记录中找 `di==DI && batch==批号` 候选（L180）：
  - **贪心最佳匹配**（L184-195）：候选打分——行已扫满(isFull)记 10 分优先避开；未满行中生产日期/效期不匹配 +1 分；选**最低分**（最该填的行）。
  - 命中 → `actualQty+1`，di/batch 置 MATCHED，日期字段调 `evaluateDateStatus`（L62-75：DateUtils 标准化后相等=MATCHED，一方空/不可解析=WARNING，不等=WARNING），数量状态 `newQty==target` MATCHED / `<target` WARNING / `>target` ERROR（L201-206）。
- 无候选 → 归入 `documentId="UNKNOWN"` 行（L219-259）：
  - **有单据时**（hasDoc）→ 整行 ERROR，errorMessage="不在单据中"；
  - 无单据 → MATCHED；产品库查不到 DI → errorMessage="库中无记录"。
- OCR 来源走 `processOcrResults`（L271-326）：`OcrFuzzyMatcher.fuzzyMatch` 批号模糊对齐，未匹配且无单据上下文时建 `di="无DI"` 行。

单据目标从何而来：金蝶单据 items 只有 `materialCode`（TaskModels.kt:102-104），App 用 `getProductByMaterialCode(...)?.di ?: ""` 反查 DI（L407,451）——**产品字典是物料编码→DI 的桥**，字典缺记录则该行 di 为空、扫码永远 ERROR。

### 3.4 实时额度核对（扫码器内）
`getTargetDiList()`（L379-384）把单据展开为 `"DI|批号"` × targetQty 配额池；`BarcodeStatusAssigner.assignStatus`（core/util/BarcodeStatusAssigner.kt:18-44）逐码消耗配额，即时红/绿框反馈（详见 docs/SCANNER.md §4）。

## 4. DI 数据质量修补机制（多处出现的"自动补 DI"）

当上游数据只有物料编码没有 DI（金蝶/服务端常见）：
- 导入：`saveImportDataWithResolution`（InventoryRepositoryImpl.kt:268-275）按物料编码反查本地已有 DI 回填。
- 全量下载：FullDownloadStrategy.kt:76-84；增量下载：IncrementalDownloadStrategy.kt:48-57。同样的三级产品查找（productKey→DI→materialCode）upsert。
- 上传兜底：产品缺失补占位 `source="upload_auto_fix"`（FullUploadStrategy.kt:43-59）。
- 诊断入口：`validateCurrentTaskDIs()`（InventoryViewModel.kt:834）+ `clearInvalidDiList`（L853）——校验当前任务 DI 有效性列表。

## 5. 修改 UDI 规则的落点

| 需求 | 文件:位置 |
|------|-----------|
| 新增 AI 规则（如 240 二级-sn） | `utils/Gs1Parser.kt:20-39` `aiRules` map |
| 新增日期格式容错 | `Gs1Parser.preprocess` / `utils/DateUtils.kt:96 parseDateToLong` |
| DI 校验开关 | `SettingsRepository`（enableDiValidation）+ `InventoryViewModel.kt:95` |
| 特殊厂家批号规则 | `utils/Hybiome.kt`（BatchCodeProcessor，按厂家加函数） |
| 单据核对判定 | `InventoryMainViewModel.kt:62-84`（evaluateDateStatus/computeQtyStatus） |
