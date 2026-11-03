# AI 助手 UI 优化方案

> 日期：2026-10-02。依据两份外部建议文档（`C:\Users\hp\Desktop\建议1.md`《AI 客户端渲染机制与
> Conversational UI 交互设计全景指南》、`C:\Users\hp\Desktop\建议2.md`《AI 聊天客户端 UI/UX
> 设计规范与功能方案》）逐条映射到本 App 现状后的落地评估。
> 现状架构基准：`docs/AI.md`（v3.5 运行时）；涉及代码均在 `ui/ai/`、`viewmodel/ChatViewModel.kt`。
> 结论先行：两份建议中共 30+ 条目，本 App **已具备 8 项、本次采纳 12 项（P0×4 / P1×5 / P2×3）、
> 明确不采纳 9 项**——不采纳项多为桌面端/通用大模型场景特性，与本 App"医疗器械仓库存单机+局域网、
> 每任务一线程"的定位冲突。
>
> **进度（2026-10-02）：P0 批次（P0-1 ~ P0-4）已全部实现**——ChatScreen 滚动锚定/脱钩 + 回底钮
> （列表改 reverseLayout + 消息稳定 id 做 key）、ChatMessageItem 三点呼吸/操作条/错误重试卡、
> ChatViewModel lastUserMessage + regenerateLast/retryLast。待实机走查（文末"总体验收"）。
>
> **进度（2026-10-03）：P1 首两项已实现**——P1-7 阅读排版基线（MarkdownText baseStyle 行高
> 1.65× + h1-h6 标题非对称外距）与 P1-9 输入框自动增高（AppTextField 透传 minLines，聊天输入
> 1→6 行生长）。编译通过，待实机走查。
>
> **进度（2026-10-04）：P1-8 IME 贴合已实施**——实机证据到位（键盘弹出把整个页面顶上去 = 系统选了
> pan），MainActivity 声明 `windowSoftInputMode="adjustResize"` 修复；未走 imePadding 路线
> （非 edge-to-edge 下为 no-op）。**待实机复验**。
>
> **进度（2026-10-04 晚）：P1-6 核心子项落地（v2 多会话模型）**——任务详情页 AI 入口、顶栏
> 「新建会话」、侧栏长按删除/任务徽标/时间分组；经用户确认推翻「每任务一线程」改为 ZCode 式
> 「任务下多会话」（`t<id>_<ts>`/`g_<ts>` 前缀编码，旧 global 降级为普通自由会话，零迁移）。
> 编译通过，待实机走查。
>
> **🏁 阶段收官（2026-10-05）**——P1-6 后续实机迭代全部完成，用户验收"基本没有问题"：
> ① 侧栏全高修复（verticalScroll 挂 ModalDrawerSheet 本体 → 面板塌成包裹内容高度，滚动必须
> 放内层 Column）+ 全局/任务分段筛选（打开抽屉自动跟随当前线程类型）；② 任务页悬浮 AI 球
> （长按顶栏图标脱落且不松手直接拖、X 轴吸边、拖回顶栏/长按双路收回、rememberSaveable 跨导航
> 存活、仅盘库页内）；③ 快捷问答面板（单击球弹出全宽输入条包球、半透明回答卡自屏幕最顶部随
> 流式向下生长、底部拖拽手柄下拉延伸 ≥100dp 松手进完整对话/回弹、点手柄收起软键盘）。剩余
> 细节后续批次：球条一体感视觉、长答案延伸到输入条后方的表现、示意卡的追问 chips/复制/
> 重新生成操作条。本阶段不含 P1-5/10、P1-6 搜索/置顶、P2（仍为待办）。

---

## 一、现状盘点

