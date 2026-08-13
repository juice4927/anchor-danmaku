# 优化方案 v2（P0 完成后）

P0 七项可靠性修复已全部完成并通过 `verifyAll` 全门（见 `docs/implementation-log.md` 阶段 14）。本版方案聚焦下一批可执行优化：P1 性能与热路径、P2 构建与工具链、P3 产品体验。每项标注了改动范围与验证方式，可直接按序实施。

## P1 性能与热路径（建议先做）

### P1-1 删除双套消息模型，UI 直接消费 core 模型 ⭐ 最高收益

现状：`core:model` 的 `LiveMessage` 与 app 层 `AnchorModels.kt`（`AnchorMessage`）完全平行，且存在两份双向转换（`AnchorSessionRepository.kt` 与 `RoomViewModel.kt` 各一份 `toCoreMessage`/`toAnchorMessage`）。每条消息都要多态转换，`applyPipelineState` 对全量列表（最多 500 条）逐条 map，高流量房间每秒数万次对象分配，是滚动抖动的主要来源。

方案：
- 删除 `AnchorModels.kt` 的 4 个消息 data class 与 `displayName()`，UI/Repository/ViewModel 直接使用 `core:model` 的 `LiveMessage`（纯 Kotlin 数据类，无 Android 依赖，UI 可用）。
- 展示文案（`displayName`、`pinLabel`、颜色映射、`describeMessage`）改为 `core:model` 的扩展函数，放在 app 的 ui 包。
- `AnchorConnectionState`/`AnchorFailureKind`/`ConnectionPhase` 是 UI 状态模型，保留（它们不是消息模型，且承载展示文本）。
- 验证：app 单元测试 + instrumentation 测试全部回归；`describeMessage` 等逻辑抽成纯函数后可补单测。

### P1-2 MessageRow 移除 `IntrinsicSize.Min`（新发现）

`MessageRows.kt` 中 `Row(Modifier.height(IntrinsicSize.Min))` + `Box(fillMaxHeight)` 让每一行都触发 intrinsic 测量（两次测量 pass），高流量 LazyColumn 滚动下是真实的组合开销。改用固定宽度 4dp 的 Box + `align(Alignment.Top)`（或直接去掉 fillMaxHeight），测量成本降到 O(1)。

### P1-3 状态流写入节流：每包一次 FrameReceived

`BiliLiveGateway.collectFrames` 对每个 packet 发 `GatewayEvent.FrameReceived`，`SessionController` 每次写 `MutableStateFlow`，高流量房间每秒几十上百次状态更新，下游 UI、通知、`addRecentRoom` 逻辑都被唤醒。方案：
- `FrameReceived` 合并为按 WebSocket 帧上报（一次帧一个事件），或对 `lastFrameAtMillis`/`popularity` 更新做 ≥200ms 节流（节流计数仍保留语义：idle 检测用最后一次真实帧时间即可）。
- 通知侧已由 P0-4 节流兜底，此优化主要省掉状态流写放大与 Compose 重组。

### P1-4 WBI keys TTL 缓存

`BiliRoomApi.getDanmuInfo` 每次（含每次重连）都先 `fetchWbiKeys()`，多一次 HTTP 往返并放大 429 风险。B站 keys 按天轮换，缓存 30 分钟足够。加一个 `kotlinx.coroutines` 无关的简单 TTL 缓存（时间戳 + 原子写），失败时不缓存（保持现有回退语义）。

### P1-5 OkHttpClient 实例合并

`AppContainer` 建一份 client，`BiliRoomApi` 与 `BiliLiveGateway` 又各 `newBuilder()` 出一份（三个连接池/线程池）。改为 AppContainer 统一构造并注入；`BiliLiveGateway` 的 WS 超时（readTimeout=0、callTimeout=10s）需真机确认 callTimeout 不会在静默期误杀 WS（应用层已有 idle 检测，可考虑只留 connectTimeout）。

### P1-6 清理 P0 遗留的死字段（新发现）

`RoomUiState.nowMillis` 在 `PinnedCountdownLabel` 自维护 ticker 后已无 UI 读者，仅 `applyPipelineState` 还在赋值。删除该字段与其赋值，减少每次消息事件的拷贝面。

### P1-7 Money 字符串往返清理（新发现）

`RoomViewModel.toCoreMessage` 中 `Money.fromCny(priceCny.toString())`、`Money.fromCny(it.toString())` 走字符串转换，分配且可能引入精度噪音。给 `Money` 增加 `fromMilliYuan`/`fromYuanDouble`（内部按毫单位换算，不经过字符串），UI 侧反向用 `milliYuan` 算术。`describeMessage` 的 `"%.2f".format` 可保留（非热路径）。

### P1-8 重要消息判定逻辑去重（新发现）

`AnchorSessionRepository.isImportant`（驱动提醒）与 `MessagePipeline`/`MessageFilter.filter.important`（驱动置顶）是两套平行判定，容易漂移（如未来给 SC 加金额阈值会只改一处）。把判定收敛到 `core:domain` 单一函数（如 `FilterDecision.important` 或 `LiveMessage.isImportant(preferences)`），Repository 与 Pipeline 共用。

