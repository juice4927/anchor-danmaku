# 优化方案（项目盘点）

基于对全量源码、构建脚本、测试与文档的审阅。项目整体质量很高：模块边界清晰、协议边界防护完善、离线验证门完整（152 项 JVM 测试、覆盖率门、Release 卫生门）。以下按优先级列出可优化项，P0 为正确性/可靠性问题，P1 为性能热点，P2 为构建与工具链，P3 为产品增强。

## P0 正确性与可靠性

1. **去重窗口为 0，去重实际不生效**
   `MessageDeduplicator.WINDOW_MILLIS = 0L`，只有同毫秒内相同 id 的消息才会被去重，跨毫秒的重复帧（重连、TCP 重发）会重复进入管线（部分被 3 秒合并器合并成 ×N，超出窗口的会重复展示）。
   建议：窗口改为 10~30 秒，或改用内容指纹（uid+text+serverTimestamp）做去重键。

2. **弹幕合并窗口表无限增长**
   `DanmakuCoalescer.windows` 是 `linkedMapOf`，只按 key 覆盖、从不按时间清理。高流量直播间运行数小时后，不同 (uid, text) 组合会累积成千上万条目，长期占用内存。
   建议：`coalesce()` 时惰性清理过期窗口（lastSeenAt 超过窗口即删除），并加容量上限（如 4096，超出淘汰最旧）。

3. **WebSocket 事件通道无界**
   `BiliWebSocketSession` 使用 `Channel(UNLIMITED)`。若下游解析/消费短暂变慢（如 GC、主线程繁忙），帧会在通道中无界堆积，极端高流量下有 OOM 风险。
   建议：有界通道（如 4096）+ 丢最旧策略，或在解码端做合并节流。

4. **前台服务每个状态变化都调用 startForeground**
   `ConnectionForegroundService.ensureNotificationSync` 用 `collectLatest { startForeground(...) }`，而高流量房间每个数据包都会触发 `SessionState.Connected.copy(lastFrameAtMillis=...)` 状态更新，即每秒几十上百次 startForeground + Binder 事务，耗电且可能造成通知抖动。
   建议：首次 startForeground 后改用 `NotificationManager.notify()` 更新；并对通知内容做节流（内容未变化不更新，或去抖 1~2 秒）。

5. **文档与代码安全边界不一致**
   `docs/architecture.md` 声明"解压后最多 4 MiB、单帧最多 1000 个子包"，代码实际为 32 MiB（`BiliPacketCodec.Limits.maxDecompressedBytes`）与 20,000 子包。
   建议：收敛代码到文档声明的更严格限制（4 MiB / 1000），或同步修改文档，保证安全审查依据一致。

6. **置顶倒计时冻结**
   `RoomUiState.nowMillis` 只在消息/偏好事件到达时刷新，安静直播间"剩余 Xs"倒计时会停住。
   建议：RoomScreen 加 1 秒 ticker（LaunchedEffect 循环更新 nowMillis）。

7. **没有版本控制与 CI**
   工作区无 `.git`。建议 `git init` 并接入 CI（GitHub Actions 等），直接复用离线可跑的 `verifyAll` 作为 PR 门禁，产物留档。

## P1 性能与流畅度

8. **消息热路径重复对象映射**
   存在两套并行消息模型：`core:model` 的 `LiveMessage` 与 app 层的 `AnchorMessage`，并在 `AnchorSessionRepository.kt` 与 `RoomViewModel.kt` 各写了一份双向转换。每条消息都要做多态转换，且 `MessagePipeline.snapshot()` 每次全量重建可见列表，`applyPipelineState` 对每条消息全量 `map`（500 条容量 = 每次事件 500 次转换+分配）。50 msg/s 时约 2.5 万次转换/秒，是 UI 抖动的主要来源。
   建议：UI 直接消费 core 模型（纯数据类，无 Android 依赖），删除 `AnchorModels.kt` 重复层级与双向转换；展示文案用扩展函数。

9. **每个数据包都触发状态流写入**
   `BiliLiveGateway.collectFrames` 对每个 packet 发 `GatewayEvent.FrameReceived`，`SessionController` 每次写 `MutableStateFlow`（高流量房间每秒几十上百次），下游 UI 与通知同步被唤醒。
   建议：合并为"按 WebSocket 帧"粒度上报，或对 UI 可见的状态（人气、lastFrameAt）做节流（如 ≥200ms 才发一次）。

