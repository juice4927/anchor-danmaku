# 主播弹幕台

主播弹幕台是一款面向 Android 的 Bilibili 直播弹幕第二屏工具。它以游客身份连接单个公开直播间，只展示弹幕、醒目留言、礼物、上舰与人气值，不播放视频，也不提供登录或发言功能。

## 功能

- 输入长号或短号，解析真实直播间后连接弹幕节点。
- 普通弹幕、SC、礼物、上舰分级展示，重要消息置顶并可提醒。
- 关键词、用户和礼物金额过滤，重复合并、事件去重与有界缓冲。
- 暂停可见消息流、清屏、自动跟随、字号与消息容量设置。
- 前台服务持有唯一连接，断线后按固定退避序列重连。
- Debug 构建提供完全离线的“回放演示”，Release 不包含 fixture。

## 构建

需要 Android SDK 34、Build Tools 34.0.0 和 JDK 17 至 21。Windows 完整自动验收：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon clean verifyAll
```

主要产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release-unsigned.apk`
- AndroidTest：`app/build/outputs/apk/androidTest/debug/`

## Debug 回放

安装 Debug APK 后，在连接页选择“回放演示”。演示只使用构建时生成的脱敏事件，不访问真实 B站接口，不需要账号、直播间或付费行为。Release 构建不显示该入口，也不会打包 `fixtures/bilibili`。

## 工程结构

- `core:model`：领域消息、金额、房间、错误和设置模型。
- `core:domain`：会话状态机、重连和消息处理策略。
- `core:protocol`：B站 HTTP/WebSocket、包编解码、压缩和命令映射。
- `app`：DataStore、前台服务、通知/震动、Compose UI 和依赖装配。

详细设计见 [docs/architecture.md](docs/architecture.md)，自动验收见 [docs/verification-report.md](docs/verification-report.md)。

## 局限

- B站公开弹幕协议并非稳定的正式开放接口，协议变化可能要求更新应用。
- 游客态可能缺少部分昵称、粉丝牌或金额字段，应用会降级展示。
- 前台服务只能尽力保持连接，不能保证绕过 OEM 后台限制。
- 真机长稳、OEM 后台、电量、真实直播和真实付费事件属于发布前人工验证。

## 非官方声明

主播弹幕台是非官方第三方工具，与哔哩哔哩无隶属或合作关系。应用仅在本机连接公开直播弹幕服务，不提供登录、发言或视频播放。B站接口变化可能导致功能暂时不可用。

