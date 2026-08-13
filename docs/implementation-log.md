# 实施日志

## 阶段 0：盘点与环境门禁

- 工作区起点：仅存在三份冻结计划，无 Android 源码，未初始化 Git。
- JDK：C:\Program Files\Android\openjdk\jdk-21.0.8，java -version 成功。
- Android SDK：C:\Users\Administrator\AppData\Local\Android\Sdk。
- 已验证 SDK Platform android-34 与 Build Tools 34.0.0 存在。
- 已验证 Gradle 8.7 缓存发行版存在。
- RED：工程尚不存在，无法执行 Gradle 项目与构建任务。
- GREEN：环境路径可用，冻结方案无产品开放项。
- 遗留风险：首次依赖解析需要从官方仓库下载 AGP、Kotlin 和 AndroidX 依赖。

## 阶段 1：Gradle 骨架

- 变更：根构建、版本目录、四模块构建、最小 Android Manifest/Activity/Application 和资源。
- RED：首次 wrapper 配置因非 ASCII 工作区路径检查和缺少本机 JDK 17 toolchain 失败。
- GREEN：显式允许已知工作区路径、使用 JDK 21 编译到 Java 17 字节码后，`wrapper` 与 `projects` 通过；四模块均被识别。
- 遗留风险：首次 `assembleDebug` 与并行 App 实现重叠，编译在代理尚未写完的类型上失败，待阶段 9 至 12 完成后重跑。

## 阶段 13：自动门禁与文档（初始接入）

- 变更：根任务实现 fixture、merged manifest 权限、Release hygiene、12,000 事件测试证据和 APK 大小检查；新增构建、隐私、架构、许可证和人工发布文档。
- RED：门禁初始任务只有任务名，没有实际断言。
- GREEN：根构建脚本配置验证通过；各门禁将在产物和测试落地后逐项执行。
- 遗留风险：最终覆盖率、Lint、Release APK 与 performance result 需等模块集成后验证。

## 阶段 13：自动门禁与文档（完成）

- 补齐协议与领域边界测试；最终 152 项 JVM 测试全部通过。
- 协议覆盖率达到 line 93.32%、branch 78.52%；领域覆盖率达到 line 92.98%、branch 76.17%，均通过 85%/75% 门槛。
- 性能烟测修正为精确 12,000 个事件，并显式约束 30 秒、重要事件完整性和 `criticalDropCount=0`。
- 修复认证回复为非对象 JSON 时的异常、OkHttp 安全 WebSocket URL 构造、缓冲统计发布以及 UI 丢弃诊断提示。
- 修复 Release hygiene 中的敏感字段常量、Manifest profile installer 移除声明与 Lint 冲突、Gradle/R8 3 GiB 堆配置。
- GREEN：`.\gradlew.bat --no-daemon clean verifyAll` 新鲜运行退出码 0；fixture、权限、Release hygiene、Lint、覆盖率、性能和 APK 门禁全部通过。
- 产物：Debug 17,391,693 bytes；unsigned Release 1,489,643 bytes；AndroidTest 997,729 bytes。完整 SHA-256 见 `docs/verification-report.md`。
- 未执行：真机 instrumentation、真实直播间、OEM 后台、8 小时长稳、生产签名与发布渠道门；人工清单保持未勾选。

## 阶段 14：P0 可靠性修复（优化方案第一批）

- 变更：去重窗口 0→10 秒并改为容量触发式惰性清理（`MessageDeduplicator`）；合并窗口新增 4,096 上限与"先过期后淘汰最旧"的准入清理（`DanmakuCoalescer`）；WebSocket 事件通道从无界改为容量 256 的 `DROP_OLDEST`（`BiliWebSocketSession`）；前台服务通知改为首次 `startForeground` + 内容变化时 `notify()` 的节流路径（`ConnectionForegroundService`/`NotificationController`）；置顶倒计时改为组件内 1 秒 ticker（`RoomScreen.PinnedCountdownLabel`）；`docs/architecture.md` 解压上限描述与代码对齐（32 MiB / 20,000 子包，fixture 佐证 32 MiB 为主动选择）。
- RED：默认 GRADLE_USER_HOME 指向系统用户目录时 wrapper 锁文件无法创建；Kotlin 守护进程无法在 `%LOCALAPPDATA%\kotlin\daemon` 写标记文件；fork 的测试执行器因非 ASCII 工作区路径报 `ClassNotFoundException: GradleWorkerMain`；Robolectric 需读写工作区外的 `~/.m2`。
- GREEN：本机测试环境配方（后续会话直接复用）——
  1. `GRADLE_USER_HOME=E:\CODEX工作区\bili弹幕软件\.gradle-user`；
  2. `LOCALAPPDATA` 重定向到 `.gradle-user\localappdata`（Kotlin 守护进程标记目录）；
  3. 从 ASCII junction `C:\Users\Administrator\AppData\Local\Temp\anchor-danmaku` 运行 gradle（fork 执行器 classpath 编码问题）；
  4. 含 Robolectric 的任务需工作区外 `~/.m2` 读权限（完整权限）。