| 能力 | 现状 | 对应建议条目 | 状态 |
|---|---|---|---|
| 流式输出（打字机） | `AiRuntime` AnswerDelta/ResponseChunk，仅 FC 能力模型外显 | 建议一 §三 | ✅ 已有 |
| 发送/停止按钮变形 | 生成中发送键变红色 Stop，`stopGeneration()` 取消 | 建议二 §5.3 | ✅ 已有 |
| Markdown 渲染 | mikepenz 0.31.0（M3 主题接入） | 建议二 §4.1 | ✅ 已有 |
| **表格自适应** | 0.31.0 修对齐 + `AutoWrapMarkdownTable`（列宽按内容比例分配、单元格换行、细网格线） | 建议一 §二 | ✅ 已有（本轮完成） |
| 历史会话抽屉 | 多会话模型（任务 `t<id>_<ts>`/自由 `g_<ts>`）+ 长按删除 + 任务徽标 + 时间分组 | 建议二 §2 | ✅ 已有（2026-10-04；搜索/置顶留 P1-6） |
| 思维链折叠卡片 | 工具调用参数/结果折叠展示，设置开关 | — | ✅ 已有 |
| 确认弹窗（CONFIRM） | 30s 超时自动关闭 | — | ✅ 已有 |
| 虚拟化列表 | LazyColumn（建议一 §五"虚拟滚动"） | 建议一 §五 | ✅ 已有 |
| 长按复制 | 任意消息长按复制全文 | 建议二 §4.2 | ✅ 部分（无显式按钮，见 P0-4） |
| **滚动锚定** | `ChatScreen.kt` `LaunchedEffect(messages.size)` **无条件** animateScrollToItem 到底 | 建议二 §4.3 | ❌ 两个方向都错，P0-1 |
| 等待反馈 | 12dp 小转圈 | 建议二 §6.1 | ❌ P0-2 |
| 错误恢复 | 红字气泡，无重试入口 | 建议二 §6.1 | ❌ P0-3 |
| 消息操作条 | 无 | 建议二 §4.2 | ❌ P0-4 |
| 流式渲染性能 | 每 chunk 全量重解析 Markdown | 建议一 §五（增量解析） | ⚠️ P1-5 |
| 会话管理（删/搜/置顶） | 只能清当前线程 | 建议二 §2.2-2.3 | ⚠️ P1-6 |
| 排版行高 | baseStyle 行高 1.65× + 标题上大下小非对称外距 | 建议一 §六.3 | ✅ 已有（2026-10-03） |
| IME 贴合 | manifest `adjustResize` 已声明（原 adjustUnspecified 实机被选为 pan，整页上移） | 建议二 §6.2 | 🔧 已实施（2026-10-04），待复验 |
| 输入框自动增高 | `minLines=1, maxLines=6` 随内容生长，超出内部滚动 | 建议二 §5.1 | ✅ 已有（2026-10-03） |
| 代码块增强 | 库默认渲染，无语言标签/复制钮 | 建议二 §4.1 | ⚠️ P1-10（本场景低频） |

---

## 二、P0 —— 体验硬伤（建议下一批一起做，合计约 1.5~2 天）

### P0-1 滚动锚定与脱钩 + 回底悬浮钮（建议二 §4.3 / 建议一 §六.1）

**现状证据**：`ChatScreen.kt:74-78`——`LaunchedEffect(uiState.messages.size)` 对任何消息条数变化
无条件 `animateScrollToItem(size-1)`。实测两个坏体验：① 用户上滑翻历史时，下一个工具气泡产生
会被**强行拉回底部**；② 流式生成期间文本增量只改内容不改条数，**视图反而不跟随**最新输出。

**方案**：
- 派生 `val isAtBottom = remember { derivedStateOf { listState 距底 < 阈值(约 200px) } }`；
- 锚定逻辑改为两条：`isAtBottom && isProcessing` 时跟随最新条目尾部（`animateScrollToItem` 或对
  末条 `scrollToItem` + 内容增量节流跟随）；用户上滑（isAtBottom=false）立即脱钩，绝不拉回；
- 右下角悬浮回底钮（`ExtendedFloatingActionButton` 或 40dp 圆形 IconButton）：`!isAtBottom` 时淡入，
  流式中有新内容时加呼吸光点；点击 `animateScrollToItem(bottom)` 并重新锚定。

**验收**：流式输出时停在底部则文字平滑跟随；上滑翻历史不被拉回；出现回底钮，点击平滑贴底并恢复跟随。

