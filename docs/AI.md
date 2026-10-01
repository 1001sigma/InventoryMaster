# AI — AI 助手模块（`ai/` 包）

> 任务模板未单列此文档，但 AI 模块约 30 个文件、正处 v2.0 通用化改造中，特建本文档承载细节。（用户已确认允许按实际功能建文档。）
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`。
> ⚠️ 《AI_MODULE.md》（历史快照，已归档至 docs/archive/）的工具清单（11 个工具）已过时，以本文为准。
> 📐 **v3 重构一期已落地（2026-09-19）**，设计与完整变更清单见《docs/AI_V3_REDESIGN.md》。本文以下描述的下列内容已被替代，以 v3 实现为准：
> - §3 动态 SQL 工具 → 已删除，替换为 `QueryRecordsTool`（结构化条件 + `AiRecordSqlBuilder` 绑定参数，关闭 P0-1）；新增 `ListSessionsTool`；
> - §2 调用链中"文本兜底拦截器 / delay(500) / CONFIRM 即时执行" → 已删除，改为 `ai/agent/strategy/` 能力路由（FC/JSON 双协议）、工具超时、CONFIRM 真确认弹窗、全程可取消；
> - memory 滑动窗口（MAX_MESSAGES=20 头部裁剪，有拆散 tool 配对导致 400 的 bug）→ `ConversationMemory` 轮次制重写；
> - 工具数 6 → 7（ToolRegistry 由全局 object 改为随 Agent 实例化）；
> - 新增单测：ConversationMemoryTest / AiRecordSqlBuilderTest（运行需 JBR 21，见 TECH_DEBT P1-7）。

## 1. 模块分层

```
ai/
├── InventoryAiAgent.kt        核心 Agent 循环（ReAct 风格）
├── agent/
│   ├── AiAgent.kt             接口 chat(): Flow<AgentEvent>（L5）
│   ├── UiAction.kt            密封类：AI → UI 的动作指令（L3-10）
│   └── ActionResultExtractor.kt  从工具 JSON 结果提取 UiAction（L8）
├── provider/                  LLM 接入层
│   ├── AiService.kt / AiModels.kt（AiMessage/AiResponse/AiToolCall/AiToolDefinition/StreamChunk）
│   ├── AiProviderConfig.kt    baseUrl/model/apiKey/isLocal/modelsEndpoint
│   ├── CloudAiService.kt      OkHttp → OpenAI 兼容 API（L22）
│   ├── LocalAiService.kt      本地模型占位（isLocal=true，未实现推理【事实：类体返回提示信息】）
│   └── ProviderPresets.kt     预设：通义千问 / DeepSeek
├── tools/                     工具系统
│   ├── AiTool.kt（接口）/ ToolRegistry.kt（注册表 object）/ ToolExecutor.kt
│   ├── ToolPermission.kt      AUTO / CONFIRM / BLOCKED（L3,9）
│   ├── QueryInventoryDataTool.kt   ⭐ 动态 SQL 查询（v2.0 核心）
│   ├── GetProductDetailTool / AnalyzeAbnormalTool / GenerateSummaryTool
│   ├── OcrCorrectTool（复用 AiService 做 OCR 纠错）
│   ├── ExecuteUiActionTool.kt      执行 UI 导航
│   └── anomaly/AnomalyDetector.kt  异常检测引擎
├── prompt/PromptBuilder.kt    系统 prompt 组装（L5 build）
├── memory/ConversationMemory.kt  滑动窗口记忆（MAX_MESSAGES=20，L11,57）
└── local/                     本地模型基建（当前未接主链路）
    ├── LlmEngine/ModelEngine/OnnxEngine/ModelManager/ModelFileManager/HardwareConfig
    └── ChatHistoryManager.kt  本地聊天历史（token 估算）
