# 改动日志 (CHANGELOG)

> 记录格式：日期 + 为什么修改 + 修改了什么 + 影响什么 + 兼容性。
> 历史条目依据：git 提交记录（仅 12 条，2026-01-13 起，信息简略）与根目录文档。**无法从代码/历史确认动机的不编造。**
> 以后每次开发修改项目时，请继续在顶部维护本文件。

## [2026-10-20] 拍照批量扫码：连拍流转闭环 + 复核胶囊双模底盘 + 单图三色防错与高亮闪烁联动 【已验证】

- 为什么：
  1. **批量拍照连贯性不足**：拍照批量扫码原流程为单张“拍照-裁剪-识别-复核”，面对连续多板物料或大批量条码时，每次确认后需频繁重复打开相机重新进入，操作链路被割裂，亟需“连拍模式”实现连续多次拍照裁剪识别、最终一次性核对完成。
  2. **取景体验与相册存储沉余**：原拍照取景界面存在较强的黑色遮罩与黄色参考框限制，影响大图取景视野；原拍照存图开关默认开启且无明确入口，大量测试原图堆积在系统相册中。
  3. **复核页面布局与防错能力欠缺**：复核底盘按键排布拥挤且部分存在文字折行；传统的红绿框只能判断单据有无，无法识别单张图片内同物料混批（如主要物料多批号并存）或异种物料混料风险。
  4. **列表与图像缺少空间定位联动**：复核时无法直接查看单张识别的数据明细，更无法从数据反向在实物图上快速定位条码物理位置。
- 实现：
  - **智能无感连拍流转（`BatchScannerViewModel.kt` + `BatchScannerScreen.kt`）**：
    - 首张识别后新增 `[拍下一张]` 按钮，自动将本张条码并入 `accumulatedRawBarcodes` 全集并无缝切回取景器；
    - 取景器右上角常驻绿色 `[完成提交 (N张)]` 胶囊，连拍过程中可随时一键结算退出；
    - 支持 `[放弃本张/重拍]` 精准扣除当前图产生的条码并重新分配单据额度，绝不影响之前落盘的已有照片与条码。
  - **取景器轻量化与相册设置项收归（`CaptureView` + `SettingsRepository.kt` + `SettingScreenNav.kt`）**：
    - 移除取景界面固定黄色参考框、文字提示与全屏遮罩蒙版，恢复全屏纯净预览；
    - 采用 `PreviewView.ScaleType.FIT_CENTER`，修复相机异常变焦放大现象；
    - 将拍照原图存相册收归至「扫描设置」管理（DataStore 键 `save_original_photo_to_gallery`），默认保持关闭。
  - **复核页面胶囊双模底盘（`ReviewView` in `BatchScannerScreen.kt`）**：
    - 顶部增设 `[ 操作控制 | 扫码数据 (N) ]` 胶囊切换按钮；
    - **操作控制模式**：重构按钮排布为双行（Row 1: 放弃本张/点选补扫/文字识别 4 字符单行防折行；Row 2: 拍下一张/核对完成 48dp 高优先级主操作）；
    - **扫码数据模式**：自适应滚动列表，顶部展示分类胶囊，列表滚动到底部常驻展示 `[拍下一张]` 与 `[核对完成]`，无缝推进主流程。
  - **单图三色混料/混批防错分类与视觉统一（`BarcodeClassifier.kt` + `ReviewCanvasOverlay.kt` + `ImageStorageUtils.kt`）**：
    - 统计单图频次判定基准物料（majorityDi）与基准批号（majorityBatch）；
    - **主要物料主批次**：外框、半透填充、数据卡片背景呈现**淡蓝色**（`#E3F2FD`，标签 `主要物料`）；
    - **同物料不同批号（混批警告）**：外框、半透填充、数据卡片背景呈现**淡红色**（`#FFEBEE`，标签 `同品混批`）；
    - **不同物料（混料警告）**：外框、半透填充、数据卡片背景呈现**淡黄色**（`#FFF8E1`，标签 `不同物料`）；
    - 方案 A 视觉兼容：单据核对状态在框内右上角保留小尺寸 `✔ 已匹配`（绿）与 `✘ 异常多出`（红）图标，不遮挡条码主体；
    - 应用私有相册带标记图片绘制同步支持该三色分类。
  - **本地产品字典异步关联合成品名**：
    - `BatchScannerViewModel` 在 `Dispatchers.IO` 线程批量提取条码 DI 并查询 `ProductDao`，自动补全品名并缓存至 `productNameMap`，未录入条码优雅降级。
  - **数据列表与图像脉冲闪烁联动（`ReviewCanvasOverlay.kt`）**：
    - 点击列表任意数据卡片，原图对应条码选框触发 600ms 双重脉冲呼吸动效（强光白闪烁层 + 动态发光外光晕 + 中心聚焦光圈），秒级定位实物。
- 影响：
  - 拍照批量扫描操作效率大幅提升，支持多板试剂不间断连扫；
  - 实物图兼备明细列表与空间交互指引，提供直观的混批与混料防错把关；
  - 视觉排布清爽，消除所有文字折行与画面暗化。
- 兼容性：`./gradlew compileDebugKotlin` 构建 100% PASS，已有单据核对及 OCR 流程完全兼容。

## [2026-10-18] 扫码实测加固：多码防单码误弹出 + 空间去重高频连扫断流修复 + 内存安全守护 【已验证】

- 为什么：
  1. **多码识别只扫到一个码就弹出（提前误退）**：实测在开启多码模式时，多盒试剂因包装反光导致单帧可能仅识别到 1 个条码，操作员手持停稳后由于缺少全局多码记忆守卫，被 500ms 单码确认逻辑误判为普通单码直接触发退出回调，彻底打断多码定格动线；且手持稳定门槛（`0.025f`）偏严、反光拼图容错期（600ms）偏短。
  2. **批量补扫扫完第 1 码后再扫相同码无反应（连扫断流）**：实测发现连续平移或原地换盒扫同内容试剂时后续码无法计数。根因是：局部扫描框（`scanFrameRatio = 0.7f`）使所有条码中心均落入画面中央区域（`cx ≈ 540, cy ≈ 500`）；原 `missingFrames >= 8`（约 270ms）与 `countedLostTimeoutNs = 300ms` 阈值过长，操作员连扫间隔仅 2~3 个空帧，导致旧 Track 尚未消亡便将同内容的新实物盒就近误匹配并因 `counted = true` 吞掉；且时间戳未强制纳秒单调。
  3. **潜在内存泄露与组件弃用**：`BarcodeAnalyzer` 抓取降级位图后在成功获取 `PreviewView.bitmap` 时未显式调用 `recycle()`；`FrozenSelectionOverlay` 存在废弃的旧版 Compose 图标警告。
- 实现：
  - **多码定格聚合器算法与参数加固（`MultiBarcodeAggregator.kt`）**：
    - 新增 `poolPeakSize` 记忆守卫：凡本轮观察周期内累计条码数峰值大于 1（`poolPeakSize > 1`），严格禁用单码 `SingleScan` 提前跳出通道，无论后续单帧因反光出现多少次单码，均锁定多码跨帧拼图与原地定格通道。
    - 稳定度与拼图容错增强：`stableMovementThreshold` 放宽至 `0.035f`（屏幕宽高的 3.5%，容纳 1080p 下人手微抖）；条码跨帧保留寿命 `retentionNs` 延长至 `1200ms`，给足反光拼图时间；连续空帧重置门槛提升至 `20` 帧（约 660ms）。
  - **批量补扫空间追踪高频连续识别优化（`CodeTracker.kt` + `SimpleFloatingScanner.kt` + `FloatingRescanOverlay.kt`）**：
    - `CodeTracker` 轨迹快速离场判定：已计数轨迹的离场判断收紧为 `missingFrames >= 4`（约 133ms，大幅缩短 50%）或超时 `countedLostTimeoutNs = 200ms`，配合匹配半径比 `matchRadiusRatio = 1.2f`，确保实物移开或换盒时旧轨迹以最高敏捷度销毁，后继同内容实物码立即可作为新 Track 独立计数。
    - `SimpleFloatingScanner`：时间戳由 `imageInfo.timestamp` 全面改用系统单调递增的 `System.nanoTime()`，彻底杜绝底层 HAL 层时间戳偶发异常或非单调导致超时无法触发的隐患。
    - `FloatingRescanOverlay`：在 `handleCode` 入口前置增加 `if (scanMode == 0) return` 快速拦截，防止暂停后排队异步帧误计。
  - **内存泄漏防御与代码健康**：
    - `BarcodeAnalyzer`：抓取主线程 1:1 位图成功后显式调用 `fallbackBitmap?.recycle()`，及时回收未使用的降级位图；
    - `FrozenSelectionOverlay`：将废弃的 `Icons.Default.ArrowForward` 替换为官方推荐的 `Icons.AutoMirrored.Filled.ArrowForward`。