### P0-2 首字等待骨架（建议二 §6.1 / 建议一 §六.5）

**现状**：`ChatMessageItem.kt` isLoading 气泡仅一个 12dp 小转圈，等待焦虑无缓解。

**方案**：保留气泡形态，内容改为三点呼吸动画（三个圆点 alpha 相位错开的 infiniteTransition，约
1.2s 周期）或 45° 线性 Shimmer 占位条；首 Token 到达时 100ms 交叉淡入真实内容（对应建议一 §六.5）。
TTFT 通常 1~3s，三点呼吸比骨架屏成本低、观感更贴聊天场景。

### P0-3 错误气泡一键重试（建议二 §6.1）

**现状**：`AgentEvent.Error` → 红字 `ChatMessage(role="error")`，用户只能手抄问题重发。

**方案**：
- `ChatViewModel` 记录 `lastUserMessage: String?`（sendMessage 时更新，成功失败都记）；
- error 角色消息渲染为浅红边框卡片 + 「重试」按钮（建议二 §6.1 的样式），点击 =
  `sendMessage(lastUserMessage)`；
- 注意点：出错轮次 memory 已写入兜底文案（`AiRuntime` catch 分支 `closeTurn(...)`），重发是**新
  一轮**，不是删除旧轮——上下文里会多一轮失败记录，模型反而知道刚才失败过，属可接受行为；文档化
  该取舍即可，无需动 memory。
- 错误文案透传 `AiServiceException.kind`（超时/限流/断网给出不同提示，429 类限流附"请稍候"）。

### P0-4 消息操作条：重新生成 + 显式复制（建议二 §4.2）

**现状**：仅长按复制（无发现性）；无重新生成。长按已被用于复制，操作条是标准解法。

**方案**：
- 最终 `Response` 气泡下方淡入一行极简图标条：复制（点击变 ✓「已复制」）、重新生成（↻）；
- 重新生成实现要点：与 P0-3 相同走"新一轮重发 lastUserMessage"；**不**实现版本分支（1/2 切换，
  建议二 §4.1/4.2 的完整版）——那需要 memory 支持轮次级撤销 + 多版本存储，收益低；旧回复保留在
  历史里即可对比；
- 点赞/点踩：本 App 无后端反馈通道，降级为本地记录（P2-13，见下）。

**验收**：AI 回复下方可见复制/重新生成；重新生成后新旧两条回复都在；复制有成功反馈。

---

## 三、P1 —— 规划项（每项 ≤1 天）

### P1-5 流式渲染节流（建议一 §五"增量解析"）

**现状**：`ChatViewModel` 每个 `ResponseChunk` 都 `messages.dropLast(1) + copy` 触发该气泡
`MarkdownText` 全量重解析（mikepenz 每 caret 变更重建 AST）。长答案 + 低端机会掉帧。

**方案**（按成本递增选一）：
- a. 最简：`ResponseChunk` 收集侧用 `conflate()`/80ms 采样后再更新 state（UI 层无感知）；
- b. 更优：流式期间该气泡渲染**纯文本**（Plain Text + 光标符），`Completed` 到达时切换 Markdown
  渲染（建议一 §四的"完成后富渲染"思路 + 100ms 交叉淡入）。注意铁律④：JSON 协议模型流式正文不
  外泄——此方案仅适用于 FC 模型（与现行为一致）。

### P1-6 会话管理增强（建议二 §2.2-2.3 裁剪版）——核心子项已实现 2026-10-04（v2 多会话模型）

> 2026-10-04 修订：经用户确认**推翻「每任务一线程」铁律**，改为 ZCode 式多会话——任务=项目、
> 会话=项目下多条对话。关键前提：任务线程从无 UI 入口（死路径）且无存量数据，threadId 格式
> 得以零迁移切换。旧「会话 Fork」不采纳条目不受影响——那是同一对话内的版本分支，多会话是并列的新对话。

