package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.BlockedUser
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.model.UserPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagePipelinePol01ToPol12Test {

    @Test
    fun `POL-01 keyword filtering only applies to danmaku bodies`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler = scheduler,
            preferences = UserPreferences(keywordBlacklist = setOf("spam")),
        )

        pipeline.ingest(danmaku(id = "d1", uid = 1L, text = "This is SPAM"))
        pipeline.ingest(superChat(id = "sc1", uid = 1L, message = "This is SPAM"))

        assertThat(pipeline.snapshot().visibleMessages).containsExactly(
            superChat(id = "sc1", uid = 1L, message = "This is SPAM"),
        )
    }

    @Test
    fun `POL-02 user blacklist filters only same uid danmaku`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler = scheduler,
            preferences = UserPreferences(blockedUsers = listOf(BlockedUser(uid = 42L, latestName = "bad"))),
        )

        pipeline.ingest(danmaku(id = "d1", uid = 42L, text = "blocked"))
        pipeline.ingest(danmaku(id = "d2", uid = 43L, text = "visible", userName = "bad"))
        pipeline.ingest(guard(id = "g1", uid = 42L))

        assertThat(pipeline.snapshot().visibleMessages.map { it.id }).containsExactly("d2", "g1").inOrder()
    }

    @Test
    fun `POL-03 gift visibility follows gold and silver threshold rules`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler = scheduler,
            preferences = UserPreferences(minimumGiftDisplay = Money.fromWholeCny(1)),
        )

        pipeline.ingest(gift(id = "gold-hidden", totalCoin = 999L, coinType = "gold"))
        pipeline.ingest(gift(id = "gold-visible", totalCoin = 1_000L, coinType = "gold"))
        pipeline.ingest(gift(id = "silver-hidden", totalCoin = 0L, coinType = "silver"))

        assertThat(pipeline.snapshot().visibleMessages.map { it.id }).containsExactly("gold-visible").inOrder()
    }

    @Test
    fun `POL-04 high value gifts trigger exactly at threshold`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler = scheduler,
            preferences = UserPreferences(highlightGiftThreshold = Money.fromWholeCny(100)),
        )

        val below = pipeline.ingest(gift(id = "gift-low", totalCoin = 99_999L, coinType = "gold"))
        val atThreshold = pipeline.ingest(gift(id = "gift-high", totalCoin = 100_000L, coinType = "gold"))

        assertThat(below.reminderTriggered).isFalse()
        assertThat(atThreshold.reminderTriggered).isTrue()
        assertThat(pipeline.snapshot().pinnedMessages.map { it.message.id }).containsExactly("gift-high")
    }

    @Test
    fun `POL-05 identical danmaku within three seconds are coalesced`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler,
            danmakuCoalescer = DanmakuCoalescer(windowMillis = DanmakuCoalescer.WINDOW_MILLIS),
        )

        pipeline.ingest(danmaku(id = "d1", uid = 1L, text = "重复"))
        scheduler.advanceTimeBy(2_000L)
        pipeline.ingest(danmaku(id = "d2", uid = 1L, text = "重复"))
        scheduler.advanceTimeBy(3_001L)
        pipeline.ingest(danmaku(id = "d3", uid = 1L, text = "重复"))

        val danmakuMessages = pipeline.snapshot().visibleMessages.filterIsInstance<LiveMessage.DanmakuMessage>()
        assertThat(danmakuMessages).hasSize(2)
        assertThat(danmakuMessages.first().repeatCount).isEqualTo(2)
    }

    @Test
    fun `POL-06 duplicate ids are suppressed until dedupe window expires`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(scheduler)

        pipeline.ingest(danmaku(id = "same-id", uid = 1L, text = "first"))
        pipeline.ingest(danmaku(id = "same-id", uid = 1L, text = "duplicate"))
        scheduler.advanceTimeBy(MessageDeduplicator.WINDOW_MILLIS + 1L)
        pipeline.ingest(danmaku(id = "same-id", uid = 1L, text = "after-window"))

        val ids = pipeline.snapshot().visibleMessages.map { it.id }
        assertThat(ids).containsExactly("same-id", "same-id").inOrder()
    }

    @Test
    fun `POL-07 visible list respects capacity and preserves important messages over ordinary ones`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler = scheduler,
            preferences = UserPreferences(maxMessageCount = 100),
        )

        repeat(100) { index ->
            pipeline.ingest(danmaku(id = "d$index", uid = index.toLong(), text = "msg-$index"))
        }
        pipeline.ingest(superChat(id = "sc-important", uid = 999L, message = "important"))

        val state = pipeline.snapshot()
        assertThat(state.visibleMessages).hasSize(100)
        assertThat(state.visibleMessages.map { it.id }).doesNotContain("d0")
        assertThat(state.visibleMessages.map { it.id }).contains("sc-important")
    }

    @Test
    fun `POL-08 pinned policy keeps three newest items, expires them, and supports dismiss plus clear`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(scheduler)

        pipeline.ingest(superChat(id = "sc", uid = 1L, message = "sc", endTimeMillis = 5_000L))
        pipeline.ingest(guard(id = "guard", uid = 2L))
        pipeline.ingest(gift(id = "gift1", totalCoin = 100_000L, coinType = "gold"))
        pipeline.ingest(gift(id = "gift2", totalCoin = 100_000L, coinType = "gold"))

        assertThat(pipeline.snapshot().pinnedMessages.map { it.message.id }).containsExactly("gift2", "gift1", "guard").inOrder()

        pipeline.dismissPinned("gift2")
        assertThat(pipeline.snapshot().pinnedMessages.map { it.message.id }).containsExactly("gift1", "guard").inOrder()

        scheduler.advanceTimeBy(30_000L)
        assertThat(pipeline.snapshot().pinnedMessages).isEmpty()

        pipeline.ingest(gift(id = "gift3", totalCoin = 100_000L, coinType = "gold"))
        pipeline.clear()
        assertThat(pipeline.snapshot().pinnedMessages).isEmpty()
    }

    @Test
    fun `POL-09 pause skips ordinary list messages but still pins and reminds important ones`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(scheduler)

        pipeline.setPaused(true)
        val ordinary = pipeline.ingest(danmaku(id = "d1", uid = 1L, text = "paused"))
        val important = pipeline.ingest(superChat(id = "sc1", uid = 2L, message = "still-important"))

        assertThat(ordinary.reminderTriggered).isFalse()
        assertThat(important.reminderTriggered).isTrue()
        assertThat(pipeline.snapshot().visibleMessages).isEmpty()
        assertThat(pipeline.snapshot().skippedCount).isEqualTo(1)
        assertThat(pipeline.snapshot().pinnedMessages).hasSize(1)

        pipeline.setPaused(false)
        assertThat(pipeline.snapshot().skippedCount).isEqualTo(0)
    }

    @Test
    fun `POL-10 clear resets lists pins and counters without touching preferences`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val preferences = UserPreferences(maxMessageCount = 500)
        val pipeline = pipeline(scheduler, preferences)

        pipeline.setAutoFollow(false)
        pipeline.ingest(danmaku(id = "d1", uid = 1L, text = "one"))
        pipeline.ingest(superChat(id = "sc1", uid = 2L, message = "important"))
        pipeline.setPaused(true)
        pipeline.ingest(danmaku(id = "d2", uid = 3L, text = "skipped"))
        pipeline.clear()

        val state = pipeline.snapshot()
        assertThat(state.visibleMessages).isEmpty()
        assertThat(state.pinnedMessages).isEmpty()
        assertThat(state.skippedCount).isEqualTo(0)
        assertThat(state.newMessagesCount).isEqualTo(0)
        assertThat(pipeline.preferences.maxMessageCount).isEqualTo(500)
    }

    @Test
    fun `POL-11 reminders are throttled into one trigger per 750 millisecond window`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(scheduler)

        val first = pipeline.ingest(superChat(id = "sc1", uid = 1L, message = "first"))
        scheduler.advanceTimeBy(500L)
        val second = pipeline.ingest(guard(id = "g1", uid = 2L))
        scheduler.advanceTimeBy(751L)
        val third = pipeline.ingest(gift(id = "gift1", totalCoin = 100_000L, coinType = "gold"))

        assertThat(first.reminderTriggered).isTrue()
        assertThat(second.reminderTriggered).isFalse()
        assertThat(third.reminderTriggered).isTrue()
        assertThat(pipeline.snapshot().pinnedMessages).hasSize(3)
    }

    private fun pipeline(
        scheduler: TestCoroutineScheduler,
        preferences: UserPreferences = UserPreferences(),
        danmakuCoalescer: DanmakuCoalescer = DanmakuCoalescer(),
    ) = MessagePipeline(
        clock = object : Clock {
            override fun nowMillis(): Long = scheduler.currentTime
        },
        preferences = preferences,
        danmakuCoalescer = danmakuCoalescer,
    )

    private fun danmaku(
        id: String,
        uid: Long,
        text: String,
        userName: String = "u$uid",
        receivedAtMillis: Long? = null,
    ) = LiveMessage.DanmakuMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = userName,
        serverTimestampMillis = receivedAtMillis,
        receivedAtMillis = receivedAtMillis ?: 0L,
        text = text,
        medalName = null,
        medalLevel = null,
        repeatCount = 1,
    )

    private fun superChat(
        id: String,
        uid: Long,
        message: String,
        endTimeMillis: Long = 60_000L,
    ) = LiveMessage.SuperChatMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "sc$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        message = message,
        priceCny = Money.fromWholeCny(30),
        startTimeMillis = 0L,
        endTimeMillis = endTimeMillis,
    )

    private fun gift(
        id: String,
        totalCoin: Long,
        coinType: String,
    ) = LiveMessage.GiftMessage(
        id = id,
        roomId = 987654L,
        uid = 10L,
        userName = "gift-user",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        giftName = "辣条",
        count = 1,
        totalCoin = totalCoin,
        coinType = coinType,
        estimatedCny = if (coinType.equals("gold", ignoreCase = true)) {
            Money.fromGoldCoin(totalCoin)
        } else {
            null
        },
    )

    private fun guard(
        id: String,
        uid: Long,
    ) = LiveMessage.GuardMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "guard$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        guardLevel = 3,
        count = 1,
    )
}
