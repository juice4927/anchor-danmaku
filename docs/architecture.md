# 架构说明

## 边界

工程由一个 Android 模块和三个纯 Kotlin/JVM 模块组成：

```text
app -> core:protocol -> core:domain -> core:model
app ----------------> core:domain -> core:model
app --------------------------------> core:model
```

`core:model` 不依赖 Android 或网络库。`core:domain` 只依赖模型与协程。`core:protocol` 负责 B站边界并通过领域端口输出事件。`app` 是唯一接触 Android API、Compose 和 DataStore 的模块。

## 连接流

1. 用户在连接页提交只含数字的直播间号。
2. Activity 启动 `ConnectionForegroundService`；Service 在网络工作前立即创建常驻通知。
3. `SessionController` 为本次连接创建新的 `GatewaySession`。
4. Gateway 依次执行 `room_init`、`getDanmuInfo`、WSS 主机回退和 op=7 鉴权。
5. 鉴权成功后每 30 秒发送 op=2 心跳；op=3 更新人气值；op=5 进入协议解析和消息管线。
6. 断线、90 秒无帧或可恢复错误进入 1/2/4/8/16/32/60 秒退避；每轮重连重新获取 token 和主机。
7. 用户停止时 Service 取消唯一会话、移除通知并停止自身。

## 协议安全

- 包头固定 16 字节大端序。
- op=5 支持明文、zlib 和 brotli，并递归解析内层串联包。
- 解压后最多 32 MiB、递归深度最多 4、单帧最多 20,000 个子包。
- 非 0/1/2/3 的业务协议版本进入不可恢复错误，不尝试静默兼容。
- 主机必须为 `bilibili.com` 或其子域，使用 WSS 和系统 TLS 验证。
- 未知命令只记录不含用户内容的计数。

## 消息管线

```text
事件 -> 去重 -> 3 秒合并 -> 过滤/金额阈值 -> 暂停
     -> 512 有界优先级缓冲 -> 列表/置顶 -> 750 ms 提醒节流
```

普通消息可在满载时优先淘汰；SC、上舰和高额金瓜子礼物为重要消息。主列表上限为 100/300/500，置顶区上限为 3。暂停期间普通流丢弃并计数，重要消息仍置顶和提醒。

## 状态与依赖装配

`AnchorApplication` 创建唯一 `AppContainer`。容器持有 SessionController、PreferencesRepository 和 ReminderSink。Service 拥有连接；ViewModel 只发送 Service Intent 并收集同一容器中的 StateFlow。生产和 Debug 演示通过小型接口装配，不使用静态 service locator。

## 验证

`verifyAll` 聚合 JVM 测试、JaCoCo 阈值、Lint、三种 APK 构建、fixture 清单、Manifest 权限、安全卫生、12,000 事件性能冒烟和 APK 大小检查。真实直播和真机长稳不进入离线自动门。