**已实现**（按 入口 → 新建 → 侧栏 顺序）：
- **线程模型 v2**：任务会话 `t<任务ID>_<创建时间戳>`（每任务 N 条）、自由会话 `g_<创建时间戳>`；
  旧 "global" 降级为普通自由会话（命名规则统一，历史数据零迁移）；旧纯数字任务线程 ID 兼容解析。
  编解码集中在 `ChatViewModel` 的 `ChatThreadIds`；`chat_messages` 表结构不动。
- **任务详情页入口**：InventoryScreen 顶栏 AutoAwesome 图标 → `ai_chat?sessionId=N` →
  `enterFromSession`：落到该任务最近活跃会话，一条都没有则现场创建；agent 绑定任务
  （PromptBuilder 任务注入从此真正生效——原机制是死路径，全 App 唯一入口硬编码 sessionId=0）。
  ⚠️ 路由托管教训（2026-10-04 实机闪退修复）：`ai_chat` 必须挂**外层** NavHost（MainActivity）——
  盘库详情页在外层路由 `inventory/{sessionId}` 上，跨层看不到内层图的路由，第一版挂 MainScreen
  内层图导致点击入口即抛 `IllegalArgumentException`；主页入口也一并改走外层回调，ChatScreen
  全 App 单一挂载点。
- **着陆规则**：主页（sessionId=0）→ 最近活跃**自由会话**，绝不自动绑任务（防沉默上下文继承）；
  侧栏点击 → `openThread` 按线程类型绑定。工具层不受线程限制（`query_records` 缺省查全部任务，
  自由会话可点名任务查询——`list_sessions` 描述已内置"先取任务ID再查询"教程）。
- **顶栏**：删除按钮 → 「新建会话」（+ 全局可用：任务语境=该任务下新对话，自由语境=新自由会话）；
  标题跟随线程（任务会话显示任务名，其余显示会话命名）。
- **统一命名**：所有会话标题 = 首条用户消息首行截断 20 字，无消息 = 「新对话」；DAO
  `getFirstUserMessages`（GROUP BY + MIN(sortIndex) 裸列约定）一次查全。空会话不落库，切走即弃。
- **侧栏**：NavigationDrawerItem 不支持长按 → Surface+combinedClickable 自绘行；长按 →
  「删除会话」红字菜单 → 确认弹窗（注明仅删聊天记录、不动任务数据）；删当前线程走
  cancel/join/清内存保护后切最近剩余线程（全删空则新开自由会话）；任务徽标（限宽 96dp）+
  时间分组（今天/7天内/更早，`getThreadSummaries` 已按 lastTime 降序）。

**未做（仍留 backlog）**：搜索（content LIKE）、置顶、Snackbar 撤销/软删除（本次降级为确认弹窗）、
侧栏按任务分组折叠（先平铺+徽标观察真实使用再定）。

**2026-10-05 实机迭代补充（阶段收官批）**：
- **侧栏全高修复**：`ModalDrawerSheet` 内部是 `modifier.sizeIn().fillMaxHeight()`，把
  `verticalScroll` 传给它会把无界高度传入、fillMaxHeight 失效 → 面板塌成包裹内容高度（"半截
  抽屉"）。滚动一律放 Sheet 内层 `Column(fillMaxSize().verticalScroll())`；
- **全局/任务分段筛选**：M3 `SingleChoiceSegmentedButtonRow`，按 `ChatThreadInfo.isTask` 过滤；
  打开抽屉（History 图标）时页签自动跟随当前线程类型；
- **悬浮 AI 球**（`InventoryScreen` 私有组件，仅盘库页内）：顶栏图标 `detectDragGesturesAfterLongPress`
  长按脱落且**同一手势不松手直接拖**（拖动期间图标保留但 `graphicsLayer` 全透明——立即隐藏会
  销毁手势节点中断拖拽流）；松手按移动距离区分"仅脱落"与"吸边/收回"；`rememberSaveable` 存
  active/offset（跨导航存活）；吸边 150ms 动画由父级 `Animatable` 驱动，拖拽起手先 cancel；