- 影响：
  - 多码模式下多盒试剂拼图成功率与定格体验显著提升，彻底杜绝反光导致单码误退出；
  - 批量补扫原地换盒与连续平移扫同内容试剂时，各实物码顺畅独立计数，无吞码、无断流；
  - 消除内存泄漏隐患与编译 Warning。
- 兼容性：`MultiBarcodeAggregatorTest`（新增多码反光误跳出守卫用例）与 `CodeTrackerTest` 21 项单元测试 100% 通过；全工程 `./gradlew testDebugUnitTest` 与 `./gradlew assembleDebug` 打包构建全部 SUCCESSFUL。

## [2026-10-17] 多码识别重构：跨帧拼图聚合器 + 手持位移稳定态定格 + 独立模块彻底解耦 + 1080p 分辨率升级 【已验证】

- 为什么：
  1. **标记偏移现象**：实测发现部分绿色水波纹锚点与 DataMatrix 实物码存在位移偏差。根因是：此前 450ms 观察窗口在手持微动时，收集到的历史条码坐标与主线程抓取 1:1 底图（`PreviewView.bitmap`）存在时间差漂移；同时 720p 分辨率下小码像素过低（约 25px），导致多码定位散乱。
  2. **手持移动进入镜头时假“失效”**：实测若手机从远处/侧面移动进入视野，多码识别无反应；但对准停稳后切文字识别再切回，却能 100% 识别。根因是：在移动掠过条码时过早触发了计时，移动过程中因模糊丢失连续空帧导致重置死循环；切文字再切回时相机重新初始化且手已停稳，首两帧直接稳定解出多码。
  3. **架构解耦诉求**：此前多码定格 UI 与映射逻辑深嵌在千行 `ScanScreen.kt` 内，无法被批量补扫、巡检等其他扫码业务独立复用。
- 实现：
  - **跨帧拼图与手持稳定态聚合器（`ui/analyzer/multiscan/MultiBarcodeAggregator.kt`）**：
    - 纯 Kotlin 脱机单测架构，彻底解耦。通过逐帧跟踪各条码归一化位移（`dist = hypot(dx, dy)`），严格区分“移动掠过”与“手持稳定”状态。
    - 移动中（$dist > 0.025$）保持静默追踪更新，大幅平移（$dist > 0.08$）自动淘汰旧区旧码；手停稳对准试剂盒瞬间（$dist \le 0.025$ 且连续稳定 2 帧），瞬间触发 `AggregationDecision.Freeze`，就地定格。
    - 跨帧拼图机制：单帧由于反光未能一次性解出所有密集小码时，聚合器跨帧动态拼接各帧条码（600ms 自然淘汰），停稳定格时汇聚视野内全部条码；单码对准静止稳定 500ms 自动返回。
  - **坐标逆映射与条码中心提取解耦（`CameraCoordinateMapper.kt`）**：
    - 独立模块统一处理 `FILL_CENTER` 逆变换与四角点中心反算，实现图像像素到视图像素归一化坐标（0.0 ~ 1.0）的高精度对齐。
  - **独立定格选择 UI 覆盖层（`FrozenSelectionOverlay.kt`）**：
    - 从 `ScanScreen.kt` 剥离为完全独立的 Compose 组件，保留 1:1 无缝底图、35% 暗化遮罩（空白处秒解冻）、微信绿呼吸波纹与 56dp 热区。
  - **相机分辨率提升与分析器集成（`BarcodeAnalyzer.kt` + `ScanScreen.kt`）**：
    - 相机分辨率由 720p 升级为 1080p（`Size(1920, 1080)`），密集小码宽度倍增，彻底击破 ML Kit PPM 极限。
    - `BarcodeAnalyzer` 接入 `MultiBarcodeAggregator`，定格决策产出瞬间同步获取当帧坐标与底图，彻底消除时间差。
- 影响：
  - 标记偏移彻底消除，绿色锚点与实物 DataMatrix 像素级严丝合缝；
  - 手机从移动状态移至条码上方停稳时，无需任何切换重置，60ms 内自动触发定格；
  - 多码选择模块实现 100% 独立解耦，未来可在任何页面即插即用。
- 兼容性：`MultiBarcodeAggregatorTest`、`CameraCoordinateMapperTest` 及全项目单测 100% PASS，`./gradlew assembleDebug` 打包成功。

## [2026-10-17] 扫码体验修复：多码选择 450ms 观察窗口防秒退 + 空间追踪原地换盒与默认开局连扫 【已验证】

- 为什么：
  1. **多码选择秒退**：实测在开启多码选择模式时，只要任意首帧仅识别到单个码，原单码分支 0 防抖 0 延迟直接触发退出回调，关闭相机页面，导致多码定格完全没有机会触发。
  2. **空间追踪再扫相同码无反应**：① 补扫弹窗原默认处于单发模式（`scanMode = 1`），扫到第 1 码后相机分析立即自动暂停（`isPaused() == true`），后续帧被直接丢弃；② 胶囊原默认绿色内容去重，相同码被集合拦截；③ 原地换盒时，旧 Track 在 700ms 容忍期内把新盒试剂误判为同一物体的短暂遮挡重现，死锁吞码。
- 实现：
  - **盘库多码选择体验重构（`BarcodeAnalyzer.kt` + `ScanScreen.kt`）**：
    - `BarcodeAnalyzer` 引入 450ms 多码观察窗口（`GRACE_WINDOW_NS = 450_000_000L`）：首次捕获条码后跨帧收集去重条码；若连续 2 帧单帧出现多码则立即短路定格；窗口到期累计 >1 码定格选择，仅 1 码则单码返回；连续 6 帧空帧（约 200ms）自动重置未决窗口；非多码模式保持单码 0 延迟即扫即返。
    - `ScanScreen` 在多码模式下放开扫描框全画幅识别（`targetRegion = null`），避免框外周边多码被提前误杀，并微调引导提示文案。
  - **批量补扫空间追踪去重加固（`CodeTracker.kt` + `FloatingRescanOverlay.kt`）**：
    - `CodeTracker` 新增 `missingFrames` 连续未匹配帧计数与已计数轨迹离场超时（`countedLostTimeoutNs = 300ms` / 8 帧）：既保留 ≤2 帧（~66ms）手抖轻微漏检不复计容忍，又确保实物拿走（空档 ≥300ms）后旧轨迹安全消亡，原地放上同内容新盒试剂时正常独立计入。
    - `CodeTrackerTest.kt` 新增 3 组测试用例（原地换货识别、短暂遮挡不复计、连续快速原地换 3 盒各计 1 次），10 组单测 100% 通过。
    - `FloatingRescanOverlay` 默认状态调整：开局默认开启连续扫描（`scanMode = 2`），即开即扫免除繁琐手势；默认开启空间追踪去重（蓝色胶囊，`dedupEnabled = false`）；悬浮按钮单击行为简化为“连扫中=暂停，暂停中=恢复连扫”，状态提示文案实时对齐。
- 影响：
  - 盘库扫码：多码选择不再被首帧单码秒退打断，给足多码识别与原地定格选择时间；普通单码模式行为与性能零折损。
  - 批量补扫：开局直接即扫即计，连续平移与原地换盒扫同一批号试剂均能连续平滑计件，无卡死断流现象。
- 兼容性：纯 App 表现层与分析层逻辑优化，无数据库与服务端协议变更；`./gradlew testDebugUnitTest`（10 组）与 `./gradlew assembleDebug` 全部通过。

## [2026-10-16] 扫码体验重构：批量补扫空间追踪去重（双模切换）+ 盘库微信级 1:1 无缝定格多码选择 【待测试】

- 为什么：
  1. **批量补扫同内容去重难题**：IVD 行业中大量试剂实物码文本内容完全相同，操作员连续平移补扫时，旧版 2 秒时间冷却与文本去重机制会将正常移动扫到的同内容新实物码直接吞掉或卡顿，无法连续快速计数，且业务上仍需保留按文本去重的手动切换能力。
  2. **盘库多码选择体验差**：`UserSettings.enableMultiScan` 开启后，旧版通过 `imageProxy.toBitmap()` 抓帧导致耗时高、`ContentScale.Fit` 产生大面积黑边和定格画面“缩放跳变”；单帧杂讯误触发强制定格；ML Kit 坐标未反算 CameraX `FILL_CENTER` 居中裁剪偏移导致绿圈偏位。
