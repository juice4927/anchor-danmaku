package cn.danmaku.anchor.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptionalFieldsM02Test {

    @Test
    fun `M-02 optional model fields can be absent`() {
        val danmaku = LiveMessage.DanmakuMessage(
            id = "d-2",
            roomId = 987654L,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = 10_000L,
            text = "匿名弹幕",
            medalName = null,
            medalLevel = null,
            repeatCount = 1,
        )
        val gift = LiveMessage.GiftMessage(
            id = "gift-2",
            roomId = 987654L,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = 11_000L,
            giftName = "银瓜子礼物",
            count = 1,
            totalCoin = 0L,
            coinType = "silver",
            estimatedCny = null,
        )

        assertThat(danmaku.userName).isNull()
        assertThat(danmaku.medalName).isNull()
        assertThat(danmaku.medalLevel).isNull()
        assertThat(gift.estimatedCny).isNull()
    }
}