- **快捷问答面板**：单击球 → `enterFromSession(sessionId)` 绑定任务线程 + 球在下半屏先抬到
  45% 线；输入条全宽、垂直中心锁定球心（球由父级**最后叠绘**在条端留白区）；回答卡自窗口
  顶端 8dp 起**随流式向下生长**（上限=输入条上沿，超出转内部滚动；`verticalScroll` 必须挂
  Surface 内层 Column，挂本体会破坏 shape 裁切）；回答 = `uiState.messages` 最后一条用户消息
  之后的 assistant/error 气泡拼接（与完整聊天页同源同线程）；
- **底部拖拽手柄**：卡片内固定于底边（内容区 weight + 手柄 24dp），`detectVerticalDragGestures`
  0.6 阻尼使卡片**向下延伸**（顶端不动，非整体平移），延长区渐显提示；松手 ≥100dp 跳完整
  聊天页、否则 150ms 回弹；单击手柄收起软键盘。早期的 nestedScroll"滚到顶再拉"方案已整体移除；
- **悬浮球包裹输入条的视觉一体感**用户评价不高，已搁置待后续批次。

### P1-7 阅读排版基线（建议一 §六.3）——✅ 已实现 2026-10-03

`MarkdownText.kt` 的 baseStyle 设 `lineHeight = fontSize × 1.65`（bodyLarge 16sp → 26.4sp）；
标题采用上大下小非对称 margin（库 typography 已分 h1-h6，补 spacing 即可）。一处改动全局生效。

**落地记录**（`ui/ai/MarkdownText.kt`）：
- `baseStyle.lineHeight = fontSize × 1.65f`（随主题字号缩放），text/paragraph 共用；code 样式维持
  bodyMedium 默认行高（等宽代码块 1.65 过于松散）；
- 标题间距：库 `MarkdownPadding` 只有统一 `block`(默认 2dp) 字段、`MarkdownHeader` 自身零间距，
  无 per-heading 配置——唯一切入点是组件覆盖：`markdownComponents(heading1..6 = headerComponent(level))`
  以 `Box(PaddingValues(top, bottom))` 包 `MarkdownHeader`。外距取值（上/下）：h1 12/4、h2 10/4、
  h3 8/4、h4-h6 6/2，叠加库的 2dp 块前间隔后保持 上 > 下；setext 标题未覆盖（模型输出几乎不产生）。

### P1-8 IME 贴合核验（建议二 §6.2）

**现状**：MainActivity 未开 edge-to-edge，manifest 无 `windowSoftInputMode`（=adjustUnspecified，
系统自行选择 resize 或 pan）；`ChatScreen` 输入行无 `imePadding`。实机表现为"贴合"还是"上移遮盖"
未验证。

**方案**：先实机核验；若输入框被键盘遮挡或跳动，标准修法 = ChatScreen 根部 `Modifier.imePadding()`
+ `imeNestedScroll`，消息流 `LazyColumn` 的 contentPadding 底部随 IME 变化，保证最后一条消息悬在
输入框上方。这是独立小改动，但**必须先有实机证据再动手**。

**实机证据与落地记录（2026-10-04）**：
- 证据（用户实机）：键盘弹出后**整个页面被顶上去**——非"遮挡"而是 **pan 平移**，与原方案预设的
  imePadding 修法（针对遮挡/跳动）不是同一症状；
- 根因：MainActivity 未开 edge-to-edge 且 manifest 无 `windowSoftInputMode`（=adjustUnspecified），
  系统对聊天页自行选择了 pan；
- 修复：`AndroidManifest.xml` MainActivity 声明 `android:windowSoftInputMode="adjustResize"`
  （App 全局生效：键盘弹出改为压缩窗口，输入行贴键盘上方，LazyColumn 随之缩短，页面不再平移）；
- **不采用** imePadding/imeNestedScroll：非 edge-to-edge（DecorFitsSystemWindows=true）下
  `WindowInsets.ime` 被框架消费，`imePadding` 是 no-op——该路线仅在将来开启 edge-to-edge 时才需要；
