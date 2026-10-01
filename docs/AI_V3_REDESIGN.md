# AI 助手 v3 重构设计（Chat Assistant Redesign）

> **状态**：设计稿 v1.0（2026-09-19），方向已与使用者对齐（见 §0 决策记录），分期实施。
> **范围**：聊天助手主线 —— `ai/` 包、`viewmodel/ChatViewModel`、`ui/ai/`。视觉链（YOLO/DecoderChain/VisionManager）与批量扫码 OCR 提取不在本次重构范围，仅在 §4.12 说明兼容衔接。
> **关联**：`docs/AI.md`（现状事实基准）、`docs/TECH_DEBT.md`（P0-1 / P1-4 / P1-7 / P3-3）、`AI_CONTEXT.md`（项目入口）。
> **基准**：本文行号以 2026-09-19 工作区代码为准。

---

## 0. 决策记录（已与使用者确认）

| # | 决策点 | 结论 |
|---|--------|------|
| D1 | 测试主要症状 | **工具调用不可靠、慢/卡住、报错**三类并存（查询结果错误未首报，但 §1.3 时间戳问题会诱发它） |
| D2 | 模型服务 | 多家并存：阿里云百炼 Qwen、DeepSeek、NVIDIA 免费 API；**另需本地离线模型**（定位收敛为"无网降级模式"，见 §7） |
| D3 | 动态 SQL 工具 | **废除自由 SQL**，替换为结构化参数查询工具（App 拼 SQL、App 转时间戳），同时消灭 TECH_DEBT P0-1 |
| D4 | 改造节奏 | **不整体推翻**：重写 `InventoryAiAgent` 与 `ConversationMemory` 两个策略层文件，保留健康的 provider/配置层；分三期交付（§8） |

## 1. 现状问题诊断

### 1.1 症状 → 代码级根因对照

| 症状 | 根因 | 证据 |
|------|------|------|
| 工具调用不可靠 | ① 兜底拦截器**误触发**：模型正文只要出现别名词（"生成总结/盘点汇总/查询库存"…）即被劫持为伪造工具调用——回答总结类问题时必然误伤 | `InventoryAiAgent.kt:67-106` |
| | ② 60+ 行中文规则 prompt + 全量任务列表注入，弱模型遵循率随规则数下降 | `PromptBuilder.kt:13-77` |
| | ③ 死循环检测只匹配"完全相同签名"，参数微变即绕过；超 5 轮后的英文过滤 hack（`Let's call that`）说明模型失控行为真实发生过 | `InventoryAiAgent.kt:119-127,175` |
| 慢 / 卡住 | ① 零流式：`streamChat` 全项目无人调用，`AgentEvent.ResponseChunk` 是死代码；用户盯加载文本 30-60s | `AiService.kt:12`、`AgentEvent`（AiAgent.kt:17） |
| | ② OkHttp 重试拦截器（3 次指数退避）包住 chat 调用，服务端无响应时最长 **3×60s+3s ≈ 3 分钟**才报错 | `CloudAiService.kt:314-336` |
| | ③ 每轮 `delay(500)`；每轮全量重发 memory，轮次越多 token 越大越慢 | `InventoryAiAgent.kt:164` |
| | ④ 工具无超时（`ToolExecutor` 裸执行）；用户无法取消（`sendMessage` 的 Job 未保存） | `ToolExecutor.kt:6-16`、`ChatViewModel.kt:119` |
| 报错 | **BUG-1（本文新发现，TECH_DEBT 未记录）**：`ConversationMemory.trimMessages()` 从头部 `removeAt(0)`，窗口 20 条；一轮工具调用占 3+ 条消息，很容易把中间的 `assistant(tool_calls)` 裁掉却留下后面的 `tool` 结果 → OpenAI 兼容接口（DashScope/DeepSeek/NVIDIA 网关均强制配对校验）**直接 400 拒收整段对话** | `ConversationMemory.kt:11,56-60` |

### 1.2 其他已确认缺陷

