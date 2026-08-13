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
