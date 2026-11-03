# InventoryMaster AI 集成进度报告

## 项目概述

为 InventoryMaster 医疗器械仓库盘存 Android 应用集成 AI 能力，支持自然语言库存查询、智能分析、多 Provider 切换等功能。

---

## 已完成（Phase 1）

### 1. AI 模块架构

采用分层解耦架构：

```
ChatViewModel → InventoryAiAgent → ToolExecutor → Repository → AiService
                       ↓                                    ↓
                 PromptBuilder（session 列表注入）     ToolRegistry（插件化工具）
                       ↓
              ConversationMemory（多轮上下文）
```

### 2. 新增文件（18个）

**AI 核心模块 `ai/`：**

| 文件 | 说明 | 状态 |
|------|------|------|
| `ai/agent/AiAgent.kt` | Agent 接口 + AgentEvent 定义 | ✅ |
| `ai/InventoryAiAgent.kt` | Agent 实现（编排层） | ✅ |
| `ai/tools/AiTool.kt` | 工具接口 | ✅ |
| `ai/tools/ToolRegistry.kt` | 工具注册中心（单例） | ✅ |
| `ai/tools/ToolExecutor.kt` | 工具执行器 | ✅ |
| `ai/tools/SearchProductsTool.kt` | 搜索产品工具 | ✅ |
| `ai/tools/SearchRecordsTool.kt` | 搜索记录工具 | ✅ |
| `ai/tools/CountInventoryTool.kt` | 统计库存工具 | ✅ |
| `ai/tools/GetProductDetailTool.kt` | 获取产品详情工具 | ✅ |
| `ai/provider/AiModels.kt` | DTO 定义 | ✅ |
| `ai/provider/AiService.kt` | AI 服务统一接口 | ✅ |
| `ai/provider/AiProvider.kt` | Provider 枚举（Qwen/DeepSeek） | ✅ |
| `ai/provider/CloudAiService.kt` | 云端 API 实现 + 流式输出 + 模型列表获取 | ✅ |
| `ai/prompt/PromptBuilder.kt` | 动态 Prompt 构建 | ✅ |
| `ai/memory/ConversationMemory.kt` | 会话记忆管理 | ✅ |
| `viewmodel/ChatViewModel.kt` | 聊天 ViewModel | ✅ |
| `ui/ai/ChatScreen.kt` | 聊天界面 | ✅ |
| `ui/ai/ChatMessageItem.kt` | 消息气泡组件 | ✅ |

### 3. 修改文件（8个）

| 文件 | 改动 | 状态 |
|------|------|------|
| `SettingsRepository.kt` | Per-provider 存储：`qwenApiKey`/`qwenModel` + `deepseekApiKey`/`deepseekModel` | ✅ |
| `SettingsViewModel.kt` | Per-provider setter + `fetchModels()` + `modelList` 状态 | ✅ |
| `AppViewModelFactory.kt` | 注入 ChatViewModel | ✅ |
| `SettingScreenNav.kt` | "AI 助手"设置分组 + 模型下拉菜单 | ✅ |
| `HomeScreen.kt` | 底部悬浮 AI 输入框 | ✅ |
| `MainScreenNav.kt` | "ai_chat" 路由 + ChatViewModel 传递 | ✅ |
| `MainActivity.kt` | 创建 ChatViewModel | ✅ |
| `build.gradle.kts` | OkHttp 依赖 | ✅ |

### 4. 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| 多 Provider 支持 | ✅ | 通义千问 + DeepSeek |
| Per-provider 配置 | ✅ | 每个 Provider 独立 API Key + 模型 |
| 模型列表获取 | ✅ | 调用 Provider API 获取可用模型 |
| 模型下拉选择 | ✅ | ExposedDropdownMenu 选择模型 |
| Tool Calling | ✅ | 4 个工具：搜索产品/记录、统计库存、获取详情 |
| 会话记忆 | ✅ | 支持多轮对话上下文 |
| 动态 Prompt | ✅ | 根据当前盘点状态构建 Prompt |
| 流式输出 | ✅ | Flow<StreamChunk> 实时输出 |
| 首页悬浮入口 | ✅ | 小爱同学风格悬浮输入框 |
| 聊天界面 | ✅ | 全屏聊天 + 返回按钮 |

### 5. 修复的问题

| 问题 | 原因 | 修复 |
|------|------|------|
| API Key 401 错误 | 配置问题 | 用户正确配置 API Key |
| Provider 选择写死 QWEN | 代码硬编码 | 改为读取 `settings.aiProvider` |
| DeepSeek Base URL 缺 /v1 | URL 配置错误 | 添加 `/v1` 后缀 |
| tool_calls 解析 NPE | 字段名 `"asString"` 应为 `"arguments"` | 修正字段名 |
| 进入聊天弹窗"连接失败" | `isAvailable()` 误报 | 移除启动时连接测试 |

