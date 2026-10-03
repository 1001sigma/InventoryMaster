# AI 模块 — 待办问题交接清单

> 更新：2026-10-02。供新会话接手；每条含实机证据、根因、修复方向。修复后请回填状态。
> 背景架构见 `docs/AI.md`；铁律见 `AGENTS.md` AI 模块条目。

---

## ISSUE-1 「跳转」意图失败：批准后无跳转 / 模型改走查询并触发循环截断

**状态**：✅ 已修复（2026-10-02，F1-F4 全部落地）

**修复内容**：
- **F1** `QueryRecordsTool` 卡片模式概要：前 3 条重点行追加「记录ID（任务ID N）」，content 末尾
  加跳转用法提示（navigate_to_record 要 记录ID+任务ID；navigate_to_session 只需任务ID）。
- **F2** `UiAction.NavigateToSession(sessionId)` 新增；`ExecuteUiActionTool` 增加
  `navigate_to_session` action_type（枚举+业务校验）；`ChatScreen` pendingActions 消费分支接
  `onNavigateToSession`（回调链 MainActivity→MainScreen→ChatScreen 原已接通）。
- **F3** `ExecuteUiActionTool.description` 重写为「动作→所需参数」对照表，并明确
  "跳转盘库页面/任务N 用 navigate_to_session，不要硬凑 navigate_to_record"。
- **F4** 确认超时事件贯通：`AiEvent.ConfirmTimedOut(requestId)` → `AgentEvent.ConfirmTimedOut` →
  ChatViewModel 按 requestId 清 `pendingConfirm`（弹窗主动关闭，不再"挂着但已失效"）。
  回归测试 `AgentLoopTest.CONFIRM超时按拒绝处理并发出TimedOut事件`（100ms 压缩超时直测 AiRuntime）。

**回归验证**：问"跳转到最近一次库存记录"→ 应看到 navigate_to_record 带完整 ID 被批准并
真实跳转；问"跳转盘库页面"→ 应 navigate_to_session 直达任务详情，不再反问。
✅ 实机验证通过（2026-10-02）。

**现象**（实机截图 + 黑匣子 chat_messages id=323-336，2026-10-02 13:28 前后）：
1. "跳转到最近一次库存记录页面" → `query_records(limit=1, order_by=expiry_desc)` **查询成功**（479 条返 1 条）→ 模型以**完全相同参数**再查 → 同签名闸截断"查询过程陷入循环"；
2. "跳转盘库页面" → `execute_ui_action(action_type=navigate_to_record)` **无任何参数** → 校验失败"需要提供 session_id 与 record_id" → 模型转 `list_sessions` 拿到任务23 → 反问用户"请提供记录 ID"；
3. "跳转 任务23" → `query_records(session_id=23, limit=1)` 成功 → 同签名循环截断（模型拿不到想要的 ID，只能重查）；
4. 用户见过确认弹窗并点击批准，但没有发生任何跳转——该次批准的是一个**缺参的、注定失败的** navigate_to_record。

**根因**：
1. **导航参数与模型可见信息脱节**（主因）：选型 a（明细归卡片，模型只收概要）落地后，
   `QueryRecordsTool` 给模型的概要里**没有记录ID/任务ID**，而 `navigate_to_record` 强依赖
   这两个参数——模型永远拿不到跳转所需信息。`ToolResult.data` 里有完整 ID 但只有程序可见，
   模型看不见。
2. **动作枚举缺口**：`UiAction` 里没有"跳转到任务详情/任务列表"级别的动作
   （现有：navigate_to_record/highlight_field/open_edit_dialog/show_anomalies/show_summary/
   navigate_to_product），"跳转盘库页面、跳转任务23"这类**无 record 意图**只能硬凑
   navigate_to_record，必被参数校验拦下。
3. **CONFIRM 超时 UX 缺陷**（疑似伴生）：30 秒超时静默拒绝后确认弹窗仍留在屏幕上，
   用户再点"执行"是无效操作——造成"弹窗点了但没反应"的观感，需一并修。

**修复方向**（建议按序）：
- **F1** `QueryRecordsTool`：卡片模式概要的**前 3 条重点行追加「记录ID」**，并加一句
  "如需跳转查看某条记录，用其记录ID（与任务ID）调用 execute_ui_action"。
  注意权衡：不能把全量 ID 列表塞回去（会退回各司其职之前的重复问题），只给重点几条。
- **F2** `UiAction` 新增 `navigate_to_session(sessionId)`（跳任务盘点详情）；
  `ExecuteUiActionTool` 增加对应 action_type（如 `navigate_to_session`，只需 session_id）；
  `ChatScreen` 的 pendingActions 消费分支接 `onNavigateToSession`（回调链
  MainActivity→MainScreen→ChatScreen 已通，`MainScreenNav.kt:322-326`）。
  这样"跳转盘库页面/任务23"就有了正确的落点。
- **F3** `ExecuteUiActionTool.description` 补一张"动作→所需参数"对照（navigate_to_session
  只要 session_id；navigate_to_record 要 session_id+record_id，ID 从 query_records 结果获取）；
  `QueryRecordsTool` 卡片模式 content 里同步提示跳转用法。
- **F4** CONFIRM 超时后主动关闭弹窗：`AgentEvent` 增加确认超时事件（或在 ChatViewModel
  对 pendingConfirm 计时），避免"弹窗挂着但已失效"。

**回归验证**：问"跳转到最近一次库存记录"→ 应看到 navigate_to_record 带完整 ID 被批准并
真实跳转；问"跳转盘库页面"→ 应 navigate_to_session 直达任务详情，不再反问。

---

## 已知小项（低优先）

- ~~mikepenz 渲染器（0.28.0）表格无列宽分配~~ **已解决（2026-10-02，两步）**：
  ① 依赖升级 0.28.0 → 0.31.0（该版本起表格单元格 `weight(1f)` 等分列宽，表头与数据共享列网格，
  错位消除；App 侧 6 个渲染器 API 逐一核实兼容，Kotlin 2.0.21 无需升级——发布件 @Metadata mv=2.0.0）；
  ② `ui/ai/AutoWrapMarkdownTable.kt` 经 `markdownComponents(table=...)` 替换默认表格：列宽按
  TextMeasurer 内容测量后比例分配收进屏宽，单元格自动换行（ChatGPT 风格），仅 ≤8 列生效，
  更多列退回库默认（固定列宽+横滚）。库默认仍是"每列 160dp 固定 + maxLines=1"。
- `ai/local/*` 脚手架未接线；端侧路线建议 LiteRT-LM 或局域网 Ollama（见
  `D:\xz\repos\AI Agent\项目分析\` 四份参考项目分析）。
- 确认流状态在内存（CompletableDeferred），进程被杀确认即丢——knotwork 的
  `PendingInteractionEntity` 落库方案可作远期参考（当前 30s 超时策略下影响有限）。
