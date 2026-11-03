# DATABASE — 数据库设计

> Room 数据库 `inventory_master_db`（`data/AppDatabase.kt`），版本 **12**（`AppDatabase.kt`；v11→v12 新增 `is_verified` 查验字段与迁移），`exportSchema = true`。包含四张表：`product_base`、`inventory_sessions`、`stock_records`、`chat_messages`。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。

## 0. 数据库级配置（高风险，修改前必读）

`data/AppDatabase.kt`：
- 单例 `getDatabase()`，双检锁。
- `.allowMainThreadQueries()`——允许主线程查库（防开发期闪退的权宜之计）。
- `.addMigrations(...)` 完整迁移链：
  - `MIGRATION_5_6`：inventory_sessions 新增 startedAt；
  - `MIGRATION_6_7`：创建 chat_messages 表；
  - `MIGRATION_7_8`：chat_messages 新增 durationMs；
  - `MIGRATION_8_9`：chat_messages 新增 model；
  - `MIGRATION_9_10`：chat_messages 新增 cardActionJson；
  - `MIGRATION_10_11`：product_base 为 di 和 materialCode 新增二级索引；
  - `MIGRATION_11_12`：stock_records 新增 `is_verified` INTEGER NOT NULL DEFAULT 0 字段与 `(sessionId, is_verified)` 索引，并将存量备注含“已查验”的历史数据平滑迁移（2026-10-30）。
- `.fallbackToDestructiveMigration()`——**未被 Migration 覆盖的版本跳转仍直接清空重建**。改 Entity 必须 +1 version 并在同槽位补 Migration，否则用户本地数据全部丢失。

## 1. 表：product_base（L1 产品字典）

Entity：`data/entity/ProductBase.kt:11`

| 字段 | 类型 | 约束/默认 | 含义 |
|------|------|-----------|------|
| `productKey` | String | **主键** | 统一关联键：DI 非空时=DI，否则=物料编码（L15 注释） |
| `di` | String? | — | 真实 GTIN（GS1 AI 01），只存真实 DI，可为空 |
| `productName` | String | 非空 | 通用名称（NMPA: CPMC） |
| `specification` | String? | — | 规格 |
| `model` | String? | — | 型号 |
| `manufacturer` | String | 非空 | 生产企业/注册人 |
| `registrationCert` | String? | — | 注册证编号 |
| `materialCode` | String? | — | 院内/企业内部物料编码（对接 ERP/金蝶） |
| `unit` | String? | — | 最小销售单元 |
| `categoryCode` | String? | — | 分类编码（68xx） |
| `lastSyncTime` | Long | 默认 0 | 上次同步/更新时间 |
| `source` | String | 默认 "local" | 来源：`local`(手动/Excel)、`nmpa`、`json`；代码还产生 `scan_auto`（InventoryRepositoryImpl.kt:123）、`upload_auto_fix`（FullUploadStrategy.kt:49） |

主键生成/冲突规则（companion，ProductBase.kt:34-97）：
- `computeProductKey(di, materialCode)`（L41）：DI 优先，二者皆空抛 `IllegalArgumentException`。
- `resolveProductKey(di, materialCode, existingProduct)`（L58）：DI 已存在且物料编码相同 → 覆盖（用 DI）；物料编码不同 → **用新物料编码作 key**（同 DI 两产品共存）；新产品物料编码为空 → 跳过（`shouldSkip=true`）。
- `hasKeyChanged()`（L93）。

## 2. 表：inventory_sessions（任务/会话）

Entity：`data/entity/InventorySession.kt:6`

| 字段 | 类型 | 约束/默认 | 含义 |
|------|------|-----------|------|
| `id` | Long | 主键自增 | |
| `uuid` | String | 默认随机 UUID | 跨端同步标识 |
| `name` | String | 非空 | 任务名 |
| `sessionType` | Int | 0 | 0=盘库，1=核对（L14 注释） |
| `date` | Long | 当前毫秒 | 创建时间 |
| `status` | Int | 0 | 0=进行中，1=已归档，2=已锁定（L18） |
| `isLocked` | Boolean | false | 与 status=2 冗余【待确认-为何双字段】 |
| `startedAt` | Long? | null | 服务端任务开始时间（毫秒）；与 sessionSource/status 派生 `isPendingStart`（`data/model/InventorySessionExt.kt`：source=1 且 status=0 且 startedAt=null ⇒ 待开始只读）。DB 5→6 新增，`MIGRATION_5_6` 保数据迁移（2026-09-25） |
| `sessionSource` | Int | 0 | 0=本地创建，1=服务器下发（L22） |
| `remoteId` | String? | — | 服务器端原始 ID（当前代码未见写入点【待确认】） |
| `totalItems` / `scannedItems` | Int | 0 | UI 统计冗余（L25-27 注释"可选"） |
| `extJson` | String? | — | 预留 JSON 扩展（L29-30） |

