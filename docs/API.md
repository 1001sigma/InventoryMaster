# API — 网络接口（Android ↔ 两个 Python 服务 + 云端 AI）

> 本项目 App 侧全部网络调用经 Retrofit。共 **3 类对端**：局域网同步服务（:8080）、Python 任务系统（:8000）、AI 云端（OpenAI 兼容）。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。服务端代码**不在本仓库**。

## 0. 对端总览

| 对端 | 地址 | 鉴权 | Android 客户端 | 服务端代码 |
|------|------|------|----------------|------------|
| 同步服务 | `http://<用户配置IP>:8080/` | Bearer Token（`/api/auth/verify` 换取） | `data/network/InventoryApiService.kt` | `D:\xz\inventory_server\main.py`（FastAPI+SQLModel，SQLite `inventory.db`；Token 中间件 `AuthMiddleware`，豁免路径 `/`、`/api/auth/`、`/session/`、`/static/`） |
| 任务系统 | `http://<同一IP>:8000/`（强制，TaskApiService.kt:58-60 剥离端口重拼） | 无 | `data/network/TaskApiService.kt` | `D:\xz\pythonUDI\main.py`（FastAPI，SQLite `tasks.db`） |
| AI 云端 | 用户配置 baseUrl（如 `https://dashscope.aliyuncs.com/compatible-mode/v1`） | Bearer API Key | `ai/provider/CloudAiService.kt`（OkHttp 手写，非 Retrofit） | 第三方 LLM（OpenAI 兼容 `/chat/completions`），详见 docs/AI.md |

IP 配置来源：DataStore `server_ip`（`data/SettingsRepository.kt:303-313`），两个服务共用同一 IP（一个 IP 两个端口）。

## 1. 同步服务 API（InventoryApiService.kt）

客户端构造：`InventoryApiService.create(ip)`（L62-82）——IP 不带协议时拼 `http://$ip:8080/`；OkHttp 拦截器附加 `Authorization: Bearer <token>`（token 静态缓存于 companion `cachedToken`，`setToken()` 写入 L56-60）；Gson 转换器；**未显式配置超时**（OkHttp 默认 10s 连接/10s 读【推测-默认值生效】）。

| Endpoint | Method | 请求 | 响应 | Android 调用方 | 行号 |
|----------|--------|------|------|----------------|------|
| `/api/upload` | POST | `SyncData{session, products[], records[]}` | `Map<String,Any>` | `FullUploadStrategy.execute` | 19-20 |
| `/api/download` | GET | `?sessionUuid=` 或 `?sessionId=` | `SyncData` | `FullDownloadStrategy.execute` | 22-26 |
| `/api/sessions` | GET | — | `List<SessionDto>` | `fetchCloudSessions`（InventoryRepositoryImpl.kt:407） | 28-29 |
| `/api/sync/push` | POST | `?sessionUuid=` + `PushRequest{records[], products[]}` | `Map<String,Any>`（含逐条 `results[]:{uuid,status,serverTimestamp,reason}`） | `IncrementalUploadStrategy.execute` | 32-36 |
| `/api/sync/pull` | GET | `?sessionUuid=&lastSyncTime=`（毫秒锚点） | `PullResponse{records[], products[], deletedRecordUuids[], conflictSummary[]}` | `IncrementalDownloadStrategy.execute` | 38-42 |
| `/api/sync/delete` | POST | `?sessionUuid=` + `DeleteRequest{recordUuids[], sessionUuid}` | `Map<String,Any>` | `deleteRecord`（InventoryRepositoryImpl.kt:131-150，best-effort） | 44-48 |
| `/api/auth/verify` | GET | `?token=` | `Map<String,Any>`（成功即视为有效） | `verifyAndSaveToken`（InventoryRepositoryImpl.kt:503-518） | 50-52 |

数据流（全量上传为例）：

```
InventoryScreen/SyncDialog
  → SyncViewModel.handleIntent(UploadToServer)          viewmodel/SyncViewModel.kt:82
  → repository.exportFullSession(ip, sessionId)         InventoryRepositoryImpl.kt:389
  → SyncOrchestrator.execute(FULL_UPLOAD, ctx)          data/sync/SyncOrchestrator.kt:30
  → FullUploadStrategy: DAO 取数 → toDto() 打包
  → Retrofit POST /api/upload → FastAPI → SQLModel 写 inventory.db
```

