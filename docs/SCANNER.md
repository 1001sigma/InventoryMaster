# SCANNER — 扫码/视觉引擎（本项目重点模块）

> 覆盖：拍照批量扫码（batchscanner/core）、视频流连续扫码（batchscanner/videostream）、静态图单扫（ui/analyzer）、视觉管线（utils/）、OCR（ocr/ + batchscanner/ocr）、NDK libdmtx。
> 代码位置相对 `app/src/main/java/com/example/inventorymaster/`（cpp 在 `app/src/main/cpp/`）。

## 1. 技术分工总表

| 技术 | 职责 | 关键文件 |
|------|------|----------|
| CameraX 1.3.0 | 取景、拍照（ImageCapture）、实时帧分析（ImageAnalysis）、录像（camera-video） | BatchScannerScreen / VideoStreamScreen / SimpleFloatingScanner.kt:71 |
| ML Kit Barcode 17.2.0 | 一维/二维码解码主力（QR、CODE-128、DATA_MATRIX、CODE-39/93、EAN-13/8），全 app 唯一 client 由 Decoder 门面持有 | utils/DecoderChain.kt（object Decoder） |
| ONNX Runtime + YOLO | 缩略图上定位条码区域（BoundingBox），不解码 | utils/YoloDetector.kt（模型 `assets/barcode_detector.onnx`） |
| OpenCV 4.10.0 | ROI 裁剪、CLAHE/USM 增强、双三次放大、透视校正 | utils/ImagePreprocessor.kt、MatBitmapConverter.kt、OpenCVInit.kt |
| libdmtx（NDK 编译 C 源码） | DataMatrix 专用解码（ML Kit 失败后的第二引擎） | cpp/libdmtx/* + cpp/libdmtx_wrapper.cpp + utils/LibDmtxDecoder.kt |
| ML Kit 中文 OCR 16.0.1 | 标签文字识别 | ocr/MlKitTextRecognizer.kt |
| Jieba 分词 | OCR 文本分词/纠错 | ocr/JiebaTokenizer.kt |
| ZXing 3.5.2 | 二维码**生成**（水印/展示） | utils/QRCodeUtil.kt |

## 2. 静态图批量扫码主链路（拍照后的大图处理）

入口：`QRCodeScannerUtil.processBitmap(originalImage)`（batchscanner/core/util/QRCodeScannerUtil.kt:73-113）

```
Bitmap
 ├─ 路径A（useOpenCV=true 且 OpenCVInit.init() 成功，L86）
 │   → VisionManager.scanImage()                    utils/VisionManager.kt:62
 │      1. 缩略图（最长边 ≤1920，THUMBNAIL_MAX_WIDTH L46）
 │      2. YoloDetector.detect(thumbnail)           L84  → 条码区域列表
 │      3. 坐标映射回原图 + 40px padding（ROI_PADDING L47）   L108-113
 │      4. 原图裁 ROI → ImagePreprocessor.enhanceForBarcode  L125-127
 │      5. DecoderChain.decodeWithFallback（ML Kit → libdmtx）L131
 │   → convertScanResult（L283）→ BarcodeDeduplicator.deduplicate
 └─ 路径B（降级：金字塔暴力切片 pyramidScan，L121-168）
     顶层：整图缩到 ≤2560（TOP_LAYER_MAX_SIDE L43），Decoder.decodeFrameAwait 扫大码（仅 ML Kit）
     底层：动态网格切片 decodeBitmapByTiling（L190-278）
        - 目标片尺寸 600px（BOTTOM_LAYER_TARGET_TILE L44）→ cols×rows = ceil(w/600)×ceil(h/600)
        - 重叠率 35%（OVERLAP_RATIO L45）
        - 并发信号量 = CPU核数/2，钳制 2..4（L62-64）
        - Decoder.decodeFrameAwait 逐片解码（仅 ML Kit）→ 局部坐标+偏移 = 全局坐标
     → BarcodeDeduplicator.deduplicate（IoU + 中心距，L166）
```

YOLO 参数（utils/YoloDetector.kt:16-23）：输入 640×640，confThreshold 0.7，iouThreshold 0.45，maxDetections 50。模型加载失败返回 false 不崩溃（L38-52），detect 返回空列表走降级。

图像增强（utils/ImagePreprocessor.kt）：
- `enhanceForBarcode`（L22-48）：灰度 → CLAHE(clipLimit 2.0, 8×8) → USM 锐化(GaussianBlur σ=3, addWeighted 1.5/-0.5)。
- `resizeWithInterpolation`（L56）：INTER_CUBIC 双三次放大（rescanLocal 用 3x）。
- `perspectiveCorrect`（L73）：四点透视校正——**已实现但主链路未调用**【事实：调用方 grep 无】。

解码链（utils/DecoderChain.kt + 统一门面 object Decoder）：
- `MlKitDecoder` 格式表 7 码制（QR、CODE-128、DATA_MATRIX、CODE-39、CODE-93、EAN-13、EAN-8）；`LibDmtxDecoder`（utils/LibDmtxDecoder.kt）纯 Kotlin 灰度（getPixels + BT.601，**不依赖 OpenCV**——金字塔降级路径可能在 OpenCV 未加载时运行）→ `LibDmtxNative.nativeDecode(buffer, w, h)`（JNI），native 调用经 Mutex 串行化（切片并发安全兜底）。
- `DecoderChain.decodeWithFallback`：按序尝试，首个非空结果即返回。
- **`Decoder`（进程级单例，DecoderChain.kt）是全 app 唯一解码入口**（2026-09 起）：`decodeFrame/decodeFrameAwait`（实时帧与金字塔切片，仅 ML Kit——libdmtx 在无码大图上是穷尽式扫描，逐帧/逐片兜底会把降级路径拖到秒级以上）+ `decodeStill`（静态图全链 ML Kit→libdmtx：相册识别、legacy 局部补扫，内部强制 Default 线程）。OpenCV 路径的 ROI 解码走 VisionManager 自有链（ML Kit→libdmtx，YOLO 已定位、通常含码，libdmtx 成本可控）。原先各处 `close()` 均为空实现。

去重（batchscanner/core/util/BarcodeDeduplicator.kt:43-90）：
- 按面积降序保留大框；`displayValue` 不同强制保留（L59 短路保护，防物理重叠的不同码被误杀）；
- 判重：IoU > 0.4 **或** 中心距 < 较小框宽 × 0.3。

局部补扫：`processLocalCropRescan`（QRCodeScannerUtil.kt:324-373）→ `VisionManager.rescanLocal`（裁 300px、3x 放大、增强、解码）→ 失败再走 `legacyLocalCropRescan`（L378，nearest 邻近放大 + ML Kit）。

## 3. libdmtx NDK 层

- CMake：`app/src/main/cpp/CMakeLists.txt` 编译目标 `dmtx`（libdmtx C 源码 24 文件）+ `dmtx_wrapper`（JNI 桥）。
- JNI：`Java_com_example_inventorymaster_utils_LibDmtxNative_*`（cpp/libdmtx_wrapper.cpp），接收灰度像素数组（`DmtxPack8bppK`），调用 `dmtxImageCreate/dmtxRegionFindNext` 系 API 解码，返回字符串数组。
- Kotlin 侧声明：`utils/LibDmtxNative.kt:3`。
- 选型依据：《libdmtx集成方案.md》（已归档至 docs/archive/）——"JNI/NDK + 现有责任链"，作为 ML Kit 失败后的第二道防线。
- abiFilters：arm64-v8a + armeabi-v7a（app/build.gradle.kts:32-34）。

## 4. 拍照批量扫码（batchscanner/core）

状态机（`batchscanner/core/models/ScannerUiState.kt:5-9`）：`Capture → Cropping(bitmap) → Processing → Review`。

`BatchScannerViewModel`（batchscanner/core/viewmodel/BatchScannerViewModel.kt:34）：
- `processCapturedImage`/`processCroppedImage`：拍照或裁剪后 → Processing → 调 `QRCodeScannerUtil.processBitmap` → Review。
- `prepareNextCapture`：连拍推进——将当前图条码并入 `accumulatedRawBarcodes`，清理单图临时状态，切回 `Capture`。
- `discardCurrentImageAndRetake`：单图重拍——精准扣除当前图产生的条码与配额，切回 `Capture`，不影响历史图。
- `addIncrementalBarcodes`：补扫结果合并。
- `handleImageTapForRescan`：Review 图上点按局部补扫。
- `toggleUseOpenCV`：运行时切换路径 A/B（同步自设置 `useOpenCV`，默认 true，SettingsRepository.kt:25）。
- `productNameMap`：根据条码 DI 异步在 `ProductDao` 匹配品名并缓存，用于复核列表展示。
- OCR 支线：`startOcrCrop` → OcrScanState（Cropping/Recognizing/Extracted，batchscanner/ocr/models/OcrModels.kt:22-27）。

状态分配（batchscanner/core/util/BarcodeStatusAssigner.kt:18-44）：
- 无单据：全部 `MATCHED`。
- 有单据：解析 `DI|批号` 与 `remainingTargets`（`InventoryMainViewModel.getTargetDiList()` 生成的配额池，每条目标 ×targetQty 展开）匹配，命中消耗配额 → `MATCHED`，否则 `MISMATCHED`。状态枚举 `ScanStatus{MATCHED,MISMATCHED,UNKNOWN}`（core/models/GlobalBarcode.kt:8）。

### 4.1 拍照连拍流转与三色复核底盘（2026-10-20 升级）

- **无感连拍作业动线**：
  1. 取景器全屏纯净预览（移除黄色参考框与遮罩，`PreviewView.ScaleType.FIT_CENTER` 防畸变）；右上角常驻绿色 `[完成提交 (N张)]` 胶囊，随时结算。
  2. 首张裁剪解析进入 `ReviewView`，点击 `[拍下一张]` 即可带标记落盘当前图并瞬切回取景器拍下一张；
  3. 若某张拍摄不满意，点击 `[放弃本张]` 仅扣除本张数据并重分配单据池，前序成果安全无损。
  4. 拍照原图存相册收归扫描设置统一管理（`save_original_photo_to_gallery`，默认关）。

- **复核页胶囊双模底盘（`ReviewView` in `BatchScannerScreen.kt`）**：
  - 顶部胶囊切换器：`[ 操作控制 | 扫码数据 (N) ]`，底盘高度自适应（最高 280dp），上半屏原图始终保持良好可视性。
  - **操作控制面板**：Row 1 辅助工具行（`[放弃重拍/本张] [点选补扫] [文字识别]` 单行防折行）；Row 2 主流程大按键（`[拍下一张] [核对完成]`）。
  - **扫码数据列表**：`LazyColumn` 展示数据卡片，滑动到最下方常驻展示 `[拍下一张]` 与 `[核对完成]`，支持直接在此模式下连拍推进。

- **单图三色混料/混批防错分类（`batchscanner/core/util/BarcodeClassifier.kt`）**：
  - 单图频次统计决出基准物料 `majorityDi` 与基准主批号 `majorityBatch`。
  - **主要物料主批次**：外框、半透填充、数据卡片底色统一为**淡蓝色**（`#E3F2FD`，标签 `主要物料`）。
  - **同物料不同批号（混批警告）**：外框、半透填充、数据卡片底色统一为**淡红色**（`#FFEBEE`，标签 `同品混批`）。
  - **不同物料（混料警告）**：外框、半透填充、数据卡片底色统一为**淡黄色**（`#FFF8E1`，标签 `不同物料`）。
  - **方案 A 视觉兼顾**：有单据核对时，条码框右上角与卡片右侧以小图标呈现 `✔ 已匹配`（绿）/ `✘ 异常多出`（红），与三色防错完美共存；`ImageStorageUtils.drawBarcodesOnBitmap` 同步采用三色规范。

- **列表点击与原图框选脉冲呼吸联动（`ReviewCanvasOverlay.kt`）**：
  - 点击数据卡片触发 `highlightKey`，原图对应框执行 600ms 强光白闪烁 + 动态发光外光晕 + 中心聚焦光圈双重脉冲动画，实现实物图秒级定位。

导航接入：`MainScreenNav.kt:355-390`——`BatchScannerScreen(inputUri, targetList)` 完成后回调 `onComplete → mainVM.processScannerResults`、`onOcrConfirmed → mainVM.processOcrResults`，然后 `popBackStack`。

## 5. 视频流连续扫码（batchscanner/videostream）

`VideoStreamViewModel`（videostream/viewmodel/VideoStreamViewModel.kt:21）组合四引擎（L40-43）：
- `TrackerEngine`（util/TrackerEngine.kt:13）：帧间目标跟踪状态机（`TrackerState`、`ActiveTracker`，models/TrackerModels.kt:8-40）——判断条码是否"停留稳定"。
- `GridManager`（util/GridManager.kt:14）：画面网格划分，跟踪中心点落入格子防重复计数（VM L193-204 转换坐标后传入）。
- `DirectionDetector`（util/DirectionDetector.kt:14）：移动方向检测（`ScanDirection`）。
- `ImuMotionDetector`（util/ImuMotionDetector.kt:22）：IMU 防抖（VM L5 构造时注入 application）。

流程：`startStreaming`(L84) → CameraX `processFrame(ImageProxy)`（L186，经 `MLKitFrameProcessor`——已委托 Decoder 门面，util/FrameProcessor.kt）→ `onFrameDetected`(L193) 更新 tracker/grid → 稳定条码经 BarcodeStatusAssigner 记入 `ScanSession`（models/ScanSession.kt:48，含 `ScanCheckpoint` 断点 L9、`ScanConfig` L19、`ScanStatistics` L31、`GridCellData` L97）→ `pause`(L100)/`resume`(L142) 断点续扫 → `confirm`(L161) 输出确认集合。录像：`VideoRecordHelper`（util/VideoRecordHelper.kt:19，camera-video）用于留存证据视频。

UI：`VideoStreamScreen`（screen/VideoStreamScreen.kt）+ `VideoStreamOverlay`（overlay，绿框/红框/网格绘制）。

## 6. 静态图单扫与实时扫码（ui/analyzer）

- `ScanScreen`（ScanScreen.kt）：模式切换（条码/OCR），回调出口 `onScanResult: (String) -> Unit` 回传给上游页面；相册静态图识别走 `QrScanner.recognizeQRCode(bitmap)`（Decoder 全链，utils/QrScanner.kt）。扫描框为 `ResizableScanOverlay`：中心对称四角框 + 右下角拖拽手柄（交互参考 BinaryEye DetectorView），**无扫描线动画**；条码/OCR 框尺寸以归一化比例各自记忆（SettingsRepository `scanFrameRatioW/H`、`ocrFrameRatioW/H`，设置页已无扫描框尺寸项，比例未设置时默认 260dp / 300×80dp）；拖拽期间帧分析暂停（isResizing）。
- `ResizableScanOverlay`（ResizableScanOverlay.kt）：遮罩 + 四角绘制；拖拽开始 `onResizeStart` 暂停解码，松手 `onResizeEnd` 回调归一化比例由调用方持久化；框尺寸按当前容器比例还原，横竖屏/换机自适应。
- `SimpleFloatingScanner`（SimpleFloatingScanner.kt:34）：浮窗实时扫码——CameraX `ImageAnalysis` + `STRATEGY_KEEP_ONLY_LATEST`，`PreviewView` FILL_CENTER/COMPATIBLE。
- `BarcodeAnalyzer`（BarcodeAnalyzer.kt:24）：ImageAnalysis 帧分析器（DetectedBarcode）。
- `ScanRegionMapper`（ScanRegionMapper.kt:9）：图像坐标 ↔ 视图坐标 ROI 换算（裁剪区域与屏幕显示映射）。

### 6.1 盘库扫码：微信级 1:1 无缝定格多码选择（ScanScreen.kt + BarcodeAnalyzer.kt）
> **【状态】：2026-10-16 重构完成，单元测试与编译通过，【待真机测试验证】**

- **业务范围**：仅针对盘点详情主页的盘库扫码（`ScanScreen.kt`）生效，受系统设置 `UserSettings.enableMultiScan` 独立控制，**绝不影响批量扫描与补扫弹窗**。
- **痛点与问题根因**：
  - 旧版采用 `imageProxy.toBitmap()` 耗时大且按 `ContentScale.Fit` 渲染，产生黑边且定格瞬间画面尺寸“缩放抽动”；
  - 缺乏多帧确认，单帧偶发误识别出 2 个码时强制定格打断扫码；
  - 归一化公式未按 CameraX `FILL_CENTER` 逆变换补偿居中裁剪偏移，导致绿圈指示偏移实物码。
- **重构实现**：
  1. **2 帧连续识别防抖门槛**：`BarcodeAnalyzer.kt` 引入 `consecutiveMultiFrames >= 2`，单帧光影闪烁或杂讯误识别不再触发定格。
  2. **1:1 原地抓帧（零黑边、零跳变）**：`BarcodeAnalyzer` 通过 `getPreviewBitmap = { previewViewRef?.bitmap }` 在主线程直接截取当前屏幕视图渲染层，在 Compose 层以 `ContentScale.FillBounds` 铺满容器，实现**定格瞬间与相机画面在视觉上 100% 贴合，零缩放跳跃、零黑边**。
  3. **精确几何映射**：反算 `FILL_CENTER` 缩放/居中裁剪逆变换矩阵，将 ML Kit 坐标精确映射到 View 视图像素并归一化为 `(normX, normY)`，确保锚点与实物码 100% 居中覆盖。
  4. **微信绿呼吸水波纹锚点与极简交互**：`FrozenSelectionOverlay` 采用 `#07C160` 微信绿实心圆钮 + 持续扩散的半透明呼吸水波纹脉冲（1000ms 循环）；触控热区扩展至 56dp，点击带有触觉振动反馈（`HapticFeedbackType.LongPress`）；全屏覆盖 35% 柔和暗化遮罩，**轻触任意空白处立即取消并解冻恢复实时扫码**；顶部常驻微提示胶囊。
- **【待测试 Checklist】**：
  - [ ] 开启「设置-多码选择」，进入盘库扫码，镜头对准 2~3 个条码，验证画面是否无缝 1:1 原地定格（无黑边、无跳变）。
  - [ ] 观察微信绿锚点是否准确覆盖在各实物码中心，外圈是否有呼吸水波纹扩散动效。
  - [ ] 点击某一锚点，验证对应条码是否精准选中并入库/搜索，带震动反馈。
  - [ ] 定格后点击画面任意非按钮空白处，验证是否立刻解冻恢复实时取景。
  - [ ] 快速掠过多码，验证单帧闪烁是否不会误触发强制定格。

### 6.2 批量扫描·补扫悬浮窗：同内容实物码空间追踪去重（CodeTracker.kt + FloatingRescanOverlay.kt）
> **【状态】：2026-10-16 重构完成，7 组脱机单元测试与编译通过，【待真机测试验证】**

- **业务背景**：IVD 试剂场景下，同批次的多件实物耗材上的条码文本内容完全相同。操作员手持移动连续扫描货架时，无法按文本内容去重，且旧版 2 秒时间冷却（`SAME_CODE_COOLDOWN_MS`）与文本去重机制会导致正常连扫被误吃或卡顿。
- **重构实现**：
  1. **空间多目标追踪器（`batchscanner/task/tracking/CodeTracker.kt`）**：
     - **坐标与尺寸**：在旋转正向分析图像坐标系中运作，基于 ML Kit 返回的 `cornerPoints` 4 角点计算几何中心 $(cx, cy)$ 与边长均值；
     - **最近邻贪心匹配**：同内容 && 欧氏距离 $dist \le size \times 0.8$ 判定为同一实物码轨迹；
     - **抗幽灵码与单计保证**：`minHits = 2` 过滤单帧误读，实物码稳定出现 2 帧后计数 +1；每个实物码 Track 终身仅计一次（`counted = true`）；
     - **超时与遮挡容忍**：采用 `imageInfo.timestamp` 纳秒级时间戳，700ms 丢失超时清理，容忍操作员手指或货架短暂遮挡；
     - **单元测试**：`CodeTrackerTest.kt` 包含 7 组脱机测试（原地停留 20 帧单计、单帧幽灵码过滤、同内容多码并存各计、货架平移、短暂遮挡、超时新建、重置）。
  2. **分析管线加固（`SimpleFloatingScanner.kt`）**：
     - 码制限定为 `FORMAT_DATA_MATRIX` 与 `FORMAT_CODE_128`，Scanner 实例单例复用并在 `DisposableEffect` 释放；
     - 后台单线程使用 `Tasks.await` 同步处理，在 `finally` 中严格 `imageProxy.close()`，杜绝管线卡死。
  3. **业务层双模去重切换（`FloatingRescanOverlay.kt`）**：
     - **彻底移除 2 秒冷却**：删除 `SAME_CODE_COOLDOWN_MS = 2000L` 及 `lastCodeTime`；
     - **双模胶囊开关（右下角）**：
       - **开启（绿色）**：**内容去重模式**。基于 `dedupHistory` 集合防重，相同内容的码只入库一次；
       - **关闭（蓝色）**：**空间追踪去重模式**。纯靠 `CodeTracker` 空间轨迹，同一实物码停留不复计，平移到同内容新实物码各计一次；
     - 点击胶囊切换模式或长按扫码按钮重置时，同步重置 `CodeTracker` 与视觉高亮。
- **【待测试 Checklist】**：
  - [ ] 打开补扫悬浮窗，胶囊切为**关闭（蓝色·空间追踪）**。
  - [ ] 镜头对准两盒内容完全相同的实物试剂，先扫第一盒再平移扫第二盒，验证两盒是否均被正常计件入库（计数 +2）。
  - [ ] 镜头悬停在第一盒上停留 3 秒以上不动，验证是否严格保持只计 1 次，绝不重复计数。
  - [ ] 胶囊切为**开启（绿色·内容去重）**，扫描第二盒同内容试剂，验证是否被内容防重拦截（提示读到但不重复计件）。
  - [ ] 验证单击单发武装扫码、长按 1.5s 锁定连续扫码各模式下的空间追踪去重是否流畅。

## 7. OCR 子系统

### 7.1 词典分词（ocr/）
- `MlKitTextRecognizer`（ocr/MlKitTextRecognizer.kt:13）实现 `TextRecognizer` 接口（ocr/TextRecognizer.kt:5）；全局 `OcrProvider`（ocr/OcrProvider.kt:8）在 `InventoryApplication.onCreate` 初始化（InventoryApplication.kt:14）。
- `JiebaTokenizer`（ocr/JiebaTokenizer.kt:7）+ `JiebaDictionaryManager`（ocr/JiebaDictionaryManager.kt:12）+ `DictionaryManager` 接口（ocr/DictionaryManager.kt:6）：用户可导入自定义词典辅助分词纠错。
- 模型：`ocr/OcrModels.kt`（OcrResult = Success/Empty/Error 密封类 L19-22）。
- UI 侧：`OcrViewModel`（ui/analyzer/OcrViewModel.kt:20，UiEvent L67：DictImportSuccess/Error）+ `WordSelectionSheet`（词选择底部弹层）。

### 7.2 OCR 信息提取（batchscanner/ocr/）
`OcrInfoExtractor.extract(text, aiService?)`（batchscanner/ocr/OcrInfoExtractor.kt:39）：
1. **规则优先**：批号正则组（LOT/批号/生产批号 等，L18-28）→ 效期正则组（EXP/有效期/Use By 等，L30-36）。
2. **AI 兜底**：规则提取不全且 aiService 可用 → `extractByAi`（L95，构造中文 prompt 要求返回 batch/prodDate/expDate JSON）→ `JsonExtractor`（utils/JsonExtractor.kt:10）容错解析。
- 结果模型 `OcrExtractedData`（models/OcrModels.kt:7，ExtractSource 标记来源）。
- `OcrFuzzyMatcher.fuzzyMatch(a, b, charMap)`（OcrFuzzyMatcher.kt:26）：字符映射表（如 O↔0）+ 相似度模糊匹配，用于 OCR 批号 vs 单据批号。
- `OcrNormalizer`（OcrNormalizer.kt:10）：日期格式归一（依赖 DateUtils）。
- UI：`OcrCropOverlay`（选框）+ `OcrResultDialog`（结果确认）。

## 8. 图片存储

`utils/ImageStorageUtils.kt:15`：扫码照片存应用私有目录（拍照批量扫码的轮播图 + PDF 照片页来源）；`utils/BitmapUtils.kt` 压缩/回收辅助。PDF 水印设置（utils/pdf/WatermarkSettingsScreen.kt，路由 `watermark_settings`）作用于导出照片。

## 9. 已知风险/怪癖（详见 TECH_DEBT）

- 高分辨率小码：YOLO 走 1920 缩略图可能漏检小码 → 靠路径 B 金字塔兜底（性能换准确率）。《yolo方案优化.md》提出滑动窗口方案未落地。
- `ImagePreprocessor.perspectiveCorrect` 未接线（遮挡/倾斜码无透视矫正）。
- ML Kit scanner 已统一为 `Decoder` 单例（进程级存活，QRCodeScannerUtil.close 等均为空实现，BatchScannerViewModel 既有调用无需改动）。
- libdmtx 定位：只服务 OpenCV 路径 ROI 解码（VisionManager 链）、相册静态图与 legacy 局部补扫（Decoder.decodeStill）；金字塔顶层/瓦片与所有实时帧仅 ML Kit——libdmtx 在无码图上穷尽式扫描（秒级），逐片/逐帧兜底曾把降级路径拖慢数十倍（2026-09-06 回归修复）；native 调用经全局 Mutex 串行（LibDmtxDecoder companion），灰度转换不依赖 OpenCV。
- 大图多次 createBitmap 有 OOM 防护（try-catch，QRCodeScannerUtil.kt:231-239）但 Bitmap 回收依赖手工 recycle。