## P2 构建与工具链

### P2-1 工具链升级（一次性、需完整 verifyAll 回归）

- Kotlin 1.9.24 → 2.1.x，Compose 编译器随 Kotlin（`org.jetbrains.kotlin.plugin.compose`），删除 `composeOptions.kotlinCompilerExtensionVersion`。
- AGP 8.5.2 → 8.7+（配合 Gradle 8.11+）；`compileSdk`/`targetSdk` 34 → 35（商店对新版本要求）；Compose BOM 2024.06 → 2025.x。
- `kotlinOptions` 迁移到 `compilerOptions` DSL。
- 风险：Compose API 兼容性、AGP 行为变化；验证靠现有 verifyAll 门 + 真机清单。

### P2-2 签名配置可选化

`app/build.gradle.kts` 配置期强制要求 `keystore.properties` 存在，否则整个工程（含 debug）无法配置。改为：文件存在 → release 用正式签名；缺失 → 用 debug 签名并命名 `app-release-unsigned.apk`（与现状产物名一致）。CI 保留生成临时 keystore 的步骤（也可删除，若本地可构建则 CI 同样可）。

### P2-3 Configuration Cache

自定义 verifyAll 门禁任务（`protocolFixtureCheck`、`permissionAllowlistCheck`、`releaseHygieneCheck`、`apkSizeCheck`、`perfSmoke`）在 doLast 里直接读写文件，需先声明 inputs/outputs（或改用 `@TaskAction` + `@InputFiles`/`@OutputFile`），再开启 `org.gradle.configuration-cache=true`。收益：本地与 CI 配置阶段从秒级降到毫秒级。

### P2-4 Baseline Profile + Macrobenchmark

滚动密集型应用的核心体验在 LazyColumn 高流量场景。新增 `:macrobenchmark` 与 `:baselineprofile` 模块（或仅手写 profile 规则），生成 Baseline Profile 打进 release。配合现有 12k 事件烟测，可量化滚动掉帧改进。

### P2-5 Debug APK 瘦身

17.4MB 主要来自 `compose-material-icons-extended`。换 `material-icons-core`（当前用到的图标：ArrowBack、CleaningServices、Pause、PlayArrow、Refresh、Settings、South 都在 core 或可自绘），Debug 体积可降 8-10MB，release 不受影响（R8 已裁剪）。

## P3 产品与体验

### P3-1 重要提醒改用专用通知渠道（新发现）

现状：`AndroidReminderSink` 用 `RingtoneManager` 直接 `play()` 默认通知音 + 手动震动。问题：不受系统通知音量/免打扰策略管理，用户无法按渠道关闭声音，OEM 上行为不一致。方案：新增 `IMPORTANCE_HIGH` 渠道（如 "anchor_important"），提醒时 `notify()` 一条临时通知（自动消失或带文案），系统负责声音/震动策略；直接 play 作为渠道不可用时的兜底。P0-4 的通知节流同样适用于提醒通知。

### P3-2 进程被杀后恢复

`START_NOT_STICKY` + 状态不持久化：进程被杀后连接消失，用户要手动重进。方案：持久化最近房间号（DataStore 已有 recentRooms），启动页提示"恢复上次连接"；或 `START_REDELIVER_INTENT`（注意 Android 14+ FGS dataSync 后台启动限制，需验证系统重启场景）。

### P3-3 消息交互增强

- 点击弹幕行 → 拉黑该用户（当前只能进设置页操作）。
- 长按 → 复制文本（弹幕/SC/礼物）。
- 重连后 `mayHaveMissedMessages=true` 时顶部提示"连接中断，可能遗漏消息"（字段已建模，UI 未用）。

### P3-4 深链与第二屏体验

- intent filter `bilibili://live/<id>`，从 B站 App 直接跳入。
- 沉浸模式 + 设置项"强制横屏"（第二屏/平板场景）。
- 通知 smallIcon 换专用单色 vector（当前用 adaptive icon mipmap，部分系统渲染成空白方块）。

### P3-5 权限引导

Android 13+ 拒绝 POST_NOTIFICATIONS 后 FGS 通知不可见、提醒不可用。连接页加一次性解释（"用于显示连接状态与重要消息提醒"），拒绝后可再次引导到设置。

## 执行顺序建议

1. P1-1（双模型删除）+ P1-2 + P1-6 + P1-7 + P1-8：同属消息链路重构，一次性做完并跑 verifyAll。
2. P1-3 + P1-4 + P1-5：网络/状态节流。
3. P2-1 工具链升级（改完必须全量回归）。
4. P2-2/P2-3 构建体验。
5. P3 按产品优先级挑选；P3-1 提醒渠道建议随 P1 批次做（与通知节流同域）。

## 验证与环境

- 每批改动后跑 `verifyAll`（本机环境配方见 `docs/implementation-log.md` 阶段 14：ASCII junction + GRADLE_USER_HOME/LOCALAPPDATA 重定向 + Robolectric 完整权限）。
- 真机项保持人工门：WS callTimeout、OEM 后台、提醒渠道声音策略、深链跳转。
