# SYNC — 数据同步机制

> 同步 = 本地 Room ↔ 局域网同步服务（:8080，`D:\xz\inventory_server`）。**数据源判定：多端对等、服务端为汇聚点**——手机端写入 `syncStatus=1` 脏数据，推送到服务端；服务端（及其他客户端的推送）经拉取回流覆盖本地。无自动轮询/长连接/广播，**全部由用户手动触发**【事实：全库无 WorkManager/定时器/轮询代码】。

## 1. 组件清单（app/src/main/java/com/example/inventorymaster/data/sync/）

| 文件 | 职责 |
|------|------|
| `SyncStrategy.kt` | 接口 + `SyncType` 枚举（FULL_UPLOAD / INCREMENTAL_UPLOAD / FULL_DOWNLOAD / INCREMENTAL_DOWNLOAD，L18-23） |
| `SyncContext.kt` | `data class SyncContext(ip, sessionId)`（L6） |
| `SyncOrchestrator.kt` | 策略注册表 + `Mutex` 串行化（L14-39） |
| `RetryPolicy.kt` | 指数退避重试（L16-55） |
| `TimestampManager.kt` | 每 session 双锚点（pull/push），防倒退（L21-60+） |
| `FullUploadStrategy.kt` / `FullDownloadStrategy.kt` / `IncrementalUploadStrategy.kt` / `IncrementalDownloadStrategy.kt` | 四种策略实现 |

注册点：`InventoryRepositoryImpl` 构造（`data/repository/InventoryRepositoryImpl.kt:64-73`）。Repository 对外暴露四个语义化方法（L389-402）：`exportFullSession` / `pushUnsyncedData` / `exportdownloadFromPC` / `pullNewData`。

## 2. 脏标记（dirty flag）写入路径

所有业务写库必须打脏标记——`InventoryViewModel.prepareRecordForSync(record, logAction)`（viewmodel/InventoryViewModel.kt:243-263）：
- `lastUpdateTime = 当前毫秒`
- `syncStatus = 1`（L259 注释 "1 = 待上传 UNSYNCED"）
- `operator = "手机端"`（注意：覆盖了 Entity 默认的 Build.MODEL）
- `historyLog` 追加 `"[MM-dd HH:mm 设备型号] 手机端: <动作>"`

调用方：`addRecord`(L266)、`updateRecord`(L279)、`deleteRecord`(L304)、`quickCheck`(L313)。
反向保护：**服务端数据落库强制 `syncStatus=0`**——`StockRecordDao.saveRemoteRecords`（data/dao/StockRecordDao.kt:141-146，L139 注释"防止死循环的关键"）；增量下载路径手工构造 `syncStatus=0` 的记录（IncrementalDownloadStrategy.kt:130,150,171）。

## 3. 四种同步策略

### 3.1 全量上传（FullUploadStrategy.kt:22-84）
1. 读 session + `getExportData` 全部记录；记录为空则失败（L37-39）。
2. 产品缺失时内存补占位 `source="upload_auto_fix"`（L43-59）。
3. `POST /api/upload`（SyncData 全包）→ 成功后 `markAsSynced(全部id)`（L72-75）。
4. 服务端语义（注释 L14）："事务中先删后插"。

### 3.2 增量上传（IncrementalUploadStrategy.kt:26-122）
1. `getDirtyRecords(sessionId)` 取 `sync_status=1`（L28）。
2. 脏记录关联产品一并打包 `PushRequest`（L38-50）。
3. `POST /api/sync/push?sessionUuid=`（L58-61）。
4. **逐条结果处理**（L63-105）：解析响应 `results[]`，`status=="inserted"/"updated"` 的记录才 `markAsSynced`；失败 UUID 记警告；`serverTimestamp` 最大值 → `saveLastPushAnchor`（L94-98）。响应无 results 时兼容旧服务端全标已同步（L106-111）。

### 3.3 全量下载（FullDownloadStrategy.kt:24-128）
1. 本地有 session 按 UUID 下载，否则按服务端 ID（L32-38）。
2. upsert session（按 uuid 匹配，L48-72）。**M8 起（2026-09-25）插入/更新均落 `sessionSource=1 + startedAt`**（服务器下发任务标记来源；此前 sessionSource 从未赋值，拉取任务与本地任务无法区分）。
3. 产品 upsert + **自动补 DI**（物料编码反查本地已有 DI，L76-98）。
4. 记录映射 `sourceType=2`（L115）后 `overwriteSessionData`（L120）——**@Transaction 先删后插，本地该 session 数据被完全替换**（含未上传脏数据，见 §5 冲突防护）。
5. 不改任何锚点。

