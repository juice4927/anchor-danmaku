package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.UserPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerfSmokeTest {
    @Test(timeout = 30_000L)
    fun perfSmokeProcessesTwelveThousandEventsWithBoundedVisibleState() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = MessagePipeline(
            clock = Clock { scheduler.currentTime },
            preferences = UserPreferences(
                maxMessageCount = 300,
                keywordBlacklist = setOf("spam"),
            ),
        )

        var submittedEvents = 0
        repeat(11_880) { index ->
            submittedEvents += 1
            val result = pipeline.ingest(
                LiveMessage.DanmakuMessage(
                    id = "perf-$index",
                    roomId = 987654L,
                    uid = (index + 1_000).toLong(),
                    userName = "viewer-$index",
                    serverTimestampMillis = index.toLong(),
                    receivedAtMillis = index.toLong(),
                    text = "message-$index",
                    medalName = null,
                    medalLevel = null,
                ),
            )
            assertThat(result.reminderTriggered).isFalse()
        }
        repeat(20) {
            submittedEvents += 1
            val result = pipeline.ingest(
                LiveMessage.DanmakuMessage(
                    id = "dup",
                    roomId = 987654L,
                    uid = 99L,
                    userName = "repeat",
                    serverTimestampMillis = 0L,
                    receivedAtMillis = 0L,
                    text = "repeat",
                    medalName = null,
                    medalLevel = null,
                ),
            )
            assertThat(result.reminderTriggered).isFalse()
        }
        repeat(20) { index ->
            submittedEvents += 1
            val result = pipeline.ingest(
                LiveMessage.DanmakuMessage(
                    id = "filtered-$index",
                    roomId = 987654L,
                    uid = (index + 5_000).toLong(),
                    userName = "filtered-$index",
                    serverTimestampMillis = 0L,
                    receivedAtMillis = 0L,
                    text = "spam-$index",
                    medalName = null,
                    medalLevel = null,
                ),
            )
            assertThat(result.reminderTriggered).isFalse()
        }
        repeat(20) { index ->
            submittedEvents += 1
            pipeline.ingest(superChat(id = "sc-$index", uid = (index + 1).toLong()))
        }
        repeat(20) { index ->
            submittedEvents += 1
            pipeline.ingest(guard(id = "guard-$index", uid = (index + 100).toLong()))
        }
        repeat(40) { index ->
            submittedEvents += 1
            pipeline.ingest(
                gift(
                    id = "gift-$index",
                    uid = (index + 200).toLong(),
                    totalCoin = 100_000L,
                    coinType = "gold",
                ),
            )
        }

        assertThat(submittedEvents).isEqualTo(12_000)
        val state = pipeline.snapshot()
        assertThat(state.visibleMessages).hasSize(300)
        assertThat(state.visibleMessages.count { it.id.startsWith("sc-") }).isEqualTo(20)
        assertThat(state.visibleMessages.count { it.id.startsWith("guard-") }).isEqualTo(20)
        assertThat(state.visibleMessages.count { it.id.startsWith("gift-") }).isEqualTo(40)
        assertThat(state.visibleMessages.count { it.id == "dup" }).isEqualTo(1)
        assertThat(state.visibleMessages.none { it.id.startsWith("filtered-") }).isTrue()
        assertThat(state.visibleMessages.last().id).isEqualTo("gift-39")
        assertThat(state.pinnedMessages.size).isAtMost(3)
        assertThat(state.bufferStats.droppedCount).isEqualTo(0)
        assertThat(state.bufferStats.criticalDropCount).isEqualTo(0)
        assertThat(state.skippedCount).isEqualTo(0)
        assertThat(state.newMessagesCount).isEqualTo(0)
    }

    private fun superChat(id: String, uid: Long) = LiveMessage.SuperChatMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "sc$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        message = id,
        priceCny = cn.danmaku.anchor.model.Money.fromWholeCny(30),
        startTimeMillis = 0L,
        endTimeMillis = 60_000L,
    )

    private fun guard(id: String, uid: Long) = LiveMessage.GuardMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "guard$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        guardLevel = 3,
        count = 1,
    )

    private fun gift(id: String, uid: Long, totalCoin: Long, coinType: String) = LiveMessage.GiftMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "gift$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        giftName = id,
        count = 1,
        totalCoin = totalCoin,
        coinType = coinType,
        estimatedCny = if (coinType.equals("gold", ignoreCase = true)) {
            cn.danmaku.anchor.model.Money.fromGoldCoin(totalCoin)
        } else {
            null
        },
    )
}