| 编号 | 问题 | 位置 | 关联 |
|------|------|------|------|
| BUG-2 | CONFIRM 权限形同虚设：`ToolPermission.CONFIRM` 与 AUTO 走同一分支立即执行，确认 UI 不存在 | `InventoryAiAgent.kt:139-144` | `ToolPermission.kt:11-14` 声明了 confirmRequired |
| BUG-3 | `aiService.chat()` 异常被吞成普通字符串返回，无法区分 未配置/网络/鉴权/限流；未配置 key 时也发真实请求 | `CloudAiService.kt:54-69` | — |
| BUG-4 | `ChatViewModel.init` 对 settingsFlow **每次发射**都 `initAgent` → 全局 `ToolRegistry.clear()` 重注册；`OcrCorrectTool` 捕获创建时刻的 aiService，服务商切换后可能持旧实例 | `ChatViewModel.kt:60-78,92-104` | ToolRegistry 是全局 object |
| BUG-5 | `QueryInventoryDataTool` 只做"SELECT 开头"字符串检查（TECH_DEBT P0-1）；`LIMIT 100` 静默截断且不告知模型 | `QueryInventoryDataTool.kt:91,94` | P0-1 |
| BUG-6 | `handleCardActionClick` recordId=0 TODO，产品卡片点击无实际效果 | `ChatViewModel.kt:266-275` | P3-3 |
| BUG-7 | `ai/local/*` 与主链路完全脱节（LocalAiService 占位、LlmEngine 全 TODO、OnnxEngine 与视觉链 `utils/YoloDetector` 功能重复且未使用） | `LocalAiService.kt:62-64`、`LlmEngine.kt` | §7 处置 |

### 1.3 结构性问题（设计层面根因）

1. **把确定性任务交给概率性模型**：LLM 被要求心算 13 位毫秒时间戳做日期过滤、自由拼 SQL where 子句、遵守 60 行规则表——这三件事模型都不可靠，且错误后果直接落到用户数据上。
2. **无能力协商**：所有云端 provider 一律按"支持 Function Calling"处理，弱模型/网关不配合时靠运行时 hack 补救。
3. **无预算概念**：轮次、token、单工具耗时均无上限或上限形同虚设；不可取消。
4. **核心逻辑零测试**（P1-7）：Agent 循环、JSON 解析、裁剪逻辑全部无防护网，回归靠手测。

## 2. 设计原则

1. **确定性的归 App，概率性的归 LLM**：SQL 拼装、时间戳换算、字段白名单、截断控制全部在 App 侧；LLM 只负责意图理解和自然语言表达。
2. **能力协商优于运行时补丁**：provider 显式声明能力（FC / 流式 / JSON 模式），Agent 按能力选协议路径，删除"扫描正文猜工具"类 hack。
3. **一切有预算**：轮次、token 估算、单工具超时、结果大小全部有界且对用户可见。
4. **一切可取消**：生成中可停止；离开页面即取消；确认有超时。
5. **核心可单测**：Agent 循环、策略解析、Memory 裁剪、SQL 构造器保持纯 Kotlin（不依赖 Android 类），用 fake AiService 做 JVM 单测。

## 3. 目标架构

```
ChatScreen（流式气泡 + 工具卡片 + 确认弹窗 + 停止按钮 + 错误卡片）
    ↓ uiState 状态机: Idle / Streaming / AwaitingConfirm / Error
ChatViewModel（Job 取消；流式接线；确认转发；provider 切换 only 重建 service）
    ↓ Flow<AgentEvent>
InventoryAiAgent v2（重写，纯逻辑可单测）
    ├─ 能力路由：FunctionCallingStrategy ｜ JsonToolCallStrategy（§4.1-4.2）
    ├─ 循环预算：轮次≤5、token 估算上限、相似签名死循环检测、单工具 withTimeout(10s)
    └─ ConversationMemory v2（轮次感知裁剪，§4.4）
    ↓
ToolRegistry（改为 Agent 私有实例，不再全局 object）
    ├─ QueryRecordsTool   ⭐ 新（替代动态 SQL，§4.5）
    ├─ ListSessionsTool   ⭐ 新（替代 prompt 全量任务注入，§4.6）
    ├─ GetProductDetailTool / AnalyzeAbnormalTool / GenerateSummaryTool（保留，加超时+结构化返回）
    └─ ExecuteUiActionTool（保留；CONFIRM 真实现，§4.7）
    ↓
AiService（接口增加 capabilities 声明 + 类型化异常）
    ├─ CloudAiService（Qwen / DeepSeek / NVIDIA 网关）——保留实战层，补 tool_calls 流式 delta 解析（§4.8）
    └─ LocalAiService —— 一期保持占位但走 JSON 协议；三期接 llama.cpp（§7）
```