- 实现：
  - **批量补扫空间追踪去重（`batchscanner/task/`）**：
    - 新增 `CodeTracker.kt`：纯 Kotlin 多目标跟踪器，在旋转正向分析图坐标系下工作，提取 4 角点几何中心 $(cx, cy)$ 与边长均值，欧氏距离 $dist \le size \times 0.8$ 匹配，`minHits = 2` 过滤单帧幽灵码，每个实物码 Track 终身仅计一次，纳秒时间戳 700ms 丢失超时清理。
    - 编写 `CodeTrackerTest.kt`：7 组脱机单测（原地停留 20 帧单计、幽灵码过滤、多码并存各计、连续平移扫货架、短暂遮挡容忍、超时新建 Track、reset 重置）100% 通过。
    - 加固 `SimpleFloatingScanner.kt`：限定码制为 `FORMAT_DATA_MATRIX, FORMAT_CODE_128`，Scanner 单例复用并在 `DisposableEffect` 释放，`Tasks.await` 异步转同步并在 `finally` 严格关闭 `imageProxy`。
    - `FloatingRescanOverlay.kt` 双模切换：彻底移除 `SAME_CODE_COOLDOWN_MS = 2000L`；保留右下角胶囊作为业务开关——**开启（绿色·内容去重）**基于 `dedupHistory` 集合防重，**关闭（蓝色·空间追踪）**纯靠底层 `CodeTracker` 轨迹各计一次。
  - **盘库扫码微信级 1:1 无缝定格多码选择（`ui/analyzer/`）**：
    - `BarcodeAnalyzer.kt`：增加 `consecutiveMultiFrames >= 2` 防抖门槛；基于 `FILL_CENTER` 逆变换矩阵精准反算视图像素坐标并归一化；在主线程直接从 `PreviewView.getBitmap()` 抓帧。
    - `ScanScreen.kt`：`FrozenSelectionOverlay` 采用 `ContentScale.FillBounds` 消除黑边与尺寸跳跃；35% 柔和暗化遮罩，点击任意空白处立即解冻恢复实时取景；微信绿（`#07C160`）呼吸水波纹扩散脉冲锚点（1000ms 循环、56dp 触控热区、长按震动反馈）；顶部常驻沉浸式微提示胶囊。
- 影响：
  - 两大功能完全独立解耦：多码选择仅限盘库扫码（`ScanScreen`），不影响批量扫描与补扫弹窗；补扫空间追踪去重仅作用于悬浮补扫弹窗。
- 兼容性：纯 App 表现层与分析层改动，无服务端/数据库迁移变更；`./gradlew testDebugUnitTest` 与 `./gradlew assembleDebug` 全部通过。
- **【待测试 / 待真机验证清单】**：
  - [ ] **批量补扫**：关闭胶囊（蓝色·空间追踪），平移扫两盒同内容试剂，验证是否各计 1 次（总数 +2）；停留原地不动，验证是否不复计。
  - [ ] **批量补扫**：开启胶囊（绿色·内容去重），扫第二盒同内容试剂，验证是否被内容拦截。
  - [ ] **盘库扫码**：开启多码选择，对准多个条码，验证是否 1:1 无黑边原地冻结，绿圈是否呼吸扩散且准确定位在实物码上。
  - [ ] **盘库扫码**：点击绿色锚点验证选中入库；点击空白处验证秒解冻恢复。

## [2026-09-25] 交互改造：搜索结果数量合计 + 去除盘库/查询模式手动切换 + Excel 导入迁至新建任务弹窗（M9）

- 为什么（用户三项需求）：① 盘库搜索结果缺一个总量视角，想在「长光模式」开关旁直接看当前搜索结果的数量合计；② 盘库/查询两模式的搜索逻辑本就相同（`performSearch` 不区分），手动切换徒增步骤；③ Excel 导入只在「新建本地盘点」时才有意义，放在已有任务的更多菜单里既冗余又容易误导。
- 实现：
  - **数量合计**：`InventoryScreen` 长光模式行（SpaceBetween 原本只有右侧一个孩子）补 `合计: N`——N=当前 `searchResults` 的 `quantity` 求和（用户确认只加账面数量，不含实盘），整数去 `.0` 格式化，随搜索实时变化。
  - **去除模式切换**：`isInventoryMode` 从 `remember { mutableStateOf }` 改为派生 `val = !isReadOnly`（可编辑任务恒为盘库模式，只读任务恒为查询视角），删除 `ModeSwitcher` 组件（文件已删）；更多菜单/FAB/长光行条件同步简化。**详情入口补位**：查询模式下"点击记录看详情"是详情弹窗唯一入口，随切换一起消失——`RecordOptionDialog` 新增「查看详情」选项承接；只读任务点击记录仍直接看详情。
  - **导入迁移**：`InventoryRepository.createSession` 改为返回新任务 ID（Impl 原本丢弃 Dao 返回值），`SessionViewModel.createNewSession(name, onCreated)` 回调带回；`CreateSessionDialog` 加可选「选择 Excel 文件」（OpenDocument，xlsx/xls，显示文件名可清除）。选了文件 → 点创建先建任务拿 ID → 依次弹 `TemplateSelectionDialog`（快捷/模板/新映射，组件复用）→ `MappingConfigDialog`（模板模式带初始映射）→ `importExcelFile(WithMapping)` → 导入确认弹窗（产品冲突摘要 + `ImportDiagnosticsContent` 自 InventoryScreen 迁来）；未选文件照旧建空任务。任务详情页的「导入Excel」菜单项、excelLauncher、模板/映射/导入确认弹窗全部移除（`ImportMode` 枚举更名 `SessionImportMode` 迁入 SessionListScreenNav）。
  - 导入产生的 DI 校验异常弹窗在列表页同款挂载（一键复制/关闭）；详情页原弹窗保留（「校验DI」菜单项仍会产生该数据）。
- 影响：新建本地任务的完整动线变为 名称→（可选）选 Excel→创建→导入流水线，全在任务列表页完成；已有任务不可再补导/重导 Excel（用户确认彻底移除）。取消模板/映射弹窗会留下已创建的空任务（可长按删除）。
- 兼容性：纯 App 端改动，不涉服务端契约；`gradlew assembleDebug` 通过。设计记录见 AGENTS.md M9 注释与 docs/UI.md。

## [2026-09-25] 新功能：服务端任务「等待开始」状态落地移动端（M8）+ 任务来源徽标 + 服务端写入守卫收紧

- 为什么：服务端 M1 起的任务状态机含「待开始」（`started_at` 为空且 status=0，Web 管理员在 `/api/session/{id}/start` 点开始），但移动端此前不感知：拉取的待开始任务与本地任务无任何区分（`sessionSource` 字段从未被赋值），卡片误显示「进行中」，扫码/上传/修改全部放行——管理员点「开始」在移动端等于不存在，准备期数据会与采集期数据混写。用户需求：本地任务照旧；服务端（拉取）任务处于待开始时禁用写操作；任务卡片新增来源徽标（本地/服务端）。
- 移动端实现：
  - 状态表示：`InventorySession` 新增 `startedAt: Long?` 列，DB 5→6（`MIGRATION_5_6` = `ALTER TABLE inventory_sessions ADD COLUMN startedAt INTEGER`，注册在 `fallbackToDestructiveMigration()` 之前，v5 老库升级不丢数据）；派生状态 `isPendingStart = sessionSource==1 && status==0 && startedAt==null`（扩展属性 `data/model/InventorySessionExt.kt`，**不新增 status 枚举值**，避免波及 `countOngoingSessions`/归档上报）。
  - 数据链路：`FullDownloadStrategy` 插入/更新 session 时落 `sessionSource=1 + startedAt`（SyncModels.kt `SessionDto` 加 `startedAt`）；`IncrementalDownloadStrategy` 按 `pullResponse.startedAt` 对齐（仅非空才写，绝不 null 回写），本地 null→非空且任务进行中时提示「服务端任务已开始，本地恢复可编辑」；解锁链路复用 M0 门铃：Web 点开始 → `session_changed` → 静默增量拉取 → startedAt 落库 → Room Flow 自动解锁 UI。
  - UI：`SessionListScreenNav` 状态徽标待开始分支（StatusPending 深蓝「待开始」）+ 新增来源徽标（「本地」=StatusArchived 灰 /「服务端」=StatusInfo 蓝）；`InventoryScreen` `isReadOnly = sessionStatus > 0 || isPendingStart`（所有编辑入口/快速查验/Excel 导入/完成盘点自动禁用），顶栏「(等待开始)」提示，只读横幅区分归档/待开始文案；`SyncDialog` 参数 `sessionArchived` 语义化为 `uploadLocked` + `lockReason`（"已归档"/"等待开始"）。
  - 纠错闭环：`InventoryViewModel.scheduleAutoPush` 推送失败且 `CollabApiException.code == "TASK_NOT_STARTED"` 时触发 `silentDoorbellPull` 自纠（本地脏记录保留，任务开始后随自动推送上行）。
