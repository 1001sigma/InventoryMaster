# AI 模块架构文档

> 自动生成于 codegraph 分析，路径：`com.example.inventorymaster.ai`

## 分层结构

```
ai/
├── agent/           # Agent 抽象层
│   ├── AiAgent.kt           # 接口：chat() → Flow<AgentEvent>
│   ├── UiAction.kt          # 密封类：UI 导航动作（跳转、高亮、编辑等）
│   └── ActionResultExtractor.kt  # 从工具结果中提取 UiAction
├── provider/        # AI 服务提供层
│   ├── AiService.kt         # 接口：chat() / streamChat() / isAvailable()
│   ├── AiModels.kt          # 数据模型：AiMessage, AiResponse, AiToolCall, StreamChunk
│   ├── AiProviderConfig.kt  # 配置：baseUrl, model, apiKey, isLocal 等
│   ├── CloudAiService.kt    # 云端实现（OkHttp → OpenAI 兼容 API）
│   └── LocalAiService.kt    # 本地模型实现（TODO: llama.cpp binding）
├── tools/           # 工具系统
│   ├── AiTool.kt            # 工具接口
│   ├── ToolRegistry.kt      # 工具注册表（单例）
│   ├── ToolExecutor.kt      # 工具执行器
│   ├── ToolPermission.kt    # 权限控制（AUTO / CONFIRM / BLOCKED）
│   ├── SearchProductsTool.kt
│   ├── SearchRecordsTool.kt
│   ├── CountInventoryTool.kt
│   ├── GetProductDetailTool.kt
│   ├── AnalyzeAbnormalTool.kt
│   ├── GenerateSummaryTool.kt
│   ├── CompleteProductInfoTool.kt   # 单个产品补全
│   ├── BatchCompleteProductsTool.kt # 批量产品补全
│   ├── OcrCorrectTool.kt           # 单条 OCR 纠错
│   ├── BatchOcrCorrectTool.kt      # 批量 OCR 纠错
│   ├── ExecuteUiActionTool.kt      # UI 导航动作执行
│   └── anomaly/
│       └── AnomalyDetector.kt      # 异常检测引擎（过期/近效期/空批号/重复库位/数量差异）
├── memory/
│   └── ConversationMemory.kt  # 对话记忆（滑动窗口 MAX=20 条）
├── prompt/
│   └── PromptBuilder.kt       # 系统 prompt 构建（角色、上下文、工具策略）
├── local/
│   └── ChatHistoryManager.kt  # 本地聊天历史管理（token 估算）
└── InventoryAiAgent.kt        # 核心 Agent：串联 service + tools + memory
```

## 调用流程

```
用户输入
  → ChatViewModel.sendMessage()
    → InventoryAiAgent.chat(message)
      → PromptBuilder.build() 生成 system prompt
      → AiService.chat() 调用 LLM
      → 若返回 toolCalls → ToolExecutor.execute() → ToolRegistry.get()
      → ToolPermissionManager.check() 权限校验
      → 结果写入 ConversationMemory
      → 循环（最多 5 轮工具调用）
      → 返回 AgentEvent.Response
    → ChatUiState 更新
  → ChatScreen Composable 渲染
```

## 关键文件定位

| 文件 | 路径 |
|------|------|
| Agent 接口 | `ai/agent/AiAgent.kt` |
| 核心 Agent | `ai/InventoryAiAgent.kt` |
| AI 服务接口 | `ai/provider/AiService.kt` |
| 云端实现 | `ai/provider/CloudAiService.kt` |
| 工具注册表 | `ai/tools/ToolRegistry.kt` |
| 权限管理 | `ai/tools/ToolPermission.kt` |
| 系统 Prompt | `ai/prompt/PromptBuilder.kt` |
| 异常检测 | `ai/tools/anomaly/AnomalyDetector.kt` |
| ViewModel | `viewmodel/ChatViewModel.kt:51` |
| UI 界面 | `ui/ai/ChatScreen.kt:57` |
| 导航入口 | `ui/home/MainScreenNav.kt` (route: `"ai_chat"`) |

## 工具清单（11 个）

| 工具名 | 用途 | 权限 |
|--------|------|------|
| `search_products` | 搜索产品信息 | AUTO |
| `search_records` | 搜索盘存记录 | AUTO |
| `count_inventory` | 库存统计 | AUTO |
| `get_product_detail` | 产品详情 | AUTO |
| `analyze_abnormal` | 异常分析 | AUTO |
| `generate_summary` | 盘点报告 | AUTO |
| `complete_product_info` | 单个产品补全 | AUTO |
| `batch_complete_products` | 批量产品补全 | CONFIRM |
| `ocr_correct` | 单条 OCR 纠错 | AUTO |
| `batch_ocr_correct` | 批量 OCR 纠错 | AUTO |
| `execute_ui_action` | UI 导航动作 | CONFIRM |

## 核心类说明

### AiAgent（接口）
定义 `chat(message: String): Flow<AgentEvent>`，所有 Agent 实现此接口。

### AgentEvent（密封类）
- `Thinking` — AI 思考中
- `ToolCallStart` / `ToolCallResult` — 工具调用开始/结果
- `Response` — 最终文本回复
- `ResponseChunk` — 流式回复片段
- `Error` — 错误信息
- `ActionExecuted` — UI 动作执行

### UiAction（密封类）
- `NavigateToRecord(sessionId, recordId)` — 跳转到记录
- `HighlightField(recordId, field)` — 高亮字段
- `OpenEditDialog(recordId)` — 打开编辑对话框
- `ShowAnomalies(sessionId, anomalyType)` — 显示异常列表
- `ShowSummary(sessionId)` — 显示盘点总结
- `NavigateToProduct(productKey)` — 跳转到产品

### AiService（接口）
- `chat(messages, systemPrompt, tools)` — 同步调用
- `streamChat(messages, systemPrompt, tools)` — 流式调用，返回 `Flow<StreamChunk>`
- `isAvailable()` — 服务是否可用
- `isLocal()` — 是否本地模型

### CloudAiService
使用 OkHttp 调用 OpenAI 兼容 API（`/chat/completions`），支持：
- Bearer Token 认证
- 重试机制（最多 3 次，指数退避）
- 流式 SSE 解析
- 工具调用（function calling）解析

### LocalAiService
本地模型占位实现，TODO 接入 llama.cpp binding。当前返回提示信息。

### ToolPermissionManager
- `AUTO` — 自动执行
- `CONFIRM` — 需用户确认（`execute_ui_action`, `batch_complete_products`）
- `BLOCKED` — 阻止执行

### ConversationMemory
滑动窗口对话记忆，最多保留 20 条消息，支持上下文键值对存储。

### PromptBuilder
构建系统 prompt，包含：角色定义、回答格式（Markdown）、当前任务上下文、可用任务列表、工具使用策略、注意事项。

### AnomalyDetector
异常检测引擎，检测 5 类异常：
1. **已过期** — 效期 < 今天（HIGH）
2. **近效期** — 效期 ≤ 阈值天数（HIGH/MEDIUM）
3. **空批号** — 批号为空（MEDIUM）
4. **同库位重复** — 同一库位存放不同产品（MEDIUM）
5. **数量差异** — 账面与实盘差异 > 10%（MEDIUM/HIGH）