**生命周期**：`ToolRegistry` 从全局 object 改为随 Agent 实例创建销毁；`ChatViewModel` 仅在 activeProvider **id 变化**时重建 AiService+Agent（`distinctUntilChanged`），聊天记忆跨服务商切换保留。

## 4. 详细设计

### 4.1 能力声明与策略路由

```kotlin
// AiProviderConfig 复用现有字段 supportsTools（AiProviderConfig.kt），新增:
data class AiCapabilities(
    val functionCalling: Boolean,   // 支持 OpenAI tool_calls
    val streaming: Boolean          // 支持 SSE
)
// AiService 接口新增: val capabilities: AiCapabilities
```

- `supportsTools=true`（Qwen/DeepSeek 及多数网关）→ `FunctionCallingStrategy`：现状的 tool_calls 解析路径。
- `supportsTools=false`（本地模型、NVIDIA 部分免费模型）→ `JsonToolCallStrategy`（§4.2）。
- 预设（`ProviderPresets`）为每个预设模型带默认能力值，设置页可改（`AiProviderSettingsScreen` 已有编辑表单，加一个开关）。
- **删除** `InventoryAiAgent.supportsTools()`（`InventoryAiAgent.kt:188`）和整段文本兜底拦截器（`InventoryAiAgent.kt:67-106`）——弱模型场景由 JSON 协议正式接管，不再"正文扫描劫持"。

### 4.2 JsonToolCallStrategy（弱模型 / 本地模型协议）

- **Prompt 约定**（策略内拼接，不动主系统提示词）：列出工具名+参数说明；要求"如需用数据回答，只输出一个 JSON 对象 `{"tool":"工具名","arguments":{...}}`，不输出其他文字；如无需工具，直接自然语言回答"。
- **解析**：从回复中提取首个平衡 JSON 对象（复用 `utils/JsonExtractor`），校验：
  - tool 名必须存在于 Registry，否则视为普通文本回复（不再劫持）；
  - arguments 需通过工具的参数校验，失败则带错误信息**重试一次**，再失败按普通文本回复并附提示。
- 与现有 `LocalAiService.buildPrompt/parseResponse`（`LocalAiService.kt:66-146`）思路一致，抽出为独立可测策略类，LocalAiService 复用之。

### 4.3 InventoryAiAgent v2 循环

```
suspend fun chat(message): Flow<AgentEvent> = channelFlow {
    memory.appendUser(message)                    // 记录本轮开始
    val strategy = 选择策略(aiService.capabilities)
    repeat(MAX_ROUNDS = 5) {
        val resp = withTimeout(LLM_TIMEOUT = 90s) { aiService.chat(...) }   // 取消传导到 OkHttp: call.cancel()
        val toolCalls = resp.toolCalls ?: strategy.parse(resp.content)      // JSON 模式
        if (toolCalls.isEmpty()) {
            emit 流式/最终 Response; return@channelFlow
        }
        if (isSimilarSignature(toolCalls, lastSignature)) {                 // 参数相似度, 非 §1.1 的全等
            emit Response("查询陷入循环，请换一种问法"); return@channelFlow
        }
        lastSignature = signature(toolCalls)
        for (call in toolCalls) {                                           // 单轮工具数上限 4
            when (ToolPermissionManager.check(call.name)) {
                BLOCKED -> 结果写 "被阻止"
                CONFIRM -> {
                    val reqId = emit ConfirmRequired(call.name, 参数摘要)    // 挂起等待 UI（§4.7，30s 超时自动取消）
                    if (用户拒绝) { 结果写 "用户拒绝执行"; continue }
                }
                AUTO -> {}
            }
            val result = withTimeout(TOOL_TIMEOUT = 10s) { executor.execute(call) }
            memory.appendToolResult(call.id, call.name, truncate(result))    // 配对写入（§4.4）
            ActionResultExtractor.extract(...)?.let { emit ActionExecuted / 暂存卡片 }
        }
    }
    // 轮次耗尽：带上下文做一次无工具总结调用（保留现状行为，删除英文短语过滤 hack）
}
```

