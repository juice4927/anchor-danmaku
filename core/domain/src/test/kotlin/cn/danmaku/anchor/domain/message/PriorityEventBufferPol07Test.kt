package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PriorityEventBufferPol07Test {

    @Test
    fun `POL-07 ordinary overflow evicts the oldest ordinary item first`() = runTest {
        val buffer = PriorityEventBuffer(capacity = 3)

        buffer.offer(danmaku("d1"), important = false)
        buffer.offer(danmaku("d2"), important = false)
        buffer.offer(superChat("sc1"), important = true)
        buffer.offer(danmaku("d3"), important = false)

        assertThat(buffer.snapshot().map { it.message.id }).containsExactly("d2", "sc1", "d3").inOrder()
        assertThat(buffer.stats().droppedCount).isEqualTo(1)
        assertThat(buffer.stats().criticalDropCount).isEqualTo(0)
    }

    @Test
    fun `POL-07 all important overflow increments critical drop count`() = runTest {
        val buffer = PriorityEventBuffer(capacity = 2)

        buffer.offer(superChat("sc1"), important = true)
        buffer.offer(superChat("sc2"), important = true)
        buffer.offer(superChat("sc3"), important = true)

        assertThat(buffer.snapshot().map { it.message.id }).containsExactly("sc2", "sc3").inOrder()
        assertThat(buffer.stats().droppedCount).isEqualTo(1)
        assertThat(buffer.stats().criticalDropCount).isEqualTo(1)
    }

    private fun danmaku(id: String) = LiveMessage.DanmakuMessage(
        id = id,
        roomId = 987654L,
        uid = 1L,
        userName = "u",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        text = id,
        medalName = null,
        medalLevel = null,
        repeatCount = 1,
    )

    private fun superChat(id: String) = LiveMessage.SuperChatMessage(
        id = id,
        roomId = 987654L,
        uid = 1L,
        userName = "u",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        message = id,
        priceCny = Money.fromWholeCny(30),
        startTimeMillis = 0L,
        endTimeMillis = 60_000L,
    )
}