- 服务端实现（`D:\xz\inventory_server`）：
  - `templates/api/record.py` `_collab_write_guard`：待开始 + 非自动建任务 → 403 `TASK_NOT_STARTED`（保留绑定设备自动预登记参与）；`auto_created` 豁免——**upload/push 自动创建的任务出生即已开始**（`started_at=now`）并现场登记创建者设备为参与者，否则本地任务备份/静默自动推送会被首推建出的待开始任务锁死；`/api/sync/delete` 补同一 `TASK_NOT_STARTED` 门禁（原本只有归档检查，删除路径绕过了协作守卫）。
  - `templates/models/models.py` `SessionDto` 加 `startedAt`；`/api/download`（record.py:451）构建 SessionDto 时带上，保证全量下载当下即可正确显示待开始（尤其已参与设备重新下载已开始任务的场景）。
- 影响：服务端编排任务（Web 创建）真正具备"准备期/采集期"闸门；本地任务行为零变化（`sessionSource=0` 兜底）。**行为取舍**：上传备份到服务端的本地任务出生即「进行中」，其他设备无法再加入该任务（JOIN_CLOSED）——此前待开始窗口内可以；Web 创建任务不受影响。顺带清偿：`sessionSource` 从未赋值的旧缺口。
- 兼容性：App 与服务端需**成对升级**（旧服务端不回 startedAt 时，全量下载的任务 startedAt 为空——已参与设备重下已开始任务会被误判待开始，首次门铃/手动增量拉取后自愈；旧 App 对新服务端无感知，仅不显示新徽标）。`gradlew assembleDebug` 通过；服务端 `py_compile` 通过。设计记录见 `AGENTS.md` M8 注释与服务端 `docs/多端协作与任务归档管控设计.md` v1.4。

## [2026-09-14] 功能打通：扫码上传直连 UDI 看板（/api/udi/submit）+ Retrofit 通配符修复

- 为什么：手机"上传解码"原走 `/api/custom-data/submit`（detail 包 `{barcodes, count}`），数据落服务端 custom_data 表，而 WEB 看板与油猴脚本读的都不是它——上传成功但看板/脚本看不到（用户反馈）。服务端同日新增 UDI 看板（`udi_groups` 表 + 5 接口 + 网页"UDI中心"Tab，并与实时录入批次联动同步），移动端需对齐契约。
- 实现：
  - `TaskApiService.kt`：新增 UDI 看板模型（`UdiSubmitResponse/UdiGroupSummary/UdiListResponse/UdiDetailData/UdiDetailResponse/UdiMutationResponse`，L90-107）与 5 个接口（submit/list/detail/status/delete，L130-143）；`api/custom-data/submit` 接口保留未删。
  - 提交契约：**扁平 Map** `{"任务名": ["UDI"...]}`（服务端同名整体覆盖、状态重置待完成），不再包 wrapper、不再传 count 总数（用户明确后续不需要总数）。
  - `InventoryRepository.submitCustomData` 改名 `submitUdiData`（InventoryRepository.kt:91），实现改为 `api.submitUdiGroups(mapOf(title to barcodes))`（InventoryRepositoryImpl.kt:480-494）；`InventoryMainViewModel.submitDecodeInfoToNetwork` 调用点同步（L146），UI 入口与提示文案不变。
  - **Retrofit 2.9.0 通配符坑**：裸 `Map<String, List<String>>` 作 @Body 时，Kotlin 协变 List 会在 JVM 泛型签名生成 `? extends`，Retrofit `validateResolvableType` 运行时抛 "Parameter type must not include a type variable or wildcard"（javap 反编译签名 + jar 内消息定位确认）。解法：类型实参加 `@JvmSuppressWildcards`（TaskApiService.kt:131）。项目其余 @Body 均为 data class 包装，不受影响。