- GREEN：新增 coalescer 容量淘汰/过期优先清理与通知 `notify()` 固定 id 共 3 条单元测试；domain/protocol/app 单元测试全量回归通过（合计 164 项，0 失败）。
- 变更：`git init` 初始提交 `31ea2c6`，新增 `.github/workflows/ci.yml`（JDK 21 + SDK 34 + CI 临时 keystore + `verifyAll` + APK 产物上传），`.gitignore`/`.gitattributes` 补齐。
- 遗留风险：通知节流的真机表现（Android 13+ 隐藏通知、OEM 后台）与高流量房间 256 帧丢弃策略仍属人工门范围。

## 阶段 15：P1/P2/P3 优化批次（按 optimization-plan v2 全量实施）

### P1 性能与热路径
- 删除双套消息模型：UI/Repository/ViewModel 直接消费 `core:model` 的 `LiveMessage`，删除 `AnchorModels.kt` 消息类与两份双向转换；展示文案（`displayName`/`describeMessage`）收敛为共享扩展。
- `MessageRow` 移除 `IntrinsicSize.Min`，改用 `Box + matchParentSize` 色条（免二次测量）。
- `SessionController` Connected 状态写入 200ms 节流 + idle 看门狗秒级 flush 兜底（保证人气等最终值收敛）；`RoomUiState.nowMillis` 死字段删除。
- WBI keys 30 分钟 TTL 缓存；OkHttp 超时配置统一收敛到 `AppContainer`（HTTP/WS 各一份）。
- 重要消息判定收敛为 `LiveMessage.isImportant(highlightGiftThreshold)`（置顶与提醒共用）。
- 回归：164+ 单元测试与 `verifyAll` 全绿；`SessionControllerEdgeCasesTest` 适配节流语义。

### P2 构建与工具链
- `kotlinOptions` 全量迁移到 `compilerOptions` DSL（Kotlin/AGP/Compose BOM/targetSdk 版本号升级因离线环境受限，标记待联网执行）。
- 签名配置可选化：`keystore.properties` 缺失不再阻断配置阶段，release 产出未签名 APK。
- Configuration Cache 开启：5 个自定义门禁任务重构为声明 inputs 的 `VerificationTask` 子类（修 inner-class、`from(Directory)` 语义、隐式依赖三处坑）。
- 手写 Baseline Profile（`L<类>` 类规则，规避 AGP 8.5 通配符展开 bug）。
- 图标瘦身：`material-icons-extended` → `material-icons-core` + 6 个自绘 vector；Debug APK 17.5MB → **10.6MB**。
- 回归：`verifyAll` 全绿，Configuration Cache 命中。

### P3 产品与体验
- 重要提醒改用 `IMPORTANCE_HIGH` 专用通知渠道（`anchor_important`），无通知权限时硬件提醒兜底。
- 消息长按菜单：复制文本 / 拉黑用户（管道过滤即时生效）；重连"可能遗漏消息"横幅。
- 深链 `bilibili://live/<id>`（门禁同步允许第二个 VIEW filter，且仅匹配该 scheme）；连接页输入框预填恢复。
- 屏幕方向设置（自动/竖屏/横屏），MainActivity 实时应用。
- 通知专用单色图标 `ic_stat_danmaku`（连接与提醒共用）。
- Android 13+ 通知权限一次性解释引导。
- 回归：`verifyAll` 全绿（Debug 10.6MB / Release 1.5MB，配置缓存命中，164+ 测试 0 失败）。
- 遗留：版本号升级（Kotlin 2.x/AGP/targetSdk 35/BOM）与真机人工门（深链跳转、方向切换、渠道提醒策略、OEM 后台）待联网/真机执行。