```

## 2. 装配与调用链（实际代码）

```
ChatViewModel.init（viewmodel/ChatViewModel.kt:60-78）
  collect settingsFlow → activeProvider
  → initAgent(provider)（L80-90）：Cloud/Local AiService + registerTools + InventoryAiAgent
  → registerTools（L92-104）ToolRegistry.clear() 后注册 6 个工具：
     ① QueryInventoryDataTool{ repository.executeDynamicSqlForAi(sql) }   L95-97
     ② GetProductDetailTool(repository)                                  L99
     ③ ExecuteUiActionTool()                                             L100
     ④ OcrCorrectTool(aiService)                                         L101
     ⑤ AnalyzeAbnormalTool(repository, settingsRepository)                L102
     ⑥ GenerateSummaryTool(repository, settingsRepository)                L103

用户发消息 ChatViewModel.sendMessage（L119）
  → InventoryAiAgent.chat(message)（ai/InventoryAiAgent.kt:37）channelFlow
     1. memory.addUserMessage
     2. PromptBuilder.build(session, allSessions)（L43-48）
     3. 循环最多 MAX_TOOL_ROUNDS=5 轮（L53,58）：
        a. aiService.chat(messages, systemPrompt, tools)
        b. 【文本兜底拦截器】LLM 未走 function calling 时，按中文别名表识别工具意图
           （L67-106：如"查询库存/看近效期"→QueryInventoryDataTool），
           正则抓 sessionId，伪造 AiToolCall
        c. 无工具调用 → AgentEvent.Response 结束（L108-112）
        d. 死循环防护：连续两轮工具签名相同 → 截断（L119-127）
        e. 工具并发执行（async，L131-148）：
           ToolPermissionManager.check：BLOCKED→拒绝文本；CONFIRM/AUTO→ToolExecutor.execute
        f. 结果写 memory；ActionResultExtractor.extract 提取 UiAction
           （ShowProductCardList 暂存 pendingCardAction 随最终回复下发，其余即时 send ActionExecuted，L151-161）
        g. round++ + delay(500)
     4. 超轮数后不带 tools 再调一次 LLM 出最终答复（L167-181）
  → ChatViewModel 收集 AgentEvent 更新 ChatUiState（L140-231）
     ActionExecuted → uiState.pendingActions（L225-228）→ ChatScreen 消费执行导航/弹窗
