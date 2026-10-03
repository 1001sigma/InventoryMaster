# AI — AI 助手模块（`ai/` 包）

> 📐 **本文档为现状事实基准（2026-10-02，v3.5 运行时架构）**。历史设计见《AI_V3_REDESIGN.md》（v3 重构）、
> 《AI Runtime 架构改造与演进建议》（Phase 1-9 路线，Phase 1/3/4/7 已落地）。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。

## 1. 模块分层（当前真相）

```
ChatScreen（流式气泡/思维链折叠卡片/确认弹窗/停止/历史会话抽屉）
    ↓ AgentEvent（UI 事件契约）
ChatViewModel（线程切换/落库/确认转发；threadId = 任务ID 或 "global"）
    ↓ Flow<AgentEvent>
InventoryAiAgent（业务适配层：PromptBuilder 注入、卡片持有、事件映射）
    ↓ Flow<AiEvent>（运行时统一事件）
AiRuntime（runtime/，通用工具循环：协议路由/流式聚合/循环防护/预算/确认流/轮次制记忆）
    ↓
ToolRegistry（实例化，随 Agent 生命周期）→ ToolExecutor（校验→超时→异常归一）→ 7 个 AiTool
    ↓
AiService（capabilities 声明 FC/流式）
    ├─ CloudAiService（OpenAI 兼容；SseParser 解析 SSE；流式专用无重试 client）
    └─ LocalAiService（占位，无推理）
```

关键文件：
- `ai/runtime/AiRuntime.kt` — 通用循环（Phase 2 产出）；`ai/runtime/AiEvent.kt` — 统一事件（Phase 3 产出）；`ai/runtime/ToolCallAccumulator.kt` — 流式 tool_calls 分片聚合
- `ai/agent/strategy/ToolCallStrategy.kt` — FC / JSON 文本双协议路由
- `ai/InventoryAiAgent.kt` — 业务适配（瘦）
- `ai/memory/ConversationMemory.kt` — 轮次制记忆（裁剪永不拆 tool 配对）+ `restoreFrom`（持久化恢复）+ `ChatThreadCodec`
- `ai/tools/` — ToolResult/ToolError/ToolValidator + 7 工具 + `ToolPermissionManager`（CONFIRM 名单：execute_ui_action）
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
| Completed(answer) | Response(answer, pendingCardAction) |
| Failed(AiServiceException) | Error(message, kind) |
| AnswerDelta（预留） | ResponseChunk（FC 模型流式正文实时外显；JSON 协议正文不外泄） |

## 3. 工具系统（ToolResult 结构化）

`AiTool.execute(toolCall: AiToolCall): ToolResult`。ToolResult = content（给模型，进 memory）/
data（给程序）/error（六类 ToolErrorKind）/uiAction（直达 UI，替代已删除的 ActionResultExtractor 字符串协议）。

工具 7 个：query_records（结构化查询，唯一库存入口）、list_sessions、search_products（产品库唯一
入口：名称/厂家/DI/物料编码模糊 + 精确 productKey/DI 三级降级，含注册证号/型号全字段与 total 计数；
2026-10-02 合并原 get_product_detail）、execute_ui_action（CONFIRM）、ocr_correct、
analyze_abnormal、generate_summary。

铁律：
- **ToolValidator** 在执行边界校验 required/类型/enum；工具内只做业务校验；错误文案必须可自纠
  （"缺少必填参数 X，请补全后重试"），未知工具回喂可用清单。
- **日期三形态**（实机库证据）：expiryDate/productionDate 列混存 毫秒/8位yyyyMMdd/6位yyMMdd
  （Excel 导入产 8 位、GS1 扫码产 6 位、服务端历史数据为毫秒）。任何日期逻辑必须经
  `AiRecordSqlBuilder.dateCondition`（三形态 OR 区间）或 `DateUtils.toLocalDate`，**禁止假设毫秒**。
  效期显示/排序用 `displayDateExpr` 规范化表达式。
- **查询呈现各司其职**（用户选型 a）：明细由产品卡片呈现（可点击弹详情），模型只收到
  总数/效期范围/前3条最近到期提醒（重点行附带 记录ID+任务ID，供 navigate_to_record 跳转——
  ISSUES-1 修复，不回喂全量 ID）；聚合查询返回统计 JSON。0 结果时工具自动放宽诊断
  （去日期条件、不限任务各数一次）回喂模型。

## 4. 记忆与持久化

- `ConversationMemory`：轮次（Turn）制；裁剪只在轮边界（MAX_TURNS=10、SOFT_CHAR_BUDGET=24k、
  **单条工具结果截断 8000**——2000 曾把查询结果拦腰截断引发模型重查循环）；`restoreFrom` 从快照重建。
- 持久化：Room `chat_messages` 表（DB 6→7），threadId = 任务ID字符串或 "global"；每轮结束整线程
  事务落库（delete+insert）；`ChatThreadCodec` 负责消息 ↔ 行（toolCalls JSON 序列化）。
  ChatViewModel 按任务分线程：切换任务取消在飞生成 → join → 落旧线程 → 载新线程。
- 历史会话栏：`getThreadSummaries()` + `openThread()`。

## 5. 流式

`AiService.capabilities`（functionCalling/streaming）声明能力。流式路径：CloudAiService.streamChat →
SseParser（data 行 → StreamChunk.Text/ToolCallDelta/Completed/Error）→ runtime.callModelRound →
ToolCallAccumulator 按 index 聚合 → 完整 AiToolCall。FC 模型正文增量实时发 ResponseChunk；
JSON 协议正文可能是工具信封，不外泄 UI。流式空结果降级非流式一次。流式 client 不带重试拦截器
（SSE 中途重试不安全）。

## 6. 防护预算（runtime Policies）

MAX_ROUNDS=5；单轮工具数≤4；LLM 超时 90s；工具超时 10s；CONFIRM 30s 超时按拒绝；
死循环双检测：完全相同签名（参数归一化）→ 立即截断；同工具单调用连 4 轮 → 截断
（4 而非 3：实机证据 2000 截断时代模型合理重试一次会被 3 误杀）。

## 7. 设置

- DataStore `ai_providers_json` + `active_provider_id`（多服务商，supportsTools/流式按服务商配置）
- `ai_show_thinking`（默认开）：思维链折叠卡片；关闭 = 工具行只显示一行摘要
- 入口：设置 → AI 助手（AiSettingsContent）

## 8. 已知事项

- `ai/local/*` 为未接线的脚手架（LocalAiService 占位无推理）；端侧路线建议 LiteRT-LM 或局域网
  Ollama（OpenAI 兼容端点），见 4 个参考项目分析（D:\xz\repos\AI Agent\项目分析\）。
- mikepenz markdown 渲染器（0.28.0）表格无列宽分配——模型输出已被引导用列表而非表格。
- 待办 Phase：5/6（本轮模型级能力目录 `ModelCapabilityCatalog` + 请求构造 `OpenAiRequestBuilder`）
  已落地；8/9（高级能力——暂缓）。