DTO（`data/dto/SyncModels.kt`）：
- `SyncData`(L7)、`SessionDto`(L14)、`ProductDto`(L24)、`StockRecordDto`(L38)。
- `SessionDto.startedAt`（2026-09-25 M8 起）：服务端任务开始时间（毫秒，null=待开始），`/api/download` 返回；FullDownloadStrategy 落库并派生 `isPendingStart`（待开始只读门禁）。
- `PullResponse`(L58)：增量下载 + 服务端删除清单 + 字段级冲突摘要 `ConflictItem{uuid, field, localValue, serverValue}`(L65)；`sessionStatus/startedAt/archivedAt` 为任务生命周期状态（M8 起消费 startedAt 对齐待开始→进行中）。
- Entity↔DTO 转换扩展：`toDto()`(L85,L106)、`ProductDto.toEntity()`(L121)。注意 ProductDto 缺 `lastSyncTime/source`（同步会丢这两个字段，见 TECH_DEBT P2）。

错误处理与重试：所有策略调用包在 `RetryPolicy.execute`（data/sync/RetryPolicy.kt:26-41）——最多 3 次，500ms 起指数退避（×2，上限 5s），**只重试网络异常**（SocketTimeout/Connect/UnknownHost/Socket/Protocol/SSL，L46-54），HTTP 4xx/5xx 不重试。失败统一 `Result.failure` 传回 ViewModel 展示。协作类错误以 `CollabApiException`（`data/network/CollabApiException.kt`）承载 `code`（TASK_ARCHIVED / JOIN_CLOSED / **TASK_NOT_STARTED**（2026-09-25 M8 起，待开始任务拒绝写入，App 自动重拉对齐）/ DEVICE_NOT_BOUND 等）。

## 1.5 协同控制与移动 Web 盘点 API（collab.py & panku.py，2026-11-04）

服务端同时在 `HTTP 8080`（App 同步）与 `HTTPS 8443`（移动 Web）提供协同控制与轻量化免装扫码 API：

| Endpoint | Method | 鉴权 | 说明 |
|----------|--------|------|------|
| `/panku?sessionId=` | GET | 免 Token | 移动端免装 Web 单页盘点入口（摄像头取景扫码 + 快速查验） |
| `/api/task/join-request` | POST | 免 Token | 移动端/App 扫码提交加入申请（支持昵称排重与被拒后重提） |
| `/api/task/join-status` | GET | 免 Token | 轮询审批状态与下发 Token，返回 `approvalStatus`、`localStatus` |
| `/api/task/{session_id}/approve` | POST | Bearer Token | PC 管理端批准/拒绝设备加入协同（支持重新批准） |
| `/api/task/report` | POST | Bearer Token | 设备离场/恢复盘点上报（`localStatus: 1` 离场只读，`0` 恢复编辑） |
| `/api/session/{session_id}/participants` | GET | Bearer Token | 协同参与设备列表（返回待审批 `pending`、已批准 `participants`、已拒绝 `rejected`） |
| `/api/panku/items` | GET | Bearer Token | 任务物料清单，支持「名称/规格/批号/长光智能提取批号」即席检索 |
| `/api/panku/lookup` | GET | Bearer Token | 条码解析（长光华医提取批号优先 $\rightarrow$ GS1/UDI $\rightarrow$ 纯文本），未列物料自动记入异常 |
| `/api/panku/lock` | POST | Bearer Token | 60s 内存防撞核验锁（申请、超时续期、强制接管与主动释放） |
| `/api/panku/verify` | POST | Bearer Token | 单条实盘核验提交，服务端强制拦截已离场写入（403 `PARTICIPANT_ALREADY_EXITED`） |

## 2. 任务系统 API（TaskApiService.kt，金蝶单据中转）

客户端构造：`create(ip)`（L57-67）剥离用户输入的协议/端口，强制 `http://<ip>:8000/`；无鉴权。

| Endpoint | Method | 请求 | 响应 | 用途 | 行号 |
|----------|--------|------|------|------|------|
| `api/task/list` | GET | — | `TaskSummaryResponse{status, data: List<TaskSummary>}` | 云端任务列表（金蝶单据） | 40-41 |
| `api/task/{documentId}` | GET | 路径参数 | `TaskDetailResponse{status, data: TargetDocument}` | 单据明细 | 43-44 |
| `api/task/upload` | POST Multipart | `documentIds`(text/plain) + `file`(PDF) | `UploadResponse{status, message}` | 盘点结果 PDF 归档 | 46-51 |
| `api/custom-data/submit` | POST | `CustomDataSubmitRequest{title, detail: JsonObject}` | `CustomDataSubmitResponse` | 上传原始条码串（"上传解码"按钮） | 53-54 |
| `api/udi/submit` | POST | 扁平 Map `{"任务名": ["UDI"...]}`，同名整体覆盖 | `UdiSubmitResponse` | **上传解码按钮（2026-09-14 起替代 custom-data）**，数据进 udi_groups，WEB 看板"UDI中心"可见 | 130-131 |
| `api/udi/list` | GET | — | `UdiListResponse{data: List<UdiGroupSummary>{id,name,count,status,created_at,updated_at}}` | UDI 看板组列表 | 133-134 |
| `api/udi/detail` | GET | `?name=` | `UdiDetailResponse{data: {name, udis[], status}}` | 组原始 UDI 纯数组 | 136-137 |
| `api/udi/status` | PUT | `?name=&status=`（0待完成/1已完成/2已跳过） | `UdiMutationResponse` | 改组状态 | 139-140 |
| `api/udi` | DELETE | `?name=` | `UdiMutationResponse` | 删除整组 | 142-143 |