10. **WBI keys 每次重连都重新抓取**
    `BiliRoomApi.getDanmuInfo` 每次都先 `fetchWbiKeys()`（多一次 HTTP 往返），重连退避序列会放大请求量，也更容易触发 429。
    建议：带 TTL（如 30 分钟）缓存 img_key/sub_key。

11. **OkHttpClient 三份实例**
    `AppContainer` 建一个 client，`BiliRoomApi` 与 `BiliLiveGateway` 又各 `newBuilder()` 出一份（独立连接池与调度线程池）。
    建议：统一在 AppContainer 构造一份带各自超时配置的实例，共享连接池。另需真机确认：网关 client 的 `callTimeout(10s)` 对 WebSocket 长连接是否会在静默期误杀（应用层已有 idle 检测与心跳，可考虑去掉 WS 的 callTimeout 只保留 connectTimeout）。

## P2 构建与工具链

12. **工具链版本偏旧**
    Kotlin 1.9.24 + Compose 编译器 1.5.14（`composeOptions.kotlinCompilerExtensionVersion` 已属废弃路径）、AGP 8.5.2、compileSdk/targetSdk 34、Compose BOM 2024.06.00。
    建议：Kotlin 2.x + `org.jetbrains.kotlin.plugin.compose`、AGP 8.7+、compileSdk/targetSdk 35（商店新版本要求）、Compose BOM 更新；`kotlinOptions` 迁移到 `compilerOptions`。升级后需跑完整 verifyAll 回归。

13. **签名配置阻断本地构建**
    `app/build.gradle.kts` 在配置期强制要求 `keystore.properties` 存在，否则整个工程（含 debug 构建）无法配置。新克隆环境没有该文件会直接失败。
    建议：签名改为可选——文件存在则用 release 签名，否则 release 构建使用 debug 签名并在产物名标注 unsigned；真实发布签名由 CI secret 注入。

14. **可尝试开启 configuration cache**
    `gradle.properties` 已开 parallel/caching。自定义 verifyAll 门禁任务（fixture/permission/hygiene）在 doLast 里直接读写文件，需先声明 inputs/outputs 或改造成任务，才能安全开启 configuration cache 以加速本地与 CI 构建。

15. **Debug APK 偏大（17.4 MB）**
    主要来自 `compose-material-icons-extended`。release 已 R8 裁剪到 1.5 MB，若在意 debug 安装体验可换 `material-icons-core` + 自绘矢量图标。

16. **无 Baseline Profile**
    高流量 LazyColumn 滚动是本应用的核心交互，建议新增 Macrobenchmark + Baseline Profile 模块，显著改善启动与滚动掉帧。

## P3 产品与体验

17. **进程被杀后不恢复**：`START_NOT_STICKY` 且房间号不持久化。可选：持久化最近房间 + 通知栏"恢复连接"；注意 Android 14+ FGS dataSync 后台启动限制。
18. **点击弹幕拉黑用户**：目前拉黑只能进设置页，弹幕列表直接点击拉黑更自然。
19. **长按复制弹幕/SC 文本**。
20. **B 站 App 深链**：`bilibili://live/<id>` intent filter，从 B 站直接跳入房间。
21. **横屏/第二屏体验**：沉浸模式、可选的强制横屏设置项。
22. **重连后提示"可能遗漏消息"**：`mayHaveMissedMessages` 已建模但 UI 未展示。
23. **通知小图标**：当前用 adaptive icon mipmap 作 smallIcon，建议补一个专用单色 vector 通知图标。
24. **通知权限被拒的引导**：Android 13+ 拒绝 POST_NOTIFICATIONS 后 FGS 通知不可见，连接状态只能靠 App 内查看，可加一次性的解释引导。

## 建议执行顺序

1. P0 第 1~6 项（正确性与资源边界，改动小、有现成测试框架可回归）
2. P1 第 8~11 项（热路径性能，重点是删除双模型映射 + 通知节流）
3. P2 工具链升级与 CI 接入（需完整 verifyAll 回归）
4. P3 按产品优先级挑选

每项完成后补充对应单元测试并跑 `verifyAll` 全门回归。