---

## 已完成（Phase 2）

### 1. 新增文件（11个）

**异常检测模块 `ai/tools/anomaly/`：**

| 文件 | 说明 | 状态 |
|------|------|------|
| `ai/tools/anomaly/AnomalyDetector.kt` | 异常检测引擎（5种异常类型） | ✅ |

**智能分析工具 `ai/tools/`：**

| 文件 | 说明 | 状态 |
|------|------|------|
| `ai/tools/AnalyzeAbnormalTool.kt` | 异常分析工具 | ✅ |
| `ai/tools/GenerateSummaryTool.kt` | 自动生成盘点总结 | ✅ |
| `ai/tools/CompleteProductInfoTool.kt` | 单产品智能补全 | ✅ |
| `ai/tools/BatchCompleteProductsTool.kt` | 批量产品补全（Semaphore 分片） | ✅ |
| `ai/tools/OcrCorrectTool.kt` | OCR 纠错 | ✅ |
| `ai/tools/BatchOcrCorrectTool.kt` | 批量 OCR 纠错（Semaphore 分片） | ✅ |
| `ai/tools/ExecuteUiActionTool.kt` | 隐藏 UI 操作工具（Tool Calling） | ✅ |

**结构化响应模块 `ai/agent/`：**

| 文件 | 说明 | 状态 |
|------|------|------|
| `ai/agent/UiAction.kt` | 结构化操作定义 | ✅ |
| `ai/agent/ActionResultExtractor.kt` | 从工具结果提取 UiAction | ✅ |

### 2. 修改文件（6个）

| 文件 | 改动 | 状态 |
|------|------|------|
| `ai/provider/CloudAiService.kt` | 添加 RetryInterceptor（指数退避重试）+ 优雅降级 catch | ✅ |
| `ai/agent/AiAgent.kt` | AgentEvent 新增 ActionExecuted | ✅ |
| `ai/InventoryAiAgent.kt` | 工具执行后集成 ActionResultExtractor | ✅ |
| `ai/prompt/PromptBuilder.kt` | 添加 7 个新工具描述 + UiAction 使用说明 | ✅ |
| `viewmodel/ChatViewModel.kt` | 注册 11 个工具，处理 ActionExecuted 事件，pendingActions 状态 | ✅ |
| `data/repository/InventoryRepository.kt` + `Impl` | 新增 `getSessionRecords()` 方法 | ✅ |

### 3. 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| 批号/效期异常检测 | ✅ | 5种异常：已过期、近效期、空批号、同库位重复、数量差异 |
| 异常分析工具 | ✅ | AI 主动分析盘存任务异常，返回结构化报告 |
| 自动生成盘点总结 | ✅ | 包含核心指标、厂家分布、异常汇总、建议 |
| 产品信息智能补全 | ✅ | 本地匹配 + LLM 推测，返回带置信度的建议 |
| 批量产品补全 | ✅ | Semaphore(3) 分片限流，防止 API Rate Limit |
| OCR 纠错 | ✅ | 医疗器械专业术语纠错，返回修改内容和置信度 |
| 批量 OCR 纠错 | ✅ | Semaphore(3) 分片限流 |
| 结构化响应 UiAction | ✅ | 基于 Tool Calling，AI 可返回导航/高亮/编辑等操作 |
| 网络容错 | ✅ | 指数退避重试（1s/2s/4s）+ 优雅降级提示 |

### 4. AI 工具总览（11个）

| 工具名 | 说明 | 依赖 |
|--------|------|------|
| `search_products` | 按关键词搜索产品 | Repository |
| `search_records` | 搜索盘存记录 | Repository |
| `count_inventory` | 统计库存概况 | Repository |
| `get_product_detail` | 获取产品详情 | Repository |
| `analyze_abnormal` | 分析异常情况 | Repository + SettingsRepository |
| `generate_summary` | 自动生成盘点总结 | Repository + SettingsRepository |
| `complete_product_info` | 智能补全产品信息 | Repository + AiService |
| `batch_complete_products` | 批量补全产品信息 | Repository + AiService |
| `ocr_correct` | OCR 文字纠错 | AiService |
| `batch_ocr_correct` | 批量 OCR 纠错 | AiService |
| `execute_ui_action` | 执行 UI 操作（导航/高亮/编辑） | Repository（session 列表注入） |

---

## 已完成（Phase 2.1 — execute_ui_action 修复 + 上下文注入）