### 3.4 增量下载（IncrementalDownloadStrategy.kt:26-205）
1. 锚点 `getLastPullAnchor(sessionId)`（L39，0=首次全量）→ `GET /api/sync/pull?lastSyncTime=`（服务端用 `>=`，配合 UUID 去重防毫秒边界遗漏，TimestampManager 类注释 L18）。
2. 产品处理同全量下载（补 DI + 三级查找 upsert，L48-72）。
3. 墓碑处理：`deletedRecordUuids` → 本地 `deleteRecordsByUuids`（L75-78）。
4. 记录三分支（L84-177）：
   - 本地存在且 `syncStatus==1`（本地有未推送修改）→ **冲突**：字段级比对（quantity/actualQuantity/remarks/operator，L90-113）生成 ConflictItem 仅记日志，然后**服务端覆盖**，旧值写入 historyLog `"server_overwrite: ..."`（L115-135）。
   - 本地存在且干净 → 直接覆盖（syncStatus=0）。
   - 本地不存在 → 插入（syncStatus=0）。
5. 锚点防倒退更新为服务端最大 `lastUpdateTime`（L188-193）。
6. **任务生命周期对齐（M8，2026-09-25）**：读响应中的 `sessionStatus` 与 `startedAt`——
   - `sessionStatus==1` 且本地 status=0 → 本地置 1（服务端已归档 → 只读）；
   - `startedAt` 非空且本地为 null → `SessionDao.updateStartedAt` 落库（**仅非空才写，绝不用 null 回写**），本地任务进行中时提示「服务端任务已开始，本地恢复可编辑」。落库后 Room Flow 自动驱动列表/详情页解锁（门铃 `session_changed` → 静默拉取即走此路径）。

## 4. 锚点管理（TimestampManager.kt）

- 每 session 两个独立 DataStore 键：`last_pull_anchor_<id>`、`last_push_anchor_<id>`（long）。
- 所有写入取 `max(current, new)`（L49-56 及 push 对应实现）。
- 旧键 `last_sync_time_<id>`（string）首次访问自动迁移并删除（L28-44）。
- 注意 `SettingsRepository.saveLastSyncTime/getLastSyncTime`（data/SettingsRepository.kt:343-351）是**旧 API 残留**，读写的是旧键——新代码勿再调用【事实：TimestampManager 注释声明取代它；该旧方法当前仍被谁调用【待确认-未逐一排查】】。

## 5. 冲突与并发防护

| 防护 | 机制 | 位置 |
|------|------|------|
| 同一时刻只有一个同步 | Orchestrator `Mutex` | SyncOrchestrator.kt:30-38 |
| UI 重复点击 | `SyncViewModel.isSyncRunning` 布尔锁（非线程安全，与 Mutex 双层） | viewmodel/SyncViewModel.kt:66,72 |
| 全量下载覆盖脏数据 | 下载前检查 dirty 数：`performDownload` 统计 `getDirtyRecordsCount`，>0 则挂起并弹 `DownloadConflictInfo` 对话框 | SyncViewModel.kt:164-177 |
| 冲突三选一 | 用户可选：强制下载（ConfirmForceDownload）/ 先推后下（PushThenDownload）/ 取消（dismissConflict） | SyncViewModel.kt:182-199,225-227 |
| 下载死循环 | 落库强制干净 | StockRecordDao.kt:141-146 |
| 增量上传部分失败 | 逐条 markAsSynced，失败保持脏 | IncrementalUploadStrategy.kt:90-92 |
| 删除同步 | 本地删 + best-effort `POST /api/sync/delete`；失败静默（注释：下次拉取会同步） | InventoryRepositoryImpl.kt:131-150 |

⚠️ 已知语义漏洞：**增量下载的冲突解决永远偏向服务端**（本地未推送的修改被覆盖，仅留 historyLog 痕迹；ConflictItem 只打 Log 未上抛 UI）（IncrementalDownloadStrategy.kt:115-135）。字段级 `conflictSummary` 由服务端返回但客户端当前未消费【事实：PullResponse.conflictSummary 定义于 SyncModels.kt:62，策略代码未引用】。

## 6. 完整数据流（端到端）