要点：
- **取消传导**：chat 请求包在 `suspendCancellableCoroutine` 中，`invokeOnCancellation { okHttpCall.cancel() }`；流式路径 `callbackFlow` 自带 awaitClose 处理。
- **删除 `delay(500)`**（`InventoryAiAgent.kt:164`）——轮次间隔无必要。
- **token 预算**（软限制）：memory 总字符 > 24k（中文 ≈ 12k token 量级，`字符数/2` 粗估）时对最旧轮次做更激进裁剪；避免每轮全量重发导致响应变慢。

### 4.4 ConversationMemory v2（重写，修 BUG-1）

存储单元从"消息"改为**轮次（Turn）**：

```kotlin
class ConversationMemory {
    // 一轮 = [user] + 0..N × (assistant(tool_calls) + tool结果…) + 最终 assistant 回复
    private val turns: ArrayDeque<Turn>
    private var openTurn: Turn? = null     // 工具循环进行中的一轮

    fun openUserTurn(text) / appendToolCalls(...) / appendToolResult(...) / closeTurn(reply)
    fun messagesForRequest(): List<AiMessage>   // 仅导出"完整轮次"，openTurn 不完整时也按现状导出（正在请求中）
}
```

裁剪规则（**永不拆散 tool 配对**）：
1. 只在轮边界裁剪：移除最旧的**完整轮次**（整轮一起走），绝不单独移除轮内的 `assistant(tool_calls)` 或 `tool` 消息。
2. 上限从"20 条消息"改为"最近 10 个完整轮次 + token 软预算"。
3. 工具结果 > 2000 字符时截断存储，并附注 `[已截断，原始长度 X，共 Y 行]`；被裁剪轮次的用户问题保留一行摘要（可选，二期实现）。

### 4.5 QueryRecordsTool（替代动态 SQL，D3 决策落地）

**参数 schema（LLM 视角，全部为人类可读值）**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `session_id` | long? | 缺省=当前任务（Agent 注入） |
| `product_keyword` | string? | 对 productName/specification/manufacturer/materialCode/di 做 LIKE |
| `batch_number` / `location` | string? | 精确 / LIKE |
| `expiry_before` / `expiry_after` | "YYYY-MM-DD"? | **App 换算 13 位毫秒时间戳**（设备本地时区） |
| `expiry_within_days` | int? | 与 expiry_before 互斥，App 以"今天+N"计算 |
| `quantity_min` / `quantity_max` | number? | — |
| `group_by` | enum? | `productName / location / manufacturer / categoryCode` 白名单 |
| `aggregate` | enum? | `count / sum_quantity` |
| `order_by` | enum? | `expiry_asc / expiry_desc / quantity_desc / name_asc` 白名单 |
| `limit` | int? | 默认 20，最大 100 |

**执行**：新增 `InventoryRepository.queryRecordsForAi(criteria): String` —— 内部用白名单列 + `SimpleSQLiteQuery` **绑定参数**（selectionArgs）构造 SQL；`productKey` 组合键（"DI|物料编码"）与 JOIN 逻辑封装在仓库内部。现有 `executeDynamicSqlForAi`（`InventoryRepositoryImpl.kt:814`）标记 `@Deprecated`，无调用方后删除（P0-1 随之关闭）。