### 问题诊断

`execute_ui_action` 实测发现没效果，根因是三层断裂：

1. **LLM 不知道上下文** — `setCurrentSession()` 存在但从未被调用，PromptBuilder 永远收到 null session，LLM 无法构造有效 sessionId 参数
2. **ChatScreen 不消费 pendingActions** — ViewModel 累积了 actions 但 UI 没有任何代码读取
3. **ChatScreen 缺少导航回调** — 即使 UI 消费了 actions，也没有导航回调执行跳转

### 修改文件（6个）

| 文件 | 改动 | 状态 |
|------|------|------|
| `data/repository/InventoryRepository.kt` + `Impl` | 新增 `getSessionById()` + `getAllSessionsOnce()` 方法 | ✅ |
| `viewmodel/ChatViewModel.kt` | 新增 `loadSessionById(sessionId)` 方法 | ✅ |
| `ai/prompt/PromptBuilder.kt` | 注入 sessionId + session 列表到 System Prompt | ✅ |
| `ai/InventoryAiAgent.kt` | 注入 Repository，每次对话拉取 session 列表传给 PromptBuilder | ✅ |
| `ui/ai/ChatScreen.kt` | 添加 `onNavigateToSession`/`onNavigateToProduct` 回调 + `LaunchedEffect` 消费 pendingActions | ✅ |
| `ui/home/MainScreenNav.kt` | `"ai_chat"` → `"ai_chat?sessionId={sessionId}"` 可选参数，进入时自动加载 session | ✅ |

### 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| Session 上下文自动注入 | ✅ | 从盘点任务详情页进入 AI 聊天时自动携带 sessionId |
| Session 列表注入 Prompt | ✅ | LLM 可通过任务名称/日期匹配 sessionId，无需用户手动输入 ID |
| pendingActions UI 消费 | ✅ | LaunchedEffect 监听 actions，自动触发导航并 dismiss |
| 导航回调传递 | ✅ | ChatScreen → MainScreen → MainActivity 双层 NavHost 贯通 |
| 双层 NavHost AI 路由 | ✅ | 内层（MainScreen）和外层（MainActivity）均支持 AI 聊天路由 |

### 用户体验改进

| 之前 | 之后 |
|------|------|
| 用户必须手动说 sessionId | 系统自动注入，用户说"6月的任务"即可匹配 |
| AI 回复中包含操作按钮但无响应 | LaunchedEffect 自动执行导航 |
| 首页 AI 入口无上下文 | 从首页进入为全局模式，从任务详情页进入自动带上下文 |

---

## 待实现（Phase 3）

### Phase 3 — Agent 能力

- [ ] 本地模型（MLC LLM）
- [ ] RAG 知识库
- [ ] SOP 问答
- [ ] 语音盘点助手
- [ ] 自动生成报告
- [ ] ERP 联动

---

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material3
- **架构**: MVVM（手动 DI，无 Hilt/Dagger）
- **网络**: OkHttp + Gson
- **存储**: DataStore Preferences
- **AI 协议**: OpenAI 兼容格式（Chat Completions）
- **异步**: Kotlin Coroutines + Flow
- **并发控制**: Semaphore（批量工具限流）

---

## 关键设计决策

1. **ToolRegistry 插件化** — 新增工具只需实现 `AiTool` 接口并注册，无需修改现有代码
2. **Per-provider 存储** — 切换 Provider 时自动切换对应的 API Key 和模型配置
3. **无启动连接测试** — 避免误报，实际错误在用户发消息时捕获
4. **动态 Prompt** — 根据当前盘点任务状态构建更精准的 System Prompt
5. **异常检测在数据层实现** — AnomalyDetector 可复用于 UI 展示（如效期角标）
6. **UiAction 用 Tool Calling** — 利用 LLM 原生结构化输出，比正则解析更稳定
7. **批量工具分片限流** — Semaphore(3) + chunked(3) 防止 API Rate Limit
8. **网络容错** — 指数退避重试 + 优雅降级，应对仓库弱网环境
9. **AI 补全/纠错标注置信度** — 不改实体字段，轻量级追溯
10. **Session 上下文自动注入** — 双层 NavHost 透传 sessionId，PromptBuilder 注入 session 列表，LLM 可按名称/日期匹配任务

---

## 文件变更统计

| 类型 | Phase 1 | Phase 2 | Phase 2.1 | 合计 |
|------|---------|---------|-----------|------|
| 新增文件 | 18 | 11 | 0 | 29 |
| 修改文件 | 8 | 6 | 6 | 20 |
| **总计** | **26** | **17** | **6** | **49** |