- 复验点：聊天页输入贴键盘、历史列表可滚动到最新消息；盘点详情搜索、产品库、设置各输入框同受
  adjustResize 影响应一并过一遍；Dialog 内输入（独立窗口）不受影响。
- **2026-10-08 Edge-to-Edge 全面重构落地与避坑记录**：
  - **坑点 1：输入栏被软键盘覆盖**
    - 现象：开启 `enableEdgeToEdge()` 后，ChatScreen 和悬浮胶囊在键盘弹起时直接沉入键盘后方。
    - 根因：非 Edge-to-Edge 下依靠 `adjustResize` 压缩物理窗口；沉浸式（`DecorFitsSystemWindows=false`）下整个 Activity 窗口恒 100% 满屏，若只给输入栏挂 `navigationBarsPadding()`，系统键盘高度（IME）未被感知，输入框留在屏幕底部被挡死。
    - 修复：采用动态计算 `val bottomInset = maxOf(imeBottom, navBottom)`。键盘收起时保持手势条安全区，键盘弹出时跟随软键盘高度精确抬升。
  - **坑点 2：Padding 累加导致悬空空隙（Gap）**
    - 现象：在部分页面同时挂 `.calculateBottomPadding()` 和 `.imePadding()`，键盘弹出后输入框悬浮在键盘上方很远，留有一大块空白。
    - 根因：Compose Modifier 的 Padding 是物理累加的；而软键盘弹起时在屏幕上已经覆盖了导航栏，二者叠加会导致高度重复计算。
    - 修复：Scaffold 明确指定 `contentWindowInsets = WindowInsets.statusBars`（不让 Scaffold 重复注入底部 padding），页面底部单点使用 `maxOf(ime, nav)` 统一控制。
  - **坑点 3：产品库搜索时底部导航栏被键盘顶起**
    - 现象：产品库等带全局导航的页面输入时，底部 NavigationBar 被键盘顶在半空中。
    - 修复：`MainScreenNav.kt` 监听键盘高度 `rememberIsKeyboardOpen()`，软键盘弹出时平滑隐藏全局底部导航栏，收起时自动恢复，产品库列表在软键盘上方占满可视空间。
  - **坑点 4：快捷输入胶囊拖拽手柄连带整体平移**
    - 现象：胶囊上方上拉横条被向上拖动时，`barPullPx` 作用到了胶囊 Surface 本体，导致整个输入框跟着被拉离原位。
    - 修复：解耦胶囊与拖拽条位移，胶囊 Surface 保持稳固贴底/贴键盘；仅拖拽小横条随手势向上被拉起，并实时渐显「↑ 继续上拉进入完整对话」/「↑ 松开进入完整对话」的引导文本与主色高亮反馈，松手超过 100dp 阈值平滑进入完整对话，未达阈值弹簧平滑复位。

### P1-9 输入框自动增高（建议二 §5.1）——✅ 已实现 2026-10-03

**现状**：`AppTextField(maxLines=3)` 固定三行后内部滚动，已是"向上生长"的近似。
**方案**：`maxLines` 随内容动态（1 → 6 行封顶，约 200px），`minLines=1`；发送键逻辑不变。约 5 行代码。

**落地记录**：`AppTextField` 新增 `minLines` 参数透传给 `OutlinedTextField`（默认 1，既有调用方
不受影响）；`ChatScreen` 聊天输入改 `minLines=1, maxLines=6`——Compose 原生行为即随内容自动生长
至 6 行后转内部滚动，无需按内容手动计算 maxLines。material3 1.3.0（BOM 2024.09.00）已支持 minLines。

### P1-10 代码块增强（建议二 §4.1 降级）

本 App 的 AI 场景（库存查询/盘点分析）代码块低频，降级实现：语言标签 + 右上角复制按钮 +
超 10 行折叠"展开"。用 `markdownComponents(codeFence=...)` 覆盖，参考本轮 `AutoWrapMarkdownTable`
的接入方式。若后续确认无代码场景可直接不做。

---

## 四、P2 —— 可选/远期

