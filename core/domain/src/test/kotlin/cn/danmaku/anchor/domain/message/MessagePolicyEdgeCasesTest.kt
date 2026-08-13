package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.model.UserPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagePolicyEdgeCasesTest {

    @Test
    fun `message filter displays non gold gifts when minimum display is zero`() {
        val filter = MessageFilter()

        val decision = filter.evaluate(
            gift(
                id = "gift-zero",
                coinType = "silver",
                estimatedCny = null,
            ),
            UserPreferences(minimumGiftDisplay = Money.ZERO),
        )

        assertThat(decision.display).isTrue()
        assertThat(decision.important).isFalse()
        assertThat(decision.reason).isNull()
    }

    @Test
    fun `message filter marks gold gifts below minimum display as thresholded`() {
        val filter = MessageFilter()

        val decision = filter.evaluate(
            gift(
                id = "gift-low",
                coinType = "gold",
                estimatedCny = Money.fromGoldCoin(999L),
            ),
            UserPreferences(minimumGiftDisplay = Money.fromWholeCny(1)),
        )

        assertThat(decision.display).isFalse()
        assertThat(decision.important).isFalse()
        assertThat(decision.reason).isEqualTo(FilterReason.GiftThreshold)
    }

    @Test
    fun `message filter hides gold gifts without estimated value when minimum display is above zero`() {
        val filter = MessageFilter()

        val decision = filter.evaluate(
            gift(
                id = "gift-null",
                coinType = "gold",
                estimatedCny = null,
            ),
            UserPreferences(minimumGiftDisplay = Money.fromWholeCny(1)),
        )

        assertThat(decision.display).isFalse()
        assertThat(decision.reason).isEqualTo(FilterReason.GiftThreshold)
    }

    @Test
    fun `reminder policy suppresses reminders when sound and vibration are both disabled`() {
        val policy = ReminderPolicy(clock = Clock { 0L })

        assertThat(
            policy.shouldTrigger(
                important = true,
                preferences = UserPreferences(soundEnabled = false, vibrationEnabled = false),
            ),
        ).isFalse()
    }

    @Test
    fun `reminder policy still triggers vibration when sound is disabled`() {
        val policy = ReminderPolicy(clock = Clock { 0L })

        assertThat(
            policy.shouldTrigger(
                important = true,
                preferences = UserPreferences(soundEnabled = false, vibrationEnabled = true),
            ),
        ).isTrue()
    }

    @Test
    fun `reminder policy suppresses reminders for ordinary messages`() {
        val policy = ReminderPolicy(clock = Clock { 0L })

        assertThat(policy.shouldTrigger(important = false, preferences = UserPreferences())).isFalse()
    }

    @Test
    fun `pinned message policy uses default duration when super chat end time is missing`() {
        val scheduler = TestCoroutineScheduler()
        val policy = PinnedMessagePolicy(clock = schedulerClock(scheduler))

        val pins = policy.add(
            superChat(
                id = "sc-default",
                endTimeMillis = null,
            ),
            important = true,
        )

        assertThat(pins).hasSize(1)
        assertThat(pins.single().expiresAtMillis).isEqualTo(60_000L)
    }

    @Test
    fun `pinned message policy prunes expired pins on snapshot`() {
        val scheduler = TestCoroutineScheduler()
        val policy = PinnedMessagePolicy(clock = schedulerClock(scheduler))

        policy.add(
            superChat(
                id = "sc-expiring",
                endTimeMillis = 15_000L,
            ),
            important = true,
        )
        scheduler.advanceTimeBy(15_001L)

        assertThat(policy.snapshot()).isEmpty()
    }

    @Test
    fun `pinned message policy ignores important danmaku messages`() {
        val scheduler = TestCoroutineScheduler()
        val policy = PinnedMessagePolicy(clock = schedulerClock(scheduler))

        val pins = policy.add(danmaku("d-important", uid = 1L, text = "important"), important = true)

        assertThat(pins).isEmpty()
    }

    @Test
    fun `message deduplicator evicts the oldest id when capacity is exceeded`() {
        val deduplicator = MessageDeduplicator(maxEntries = 2, windowMillis = 1_000L)

        assertThat(deduplicator.shouldAccept(danmaku("d1", uid = 1L, text = "one"), nowMillis = 0L)).isTrue()
        assertThat(deduplicator.shouldAccept(danmaku("d2", uid = 2L, text = "two"), nowMillis = 1L)).isTrue()
        assertThat(deduplicator.shouldAccept(danmaku("d3", uid = 3L, text = "three"), nowMillis = 2L)).isTrue()
        assertThat(deduplicator.shouldAccept(danmaku("d1", uid = 1L, text = "one"), nowMillis = 3L)).isTrue()
    }

    @Test
    fun `priority event buffer drops ordinary overflow when no ordinary entry is available`() = runTest {
        val buffer = PriorityEventBuffer(capacity = 2)

        buffer.offer(superChat("sc1", uid = 1L), important = true)
        buffer.offer(guard("g1", uid = 2L), important = true)
        buffer.offer(danmaku("d1", uid = 3L, text = "plain"), important = false)

        assertThat(buffer.snapshot().map { it.message.id }).containsExactly("sc1", "g1").inOrder()
        assertThat(buffer.stats().droppedCount).isEqualTo(1)
        assertThat(buffer.stats().criticalDropCount).isEqualTo(0)
    }

    @Test
    fun `priority event buffer drains empty buffer without emitting entries`() = runTest {
        val buffer = PriorityEventBuffer(capacity = 2)

        assertThat(buffer.drainAll()).isEmpty()
    }

    @Test
    fun `message pipeline resets new message count when auto follow is re enabled`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(scheduler)

        pipeline.setAutoFollow(false)
        pipeline.ingest(danmaku("d1", uid = 1L, text = "one"))

        assertThat(pipeline.snapshot().newMessagesCount).isEqualTo(1)

        pipeline.setAutoFollow(true)

        assertThat(pipeline.snapshot().newMessagesCount).isEqualTo(0)
    }

    @Test
    fun `message pipeline trims oldest important entry when only important messages exceed capacity`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler,
            preferences = UserPreferences(maxMessageCount = 100),
            danmakuCoalescer = DanmakuCoalescer(windowMillis = DanmakuCoalescer.WINDOW_MILLIS),
        )

        repeat(101) { index ->
            pipeline.ingest(superChat("sc$index", uid = (index + 1).toLong(), message = "first-$index"))
        }

        val visibleIds = pipeline.snapshot().visibleMessages.map { it.id }
        assertThat(visibleIds).hasSize(100)
        assertThat(visibleIds.first()).isEqualTo("sc1")
    }

    @Test
    fun `message pipeline appends a coalesced danmaku when the original entry is gone`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler,
            preferences = UserPreferences(maxMessageCount = 100),
        )

        pipeline.ingest(danmaku("d1", uid = 1L, text = "same"))
        repeat(100) { index ->
            pipeline.ingest(danmaku("d-fill-$index", uid = (index + 2).toLong(), text = "other-$index"))
        }
        pipeline.ingest(danmaku("d3", uid = 1L, text = "same"))

        val visibleIds = pipeline.snapshot().visibleMessages.map { it.id }
        assertThat(visibleIds).hasSize(100)
        assertThat(visibleIds).contains("d1")
        assertThat(visibleIds.last()).isEqualTo("d1")
        assertThat(visibleIds).doesNotContain("d3")
    }

    @Test
    fun `message pipeline trims visible entries when preferences lower capacity`() = runTest {
        val scheduler = TestCoroutineScheduler()
        val pipeline = pipeline(
            scheduler,
            preferences = UserPreferences(maxMessageCount = 300),
        )

        repeat(101) { index ->
            pipeline.ingest(danmaku("d$index", uid = (index + 1).toLong(), text = "one-$index"))
        }
        pipeline.updatePreferences(UserPreferences(maxMessageCount = 100))

        val visibleIds = pipeline.snapshot().visibleMessages.map { it.id }
        assertThat(visibleIds).hasSize(100)
        assertThat(visibleIds.first()).isEqualTo("d1")
    }

    @Test
    fun `danmaku coalescer appends messages that are not eligible for coalescing`() {
        val coalescer = DanmakuCoalescer()

        val result = coalescer.coalesce(superChat("sc-non-danmaku"), nowMillis = 0L)

        assertThat(result).isInstanceOf(CoalesceResult.Append::class.java)
    }

    @Test
    fun `danmaku coalescer appends danmaku messages without a uid`() {
        val coalescer = DanmakuCoalescer()

        val result = coalescer.coalesce(
            danmaku(
                id = "d-null-uid",
                uid = null,
                text = "same",
            ),
            nowMillis = 0L,
        )

        assertThat(result).isInstanceOf(CoalesceResult.Append::class.java)
    }

    @Test
    fun `danmaku coalescer evicts the oldest window when capacity is exceeded`() {
        val coalescer = DanmakuCoalescer(windowMillis = 60_000L, maxWindows = 2)

        assertThat(coalescer.coalesce(danmaku("d1", uid = 1L, text = "a"), nowMillis = 0L))
            .isInstanceOf(CoalesceResult.Append::class.java)
        assertThat(coalescer.coalesce(danmaku("d2", uid = 2L, text = "b"), nowMillis = 1L))
            .isInstanceOf(CoalesceResult.Append::class.java)
        assertThat(coalescer.coalesce(danmaku("d3", uid = 3L, text = "c"), nowMillis = 2L))
            .isInstanceOf(CoalesceResult.Append::class.java)

        // d1 的窗口已被容量淘汰：同键重复必须重新 Append，而不是错误合并。
        assertThat(coalescer.coalesce(danmaku("d4", uid = 1L, text = "a"), nowMillis = 3L))
            .isInstanceOf(CoalesceResult.Append::class.java)
    }

    @Test
    fun `danmaku coalescer prunes expired windows before evicting live ones`() {
        val coalescer = DanmakuCoalescer(windowMillis = 1_000L, maxWindows = 2)

        coalescer.coalesce(danmaku("d1", uid = 1L, text = "a"), nowMillis = 0L)
        coalescer.coalesce(danmaku("d2", uid = 2L, text = "b"), nowMillis = 500L)

        // 第三键触达容量：d1 已过期被清理，存活的 d2 必须保留。
        assertThat(coalescer.coalesce(danmaku("d3", uid = 3L, text = "c"), nowMillis = 1_001L))
            .isInstanceOf(CoalesceResult.Append::class.java)

        val merged = coalescer.coalesce(danmaku("d4", uid = 2L, text = "b"), nowMillis = 1_100L)
        assertThat(merged).isInstanceOf(CoalesceResult.Replace::class.java)
        assertThat((merged as CoalesceResult.Replace).message.repeatCount).isEqualTo(2)
    }

    private fun pipeline(
        scheduler: TestCoroutineScheduler,
        preferences: UserPreferences = UserPreferences(),
        danmakuCoalescer: DanmakuCoalescer = DanmakuCoalescer(),
    ) = MessagePipeline(
        clock = schedulerClock(scheduler),
        danmakuCoalescer = danmakuCoalescer,
        preferences = preferences,
    )

    private fun schedulerClock(scheduler: TestCoroutineScheduler): Clock = Clock {
        scheduler.currentTime
    }

    private fun danmaku(
        id: String,
        uid: Long?,
        text: String,
    ) = LiveMessage.DanmakuMessage(
        id = id,
        roomId = 987654L,
        uid = uid,
        userName = "u$uid",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        text = text,
        medalName = null,
        medalLevel = null,
        repeatCount = 1,
    )

    private fun superChat(
        id: String,
        uid: Long = 1L,
        message: String = id,
        endTimeMillis: Long? = 60_000L,
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
        coinType: String,
        estimatedCny: Money?,
    ) = LiveMessage.GiftMessage(
        id = id,
        roomId = 987654L,
        uid = 10L,
        userName = "gift-user",
        serverTimestampMillis = 0L,
        receivedAtMillis = 0L,
        giftName = "gift",
        count = 1,
        totalCoin = 0L,
        coinType = coinType,
        estimatedCny = estimatedCny,
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
