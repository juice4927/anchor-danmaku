package cn.danmaku.anchor

enum class ConnectionPhase {
    Idle,
    Resolving,
    Connecting,
    Authenticating,
    Connected,
    Reconnecting,
    Stopped,
    Fatal,
}

enum class AnchorFailureKind(
    val message: String,
    val retryable: Boolean,
) {
    InvalidRoomInput("请输入正确的直播间号", false),
    RoomNotFound("直播间不存在，请检查房间号", false),
    RoomRestricted("直播间暂不可访问", false),
    NetworkUnavailable("网络不可用，恢复后将自动重连", true),
    RateLimited("请求过于频繁，稍后自动重试", true),
    EndpointUnavailable("暂时无法获取弹幕节点，正在重试", true),
    HostRejected("弹幕节点连接失败，正在切换节点", true),
    AuthRejected("弹幕节点连接失败，正在切换节点", true),
    ConnectionLost("连接已断开，正在重连", true),
    ProtocolUnsupported("弹幕协议已变化，请更新应用", false),
    UnknownRecoverable("连接失败，正在重试", true),
}

data class AnchorConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val roomId: Long? = null,
    val inputRoomId: Long? = null,
    val popularity: Int? = null,
    val liveLabel: String? = null,
    val failureKind: AnchorFailureKind? = null,
    val failureDetail: String? = null,
    val reconnectDelaySeconds: Int? = null,
    val disconnectedDurationSeconds: Int? = null,
    val mayHaveMissedMessages: Boolean = false,
) {
    val statusText: String
        get() = when (phase) {
            ConnectionPhase.Idle -> "未连接"
            ConnectionPhase.Resolving -> "解析房间中"
            ConnectionPhase.Connecting -> "连接节点中"
            ConnectionPhase.Authenticating -> "鉴权中"
            ConnectionPhase.Connected -> "已连接"
            ConnectionPhase.Reconnecting -> "重连中"
            ConnectionPhase.Stopped -> "已停止"
            ConnectionPhase.Fatal -> failureKind?.message ?: "连接失败"
        }
}

sealed interface AnchorMessage {
    val id: String
    val roomId: Long
    val uid: Long?
    val userName: String?
    val serverTimestampMillis: Long?
    val receivedAtMillis: Long
}

data class DanmakuMessage(
    override val id: String,
    override val roomId: Long,
    override val uid: Long?,
    override val userName: String?,
    override val serverTimestampMillis: Long?,
    override val receivedAtMillis: Long,
    val text: String,
    val medalName: String? = null,
    val medalLevel: Int? = null,
    val repeatCount: Int = 1,
) : AnchorMessage

data class SuperChatMessage(
    override val id: String,
    override val roomId: Long,
    override val uid: Long?,
    override val userName: String?,
    override val serverTimestampMillis: Long?,
    override val receivedAtMillis: Long,
    val message: String,
    val priceCny: Double,
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
) : AnchorMessage

data class GiftMessage(
    override val id: String,
    override val roomId: Long,
    override val uid: Long?,
    override val userName: String?,
    override val serverTimestampMillis: Long?,
    override val receivedAtMillis: Long,
    val giftName: String,
    val count: Int,
    val totalCoin: Long,
    val coinType: String,
    val estimatedCny: Double? = null,
) : AnchorMessage

data class GuardMessage(
    override val id: String,
    override val roomId: Long,
    override val uid: Long?,
    override val userName: String?,
    override val serverTimestampMillis: Long?,
    override val receivedAtMillis: Long,
    val guardLevel: Int,
    val count: Int,
) : AnchorMessage

fun AnchorMessage.displayName(): String = userName?.takeIf { it.isNotBlank() } ?: "匿名用户"
