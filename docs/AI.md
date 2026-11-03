# AI — AI 助手模块（`ai/` 包）

> 📐 **本文档为现状事实基准（2026-10-02，v3.5 运行时架构）**。历史设计见《AI_V3_REDESIGN.md》（v3 重构）、
> 《AI Runtime 架构改造与演进建议》（Phase 1-9 路线，Phase 1/3/4/7 已落地）。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。

## 1. 模块分层（当前真相）

```
ChatScreen（流式气泡/思维链折叠卡片/确认弹窗/停止/历史会话抽屉）
    ↓ AgentEvent（UI 事件契约）
ChatViewModel（线程切换/落库/确认转发；v2 多会话 threadId = t<任务ID>_<ts> / g_<ts>，旧 global/纯数字兼容）
    ↓ Flow<AgentEvent>
InventoryAiAgent（业务适配层：PromptBuilder 注入、卡片持有、事件映射）
    ↓ Flow<AiEvent>（运行时统一事件）
AiRuntime（runtime/，通用工具循环：协议路由/流式聚合/循环防护/预算/确认流/轮次制记忆）
    ↓
ToolRegistry（实例化，随 Agent 生命周期）→ ToolExecutor（校验→超时→异常归一）→ 10 个 AiTool
    ↓
AiService（capabilities 声明 FC/流式）
    ├─ CloudAiService（OpenAI 兼容；SseParser 解析 SSE；流式专用无重试 client）
    └─ LocalAiService（基于 Google LiteRT-LM 0.17.1 原生离线推理，支持 Gemma 4 E2B / Qwen3-4B，GPU 优先 + KV 复用）
```

关键文件：
- `ai/runtime/AiRuntime.kt` — 通用循环（Phase 2 产出）；`ai/runtime/AiEvent.kt` — 统一事件（Phase 3 产出）；`ai/runtime/ToolCallAccumulator.kt` — 流式 tool_calls 分片聚合
- `ai/agent/strategy/ToolCallStrategy.kt` — FC / JSON 文本双协议路由
- `ai/InventoryAiAgent.kt` — 业务适配（瘦）
- `ai/memory/ConversationMemory.kt` — 轮次制记忆（裁剪永不拆 tool 配对）+ `restoreFrom`（持久化恢复）+ `ChatThreadCodec`
- `ai/tools/` — ToolResult/ToolError/ToolValidator + 8 工具 + `ToolPermissionManager`（CONFIRM 名单：execute_ui_action）
- `ai/prompt/OutputFormatRules.kt` — 输出格式规则库（意图→格式三形态 + 反向约束，全局排版；
  工具内呈现规则仍在各工具。LaTeX 禁用——渲染器不支持公式）

## 2. AgentEvent ↔ AiEvent 映射

| AiEvent（runtime 发出） | AgentEvent（UI 收到） |
|---|---|
| ThinkingText | Thinking |
| ToolCallStarted(call) | ToolCallStart(name, 参数摘要) |
| ToolCallFinished(call, result) | ToolCallResult(name, result.content)；result.uiAction → 卡片持有或 ActionExecuted |
| AwaitingConfirmation(requestId, call) | ConfirmRequired（30s 超时按拒绝） |
| ConfirmTimedOut(requestId) | ConfirmTimedOut（UI 关闭对应确认弹窗，避免"弹窗挂着但已失效"） |
| AwaitingQuestion(requestId, questions) | QuestionRequired（180s 等待，UI 弹出全宽问答卡片） |
| QuestionTimedOut(requestId) | QuestionTimedOut（UI 关闭全宽问答卡片并按跳过继续） |
| Completed(answer) | Response(answer, pendingCardAction) |
| Failed(AiServiceException) | Error(message, kind) |
| AnswerDelta（预留） | ResponseChunk（FC 模型流式正文实时外显；JSON 协议正文不外泄） |

## 3. 工具系统（ToolResult 结构化）

`AiTool.execute(toolCall: AiToolCall): ToolResult`。ToolResult = content（给模型，进 memory）/
data（给程序）/error（六类 ToolErrorKind）/uiAction（直达 UI，替代已删除的 ActionResultExtractor 字符串协议）。

