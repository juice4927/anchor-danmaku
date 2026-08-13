# 优化方案 v2（P0–P3 全部完成）

P0 七项可靠性修复、P1 八项性能热路径、P2 五项构建工具链、P3 五项产品体验全部实施并通过 `verifyAll` 全门（详见 `docs/implementation-log.md` 阶段 14/15）。提交记录：`31ea2c6`（P0）、`d36504e`（P1）、`80c154f`（P2）、`9e1cefb`（P3）。

## P0 正确性与可靠性 ✅

1. 去重窗口 0→10s + 容量触发式惰性清理（`MessageDeduplicator`）。
2. 合并窗口 4,096 上限 + 先过期后淘汰（`DanmakuCoalescer`）。
3. WebSocket 事件通道有界（256 + DROP_OLDEST，idle 看门狗兜底）。
4. 通知更新节流：首次 startForeground 后仅内容变化时 `notify()`。
5. 文档与代码安全边界对齐（32 MiB / 20,000 子包）。
6. 置顶倒计时组件内 1s ticker。
7. git init + CI workflow + .gitignore/.gitattributes。

## P1 性能与热路径 ✅

- **P1-1** 删除双套消息模型：UI 直接消费 core `LiveMessage`，删除 `AnchorModels` 消息类与双向转换，展示文案收敛为共享扩展。
- **P1-2** `MessageRow` 移除 `IntrinsicSize.Min`（Box + matchParentSize 色条）。
- **P1-3** Connected 状态写入 200ms 节流 + 秒级 flush 兜底；`RoomUiState.nowMillis` 死字段删除。
- **P1-4** WBI keys 30 分钟 TTL 缓存。
- **P1-5** OkHttp 超时配置统一到 AppContainer（HTTP/WS 各一份）。
- **P1-6** nowMillis 死字段清理（随 P1-3）。
- **P1-7** Money 字符串往返随模型合并消除（UI 直接用 `toDisplayString` 毫单位格式化）。
- **P1-8** 重要消息判定收敛为 `LiveMessage.isImportant(highlightGiftThreshold)`（置顶/提醒共用）。

## P2 构建与工具链 ✅（版本号升级项待联网）

- **P2-1** `compilerOptions` DSL 迁移完成；**Kotlin 2.x / AGP 8.6+ / targetSdk 35 / BOM 升级因离线环境无新 artifact，标记待联网执行**。
- **P2-2** 签名配置可选化（无 keystore 不阻断配置，release 产出未签名 APK）。
- **P2-3** Configuration Cache 开启；5 个门禁任务重构为 inputs 声明式 `VerificationTask`。
- **P2-4** 手写 Baseline Profile（`L<类>` 类规则，规避 AGP 8.5 通配符展开 bug）。
- **P2-5** icons-extended → icons-core + 6 个自绘 vector；**Debug APK 17.5MB → 10.6MB**。

## P3 产品与体验 ✅

- **P3-1** 重要提醒专用 `IMPORTANCE_HIGH` 渠道；无权限时硬件兜底。
- **P3-2** 连接页"继续连接"恢复引导 + 输入框预填 + 深链预填（START_STICKY 取舍记录在实施日志）。
- **P3-3** 消息长按菜单（复制/拉黑）+ 重连"可能遗漏消息"横幅。
- **P3-4** `bilibili://live/<id>` 深链（门禁同步放宽并收紧校验）+ 屏幕方向设置 + 通知单色图标。
- **P3-5** Android 13+ 通知权限一次性解释引导。

## 验证

- 每批 `verifyAll` 全绿（单元测试 164+ 项 0 失败、覆盖率门、Lint、fixture/权限/hygiene 门、perfSmoke、APK 检查）。
- Configuration Cache 命中；Debug 10.6MB、Release 1.5MB。
- 环境配方见 `docs/implementation-log.md` 阶段 14（ASCII junction + GRADLE_USER_HOME/LOCALAPPDATA 重定向 + Robolectric 完整权限）。

## 遗留（需联网/真机）

1. 工具链版本升级：Kotlin 2.x + Compose 编译器插件、AGP 8.6+、targetSdk 35、Compose BOM（当前环境无新 artifact 且无网络）。
2. 真机人工门：深链跳转、方向切换、渠道提醒声音策略、OEM 后台、WS callTimeout 静默期验证（见 `docs/manual-release-checklist.md`）。