```

## 3. 动态 SQL 查询工具（v2.0 改造核心）

- 背景：《AI Agent 通用化改造开发文档 (v2.0).md》（已归档至 docs/archive/）——用单一动态查询替代硬编码统计工具。**已落地**。
- `QueryInventoryDataTool`（ai/tools/QueryInventoryDataTool.kt:11）参数（LLM 拼装）：`sessionId?`（自动附加 WHERE）、`table`（含 JOIN 表达式）、`select`、`where?`、`groupBy?`、`having?`、`orderBy?`。description（L15-22）明确告知：三张表、13 位毫秒时间戳、禁用字符串日期。
- 执行落点：`InventoryRepository.executeDynamicSqlForAi`（InventoryRepositoryImpl.kt:526-549）→ `SimpleSQLiteQuery` 原样执行 → Cursor → JSON 数组。
- ⚠️ **无 SQL 白名单/只读约束**（P0 风险，见 TECH_DEBT P0-1）。权限级别 AUTO（ToolPermission.kt）。
- 文档中 ReAct 显式 Thought 格式未实现（靠隐式多轮循环）【事实对照：代码无 Thought 文本协议】。

## 4. 其余 5 个工具

| 工具 | 文件 | 功能 | 备注 |
|------|------|------|------|
| `get_product_detail` | GetProductDetailTool.kt:6 | 按 productKey/DI/名称查产品字典 | DAO 查询，安全 |
| `execute_ui_action` | ExecuteUiActionTool.kt:7 | 输出 UiAction 供 UI 执行 | 权限 CONFIRM |
| `ocr_correct` | OcrCorrectTool.kt:8 | 调 AiService 纠正 OCR 文本 | 复用同一 LLM |
| `analyze_abnormal` | AnalyzeAbnormalTool.kt:9 | AnomalyDetector 扫描记录 | 规则引擎非 LLM |
| `generate_summary` | GenerateSummaryTool.kt:12 | 盘点汇总 | |

## 5. AnomalyDetector 规则（ai/tools/anomaly/AnomalyDetector.kt:10-131）

| 异常 | 判定 | 严重度 | 行号 |
|------|------|--------|------|
| 已过期 EXPIRED | 效期 < 今天 | HIGH | 62-70 |
| 近效期 NEAR_EXPIRY | 剩余 ≤ 阈值（设置项 nearExpiryThresholdDays 默认 30） | ≤7 天 HIGH，否则 MEDIUM | 75-86 |
| 空批号 EMPTY_BATCH | batchNumber 空 | MEDIUM | 92-98 |
| 数量差异 QUANTITY_MISMATCH | actualQuantity 与 quantity 偏差 >30% HIGH / 否则 MEDIUM | 104-114 |
| 同库位重复 DUPLICATE_LOCATION | 同 location 不同产品 | MEDIUM | 121-131 |

## 6. UiAction 协议（ai/agent/UiAction.kt:3-10）

`NavigateToRecord(sessionId, recordId)`、`HighlightField(recordId, field)`、`OpenEditDialog(recordId)`、`ShowAnomalies(sessionId, anomalyType)`、`ShowSummary(sessionId)`、`NavigateToProduct(productKey)`、`ShowProductCardList(cards: List<ExpiredProductCardData>)`（L26）。
消费端：`ChatViewModel.uiState.pendingActions`（ChatViewModel.kt:45,225）→ `ui/ai/ChatScreen.kt`；卡片点击 `handleCardActionClick`（L266-275）存在 TODO（recordId=0 未回查）。

## 7. 云端服务 CloudAiService（ai/provider/CloudAiService.kt:22-337）

- `chat`（L30）`streamChat`（L72，SSE 流式解析）`isAvailable`（L139）→ `{baseUrl}/chat/completions`，`Authorization: Bearer`。
- 内部重试 `maxRetries=3`（L319-327）——与同步层 RetryPolicy 无关。
- 工具调用解析：OpenAI `tool_calls` 格式 → `AiToolCall`。
- `supportsTools()` 判定：Agent 用 `!isLocal()`（InventoryAiAgent.kt:188）——本地服务不传工具定义。

## 8. 设置与迁移

- AI 服务商多配置：DataStore `ai_providers_json` + `active_provider_id`（SettingsRepository.kt:38-40,98-100）。
- 旧版单服务商键（ai_provider/qwen_*/deepseek_*）首次启动迁移：`migrateOldProviderSettings`（SettingsRepository.kt:138-189）。
- UI：`ui/ai/AiProviderSettingsScreen.kt`（设置页进入）；聊天页 `ui/ai/ChatScreen.kt` + 组件 ChatMessageItem / MarkdownText（multiplatform-markdown-renderer）/ ProductCardListWidget。
- 聊天历史：`ai/local/ChatHistoryManager.kt`（本地文件历史，含 token 估算）；会话上下文绑定当前盘点任务 `setCurrentSession`（ChatViewModel.kt:106-109）。

## 9. 当前状态与已知问题

- 工作区有大量未提交改动（git status：ai/ 多文件 AM），改造进行中——本档以**当前工作区代码**为基准。
- `ai/local/*`（OnnxEngine/ModelEngine/LlmEngine 等）为本地模型基建，未被 ChatViewModel 主链路使用（LocalAiService 是占位）【事实：initAgent 只 new Cloud/LocalAiService，LocalAiService 无推理】。
- 兜底拦截器的别名表（InventoryAiAgent.kt:70-76）是硬编码中文关键词，工具改名需同步维护。
- AI_MODULE.md 工具清单过时（列了 SearchProductsTool 等已删除工具）；《AI Agent 通用化改造开发文档 (v2.0).md》部分计划未完成（见 §3）。