## 3. 表：stock_records（L2 库存实物账本）

Entity：`data/entity/StockRecord.kt:15`。设计原则（L11-14 注释）：只存"变动"数据（批号/数量/位置），静态信息经 `productKey` 查 `product_base`。

| 字段 | 类型 | 约束/默认 | 含义 |
|------|------|-----------|------|
| `id` | Long | 主键自增 | |
| `uuid` | String | **唯一索引**，默认随机 UUID | 跨端同步/去重标识（Index L34） |
| `sessionId` | Long | **FK → inventory_sessions.id，CASCADE 删除**（L17-23） | 所属任务 |
| `productKey` | String | **FK → product_base.productKey，RESTRICT 删除**（L24-29） | 关联产品 |
| `batchNumber` | String | 非空 | 批号（GS1 AI 10） |
| `expiryDate` | Long | 非空 | 效期毫秒时间戳（AI 17） |
| `productionDate` | Long? | — | 生产日期（AI 11） |
| `quantity` | Double | 非空 | 账面数量 |
| `location` | String | 非空 | 库位 |
| `actualQuantity` | Double? | null | 实盘数量（null=未盘） |
| `remarks` | String? | — | 备注（撤销查验时自动转存备份实盘数据 `原实盘: 数量`） |
| `is_verified` | Boolean | 0 | **查验状态**：0=未查验, 1=已查验（v12 新增正式字段，废除脆弱备注匹配） |
| `sourceType` | Int | 1 | 0=手动，1=Excel导入，2=云端下载（L54；全量下载强制=2，FullDownloadStrategy.kt:115） |
| `operator` | String | 默认 `Build.MODEL`（列名 `operator`，L56-57） | 操作人；写库时被覆盖为"手机端"（InventoryViewModel.kt:245,260） |
| `lastUpdateTime` | Long（列名 `last_update_time`） | 当前毫秒 | 同步比较时间戳 |
| `syncStatus` | Int（列名 `sync_status`） | 0 | **dirty flag：0=干净，1=待上传**（InventoryViewModel.kt:259） |
| `historyLog` | String?（列名 `history_log`） | "" | 操作历史文本，追加式（InventoryViewModel.kt:243-263） |

索引（StockRecord.kt:31-35）：
- 非唯一 `(sessionId, productKey, batchNumber, location)` —— 查重/扫码匹配
- `(productKey)`
- 唯一 `(uuid)`
- `(sessionId, is_verified)` —— 查验进度与未查验多维快速检索 (v12)。注意：所有单任务查询（`getExportData`/`searchRecordsByQuery` 等）均显式指定 `ORDER BY id ASC`，避免依赖索引遍历导致数据顺序随查验状态跳动。

关联查询模型：`data/model/StockRecordCombined.kt:8`（`@Relation(parentColumn="productKey", entityColumn="productKey")` 内嵌 ProductBase），`data/model/SessionWithProgress.kt:6`（total/verified 冗余列）。

## 4. DAO 汇总

### ProductDao（data/dao/ProductDao.kt）
| 方法 | 行号 | 说明 |
|------|------|------|
| `insertProduct` / `insertProducts` | 13-18 | REPLACE 策略 |
| `getProductByKey(productKey)` | 21 | 扫码/匹配主入口 |
| `getProductByMaterialCode(materialCode)` | 26 | 单据导入解析物料编码→产品（LIMIT 1） |
| `searchProducts(query)` | 31-38 | productName/manufacturer/di/materialCode LIKE |
| `getAllProducts` / `getProductCount` | 41-45 | 导出/统计 |
| `deleteProductByKey` / `deleteProductsByKey` | 48-53 | |
| `updateProduct` | 57 | `@Update`；L55 注释：**不能用 REPLACE 更新已有库存记录引用的产品** |

### SessionDao（data/dao/SessionDao.kt）
`insertSession`(15)、`getAllSessions(): Flow 按 date 倒序`(20)、`getSessionById`(24)、`getSessionByUuid`(28)、`updateSession`(32)、`updateStatus`(36)、`updateStartedAt`(40)、`getAllSessionsWithProgress(): Flow`(43-51，子查询统计 verifiedCount=`is_verified = 1`)、`getSessionCount`(54)、`countOngoingSessions`(58)、`deleteSession`(60)。