工具 10 个：
1. `query_records`：结构化库存与效期查询（唯一查实物库存入口）；
2. `query_verification_records`：查验状态与库存明细查询引擎。支持查验状态（已查验/未查验）、仓库名称/库位（冷藏库/常温库等）、生产厂家（西门子/迈瑞等）、任务 ID、产品关键词多维联合筛选。支持 `query_mode` 参数（`count` / `list`）：问数量时以纯文本直接返回总数；问清单时，条数 $\le 20$ 生成产品卡片（`ShowProductCardList`），条数 $> 20$ 生成自适应 Markdown 表格展示，兼顾直观性与端侧流畅度；
3. `update_record_verification`：查验状态修改与撤销工具（CONFIRM 守卫）。标记查验或撤销查验；当撤销查验时，原先记录的实际盘点数量会自动通过 `StockRecord.uncheck()` 格式化备份转存至备注（如 `原实盘: 50; 原备注`），防止人工盘点数据丢失；
4. `ask_question`：交互式多选问答与决策澄清工具（Human-in-the-Loop 交互，2026-10-31 落地）。向用户呈现全宽贴底选择卡片（支持单选/多选/就地补充输入/跳过），运行时拦截挂起协程并等待用户作答，唤醒后将选项及自定义输入精准注入记忆上下文，大模型紧跟推进业务；
5. `list_sessions`：列出所有盘点任务；
6. `search_products`：产品档案库唯一入口（名称/厂家/DI/物料编码模糊 + 精确 productKey/DI 三级降级，含注册证号/型号全字段与 total 计数；2026-10-02 合并原 get_product_detail）；
7. `execute_ui_action`（CONFIRM 守卫）；
8. `ocr_correct`；
9. `analyze_abnormal`；
10. `generate_summary`。

铁律：
- **ToolValidator** 在执行边界校验 required/类型/enum；工具内只做业务校验；错误文案必须可自纠
  （"缺少必填参数 X，请补全后重试"），未知工具回喂可用清单。
- **日期三形态**（实机库证据）：expiryDate/productionDate 列混存 毫秒/8位yyyyMMdd/6位yyMMdd
  （Excel 导入产 8 位、GS1 扫码产 6 位、服务端历史数据为毫秒）。任何日期逻辑必须经
  `AiRecordSqlBuilder.dateCondition`（三形态 OR 区间）或 `DateUtils.toLocalDate`，**禁止假设毫秒**。
  效期显示/排序用 `displayDateExpr` 规范化表达式。
- **查询呈现各司其职与 0 Token 契约**：明细由产品卡片呈现（可点击弹详情），模型只收到
  总数/效期范围/重点记录概要。`UiAction.ShowProductCardList` / `ExpiredProductCardData` 携带
  10 个全量业务字段（`sessionName`, `location`, `manufacturer`, `specification`, `unit`, `isVerified`,
  `productionDateStr`, `remarks`, `materialCode`, `di`），持久化经 `ProductCardListCodec` 进数据库
  `chat_messages.cardActionJson`。**卡片纯作为 UI 交互载体，绝不进入大模型上下文（0 Token 消耗）**；
  20 条截断仅用于保护客户端性能。
- **单条消息视觉完整闭环**：`ChatMessageItem` 内部采用 `MarkdownText`（正文） $\rightarrow$ `ProductCardListWidget`（卡片） $\rightarrow$ `MessageActionBar`（操作栏）从上到下线性布局，保证操作栏（复制/耗时/模型/重新生成）永远位于卡片最下方；`ProductCardListWidget` 升级为现代设计（任务归属、仓库库位、彩色查验徽章、效期倒计时警示、厂家规格排版），点击弹出现代 `CardDetailDialog` 双核心大指标与圆角网格全字段弹窗。
  ISSUES-1 修复，不回喂全量 ID）；聚合查询返回统计 JSON。0 结果时工具自动放宽诊断
  （去日期条件、不限任务各数一次）回喂模型。

## 4. 记忆与持久化