**返回（结构化 + 中文键，直接消除"泄露字段名"类 prompt 规则）**：

```json
{"status":"ok","total":87,"returned":20,"truncated":true,
 "rows":[{"产品名称":"…","规格":"…","批号":"…","数量":12,"库位":"A-01","有效期":"2026-12-31"}]}
```

- 截断**必须**置 `truncated:true` 并附 total——修复 BUG-5 的静默截断。
- 聚合查询返回 `{"…分组键":…,"条数/数量合计":N}`。
- 注入面归零：LLM 传的任何字符串只进绑定参数与 LIKE 值，永不拼接进 SQL 结构。

### 4.6 ListSessionsTool（新增小工具）

替代 `PromptBuilder` 里全量任务列表注入（`PromptBuilder.kt:29-35`）：返回 `[{任务ID, 名称, 创建日期, 状态}]`（上限 20，按创建时间倒序）。系统提示词只注入**当前任务**；用户问及其他任务时由模型主动调本工具。

### 4.7 CONFIRM 确认流（修 BUG-2）

- 新事件 `AgentEvent.ConfirmRequired(requestId, toolName, argumentsSummary)`。
- Agent 持有 `MutableMap<String, CompletableDeferred<Boolean>>`；`ChatViewModel.confirmAction(requestId)` / `cancelAction(requestId)` 完成/取消 Deferred；**30 秒无响应自动按拒绝处理**（防挂起）。
- ChatScreen 弹 Material3 AlertDialog（工具名 + 参数摘要 + 执行/取消）。
- `confirmRequired` 集合维持现状：`execute_ui_action`（`batch_complete_products` 工具已不存在，随之移除，见 §6）。

### 4.8 流式（二期）

- `StreamChunk` 扩展为密封类：`Text(delta)` / `ToolCallDelta(index, idDelta?, nameDelta?, argsDelta)` / `Done`；`CloudAiService.streamChat` 聚合 OpenAI `delta.tool_calls` 分片（按 index 累积 id/name/arguments 字符串）。
- Agent 收到完整 toolCalls 后进入工具执行；纯文本回复逐 chunk 发 `AgentEvent.ResponseChunk`（ViewModel 已有处理逻辑，激活即可）。
- **重试拦截器只保留在非流式 client**：SSE 中途重试不安全；流式 client 不带 retry interceptor，连接失败直接报错并触发一次非流式降级重试。
- 网关不支持流式 tool_calls 的兜底：流式结果既无文本也无 toolCalls 时，降级为一次非流式调用（每轮最多一次）。

### 4.9 错误分类（修 BUG-3）

新增 `AiServiceException(code, message)`，`CloudAiService` 按异常类型抛出而非吞成字符串：

| code | 触发 | UI 呈现（错误卡片，非普通气泡） |
|------|------|------|
| NOT_CONFIGURED | 未配置 provider | "请先配置 AI 服务" + 跳设置页按钮 |
| UNREACHABLE | 连接失败（流式 client 不重试，快速失败） | "无法连接 {服务商名}" + 重试按钮 |
| AUTH_FAILED | HTTP 401/403 | "API Key 无效或过期" |
| RATE_LIMITED | HTTP 429 | "请求过于频繁"（带 Retry-If 提示） |
| TIMEOUT | LLM 90s / 工具 10s | "响应超时" + 重试 |
| PARSE_ERROR | 响应解析失败 | 自动重试一次后提示 |

聊天页打开时做一次 `isAvailable()` 探测（结果缓存 5 分钟；有 `modelsEndpoint` 时用 GET /models，避免消耗对话 token）。

### 4.10 Prompt 瘦身

现状 60+ 行 → 目标 ≤ 25 行，削减内容与去向：