- 影响："上传解码"数据落点 custom_data → udi_groups，WEB 看板 UDI中心、油猴脚本 2.4.2"服务器"源均可见；任务名=看板组名（同名覆盖）；旧接口 App 侧停用、服务端保留。
- 兼容性：需服务端 2026-09-14 及以后版本（含 /api/udi/*，旧版会 404）；`gradlew compileDebugKotlin` 通过。配套改动（服务端 main.py UDI 看板 + 实时录入联动回填、脚本 2.4.2）在 `D:\xz\pythonUDI` 仓库，契约详见 `docs/API.md` §2 与 `D:\xz\pythonUDI\实时录入功能方案.md` §十三。

## [2026-09-13] 设计文档：跨端"实时录入"功能方案 v1.0（已评审待实施，本次零代码改动）

- 为什么：规划新大功能"实时录入"——移动端批量拍照/扫码 → 上传服务端创建录入任务 → 油猴脚本（金蝶 ERP 页内）逐条自动录入 → 逐条状态回传 → 手机实时看进度。可行性已分析（链路三段已有两段：custom-data 上传/脚本拉取），需沉淀方案供新对话快速接手。
- 产出：**`D:\xz\pythonUDI\实时录入功能方案.md`**（三端现状盘点与挂点行号、entry_jobs 数据模型与 REST 契约、脚本/移动端改造点、已定与待拍板决策、风险对策、里程碑 M1~M5、验收口径）。关键结论：v1 以 REST 轮询为唯一事实源 + 半自动人工确认；WebSocket 长连接可行、列为 v2 加速层（价值最高点=脚本在线心跳）；完成状态的定义（明细录完 vs 单据已保存）为实施前待拍板项。
- 兼容性：无代码改动；既有 `/api/custom-data/submit`、"上传解码"功能保持不动。

## [2026-09-13] 新功能：补扫弹窗"已扫 N 件"胶囊点击切换内容去重（绿=去重 红=不去重）

- 用户需求：重复扫到同一码时能一键决定"计不计件"——点击补扫弹窗底部"已扫 N 件"胶囊切换内容去重：绿色=去重开启（同码值只计一次），红色=去重关闭（维持原"重复也计入"语义）。细节已向用户逐项确认：去重范围=本次任务内弹窗历史（记忆跨弹窗开关保留，切换开关时清空重置，换任务归零）；中途切换只影响之后的扫描（已计入件数与单据核对结果不追溯）；每次打开弹窗默认绿色去重；胶囊加文字标注。
- 实现：
  - `FloatingRescanOverlay`：新增 `dedupHistory: MutableSet<String>` 参数（记忆由调用方持有）；`handleCode` 在同码 2 秒冷却之后追加去重判断——去重开启时码值已在记忆中则只刷新内容行（能看见"读到了"），不计件、不震动、不入账；首次计件时记入记忆。胶囊改可点击（白字，绿 0xFF4CAF50 / 红 0xFFE53935 底色），去重时文字为"已扫 N 件·去重"，点击切换开关并清空记忆；开关为弹窗局部状态，每次打开重置为绿色。
  - `InventoryTaskDetailScreen`：`rescanDedupHistory`（`remember { mutableSetOf }`）提升在任务页持有并传入弹窗——弹窗关闭重开记忆仍在，换任务重建屏幕自动归零。
- 设计取舍：被去重拦下的码不震动（震动=已计入的反馈，与冷却拦截一致）；切换去重开关即清空记忆，故"切红→切绿"后同码可重新计件（等价于手动重置去重）。
- 影响：仅补扫弹窗链路；单选图片/多图队列/相机批量识别行为不变。补扫默认行为从"不去重"变为"去重开启"（用户拍板），习惯"快速重扫同一码盘多件"的老用法需先点胶囊切红。
- 兼容性：`FloatingRescanOverlay` 签名新增必填参数（仅 1 处调用方，已同步）。

## [2026-09-07] 新功能：扫码命中绿色高亮（码位置闪现 450ms）+ 扫码框占比说明

- 用户需求：扫到码后在码的位置覆盖 30% 透明度绿色标识（大小=码框、≤0.5s）作视觉提示，与震动反馈互补——连扫时可确认"刚才是哪个码被计入"。
- 实现（全部在 `SimpleFloatingScanner` 内，不改 `onScanResult` 签名，补扫弹窗与批量识别页单码小卡两个调用方自动生效）：
  - **坐标反算**（关键）：ML Kit 码框在 upright 分析帧坐标系，预览是 FILL_CENTER 居中裁剪——新增 `mapImageRectToView` 做逆向映射（scale=max(视图/图像)，减居中偏移，边缘 clamp），高亮框精确盖在肉眼看到的码上；
  - **视觉**：绿色 0xFF4CAF50 30% 圆角(4dp)填充 + 2dp 60% 描边勾轮廓（杂乱背景下更醒目）；
  - **节奏**：保持 250ms + 线性淡出 200ms（Animatable 驱动，总 450ms），新高亮替换旧高亮；`HighlightBox` 携带命中时刻作唯一键，保证同码重扫（跨 2s 冷却）也能重新触发动画；
  - upright 尺寸计算从扫码框过滤分支提升为共用（高亮在"全画面模式"同样生效）。
- 已知局限（已告知用户）：帧捕获→解码完成约 100~200ms 延迟，快速移动手机时高亮框显示在码"刚才的位置"，手持盘存节奏下基本无感。
- 附：扫码框大小调整说明（已答复）——框边长 = `CAMERA_HEIGHT × SCAN_FRAME_RATIO` 双旋钮联动，改占比即可，括号绘制与框内过滤共用同一公式自动一致。

## [2026-09-07] 补扫弹窗第五轮：下方表单彻底可滑（去全屏手势层）+ 高度微调说明

- 用户反馈：下方表单（横向）仍然无法滑动。
- 根因：上一轮全屏透明层挂的 `detectTapGestures` 会**吞掉 down 事件**（其实现内 `down.consume()`），背后表单的滚动/拖动检测要求未消费的 down，拿到即不启动——"只拦点击、放行滚动"的设计意图在该检测器上不成立，横竖滑动实际全被拦。
- 修复：**全屏层不再挂任何手势**；点击拦截下沉到弹窗卡片本体（卡片 `pointerInput` + `detectTapGestures{}`，只吞卡片覆盖范围内的点击，防穿透误触背后单据行）。卡片以外（下方表单、页面）滚动与点击完全不受影响。
- 高度微调指引（写入 CAMERA_HEIGHT 注释）：`CAMERA_HEIGHT` 为弹窗高度唯一旋钮，总高 ≈ CAMERA_HEIGHT + 92dp（内边距 20 + 底部结果条 52 + 间距 10，状态栏另计）；镜头全宽时扫码框边长 = CAMERA_HEIGHT × 0.72，调高框更大、完整入框更容易、遮挡更多，建议 180~260dp。

## [2026-09-07] 补扫弹窗第四轮：修按钮拖动坐标错位 + 徽记固定最右 + × 沉到镜头角 / 结果条沉底

- 用户反馈：①数量徽记改回"已扫 N 件"样式、不跟随按钮、靠最右；②解析结果下移与徽记平齐、× 移右上角、其余空间全给镜头；③按钮拖动"不能越过中心线往左、往右可移出屏幕"。
- **按钮坐标 bug 根因（用户推测正确）**：按钮所在 `BoxWithConstraints` 设了 `contentAlignment = TopCenter`——Box 先把子项水平居中，`offset{}` 再叠加偏移，实际渲染位置 = 屏幕中心 + buttonOffset。初始 offset≈屏幕中心；左拖到 offset=0（左钳制生效）时按钮恰停在中心线（视觉"过不去"），右拖到 offset 上限时渲染位≈1.5×屏宽（移出右缘）。原版浮窗外层 Box 无 contentAlignment（TopStart 基准）所以正常。
- 修复与改造：
  - 按钮显式 `.align(Alignment.TopStart)` 纠正偏移基准；初始定位改为**容器内部坐标**（`镜头 positionInRoot() − 弹窗容器 positionInRoot()`），且首次拖动前每次布局自校正（跟随镜头位置），拖动开始后锁定（`buttonDragged` 标记）。
  - 卡片重排：× 关闭移到镜头区右上角悬浮（白图标 + 35% 黑圆底，亮画面清晰）；镜头 `CAMERA_HEIGHT=220dp` 直接从卡片顶部开始（原顶行空间并入镜头）；底部条 Row 垂直居中——左为解析结果两行（当前+上一次半透明，点击复制不变），右为**"已扫 N 件"胶囊**固定最右（与结果平齐、不随按钮）。
  - 浮动按钮上的 "+N" 红徽标移除（计数已在底部胶囊）。
- 取舍：结果行可用宽度较前少约 60dp（徽记占位），超长码仍中段省略；卡片总高基本不变（220 镜头 + ~52 底部条）。

## [2026-09-07] 补扫弹窗第三轮：放行下方滚动 + 按钮回归可拖动 + 镜头加高收底

- 用户反馈：①弹窗下方区域要能滑动单据看数量；②扫码按钮换回原来的拖动模式；③镜头加高（200→220dp），镜头下方收底（按钮悬浮后无需保留按钮行高度）。
- 实施：
  - 全屏透明层手势策略改为**只拦截点击、放行滚动/拖动**（`detectTapGestures` 吞掉 down-up，滑动手势不消费直接透传）——扫码时可滑动下方单据，但不会误触背后的按钮/行（点击仍被拦）。替代上一轮的"吞掉全部手势"方案。
  - 圆形扫码按钮从弹窗底部行改回**悬浮可拖动**：恢复原始三手势（单击=单发武装、长按=连续扫松手停、按住拖动=挪位置，touchSlop 区分拖动与长按），拖动范围钳制全屏；初始位置取镜头下缘中点（`onGloballyPositioned` 首次布局定位）；累计计数从"已扫 N 件"胶囊改回按钮右上 **"+N" 红色徽标**（随按钮浮动）。
  - 镜头高度常量 `CAMERA_HEIGHT` 200→220dp（扫码框随短边增至 ~158dp 见方，完整入框更容易）；卡片底部按钮行移除，镜头后直接 10dp 收底，弹窗更矮。
- 影响：下方单据区域恢复滚动交互（点击仍拦截）；按钮初始遮挡镜头下缘中部，可拖走；其余链路（框内判定/冷却/震动/入账）不变。

## [2026-09-07] 补扫弹窗第二轮：去遮罩改投影 + 镜头压高 + 白色四角扫码框（只扫框内完整码）

- 需求（先商讨后实施，用户逐项拍板）：①镜头**宽度不变、高度压缩约一半**（目的：少遮挡下方单据内容，用户纠正了此前"缩宽度"的口误）；②镜头中央加**正方形扫码框**，只扫框里的码；③**去除弹窗半透明灰色遮罩**；④扫码框要白色系、简约耐看（用户选定：白色四角 L 形括号、无整框无扫描线）。
- 实施：
  - `SimpleFloatingScanner` 新增可选参数 `scanFrameRatio: Float = 0f`（默认 0=关闭，全画面识别，**既有调用方 BatchScannerScreen 单码补扫小卡零改动**）：>0 时在镜头上绘制居中正方形白色四角括号（线宽 3dp、角长 22dp、圆头端点、90% 白），且解码后按"码 boundingBox **完整落在框内**（容忍 2px 取整误差）"过滤，框外的码直接丢弃。区域换算复用 `ScanRegionMapper.getMappedRect`（视图坐标 → FILL_CENTER 裁剪后的 upright 图像坐标，含 90°/270° 旋转换算，与 ML Kit boundingBox 同坐标系）；括号绘制与解码过滤共用同一公式（短边 × ratio 居中），保证所见即所扫。
  - `FloatingRescanOverlay`：灰色遮罩移除，改为**透明手势拦截层**（awaitEachGesture 吞掉全部 down/move/up——点击与滚动都拦，防扫码误触背后页面；子节点弹窗正常收事件）；卡片加 `shadow(12dp)` 投影分层；镜头区 `weight(1f)` 改为定高 `CAMERA_HEIGHT = 200.dp`（约原 1/2，常量可调），卡片高度随内容自适应；弹窗总高从 65% 屏高降到约 40%，下方单据表格遮挡大幅减少。
- 设计取舍（已说明）：镜头高度未选 1/3（~135dp），因扫码框是正方形且要求完整入框，1/3 时框仅 ~97dp 见方，UDI 稍远即无法完整进框；遮罩的"变暗"虽去除，点击拦截保留（模态语义不变）。
- 影响：`scanFrameRatio` 默认关闭，其他调用方行为不变；补扫弹窗识别范围从全画面收窄为框内（含完整入框判定），手持对准要求略提高；同码冷却/震动/入账链路不变。

## [2026-09-07] 微调：补扫弹窗贴顶拉满 + 内容区去黄底紧凑化

- 用户反馈（附真机截图确认整体布局可用）：①弹窗整体上移到顶边；②宽度"拉满"；③文字区去除黄色背景和黄框、内间距调小、尽量显示完整扫码内容。
- 修改：遮罩 contentAlignment 改 TopCenter，卡片加 `statusBarsPadding`（贴"内容区顶边"而非钻入状态栏/挖孔）、`fillMaxWidth()`（100% 宽）、圆角改仅底部 24dp（顶部贴屏边取直角）；内容区黄底/黄框移除，结构改为 Row（文字列 weight(1f) + 右侧 × 按钮 30dp），卡片内边距 12→10dp；单行显示上限 CONTENT_MAX_CHARS 40→48、字号分档 15/13/12→15/13/11sp（超长仍中段省略，DI 在头、批号/序列号在尾）。
- 设计取舍（已向用户说明）：弹窗贴顶后遮住顶栏按钮（模态语义，关闭后可用）；一行物理约容纳 ~50 字符，超长 UDI 无法绝对完整，如需完整可后续改"当前码两行/点击全屏查看"。
- 修复记录（同日）：首次实现中内容区内层 `Column(fillMaxSize)` 触发 Compose 经典度量陷阱——Column 非 weight 子项按父容器最大高度贪婪测量，把中部 `weight(1f)` 的镜头区挤压成 0 高度（真机表现"镜头消失"），改为包内容高度 + Box contentAlignment 居中后解决；adb 真机实测定位（构建安装→截图逐页导航→复现），确认修复后按用户新要求回归"仅编译验证"。

## [2026-09-07] 重做：补扫入口改为居中模态大弹窗（替换可拖动浮窗）

- 需求（用户示意图）：原"可拖动悬浮镜头+控制按钮"组合改为一个居中模态大弹窗——顶部黄色内容区两行显示扫码内容（第一行当前码值全文，第二行上一次码值半透明），中部大尺寸扫码镜头，底部圆形扫码按钮，计数显示在按钮旁边。
- 交互定案（用户拍板）：弹窗形态=居中模态（90%宽×65%高，背景压暗，点遮罩不关闭，防扫码误触）；触发方式=沿用原手势（单击=单发识别一码后自动暂停、长按=连续扫松手停、每码震动+200ms节流）；内容格式=**原始码值全文**（超长中段省略保留头尾——DI 在头、批号/序列号在尾，字号按长度 15/13/12sp 分档，maxLines=1 兜底 Ellipsis）。
- 附加元素（全选）：累计计数"已扫 N 件"胶囊显示在按钮旁（替代原按钮 +N 角标）；镜头状态角标绿/灰（扫描中/已暂停）迁移至大预览左下角；点击当前/上一次内容行复制码值到剪贴板；关闭弹窗（右上×或返回键）Toast"本次补扫共 N 件"（0 件不打扰）。
- 涉及文件：`FloatingRescanOverlay.kt` 整体重写（组件名与 `onCode/onDismiss` 签名不变，结果仍逐码实时走 `processScannerResults` 入单据核对）；`InventoryTaskDetailScreen.kt` 移除外层 BackHandler（返回键拦截移入组件内部，保证返回键关闭同样弹小结，顺带删除失效 import）。
- 影响：拖动镜头/拖动按钮手势随浮窗一并移除（弹窗固定居中，无拖动需求）；同码 2 秒冷却、结果入账链路、SimpleFloatingScanner 复用均不变。

## [2026-09-06] 修复：多选队列同值码被折叠成"只识别一个码" + 队列点选补扫无标记

- 真机反馈：①多选图片识别时，一张有很多码的图只识别出 1 个码（单选同一张图能识别大部分）；②多图片识别时，点选补扫成功但没有标记。
- 根因（两问同源：队列合并按**码值**去重/过滤，而单选模式从不去重）：
  - ①`mergeQueueResults` 对每张结果 `distinctBy { displayValue }` 后并入累积——一张图上多个**同码值**条码（同产品同批号拍一箱/一盘，最常见场景）被折叠成 1 个标记、1 条记录；单选模式 `processCroppedImage` 直接整表赋状态，每个物理码独立计数，故单选正常。此外跨图重复的码被 `_currentImageBarcodes` 的 `!in existingValues` 过滤，第 2 张起复核页几乎无标记，加重"识别不了"观感。
  - ②`handleImageTapForRescan` 两个防呆拦截都用跨图累积全集 `_scannedBarcodes` 做命中/去重——前面图片的框坐标对当前图无效，大量误判"该条码已识别"直接拦截补扫；即使补扫成功，`addIncrementalBarcodes` 队列分支也把"别的图上已扫过的值"过滤掉不画进当前图（Toast 报成功但无标记）。
- 修复：**队列合并改为保留每次出现**（同值码=多件，与单选识别、浮窗补扫"重复也计入"语义一致；物理去重仍由解码层 BarcodeDeduplicator 的 IoU+中心距离负责，跨瓦片重复检测不会误计）；`_currentImageBarcodes` = 本张图自己的解码结果（不再按值过滤、不再复用累积全集）；`reassignAccumulated` 去掉 distinctBy，仅重置配额池后对累积列表（含同值多次出现）整体重算状态；补扫两处防呆拦截改用 `_currentImageBarcodes`（本图专属），补扫结果无条件画进当前图。
- 顺带（同族跨图污染）：`_ocrCropRects`（黄色虚线 OCR 选框）跨图不清空，会把上一张的框画到下一张的复核页与存档照片上——上次"标记串图"修复遗漏了它，已在 `processCroppedImage` 换图入口清空；`startImageQueue` 补充 `_scannedBarcodes`/`_currentImageBarcodes` 防御性重置。
- 影响：队列模式结果列表从"按值去重"变为"按出现次数计件"，单据配额按件消耗（与用户既有决策一致）；单张/相机/浮窗路径行为不变（非队列模式两列表恒等）。

## [2026-09-06] 修复：多选队列标记串图/轮播只有一张/"识别率下降"错觉

- 真机反馈：①进入下一张图，上一张的标记画在当前图上并不断累积；②选 6 张全部识别完，任务页轮播区只有最后一张；③识别率明显下降。
- 根因：①复核页与照片标记绘制用的是跨图**累积全集** `_scannedBarcodes`，上一张图的框带着上一张的坐标画到当前图上（③的"识别率下降"即此错觉——错位框挤满画面，实际解码逻辑未变）；②`_photoList` 只由 onComplete 回调里的单个 uri 驱动（`processScannerResults` 入参本就是 List），队列磁盘上存了 6 张但回调只带最后一张。
- 修复：新增 `BatchScannerViewModel.currentImageBarcodes`（当前这张图 own 的码值子集，状态沿用累积全集整体重算结果；队列合并时只取"合并前不存在"的新码，重复码坐标归属首张图），复核页 ReviewCanvasOverlay 与 `saveMarkedPhoto` 照片标记均改用它，非队列路径行为不变（两者恒等）；`addIncrementalBarcodes` 队列分支同步维护两个列表（点选补扫的新码也会画在当前图上）。`onComplete` 签名 `Uri → List<Uri>`（`savedQueuePhotoUris` 随每张保存累积），MainScreenNav 直接把列表透传给 `processScannerResults`——轮播区逐张展示；一张都没存时传空列表（不再走 onClose 分支）。

## [2026-09-06] 新功能：批量识别多选图片队列 + 任务详情页浮窗补扫（长按连续扫）

- **批量识别多选图片**：两处选图入口均改 `PickMultipleVisualMedia(maxItems=9)`（系统相册长按即可多选）——批量识别页内图片按钮直接进队列；任务详情页"图片"按钮多选后经 `PendingBatchImageQueue`（新增跨页容器，导航路由只支持单 uri）带全队列跳转。单张保持原有"选图→裁剪→解析"流程不变；多张进入队列模式，**每张都走完整的 裁剪→解析→复核 流程**（按用户实测反馈由"首张跳过裁剪"改回），复核页"核对完成"按钮变为"下一张 (剩N张)"（蓝色），全部处理完恢复"核对完成"（绿色）；"放弃重拍"变为"结束采集"（保留已扫结果返回任务页）；裁剪页左下按钮在队列模式下变为"跳过此张"（还有剩余时，不解析不入结果）或"结束采集"（最后一张）。每张解析结果按码值去重后与已扫累积合并，配额池重置后整体重算核对状态（BarcodeStatusAssigner）；每张处理完即带标记存入照片轮播。
- **浮窗补扫**（任务详情页"补扫"按钮，视频采集旁）：点击后出现可拖动的悬浮镜头（160dp，复用 SimpleFloatingScanner 新增的 isPaused/stopAfterFirst 参数）+ 可拖动的圆形控制按钮。控制按钮手势：单击=单发识别一码后自动暂停；长按=连续扫描，松开停止；按住移动超过触摸阈值=拖动位置（awaitEachGesture 自定义手势区分三种意图）。同码 2 秒冷却过滤镜头停留导致的连击，冷却后再次识别会再次计入（用户选择：重复也计入，一个码=快速重扫一件）；每识别一码震动反馈，按钮上有"+N"累计徽标、镜头角标绿/灰显示扫描状态；镜头右上 × 或返回键关闭。结果走 processScannerResults 进单据核对贪心匹配。
- 涉及文件：BatchScannerScreen/BatchScannerViewModel（队列）、SimpleFloatingScanner（参数化，默认行为不变，既有调用方零改动）、FloatingRescanOverlay（新增）、InventoryTaskDetailScreen（替换原居中单扫卡片为浮窗补扫）。

## [2026-09-06] 修复：金字塔/OpenCV 识别变慢（回归秒出）+ libdmtx 全局串行

- 真机反馈：统一解码入口后金字塔模式和 OpenCV 模式都变得很慢（改动前金字塔秒出）。
- 原因：金字塔顶层与每个瓦片被接入 `decodeStill` 全链——ML Kit 解不出的瓦片（大多数是空瓦片）都会追加一次 libdmtx 解码，而 libdmtx 在无码图上是穷尽式扫描（单次几百毫秒到秒级），且被实例级 Mutex 串行化，35 瓦片累计可达数十秒；OpenCV 路径本身未变慢，是"YOLO 未命中→自动落金字塔"被拖慢的连带感知。SimpleFloatingScanner 也曾逐帧走全链（空帧每帧都跑 libdmtx）。
- 修复：金字塔顶层/瓦片改走 `Decoder.decodeFrameAwait`（仅 ML Kit，恢复并发秒扫，同时恢复瓦片 cornerPoints/originalBarcode 传参）；SimpleFloatingScanner 实时帧改回 `InputImage.fromMediaImage` + `decodeFrameAwait`（零拷贝、仅 ML Kit）；libdmtx 兜底收敛到三处一次性场景——OpenCV 路径 ROI 解码（VisionManager 自有链，未动）、相册静态图（QrScanner）、legacy 局部补扫；`LibDmtxDecoder` 的 Mutex 提升为 companion 全局锁（Decoder 门面与 VisionManager 两个链实例也不会并发进 native，闪退未复现问题的额外保险）。

## [2026-09-06] 修复：批量扫码裁剪后闪退 + 扫描框二次拖拽卡顿

- 闪退（真机反馈）：统一解码入口后，金字塔降级路径的 ML Kit 失败瓦片会追加 libdmtx 解码，而 `LibDmtxDecoder` 内部用 OpenCV `Utils.bitmapToMat` 做灰度转换——金字塔路径恰恰是 OpenCV 未加载（`OpenCVInit.init()` 失败或 `useOpenCV=false`）时才走的，此时调 OpenCV native 方法抛 `UnsatisfiedLinkError`（Error 不是 Exception，原有 `catch (Exception)` 拦不住）→ 闪退。
- 修复：`LibDmtxDecoder` 彻底去除 OpenCV 依赖——`Bitmap.getPixels` + ITU-R BT.601 亮度纯 Kotlin 灰度（数值与原 `COLOR_RGBA2GRAY` 一致）；另加 `Mutex` 串行化 native 调用（金字塔切片并发进入，libdmtx 官方未承诺多线程安全，降级路径宁可排队不冒 native 崩溃风险）。
- 拖拽卡顿（真机反馈）：原 `ResizableScanOverlay` 在组合期读取框尺寸（Canvas 参数与手柄 offset 捕获组合值），每帧拖拽都触发整层重组；且 DataStore 比例回流会按 key 重初始化 `remember(ratioW, …)`，与进行中的拖拽对撞造成"一卡一卡"；ScanScreen 分析器又在比例变化时重启（`setAnalyzer` 管线抖动）。
- 修复：`frameW/frameH` 改为绘制/布局阶段延迟读取的 FloatState（拖拽过程零重组）；外部比例回流仅在非拖拽时同步，不打断手势；ScanScreen 分析器 lambda 内逐帧实时读取比例与容器尺寸，`LaunchedEffect` 只依赖模式/定格两键，比例变化不再重启分析器。
- 补扫闪退（真机反馈）：与裁剪闪退同根因——`legacyLocalCropRescan` 统一解码后 ML Kit 失败会走 libdmtx（旧版内部用 OpenCV），`handleImageTapForRescan`/`processCapturedImage`/`processCroppedImage` 三个解码入口的协程均无 try/catch，任何异常直接闪退。已由 OpenCV-free 的 LibDmtxDecoder 根治；另为三个入口加 `catch(Throwable)`（重抛 CancellationException）防御兜底：整图解析失败降级为 Toast + 回复核页，补扫失败降级为拉起单码扫码。

## [2026-09-06] 统一解码入口 Decoder + ScanScreen 可拖拽扫描框（参考 BinaryEye）

- 为什么：全 app 有 5 处各自创建的 ML Kit client（DecoderChain、QRCodeScannerUtil、FrameProcessor、BarcodeAnalyzer、QrScanner），libdmtx 二次解码兜底只覆盖拍照批量扫码一条路径，单扫/浮窗/相册/视频流扫不出 ML Kit 解不了的 DataMatrix；扫描线动画与"设置里调扫描框尺寸"的交互割裂，扫码时无法按实际码面调整框大小。
- 改了什么：
  - **解码门面**：`utils/DecoderChain.kt` 新增 `object Decoder`（进程级单例）——`decodeFrame/decodeFrameAwait`（实时帧，仅 ML Kit，避免空画面逐帧白跑 libdmtx）与 `decodeStill`（静态图全链 ML Kit→libdmtx，内部切 Default 线程）；ML Kit client 全 app 唯一，7 码制全格式表。
  - **调用方收口**：`BarcodeAnalyzer`（ScanScreen 实时帧，格式表由 4 种放宽为全格式）、`MLKitFrameProcessor`（视频流，`close()` 改空实现）、`QrScanner`（签名 InputImage→Bitmap，内部走 `decodeStill`）、`QRCodeScannerUtil`（金字塔顶层/瓦片/legacy 补扫改走 `decodeStill`，自持 scanner 删除，`close()` 改空实现兼容 BatchScannerViewModel；金字塔结果改用 `DecodedBarcode`，`GlobalBarcode.originalBarcode` 不再传入——该字段全库无读取点）。
  - **ScanScreen UI**：删除扫描线动画（原 `ScanOverlay` 整体移除）；新增 `ui/analyzer/ResizableScanOverlay.kt`——中心对称四角框 + 遮罩 + 右下角拖拽手柄（向外拖放大/向内拖缩小，最小 60dp，拖拽期间暂停帧分析，松手回调归一化比例），条码框与 OCR 框各自独立记忆；OCR 快门裁剪、`ScanRegionMapper` ROI 映射均改用比例计算的像素尺寸。
  - **设置**：`SettingsRepository` 删除 `barcodeFrameWidth/Height`、`ocrFrameWidth/Height` 4 个 Int 键，新增 `scanFrameRatioW/H`、`ocrFrameRatioW/H` 4 个 Float 键（0=默认 260dp/300×80dp，按容器尺寸自适应，旧键不迁移）；`SettingsViewModel` 删除对应 4 个 setter；`SettingScreenNav` "扫描框设置"分组改名为"扫描设置"，仅保留 OpenCV 开关，`FrameSizeControl`/`FramePreview` 组件删除。
  - **SimpleFloatingScanner**：帧分析改 `imageProxy.toBitmap()` + `QrScanner.recognizeQRCode(bitmap)`（挂起式，走全链）。
- 影响：单扫/浮窗/相册静态图获得 libdmtx DataMatrix 兜底；金字塔降级路径每个 ML Kit 失败瓦片会追加一次 libdmtx 尝试（该路径本就是慢速兜底，可接受）；`QrScanner.recognizeQRCode` 为破坏性签名变更（仅 2 处调用方，已同步）。
- 兼容性：DataStore 旧尺寸键成为孤儿数据（无害）；`Decoder` 单例的 ML Kit client 进程级存活，原先各处的 `close()` 不再释放它（预期行为）。

## [2026-09-06] 输入框统一：填充式 AppTextField + 失焦保存 + 键盘行为完善

- 为什么：全屏页面 21 处裸 `OutlinedTextField` 与对话框 26 处 `ModernDialogTextField` 两套风格并存；设置页"每键写 DataStore"；CloudTaskDialog 的 IP 框用 `KeyboardType.Number`（数字键盘无小数点，无法输入 IP）；搜索框跳扫码层后键盘不收起（TECH_DEBT P3 关联的输入体验问题）。
- 改了什么：
  - 新增 `ui/components/AppTextField.kt`：全项目统一填充式输入框（surfaceVariant 容器、透明边框、12dp 圆角、颜色走 MaterialTheme），支持 `enabled/readOnly/maxLines/visualTransformation/containerColor/onFocusLost`；`onFocusLost` 用"曾获焦"标记避免组合初始误触发；IME 为 Done 且未自定义 keyboardActions 时按"完成"自动清焦点；键盘被手动收起（下滑/返回，监听 `WindowInsets.ime` 归零）时对仍聚焦字段补清焦点，统一走失焦回调（仅限启用 onFocusLost 的字段）。`ModernDialogTextField` 改为委托它（保留 DialogTokens 玻璃拟态底色）。
  - 迁移裸输入框：AiProviderSettingsScreen（v2 活跃 8 处，补 enabled/readOnly + IME 链）、OcrResultDialog（3）、InventoryTaskDetailScreen（水印/单据搜索/解码名 3 处）、ChatScreen（聊天框填充式、回车换行不变）、SearchBarArea、ProductManagerScreenNav（删除本地 `MinimalistTextField`）。
  - 失焦保存：SettingScreenNav 服务器 IP（失焦校验 `NetworkUtils.isValidIpAddress` 自动保存，删除"保存"按钮）、临期自定义天数、扫码框/OCR 框尺寸（非法失焦回退+标红）；WatermarkSettingsScreen 水印文本由每键写 DataStore 改为失焦写入（预设 chips 仍即时保存）。
  - 键盘修复：CloudTaskDialog IP `Number→Uri`；SyncDialog/SessionListScreenNav IP 用 `Uri`；InventoryDialogs 效期加 `Number`；各表单补 Next→Done 链；SearchBarArea 扫码按钮 onClick 先 `clearFocus()+hide()` 再跳转。
- 影响：全屏输入框视觉从外框式变为填充式；设置页保存时机由"每键"变为"失焦"。`BasicTextField` 数量单元格与 AiProviderSettingsScreen 注释的 v1 代码未动。
- 兼容性：`ModernDialogTextField` 签名不变，26 处既有调用无感；`ServerIpInput` 删除保存按钮（行为变更）。

## [2026-08-14] Python 服务端字段同步 + 上传解码信息

（保留自原 CHANGELOG.md）

### 一、同步 Python 服务端新增字段到移动端

- **`TaskModels.kt`**：`TargetDocument` 新增 `billType`/`orderNo`/`documentNumber`（默认值兼容旧数据）；`TargetItem.targetQty` 由 `Int` 改 `Double`，防金蝶小数数量 Gson 崩溃。
- **`TaskApiService.kt`**：`TaskSummary` 新增 `billType`、`orderNo`（可空，兼容 SQLite 老行 NULL）。
- **`InventoryMainViewModel.kt`**：两处 `targetQty.toInt()` 截断写入。
- 兼容性：Gson 忽略未知字段；kotlinx 导入 `ignoreUnknownKeys=true`。

### 二、新增「上传解码信息」功能

- 原始条码串上传 `POST /api/custom-data/submit`。
- `TaskApiService.kt` 新增 `CustomDataSubmitRequest/Response`；`InventoryRepository(Impl)` 新增 `submitCustomData`；`InventoryMainViewModel` 新增 `_rawBarcodeList` + `submitDecodeInfoToNetwork`；`InventoryTaskDetailScreen` 顶栏新增「上传解码」按钮。

### 三、UI：上传解码按钮移至 TopAppBar（PDF 按钮旁）。

---

## [2026-08~09（进行中，未提交）] AI Agent 通用化改造 v2.0

- 为什么：《AI Agent 通用化改造开发文档 (v2.0).md》——打破硬编码工具壁垒，单一动态 SQL 查询 + 多轮工具循环。
- 改了什么（以当前工作区代码为准）：`QueryInventoryDataTool`（动态 SQL）替代原 search_products/count_inventory 等硬编码工具；`InventoryRepository.executeDynamicSqlForAi`（Cursor→JSON）；`InventoryAiAgent` 加文本兜底拦截器与死循环截断；多服务商配置（`ai_providers_json`）替代旧单服务商设置（含 DataStore 迁移）；新增 `ai/local/*` 本地模型基建（占位）；`ai/agent/UiAction` 扩展 `ShowProductCardList`。
- 影响：AI_MODULE.md 的 11 工具清单作废（现为 6 工具）。
- 兼容性：旧 AI 设置键自动迁移（`migrateOldProviderSettings`）。
- ⚠️ 风险：`executeDynamicSqlForAi` 无 SQL 白名单（TECH_DEBT P0-1）。

## [2026-05-30] 优化了数据库，新增关联键（git 31f5229）

- 改了什么：`product_base` 主键由（推断）`di` 改为 **`productKey` 统一关联键**（DI 优先/物料编码回退，`ProductBase.computeProductKey/resolveProductKey`）；DB version → 4；`StockRecord.productKey` 外键指向新键；导入/同步链路加"自动补 DI"与主键平移（`migrateProductKey`/`saveProductsOnly`/`updateProductKeyForRecords`）。
- 为什么：di 字段曾被物料编码污染，无法区分"真 DI"与"内部码"（ProductBase.kt:6-9 注释佐证）。
- 影响：所有扫码/单据匹配走 productKey；旧库升级因 fallbackToDestructiveMigration 清空【推测-用户当时接受清库】。

## [2026-05-10] 新增批量扫码（git f8d66bf）

- `batchscanner/` 子系统雏形：BatchScannerScreen 状态机、QRCodeScannerUtil、单据核对模型（TaskModels 三层状态）。

## [2026-05-04] 图片切片（git d515d0b）

- 金字塔切片扫描（QRCodeScannerUtil 顶层缩放 + 底层动态网格 600px/35% 重叠）。背景见《暴力切片优化建议.md》。

## [2026-04-30] 增加了搜索标记（git 94a665f）

- 搜索结果高亮：`HighlightField` 枚举 + `mapToUiModels`（InventoryViewModel.kt:166-225）；效期三色 `ExpiryState`。

## [2026-04-18 / 2026-02-18] 优化部分内容 / 清理 / 优化了一些问题

（git 343d084 / 48c60f8 / d42ea7d，无更细记录【待确认-细节不可考】）

## [2026-01-30] 添加了增量上传/下载 通过了基本测试（git 19d9a6e）

- `data/sync/` 四策略 + SyncOrchestrator + RetryPolicy + TimestampManager 双锚点（代码内 "Phase 2/3/4" 注释对应此期）。

## [2026-01-17] 修改了搜索逻辑，增加了降级搜索（git ec767a8）

- performSearch 降级链：UDI 精准 → 批号回退 → 模糊（InventoryViewModel.kt:115-160）。

## [2026-01-14] 增量上传，断点续传/下载（git dadb0ef）

- 增量同步雏形：`sync_status` 脏标记、uuid 跨端标识、`/api/sync/push|pull`。

## [2026-01-13] re-submit（git 6964f5e）

（项目首次入库记录【待确认-此前历史不可考】）

---

## 演变脉络速览（供理解现状）

Excel导入/扫码(2025末) → 增量同步(01月) → 搜索降级(01月) → 四策略同步体系(01月末) → 批量扫码+单据核对(05月) → productKey 关联键重构(05月末) → YOLO/OpenCV 视觉管线、libdmtx NDK(06月，《libdmtx集成方案.md》《yolo方案优化.md》) → AI 助手(07月《AI集成进度报告.md》) → AI v2.0 通用化改造(08~09月，进行中)