- `ConversationMemory`：轮次（Turn）制；裁剪只在轮边界（MAX_TURNS=10、SOFT_CHAR_BUDGET=24k、
  **单条工具结果截断 8000**——2000 曾把查询结果拦腰截断引发模型重查循环）；`restoreFrom` 从快照重建。
- 持久化：Room `chat_messages` 表（DB 6→7），v2 多会话（2026-10-04）：threadId = `t<任务ID>_<时间戳>`
  （任务会话，一个任务可多条）/ `g_<时间戳>`（自由会话）；旧 "global"/纯数字 ID 兼容解析零迁移；
  会话标题 = 首条用户消息截断（DAO getFirstUserMessages）；每轮结束整线程事务落库（delete+insert）；
  `ChatThreadCodec` 负责消息 ↔ 行（toolCalls JSON 序列化）。入口三处：主页（自由侧，绝不自动绑
  任务）、任务详情页顶栏图标（落到该任务最近会话，无则创建）、任务详情页悬浮 AI 球 → 快捷问答
  面板（同线程同链路，只呈现最后一条问答；设计细节见 docs/AI_UI_OPTIMIZATION.md P1-6 收官批）；
  顶栏 + 新建、侧栏长按删除。切换任务取消在飞生成 → join → 落旧线程 → 载新线程（`switchThread`
  统一收口）。
- 历史会话栏：`getThreadSummaries()` + `openThread()` + 长按删除（`deleteThread`）+
  时间分组（今天/7天内/更早）。

## 5. 流式

`AiService.capabilities`（functionCalling/streaming）声明能力。流式路径：CloudAiService.streamChat →
SseParser（data 行 → StreamChunk.Text/ToolCallDelta/Completed/Error）→ runtime.callModelRound →
ToolCallAccumulator 按 index 聚合 → 完整 AiToolCall。FC 模型正文增量实时发 ResponseChunk；
JSON 协议正文可能是工具信封，不外泄 UI。流式空结果降级非流式一次。流式 client 不带重试拦截器
（SSE 中途重试不安全）。

## 6. 防护预算（runtime Policies）

MAX_ROUNDS=5；单轮工具数≤4；LLM 超时 90s；工具执行超时 10s；CONFIRM 30s 超时按拒绝；问答交互（ask_question）180s 操作等待超时（超时按跳过处理）；
死循环双检测：完全相同签名（参数归一化）→ 立即截断；同工具单调用连 4 轮 → 截断
（4 而非 3：实机证据 2000 截断时代模型合理重试一次会被 3 误杀）。

## 7. 交互式问答系统（ask_question / Human-in-the-Loop）

> 落地于 2026-10-31，解决大模型在遇到意图歧义、多批次二选一、执行不可逆决策时需要向用户澄清并收集选项反馈的问题。

### 7.1 核心机制与协程挂起
- **方案 A 挂起机制**：`AiRuntime` 检测到工具调用为 `ask_question` 时，脱离 `ToolExecutor` 10s 短超时通道，由运行时通过 `CompletableDeferred<UserQuestionAnswer>` 挂起当前对话轮协程（预算 180s）；
- **事件流转**：`AiRuntime` emit `AiEvent.AwaitingQuestion` $\rightarrow$ `InventoryAiAgent` 映射为 `AgentEvent.QuestionRequired` $\rightarrow$ `ChatViewModel` 更新 `ChatUiState.pendingQuestion`；
- **唤醒与回填**：用户在 UI 点击提交或跳过后，`ChatViewModel` 调用 `submitQuestionAnswer / skipQuestion`，`deferred.complete(answer)` 唤醒协程，自动格式化为标准 `ToolResult` 注入记忆，大模型无缝获取上下文并推进后续推理。

