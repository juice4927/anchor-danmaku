package cn.danmaku.anchor.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveMessageM01Test {

    @Test
    fun `M-01 live message variants preserve shared and type specific fields`() {
        val danmaku = LiveMessage.DanmakuMessage(
            id = "d-1",
            roomId = 987654L,
            uid = 10001L,
            userName = "测试观众",
            serverTimestampMillis = 1_000L,
            receivedAtMillis = 2_000L,
            text = "你好",
            medalName = "粉丝牌",
            medalLevel = 7,
            repeatCount = 3,
        )
        val superChat = LiveMessage.SuperChatMessage(
            id = "sc-1",
            roomId = 987654L,
            uid = 10002L,
            userName = "土豪观众",
            serverTimestampMillis = 3_000L,
            receivedAtMillis = 4_000L,
            message = "辛苦了",
            priceCny = Money.fromCny("30"),
            startTimeMillis = 3_000L,
            endTimeMillis = 63_000L,
        )
        val gift = LiveMessage.GiftMessage(
            id = "gift-1",
            roomId = 987654L,
            uid = 10003L,
            userName = "礼物哥",
            serverTimestampMillis = 5_000L,
            receivedAtMillis = 6_000L,
            giftName = "辣条",
            count = 10,
            totalCoin = 100_000L,
            coinType = "gold",
            estimatedCny = Money.fromGoldCoin(100_000L),
        )
        val guard = LiveMessage.GuardMessage(
            id = "guard-1",
            roomId = 987654L,
            uid = 10004L,
            userName = "舰长用户",
            serverTimestampMillis = 7_000L,
            receivedAtMillis = 8_000L,
            guardLevel = 3,
            count = 1,
        )

        assertThat(danmaku.id).isEqualTo("d-1")
        assertThat(danmaku.roomId).isEqualTo(987654L)
        assertThat(danmaku.text).isEqualTo("你好")
        assertThat(danmaku.repeatCount).isEqualTo(3)

        assertThat(superChat.message).isEqualTo("辛苦了")
        assertThat(superChat.priceCny.toCanonicalString()).isEqualTo("30")

        assertThat(gift.giftName).isEqualTo("辣条")
        assertThat(gift.estimatedCny?.toCanonicalString()).isEqualTo("100")

        assertThat(guard.guardLevel).isEqualTo(3)
        assertThat(guard.count).isEqualTo(1)
    }
}
