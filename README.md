# InventoryMaster

医疗器械仓库盘点 Android 应用：以 UDI 扫码为核心，覆盖**产品字典 → 盘点任务 → 扫码核对 → 局域网同步 → Excel/PDF 导出 → 金蝶单据核对归档**的完整闭环。

## 解决的问题

- 医疗器械 UDI 码（GS1 DataMatrix/QR/128，DI+批号+效期）密集、易破损，普通扫码难以应对——本项目用 **YOLO 定位 + OpenCV 增强 + ML Kit/libdmtx 双引擎解码 + 金字塔切片兜底** 的多级视觉管线解决。
- 账面（Excel/金蝶 ERP）与实物核对效率低——批量拍照扫码/视频流连续扫码，自动红绿框比对单据，数量/批号/效期逐字段判定。
- 院内多设备协作无公网——局域网 FastAPI 服务端同步（全量/增量、脏标记、双锚点、冲突确认）。

## 核心功能

| 功能 | 入口 |
|------|------|
| 盘点任务与库存账本（Excel 导入/导出、搜索降级链、DI 校验） | 主页 → 任务 |
| 批量拍照扫码 / 视频流连续扫码 | 批量扫描 flow |
| 金蝶单据核对（拉单 → 扫码比对 → PDF 归档 + 解码信息上传） | 批量扫描 → 单据 |
| 局域网同步（推送/拉取/全量上下行） | 任务列表/详情 |
| AI 助手（自然语言查库存、异常分析、盘点总结、UI 跳转） | AI 页 |
| OCR 标签识别（ML Kit 中文 + 规则/AI 提取批号效期 + Jieba 分词） | 扫码页 OCR 模式 |

## 技术栈

Kotlin 2.0 · Jetpack Compose + Material3 · Room 2.6 · DataStore · Retrofit/OkHttp · CameraX · ML Kit（条码+中文OCR）· OpenCV 4.10 · ONNX Runtime（YOLO）· libdmtx（NDK/CMake 自编译）· Apache POI · iText7 · Jieba · ZXing（二维码生成）

架构：手动 MVVM（无 Hilt），Repository 单一数据源，策略模式同步，两级 Navigation。配套服务端（仓库外）：`inventory_server`（FastAPI :8080 同步）、`pythonUDI`（FastAPI :8000 金蝶单据中转）。

## 项目结构

```
app/src/main/java/com/example/inventorymaster/
├── data/            Room 实体/DAO/Repository/网络/同步策略
├── viewmodel/       主业务 ViewModel（手动 Factory）
├── ui/              Compose 页面（home/inventory/productManager/analyzer/ai/...）
├── batchscanner/    批量扫码子系统（core 拍照 / videostream 视频流 / task 单据核对 / ocr）
├── ai/              AI Agent + 工具系统
├── ocr/             OCR 识别器 + Jieba 分词
└── utils/           GS1 解析 / 视觉管线(YOLO/OpenCV/libdmtx) / Excel / PDF / 工具
app/src/main/cpp/    libdmtx C 源码 + JNI 封装
```

## 如何运行

1. Android Studio 打开根项目（需 JDK 11、NDK 30.0.14904198、CMake 4.1.2）。
2. 真机运行 `app`（minSdk 29）。
3. 联调同步/单据功能需在设置页配置局域网服务端 IP+Token（服务端见上）。

## 如何构建

```bash
.\gradlew.bat assembleDebug      # 调试 APK
.\gradlew.bat assembleRelease    # 发布 APK（已签名配置）
.\gradlew.bat test               # 单元测试
```

## 文档入口（项目知识库）

| 文档 | 内容 |
|------|------|
| [AI_CONTEXT.md](AI_CONTEXT.md) | **AI 编程助手入口**：项目地图、行为约束、文档索引 |
| docs/ARCHITECTURE.md | 系统与分层架构、调用关系 |
| docs/BUSINESS.md | 业务视角：角色、域、端到端流程 |
| docs/DATABASE.md | Room 全表结构、约束、历史演变 |
| docs/API.md | 两个 Python 服务 + 云端 AI 全部接口 |
| docs/SCANNER.md | 扫码/视觉引擎（重点） |
| docs/UDI.md | UDI 解析与产品/库存匹配 |
| docs/SYNC.md | 同步机制、脏标记、锚点、冲突 |
| docs/AI.md | AI Agent 与工具系统 |
| docs/UI.md | 页面结构、路由、状态来源 |
| docs/DEVELOPMENT.md | 环境、构建、常见修改速查 |
| docs/TROUBLESHOOTING.md | 已知问题排查 |
| docs/TECH_DEBT.md | 技术债务（P0-P3） |
| [CHANGELOG.md](CHANGELOG.md) | 变更日志 |