- **P2-11 聊天内模型切换器**（建议二 §3.1）：设置页已有多 provider（`ai_providers_json` +
  `active_provider_id`），ChatViewModel 对 settingsFlow 已响应式重建 agent——聊天顶栏加下拉胶囊
  写 `active_provider_id` 即可，切换后在消息流插一条微型系统提示"已切换至 X"。
- **P2-12 用户消息编辑重发**（建议二 §4.1 简化版）：长按用户消息改"编辑并重发"（预填输入框，
  不做 <1/3> 版本树）。
- **P2-13 导出与本地反馈**（建议二 §3.2/4.2 裁剪）：会话导出 Markdown 文件（SAF 写 Downloads）；
  点赞/点踩仅本地 DataStore 记录（无后端）。

---

## 四A、输出格式规则库（✅ 已落地 2026-10-02，独立于 P0/P1/P2 排期）

外部输入：一份《意图到格式映射 + 反向约束 + System Prompt 模板》建议。评估结论：**采纳并独立成库**
（`ai/prompt/OutputFormatRules.kt`，`OutputFormatRules.render()` 由 PromptBuilder 组装进系统提示词），
但做了三处领域适配，未照抄通用模板：

| 通用模板 | 本 App 适配 | 理由 |
|---|---|---|
| 公式用 LaTeX（$$...$$） | **反转为禁用** LaTeX/公式语法 | mikepenz 渲染器不支持公式，输出 $$ 会原样裸露 |
| 表格用于"数据对比" | 明确包含"2 个以上字段的记录并列（库存明细/效期对比）" | 查询结果是本 App 主输出形态，端侧 AutoWrapMarkdownTable 已保证渲染质量 |
| （无） | 补充分隔线（---）约束 | 聊天流里分隔线易与消息边界混淆 |

职责边界（v3 各司其职设计不变）：规则库只管**全局排版**（三形态映射：纯文本/结构化富文本/特殊结构，
加反向约束——短答禁标题、单维清单禁表格、非代码禁代码块、数字单位禁公式）；工具相关呈现规则仍在
各工具 description/content 内（如"勿逐条罗列"）。原 PromptBuilder 回答规范第 1 条
（"多项数据用表格或列表"，正是鼓励格式泛滥的旧规则）已被规则库取代。

**待观察**：规则对输出形态的实际约束力需实机验证（尤其"寒暄不再带标题/列表""库存明细仍正常出
表格"两点）；若小模型仍格式泛滥，下一步可把反向约束提权到工具轮的 ThinkingText 纠正路径。

---

## 五、明确不采纳（避免过度设计）

| 建议条目 | 不采纳理由 |
|---|---|
| LaTeX/公式渲染（§4.1） | 医疗器械库存场景无数学公式，KaTeX 集成成本高收益为零 |
| 多模态附件/拖拽/粘贴截图（§5.2） | 无多模态模型；拍照识码走 batchscanner 既有链路，职责不混 |
| 桌面快捷键体系（§6.3） | 纯移动端 App；移动端"回车=换行、发送键发送"现状已是建议二 §5.1 的目标行为 |
| 三栏响应式/常驻侧边栏（§1） | 手机竖屏为主，抽屉已够；平板适配属远期通用议题 |
| 会话 Fork/分支（§3.2） | 与"每任务一线程"的会话模型冲突（threadId 绑定任务上下文），分支会破坏按任务溯源（**注：2026-10-04 起任务已放开为多会话，见 P1-6 v2；本条否的"同一对话内版本分支"仍不采纳**） |
| 归档箱（§2.3） | 会话生命周期跟随任务，任务本身已有归档体系，二次归档冗余 |
| 自动重命名（§2.2） | 标题=任务名（任务列表可读名称），无"新会话"默认名问题；仅 global 线程固定标题，可在 P1-6 顺手用首条消息截断命名，不单列 |
| Token/上下文指示器（§3.2） | OpenAI 兼容接口 usage 字段各厂商不齐；对仓管用户无决策价值 |
| TTS 朗读（§4.2） | 仓库高噪声场景耳机使用率低，价值存疑；Android TTS 集成成本低但维护面+1 |
| `/`快捷指令、`@`智能体（§5.1） | 工具集固定 7 个、面向单一领域，模板库收益低；先观察真实提问模式再定 |