| 现状规则 | v3 处置 |
|----------|---------|
| "严禁泄露字段名 + 字段中文对照" 整段 | **删除**——QueryRecordsTool 返回中文键，问题在源头消失 |
| "日期必须换算 13 位毫秒时间戳" 整段 | **删除**——工具收 ISO 日期，App 换算 |
| 全量任务列表注入 | **删除**——ListSessionsTool 按需查询 |
| "核心工具库" 手抄工具说明 | **删除**——工具定义经 FC/JSON 协议原生下发，不重复 |
| "CRITICAL RULE" 英文内容过滤 | **删除**（`InventoryAiAgent.kt:175`） |
| 保留 | 角色一句话、当前任务上下文、今天日期、Markdown 输出规范、"需要数据时必须走工具"一条 |

### 4.11 UI 变更（`ui/ai/`）

- 工具调用卡片：名称 + 参数摘要 + 耗时 + 结果条数，进行中转圈/可折叠。
- 停止按钮：`isProcessing` 时输入框位置变"停止"，点击取消当前 Job。
- 确认弹窗（§4.7）。
- 错误卡片（§4.9），区别于普通消息气泡。
- 顺带修复 BUG-6（卡片点击 recordId=0）——`ShowProductCardList` 的卡片数据直接携带 `sessionId+recordId`（由 QueryRecordsTool 查询时带出），点击发 `OpenEditDialog(recordId)`。

### 4.12 兼容衔接（不在重构范围但受影响的点）

- `OcrInfoExtractor.extract(ocrText, aiService)`（`batchscanner/ocr/OcrInfoExtractor.kt:41-47`）：继续接收 `AiService?`，接口不变；其内部 CloudAiService 直接构造的路径不受 ToolRegistry 实例化影响。
- `OcrCorrectTool(aiService)`：随实例化 Registry 注册时持有**当前** AiService 实例（BUG-4 修复的副产品）。

## 5. 测试计划（对应 P1-7）

新增纯 JVM 测试（核心类不依赖 Android 类型）：

| 测试类 | 覆盖 |
|--------|------|
| `ConversationMemoryTest` | 各填充模式下裁剪**永不拆散 tool_calls/tool 配对**；轮次上限；长结果截断 |
| `AgentLoopTest`（fake AiService 脚本化） | 正常单轮/多轮工具链、JSON 模式解析、相似签名死循环截断、CONFIRM 通过/拒绝/超时、工具超时、用户取消 |
| `JsonToolCallStrategyTest` | 合法 JSON / 代码围栏包裹 / 多对象 / 非 JSON 文本 / 未知工具名 |
| `QueryRecordsSqlBuilderTest` | ISO→毫秒换算、白名单列与排序、注入样例（`' OR 1=1 --` 进绑定参数）、limit/truncated |

验收脚本化：三个云端服务商各跑 20 轮混合查询（查库存/近效期/异常/总结/跳转），**零 400、零死循环、零卡死**。

## 6. 文件改动清单

| 动作 | 文件 | 说明 |
|------|------|------|
| 重写 | `ai/InventoryAiAgent.kt` | §4.3 |
| 重写 | `ai/memory/ConversationMemory.kt` | §4.4 |
| 重写 | `ai/tools/QueryInventoryDataTool.kt` → **`ai/tools/QueryRecordsTool.kt`** | §4.5 |
| 新增 | `ai/agent/strategy/`（`AgentStrategy.kt` + 两个实现） | §4.1-4.2 |
| 新增 | `ai/tools/ListSessionsTool.kt` | §4.6 |
| 新增 | `app/src/test/.../ai/` ×4 测试类 | §5 |
| 修改 | `ai/provider/AiService.kt` / `AiModels.kt` | capabilities、AiServiceException、StreamChunk 密封化 |
| 修改 | `ai/provider/CloudAiService.kt` | 流式 tool_calls 聚合、错误分类、流式 client 无重试 |
| 修改 | `ai/tools/ToolRegistry.kt`（实例化）/ `ToolExecutor.kt`（超时）/ `ToolPermission.kt`（清理失效条目） | §3/§4.7 |
| 修改 | `viewmodel/ChatViewModel.kt` | Job 取消、确认转发、provider id 去重重建、错误卡片状态 |
| 修改 | `ui/ai/ChatScreen.kt` / `ChatMessageItem.kt` | §4.11 |
| 修改 | `ai/prompt/PromptBuilder.kt` | §4.10 |
| 修改 | `data/repository/InventoryRepository(.Impl)` | `queryRecordsForAi`；`executeDynamicSqlForAi` 弃用→删除 |
| 修改 | `data/SettingsRepository.kt` / `AiProviderConfig.kt` | capabilities 字段与 DataStore 迁移（默认值兜底） |
| 三期处置 | `ai/local/*` | LlmEngine/LocalAiService 被 llama.cpp 方案替换；OnnxEngine/ModelManager 与视觉链重复且未使用 → **删除**；ModelFileManager/ChatHistoryManager 视三期需要决定 |

