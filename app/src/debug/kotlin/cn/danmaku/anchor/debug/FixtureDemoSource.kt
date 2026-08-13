package cn.danmaku.anchor.debug

import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.ConnectionPhase
import cn.danmaku.anchor.DanmakuMessage
import cn.danmaku.anchor.GiftMessage
import cn.danmaku.anchor.GuardMessage
import cn.danmaku.anchor.SuperChatMessage

class FixtureDemoSource : DemoSource {
    override val isAvailable: Boolean = true
    override val entryLabel: String = "回放演示"

    override fun scriptFor(roomId: Long): List<DemoScriptEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Resolving,
                    roomId = roomId,
                    inputRoomId = roomId,
                ),
            ),
            DemoScriptEvent.Delay(500L),
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Connecting,
                    roomId = roomId,
                    inputRoomId = roomId,
                ),
            ),
            DemoScriptEvent.Delay(500L),
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Authenticating,
                    roomId = roomId,
                    inputRoomId = roomId,
                ),
            ),
            DemoScriptEvent.Delay(600L),
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Connected,
                    roomId = roomId,
                    inputRoomId = roomId,
                    popularity = 54321,
                    liveLabel = "直播中",
                ),
            ),
            DemoScriptEvent.Delay(700L),
            DemoScriptEvent.Message(
                DanmakuMessage(
                    id = "demo-danmaku-1",
                    roomId = roomId,
                    uid = 10001L,
                    userName = "测试观众",
                    serverTimestampMillis = now,
                    receivedAtMillis = now,
                    text = "今天状态不错，继续冲！",
                    medalName = "守护团",
                    medalLevel = 8,
                ),
            ),
            DemoScriptEvent.Delay(850L),
            DemoScriptEvent.Message(
                SuperChatMessage(
                    id = "demo-sc-1",
                    roomId = roomId,
                    uid = 10002L,
                    userName = "醒目留言观众",
                    serverTimestampMillis = now + 850L,
                    receivedAtMillis = now + 850L,
                    message = "这条醒目留言会被置顶和提醒",
                    priceCny = 30.0,
                    startTimeMillis = now,
                    endTimeMillis = now + 60000L,
                ),
            ),
            DemoScriptEvent.Delay(850L),
            DemoScriptEvent.Message(
                GiftMessage(
                    id = "demo-gift-1",
                    roomId = roomId,
                    uid = 10003L,
                    userName = "礼物支持者",
                    serverTimestampMillis = now + 1700L,
                    receivedAtMillis = now + 1700L,
                    giftName = "小心心",
                    count = 3,
                    totalCoin = 100000L,
                    coinType = "gold",
                    estimatedCny = 100.0,
                ),
            ),
            DemoScriptEvent.Delay(900L),
            DemoScriptEvent.Message(
                GuardMessage(
                    id = "demo-guard-1",
                    roomId = roomId,
                    uid = 10004L,
                    userName = "舰队用户",
                    serverTimestampMillis = now + 2600L,
                    receivedAtMillis = now + 2600L,
                    guardLevel = 3,
                    count = 1,
                ),
            ),
            DemoScriptEvent.Delay(1200L),
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Reconnecting,
                    roomId = roomId,
                    inputRoomId = roomId,
                    popularity = 54321,
                    reconnectDelaySeconds = 2,
                    disconnectedDurationSeconds = 3,
                    mayHaveMissedMessages = true,
                ),
            ),
            DemoScriptEvent.Delay(2000L),
            DemoScriptEvent.Connection(
                AnchorConnectionState(
                    phase = ConnectionPhase.Connected,
                    roomId = roomId,
                    inputRoomId = roomId,
                    popularity = 55000,
                    disconnectedDurationSeconds = 3,
                    mayHaveMissedMessages = true,
                ),
            ),
            DemoScriptEvent.Delay(600L),
            DemoScriptEvent.Message(
                DanmakuMessage(
                    id = "demo-danmaku-2",
                    roomId = roomId,
                    uid = 10001L,
                    userName = "测试观众",
                    serverTimestampMillis = now + 5200L,
                    receivedAtMillis = now + 5200L,
                    text = "重连成功，期间消息可能遗漏",
                    repeatCount = 1,
                ),
            ),
        )
    }
}