---

## 六、设计规范基线（开发对齐用，提炼自两份建议）

- **行高**：正文 1.6~1.75 倍字号；标题上边距 > 下边距（非对称）。
- **滚动**：跟随判定阈值 = 距底 200px 内视为"在底部"；回底钮出现阈值 = 脱离底部超过半屏；
  用户上滑即脱钩，永不强拉。
- **动效**：等待三点呼吸周期 ~1.2s；首内容交叉淡入 ~100ms；回底钮淡入 ~150ms。
- **深色模式**：随 MaterialTheme（App 已有动态取色/深色），禁用纯黑 #000000 纯白 #FFFFFF 高反差
  （Compose 默认 surface 方案已合规，核验即可）。
- **表格**（已落地，参数留档）：列宽下限 44dp、单元格内边距 8dp、换行布局上限 8 列、分隔线 0.5dp。
- **错误卡片**：浅红边框 + 具体错误类别文案 + 重试按钮。
- **性能红线**：流式期间单帧 UI 更新 ≤ 12/s（P1-5 节流后），长答案（>2000 字）不掉帧。

## 七、实施顺序建议

1. **第一批（P0，~2 天）**：P0-1 滚动 → P0-2 骨架 → P0-4 操作条 → P0-3 重试（P0-3 依赖
   lastUserMessage，与 P0-4 的 regenerate 共用）。
2. **第二批（P1，按需排期）**：P1-7（一行）→ P1-9（五行）→ P1-5（性能，长会话用户出现后再做也行）
   → P1-6 → P1-8（先核验）→ P1-10。（P1-7 / P1-9 已于 2026-10-03 完成；P1-8 于 2026-10-04 按实机
   证据实施 adjustResize，待复验）
3. **P2** 视真实使用反馈决定。

**总体验收**：P0 完成后完整走查——发送问题 → 三点呼吸 → 流式跟随（期间上滑不拉回、回底钮可用）
→ 回复完出现操作条 → 复制/重新生成 → 制造一次断网 → 红卡错误 + 重试成功。

---

## 八、产品卡片与操作栏闭环重构（2026-10-15）

### 1. 操作栏下移与 reverseLayout 闭环修复
- **痛点**：在聊天消息流中，若卡片与操作栏分别渲染，受 `LazyColumn(reverseLayout = true)` 影响，操作栏（复制/耗时/模型/重新生成）容易横插在 AI 回复文字与卡片之间，破坏对话视觉连贯性；
- **解法**：卡片列表直接嵌入 `ChatMessageItem` 内部，按 `[MarkdownText 正文] -> [ProductCardListWidget 卡片列表] -> [MessageActionBar 操作条]` 线性排布。操作栏恒定位于卡片正下方，彻底实现单条消息完整闭环。

### 2. 现代卡片组件（ProductCardListWidget）
- **多维标签流**：
  - 任务归属徽章：`任务 #1` 或具体任务名（PrimaryContainer）；
  - 存放库位徽章：冷藏库、常温库等（SecondaryContainer）；
  - 查验状态胶囊：绿色浅底深字「已查验」 / 橙色浅底深字「未查验」；
  - 效期倒计时警示：计算距当前天数，过期显示「已过期 X天」（ErrorContainer），近效期显示「剩 X天」（Amber 警示）。
- **材质与排版**：双层圆角 Surface、厂家与规格副标题中点连接、批号与高亮数量主次分明。

### 3. 现代全字段卡片详情弹窗（CardDetailDialog）
- 顶部大标题 + 规格副标题 + 业务标签栏；
- 双核心指标卡片：库存数量（Primary 色）、有效期至（Secondary 色）；
- 全字段网格列表：生产厂家、存放库位、生产批号、生产日期、物料编码、DI/编码、所属任务、备注信息，支持内部平滑滚动（`verticalScroll`）防止溢出屏幕。

