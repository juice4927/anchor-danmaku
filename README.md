# 主播弹幕台 (Anchor Danmaku)

[![CI](https://github.com/juice4927/anchor-danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/juice4927/anchor-danmaku/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green.svg)](#系统要求--requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.24-7F52FF.svg)](https://kotlinlang.org)

**中文** | [English](#english)

---

## 中文

**B 站直播弹幕的"现场信息台"**：主播或场控把它当作直播副屏——实时弹幕流、重点事件（SC / 礼物 / 上舰）优先展示与硬件提醒，**游客模式直连，无需登录 B 站账号**。

> 非官方第三方工具，与哔哩哔哩无隶属或合作关系；仅连接公开弹幕服务，不播放视频、不提供发言。

### 截图

| 连接页 | 房间页（真实弹幕） |
| --- | --- |
| ![连接页](docs/screenshots/connect.png) | ![房间页](docs/screenshots/room-live.png) |

| 演示模式 | 设置 | 关于 |
| --- | --- | --- |
| ![演示模式](docs/screenshots/room-demo.png) | ![设置](docs/screenshots/settings.png) | ![关于](docs/screenshots/about.png) |

### 功能特性

- **游客直连**：免登录连接公开直播间，支持短号/长号，自动记住最近 10 个房间
- **主消息流**：弹幕 / 醒目留言(SC) / 礼物 / 舰队 分类着色与标签，可暂停、清屏、跳转底部
- **重点事件**：SC、高额礼物、上舰置顶展示并带剩余时间，支持金额阈值
- **硬件提醒**：重要事件独立高优先级通知渠道 + 声音/震动，250ms 节流防轰炸
- **过滤体系**：关键词黑名单、用户拉黑（长按消息即拉黑）、普通弹幕满载优先淘汰
- **回放演示**：内置脚本化演示数据，无网络也能演示完整交互
- **观看体验**：B 站品牌色系深色主题、横竖屏锁定、保持屏幕常亮、消息合并去重
- **深链**：`bilibili://live/<房间号>` 直接拉起对应直播间

### 2025 协议适配说明

B 站直播弹幕协议近年持续收紧，本项目已跟进最新要求：

| 时间 | 变更 | 本项目适配 |
| --- | --- | --- |
| 2025-05 | `getDanmuInfo` 强制 WBI 签名（w_rid/wts） | ✅ 按社区规范实现 `BiliWbiSigner`，密钥按天缓存 |
| 2025-06 | 游客连接要求 Cookie 携带非空 `buvid3`，否则握手成功但弹幕被**静默过滤** | ✅ 首次连接从官方指纹接口获取匿名 buvid3/buvid4，仅存内存并贯通 HTTP/WS/认证包，认证体补 `support_ack`/`scene` 网页端字段 |
| 2025 | 未登录用户昵称脱敏（`某***`） | ✅ 直接展示服务端下发内容 |

匿名标识只保存在进程内存，退出即消失；不读取、不保存、不发送任何账号凭据。详见 [PRIVACY.md](PRIVACY.md)。

### 系统架构

```text
app (Android/Compose/DataStore)
 └─ core:protocol (B站 WebSocket 协议、WBI 签名、匿名身份)
     └─ core:domain (会话状态机、重连退避、消息管线端口)
         └─ core:model (纯 Kotlin 领域模型)
```

- 三个 core 模块均为纯 Kotlin/JVM，依赖方向单向，仅 `app` 接触 Android API
- 连接流：`room_init` → `getDanmuInfo`(WBI) → WSS 主机回退 → op=7 认证 → 30s 心跳
- 消息管线：去重 → 3s 合并 → 过滤/阈值 → 512 有界优先级缓冲 → 列表/置顶
- 防御性协议实现：16 字节大端包头、zlib/brotli 解压上限 32MiB、递归深度 ≤4、单帧子包 ≤20,000、主机白名单限 `*.bilibili.com`

更多细节见 [docs/architecture.md](docs/architecture.md) 与 [docs/implementation-log.md](docs/implementation-log.md)（17 个阶段的 TDD 实现记录）。

### 系统要求

- Android 8.0（API 26）及以上
- 网络可访问 B 站公开弹幕服务

### 从源码构建

需要 JDK 17–21 与 Android SDK 34（Build Tools 34.0.0）：

```bash
./gradlew.bat assembleDebug     # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat installDebug      # 安装到已连接设备/模拟器
```

运行完整质量门禁（单测 + JaCoCo 覆盖率阈值 + Lint + 三种 APK + fixture 校验 + 权限门 + 12,000 事件性能冒烟 + APK 尺寸门）：

```bash
./gradlew.bat verifyAll
```

> **Windows 用户注意**：若项目位于含中文的路径，fork 的测试执行器会因系统代码页问题报 `ClassNotFoundException`。请将仓库克隆/移动到纯 ASCII 路径后再运行测试（构建 APK 不受影响）。

### 使用

1. 打开应用，输入直播间号（短号或长号均可），点击"进入弹幕台"
2. 无网络环境可点击"回放演示"查看完整功能
3. 右上角进入设置：字号、消息容量、提醒阈值、关键词过滤、屏幕方向
4. 房间页底部工具栏：暂停 / 清屏 / 回到底部；返回键退出前会二次确认

### 项目状态

- 版本 0.1.0，处于快速迭代期
- CI：GitHub Actions（JDK 21）运行 `verifyAll` 全门禁
- 真实直播长稳测试不进入自动门禁，发布前需人工执行 [发布检查表](docs/manual-release-checklist.md)

### 参与贡献

Issue / PR 均欢迎。提交 PR 前请确保 `verifyAll` 通过；涉及协议行为的改动请一并补充对应 fixture 或单测。

### 免责声明

本项目为非官方工具，与哔哩哔哩无任何隶属或合作关系。仅连接 B 站公开弹幕服务，不提供登录、发言或视频播放能力；B 站接口变化可能导致功能暂时不可用。请遵守 B 站用户协议与当地法律法规，勿将其用于违规用途。

### 许可证

[MIT](LICENSE)

---

## English

**A "live information deck" for Bilibili live-stream chat**: use it as a second screen while streaming — real-time danmaku feed, pinned key events (Super Chat / gifts / guard), hardware alerts. **Connects as a guest, no Bilibili account required.**

> Unofficial third-party tool, not affiliated with Bilibili. Connects to the public danmaku service only — no video playback, no sending messages.

### Screenshots

| Connect | Room (live danmaku) |
| --- | --- |
| ![Connect](docs/screenshots/connect.png) | ![Room](docs/screenshots/room-live.png) |

| Demo replay | Settings | About |
| --- | --- | --- |
| ![Demo](docs/screenshots/room-demo.png) | ![Settings](docs/screenshots/settings.png) | ![About](docs/screenshots/about.png) |

### Features

- **Guest connection**: join public live rooms without logging in; supports short/long room IDs; remembers the last 10 rooms
- **Message feed**: color-coded danmaku / Super Chat / gifts / guard joins with tags; pause, clear, jump to bottom
- **Key events**: SC, high-value gifts and guard purchases pinned with countdown; configurable amount thresholds
- **Hardware alerts**: dedicated high-importance notification channel + sound/vibration, 250 ms throttle
- **Filtering**: keyword blacklist, user blocking (long-press a message), ordinary danmaku evicted first under load
- **Demo replay**: built-in scripted demo data — full experience without network
- **Viewing comfort**: Bilibili-branded dark theme, orientation lock, keep-screen-on, message merge/dedupe
- **Deep link**: `bilibili://live/<roomId>`

### 2025 protocol adaptations

| Date | Change | Adaptation |
| --- | --- | --- |
| 2025-05 | `getDanmuInfo` now requires WBI signing (w_rid/wts) | ✅ `BiliWbiSigner` per the community spec, keys cached daily |
| 2025-06 | Guest connections require a non-empty `buvid3` cookie, otherwise the handshake succeeds but danmaku is **silently filtered** | ✅ Anonymous buvid3/buvid4 fetched from the official fingerprint endpoint on first connect, kept in memory only, propagated through HTTP/WS/auth packet; web-client fields `support_ack`/`scene` added |
| 2025 | Nicknames masked for logged-out viewers (`某***`) | ✅ Server content is rendered as-is |

The anonymous identifier lives only in process memory and never touches account credentials. See [PRIVACY.md](PRIVACY.md).

### Architecture

```text
app (Android/Compose/DataStore)
 └─ core:protocol (Bilibili WebSocket protocol, WBI signing, anonymous identity)
     └─ core:domain (session state machine, reconnect backoff, message pipeline ports)
         └─ core:model (pure-Kotlin domain models)
```

- The three core modules are pure Kotlin/JVM with one-way dependencies; only `app` touches Android APIs
- Connect flow: `room_init` → `getDanmuInfo` (WBI) → WSS host fallback → op=7 auth → 30 s heartbeat
- Message pipeline: dedupe → 3 s merge → filter/threshold → bounded priority buffer (512) → list/pinned
- Defensive protocol implementation: 16-byte big-endian header, zlib/brotli decompression cap of 32 MiB, nesting depth ≤ 4, ≤ 20,000 child packets per frame, host allowlist limited to `*.bilibili.com`

See [docs/architecture.md](docs/architecture.md) and [docs/implementation-log.md](docs/implementation-log.md) (17 phases of TDD records).

### Requirements

- Android 8.0 (API 26)+
- Network access to Bilibili's public danmaku service

### Building from source

Requires JDK 17–21 and Android SDK 34 (Build Tools 34.0.0):

```bash
./gradlew.bat assembleDebug     # outputs app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat installDebug      # install to a connected device/emulator
```

Full quality gate (unit tests + JaCoCo thresholds + Lint + three APK variants + fixture validation + permission gate + 12,000-event perf smoke + APK size check):

```bash
./gradlew.bat verifyAll
```

> **Windows note**: if the project sits under a path containing non-ASCII characters, the forked test executor fails with `ClassNotFoundException` due to system code-page decoding. Clone or move the repository to a pure-ASCII path before running tests (building the APK is unaffected).

### Usage

1. Enter a room ID (short or long) and tap "进入弹幕台" (Enter danmaku deck)
2. Offline? Tap "回放演示" (demo replay) for the full experience
3. Settings (top-right): font size, message capacity, alert thresholds, keyword filters, orientation
4. Room toolbar: pause / clear / jump to bottom; back navigation asks for confirmation before disconnecting

### Status

- v0.1.0, actively developed
- CI: GitHub Actions (JDK 21) runs the full `verifyAll` gate
- Long-run stability on real streams is a manual pre-release step — see the [release checklist](docs/manual-release-checklist.md)

### Contributing

Issues and PRs are welcome. Please make sure `verifyAll` passes before submitting; protocol-behavior changes should come with fixtures or unit tests.

### Disclaimer

This is an unofficial tool, not affiliated with Bilibili. It only connects to the public danmaku service and provides no login, messaging, or video playback. Bilibili API changes may temporarily break functionality. Comply with Bilibili's user agreement and applicable laws.

### License

[MIT](LICENSE)