## 7. 本地离线模型（三期，独立立项）

- **定位**：无网降级模式，非全功能替代。无网可用能力收敛为：库存查询（QueryRecordsTool 经 JSON 协议）、盘点总结、简单问答；设置页明确标注能力差异。
- **技术路径**：llama.cpp Android JNI + Qwen 系列小模型量化 GGUF（与云端 Qwen 家族 prompt 习惯一致）。性能预期：中等手机 7B-Q4 约 5-15 token/s；3B 以下不建议跑工具循环。
- **接入点**：实现 `LlamaCppEngine` → 替换 `LocalAiService` 占位推理；走 `JsonToolCallStrategy`（本地模型一律 `functionCalling=false`）。
- **前提**：一期/二期完成后再启动；`ai/local` 现有未使用脚手架的处置见 §6。

## 8. 分期路线与验收

### 第一期：止血 + 核心重造（1-2 天）
1. `ConversationMemory` 重写（修 BUG-1，400 根因）
2. `InventoryAiAgent` 重写（能力路由替代劫持、超时、取消、相似签名检测，修 BUG-2）
3. `QueryRecordsTool` 上线 + `executeDynamicSqlForAi` 下线（关 P0-1）
4. CONFIRM 确认流 + 错误分类显式化（修 BUG-3）
5. ChatViewModel：取消按钮 + provider 切换去重（修 BUG-4）

**验收**：Qwen / DeepSeek / NVIDIA 免费模型各 20 轮混合查询，零 400、零死循环、零卡死；未配置服务商时错误卡片明确指路。

### 第二期：流式 + 体验（2-3 天）
1. CloudAiService tool_calls 流式聚合 + 全链路流式 + 停止按钮
2. 工具调用卡片 UI + 错误卡片 + 卡片点击修复（BUG-6）
3. Prompt 瘦身（§4.10）+ ListSessionsTool
4. §5 四个测试类全绿

**验收**：首字延迟 < 2s（云端正常网络）；测试套件进 CI 可重复。

### 第三期：本地离线（独立立项，节奏另定）
llama.cpp 接入、JSON 模式实测调优、能力降级 UI、`ai/local` 清理。

## 9. 风险与开放问题

| # | 风险/问题 | 应对 |
|---|-----------|------|
| R1 | NVIDIA 免费模型能力参差：部分模型流式下 tool_calls 分片格式异常 | §4.8 降级为非流式重试；预设表按模型逐个标 `supportsTools` |
| R2 | JSON 模式下弱模型仍可能输出不规范 JSON | 解析失败重试一次；连续失败按普通文本回复并提示换模型（不劫持、不伪造调用） |
| R3 | token 估算无 tokenizer，粗估偏差可能导致上下文超限 | 软预算 + 供应商侧报错时按比例收紧阈值的自适应逻辑（二期） |
| R4 | 有效期"天"用设备本地时区计算，跨时区设备表现可能差一天 | 近效期业务本身基于设备本地日期（与 AnomalyDetector 现状一致），文档明示即可 |
| R5 | 聊天历史目前纯内存，杀进程即丢（`ai/local/ChatHistoryManager` 未接主链路） | 暂不处理；三期本地模型如需持久化一并决策 |
| OQ1 | `batch_complete_products` 在 confirmRequired 名单中但工具已不存在——一期随 ToolPermission 清理移除 | — |