```
设备A 扫码 → StockRecord(syncStatus=1) → 用户点「推送」
  → SyncIntent.Push → pushUnsyncedData → /api/sync/push → 服务端 inventory.db
设备B 用户点「拉取」
  → SyncIntent.Pull → pullNewData → /api/sync/pull(lastSyncTime=pull锚点)
  → 服务端返回变更+删除清单 → 本地 upsert/delete(syncStatus=0) → 锚点推进
```

操作人字段：`operator` 随记录同步（Entity 默认 Build.MODEL，但写库即被 prepareRecordForSync 改写为 "手机端"，InventoryViewModel.kt:245——**设备区分度丢失**，见 TECH_DEBT）。

## 7. UI 入口

- `SyncDialog`（ui/sync/SyncDialog.kt）：IP/Token 输入 + 全量上传/下载。
- `DownloadConflictDialog`（ui/sync/DownloadConflictDialog.kt）：脏数据冲突三选一。
- `SessionListScreenNav`（ui/home/SessionListScreenNav.kt）与 `InventoryScreen`（ui/inventory/InventoryScreen.kt:8 处调用 SyncViewModel）均触发。
- Token 校验：`verifyAndSaveToken`（InventoryRepositoryImpl.kt:503-518）成功才持久化 IP/Token。

## 8. 多端协同与双轨制免装 Web 实时同步机制（2026-11-04 演进）

系统自 2026-09 至 2026-11 已演进为**双轨制局域网实时协同与双层归档管控体系**：

1. **增量水位线与实时门铃**：
   - 摒弃了易受系统时钟偏差影响的毫秒时间戳比对，服务端采用单调递增的 `change_seq` 作为统一水位线（`WHERE change_seq > ?`）；
   - 服务端建立 WebSocket 广播通道（`/ws`），写操作触发 `data_changed` / `session_changed` 实时门铃，App 端与 Web 端接收到门铃后秒级静默拉取增量对齐，实现无感协同。
2. **双轨制网络架构（HTTP 8080 + HTTPS 8443）**：
   - 核心仓库库管使用 Android 原生 App，走 `HTTP 8080`，免除内部内网证书自签名导致的网络握手问题；
   - 协查人员手机直接扫码打开 `panku.html` 移动单页，走 `HTTPS 8443`（自动生成内网 SANs 自签名证书），满足浏览器调用后置摄像头 `getUserMedia` 扫码的强制安全上下文要求。
3. **任务级审批准入与设备隔离**：
   - 彻底废除操作员绑定码。移动端与 Web 端扫任务二维码后携带姓名发起 `POST /api/task/join-request`，PC 管理端审核批准后下发 Token；
   - 审批状态严格按任务 `sessionId` 隔离，前端进入前查询当前任务审批记录，杜绝跨任务权限逃逸；
   - **拒绝反悔与重申闭环**：申请被拒后，移动端展示友好引导卡片并支持【原名重新申请】与【修改姓名重新申请】；PC 管理端面板常驻「已拒绝设备」列表并提供【重新批准】入口，杜绝永久黑名单死锁。
4. **60s 内存防冲突核验锁**：
   - 纯内存实现 `VerificationLockManager`，物料点击或扫码核验时申请 60s 临时锁；
   - 跨设备防撞黄色气泡提示与倒计时，支持【强制接管】；弹窗关闭或核验完成自动释放；只读模式跳过加锁。
5. **双层归档门禁体系**：
   - **个人离场**：任意参与人点击【完成离场】或 App 端【完成盘点】，向服务端上报 `local_status = 1`，解除 100% 进度限制，即刻切入只读模式；服务端 `/api/panku/verify` 增加防御拦截离场状态下的修改（403 `PARTICIPANT_ALREADY_EXITED`），顶部导航提供【恢复盘点】支持误操作恢复；
   - **服务端总归档**：PC 端总归档严格执行双指标检查（`uncounted_count == 0` 且 全员 `local_status == 1`），否则 409 拦截；提供强制归档二次确认并强制录入原因入库审计 `audit_logs`。
6. **任务 ID 单调自增与级联清理**：
   - `sessions` 表采用 SQLite `AUTOINCREMENT`（底层 `sqlite_sequence` 保证 ID 绝对单调递增，彻底消除删除任务后 ID 被复用撞库的问题）；
   - 任务删除执行全表级联清理，同步删除库存明细、准入记录、异常流水、审计日志并清空内存锁。