### StockRecordDao（data/dao/StockRecordDao.kt）
- 写：`insertRecord/insertRecords`(REPLACE,19-23)、`updateRecord`(25)、`deleteRecord`(28)、`deleteRecordsBySession`(31)、`deleteRecordsByUuids`(151)、`updateProductKeyForRecords`(148)。
- 事务组合：
  - `overwriteSessionData(sessionId, newRecords)`（L43-47）——全量下载覆写：先删后插。
  - `saveRemoteRecords(records)`（L141-146）——落库前强制 `syncStatus=0`（防下载→再上传死循环，L139 注释）。
- 读：`getRecordsBySession(): Flow`(54)、联合模糊搜索 `searchRecordsByQuery`(58-75，9 字段 LIKE)、`getRecordsByUdi(productKey,batch)`(78-85)、`getRecordsBybatchorexpiryDate(batch)`(88-94，无 UDI 码过渡方案，L87 注释)、`getRecordsByKey(productKey)`(96-103)、`getUnverifiedCount`(106-111)、`getExportData`(114-116)、`findExistingRecord(productKey,batch,location)`(118-127，导入查重)、`getRecordUuidsBySession`(154)。
- 同步：`getDirtyRecords(sync_status=1)`(131-132)、`markAsSynced(ids)`(136-137)。

## 5. 数据关联与历史演变

### 关联方式
```
InventorySession 1 ──< stock_records > N─ 1 ProductBase
        (id ← sessionId, CASCADE)         (productKey ← productKey, RESTRICT)
```
- `stock_records` 的匹配元组是 **(sessionId, productKey, batchNumber, location)**（索引即查重键）。
- 扫码场景只拿 DI + 批号：`productKey=DI` 时直查；`productKey=物料编码` 的产品需先经 `getProductByMaterialCode` 换算（单据导入 InventoryMainViewModel.kt:407,451）。

### 历史演变（git + 代码注释可确认）
- **2026-01 前后（dadb0ef/19d9a6e）**：增量同步引入 `sync_status`/`uuid`/锚点机制。
- **2026-05-30（31f5229 "优化了数据库，新增关联键"）**：引入 `productKey`。ProductBase.kt:6-9 注释明确："productKey 为统一关联键：有 DI 时=DI，无 DI 时=materialCode；di 字段只存真实 GTIN，**不再被污染**"。→ 推断旧版以 `di` 为主键且当 DI 缺失时会把物料编码写进 di 字段，导致语义混乱【推测-具体旧行为，无法从当前代码验证】。
- DB version 3→4：本次重构期间升级（CLAUDE.md 仍写 version 3，已过时）。
- DB version 5→6（2026-09-25）：`inventory_sessions` 新增 `startedAt` 列（M8 待开始门禁），首个手动 `MIGRATION_5_6`（ALTER TABLE 保数据；4→5 之间的升级无文档与迁移记录）。
- 下载侧"自动补 DI"逻辑（IncrementalDownloadStrategy.kt:48-57、FullDownloadStrategy.kt:76-84、InventoryRepositoryImpl.kt:268-275）说明：**服务端数据存在 DI 缺失但物料编码存在**的情况，客户端三层补救。原因【待确认】（可能金蝶侧只有物料编码）。

## 6. AI 动态查询通道（特殊）

`InventoryRepository.executeDynamicSqlForAi(sql)`（接口 InventoryRepository.kt:102；实现 InventoryRepositoryImpl.kt:526-549）：绕过 DAO，`SimpleSQLiteQuery` 直接执行 AI 拼装的 SQL，结果转 JSON 数组返回。使用 `readableDatabase`，异常返回错误字符串。**无白名单/只读校验**——风险详见 docs/TECH_DEBT.md P0-1。

## 7. 修改数据库的流程约束

1. 改 Entity → `version` +1（AppDatabase.kt:17）。
2. **已有 Migration 先例 `MIGRATION_5_6`**（AppDatabase.kt companion）：新迁移按同模式写入 `.addMigrations(...)` 槽位；未覆盖的版本跳转仍走 `fallbackToDestructiveMigration` 清库。
3. `StockRecord` 列名有显式 `@ColumnInfo`（operator/last_update_time/sync_status/history_log），动字段时注意 DTO 映射（data/dto/SyncModels.kt:85-119）与服务端字段一致性。
4. `exportSchema=true`，schema JSON 生成在编译期（`app/schemas/`，当前未见入库【待确认-是否 gitignore】）。