模型：
- `TaskSummary{documentId, documentName, documentDate, billType?, orderNo?, documentNumber, status}`（TaskApiService.kt:19-27；billType/orderNo 为 2026-08-14 新增，兼容旧服务端 null，见 CHANGELOG）。
- `TargetDocument/TargetItem`（`batchscanner/task/models/TaskModels.kt:90-110`，kotlinx @Serializable + Gson @SerializedName 双注解）：`TargetItem{materialCode, batch, productName, targetQty: Double, productionDate?, expiryDate?}`。**targetQty 是 Double**（金蝶推小数数量，防 Gson 崩溃，CHANGELOG 2026-08-14），App 侧 `toInt()` 截断使用（InventoryMainViewModel.kt:414,460）。

调用链（拉单据）：

```
InventoryTaskDetailScreen「单据」按钮
  → InventoryMainViewModel.fetchTaskListFromNetwork()        L86（只展示 status==0）
  → repository.fetchPythonTaskList()                         InventoryRepositoryImpl.kt:426
  → GET :8000/api/task/list
用户勾选 → loadMultipleTaskDetails(ids)                       L437
  → repository.fetchPythonTaskDetail(id) 逐个 GET /api/task/{id}
  → items 经 getProductByMaterialCode 解析 DI → InventoryRecord 列表（内存，不落库）
```

上传解码（2026-09-14 起改走 UDI 看板）：`InventoryMainViewModel.submitDecodeInfoToNetwork`（L137-153）→ `repository.submitUdiData`（InventoryRepository.kt:91，Impl:480-494）→ `api.submitUdiGroups(mapOf(title to barcodes))` → `POST /api/udi/submit`，体为扁平 `{"任务名": ["UDI"...]}`，**不再传 count 总数**。服务端 `udi_groups` 表（与 WEB 看板 UDI中心、油猴脚本 2.4.2 服务器源同源；同名整体覆盖、状态重置待完成）。
- ⚠️ Retrofit 2.9.0 拒绝参数类型含通配符/类型变量（`validateResolvableType` 抛 "Parameter type must not include a type variable or wildcard"）：裸 `Map<String, List<String>>` 因 Kotlin 协变 List 会在 JVM 签名生成 `? extends`，**必须写 `Map<String, @JvmSuppressWildcards List<String>>`**（TaskApiService.kt:131）。项目其余 @Body 均为 data class 包装不受影响。
- 旧链路 `api/custom-data/submit` + `submitCustomData`（detail `{barcodes, count}` → custom_data 表）App 侧已停用，服务端接口保留兼容。
- 实时录入 entry-jobs 接口（submit/active/{jobId}/append/abandon/check-duplicates，L146-162；claim/progress/finish 为脚本侧专用）契约与状态机见 `D:\xz\pythonUDI\实时录入功能方案.md` §五。

## 3. 云端 AI API（CloudAiService.kt）

OpenAI 兼容 `{baseUrl}/chat/completions`（L37,79,147）；`Authorization: Bearer <apiKey>`；支持流式 SSE（`streamChat` L72）；内部自带重试 `maxRetries=3`（L319-327，与同步 RetryPolicy 相互独立）。详见 docs/AI.md。

## 4. 预设服务商

`ai/provider/ProviderPresets.kt:3`（`Preset` 列表）与设置迁移逻辑 `SettingsRepository.migrateOldProviderSettings`（data/SettingsRepository.kt:138-189）确认内置：通义千问（`dashscope.../compatible-mode/v1`）、DeepSeek（`api.deepseek.com/v1`）。用户可在 AI 设置页自定义任意 OpenAI 兼容端点。

## 5. 外部系统边界（金蝶）

本 App **不直接访问金蝶**。链路：金蝶云星空网页 →（Tampermonkey《金蝶提取工具-自动巡航7.2》定时提取，`@match http://116.63.160.136/*`）→ pythonUDI :8000 → 本 App。结果回流：App 生成 PDF → `api/task/upload` → pythonUDI →【待确认-服务端后续如何回写金蝶，本仓库不可见】。

## 6. 修改/新增 API 的落点

新增同步接口：`InventoryApiService.kt` 接口 + `data/dto/SyncModels.kt` DTO + 策略或 Repository 方法。
新增任务接口：`TaskApiService.kt` + 模型 + `InventoryRepository(Impl)` + `InventoryMainViewModel` 调用。
超时定制：`InventoryApiService.create`/`TaskApiService.create` 的 OkHttpClient/Retrofit Builder（当前未设）。