### 7.2 UI/UX 与交互规范（InteractiveQuestionCard）
- **全宽贴底设计**：撑满屏幕横向两侧（`fillMaxWidth()`），顶部 24dp 圆角，挂起时独占底部输入区域并自适应导航栏/手势条安全距离（`bottomInset`），消除与常规聊天输入框重叠的问题；
- **防键盘挤压变形**：内容区包裹在 `verticalScroll` 滚动容器中，限制最大高度为屏幕高度的 78%，输入框保持固定 44dp 标准高度；当软键盘弹起时卡片自适应平滑滚动，绝不发生输入框被挤扁变形；
- **响应即时性**：选项状态由不可变 `List<String>` 驱动 `SnapshotStateMap`，点击瞬间触发 60fps 高亮反馈（淡蓝底色、蓝色边框、向右箭头）；
- **单选互斥与多选兼容**：
  - **单选模式**：预设选项与「`✎ 其他补充...`」严格互斥——选中文本框输入时自动清空预设选项高亮；切换回预设选项时自动清空文本框内容并收起输入框；
  - **多选模式**：支持自由多选勾选与自定义文本说明同时并存；
- **跳过与异常安全**：右上角 `✕` 与左侧“跳过”按钮均可安全退出，超时或用户主动跳过均会以清晰文案告知大模型，防止对话轮卡死。

## 8. 设置与服务商管理

- DataStore `ai_providers_json` + `active_provider_id`（多服务商，supportsTools/流式按服务商配置）；
- `ai_show_thinking`（默认开）：思维链折叠卡片；关闭 = 工具行只显示一行摘要；
- **模型列表智能拉取与缓存（2026-11-02 落地）**：
  - 智能端点推导（`resolveModelsEndpoint()`）：根据 `baseUrl` 智能识别规范，自动派生 `/v1/models` 或 `/models`；
  - 弹窗中点击「获取模型列表」直接调用 `fetchModelsResult()`，支持双端点自动 fallback，兼容 OpenAI `data[].id` 与 Ollama `models[].name` 结构；
  - 失败时外显红色具体错误信息，不再静默吞异常；拉取成功后持久化至 `cachedModels`；
  - **便捷切换**：服务商列表卡片支持一键弹出下拉菜单在已缓存模型中快速切换，编辑弹窗支持既可自由打字输入也可从下拉列表中点选；
- **高级模型超参数管理（参考 Operit 架构设计）**：
  - 第一期落地常用三大参数：温度 `temperature`（0.0 ~ 2.0，步长 0.05）、核采样 `top_p`（0.0 ~ 1.0，步长 0.05）、最大输出长度 `max_tokens`（预设 1024/2048/4096/8192 档位与自定义数字输入）；
  - 每个参数具备独立开启 Switch 开关，未开启时不向 API 请求体写入该字段；开启后动态注入 `OpenAiRequestBuilder` 请求 JSON；
  - 服务商卡片直观外显生效的参数摘要（如 `T:0.7 | P:0.95 | Max:4096`）；
- 入口：设置 → AI 助手（AiSettingsContent）。

## 9. 本地模型与已知事项

- **本地离线模型（已落地，2026-10-05）**：`ai/local/` 经 Google `com.google.ai.edge.litertlm:litertlm-android:0.17.1` 原生支持 Gemma 4 E2B 和 Qwen3-4B-Instruct-2507。
  - **硬件加速与显存优化**：OpenCL GPU 优先运行（dmabuf 显存不占 PSS 内存，Xiaomi 12GB 实测 PSS 从 8.09GB 锐降至 1.12GB，彻底解除 MIUI 前台 6GB 杀后台限制）。
  - **流式与交互**：`capabilities.streaming=true`，支持流式分片与用户停止主动中断（`cancelProcess()` 跨线程立即打断）。
  - **KV 缓存复用**：`LocalLlmRuntime` 按线程维护活跃 `Conversation` 与指纹，前缀匹配时仅增量 prefill。
  - **设置与获取**：设置页支持断点续传下载与 SAF 本地文件导入，SHA-256 校验。
- **Markdown 表格渲染**：渲染器已升级至 `0.31.0`，引入 `AutoWrapMarkdownTable` 单元格自适应换行，彻底修复列宽错位问题。
- 待办 Phase：5/6（模型级能力目录 `ModelCapabilityCatalog` + 请求构造 `OpenAiRequestBuilder`）已落地；8/9（高级能力）暂缓。
