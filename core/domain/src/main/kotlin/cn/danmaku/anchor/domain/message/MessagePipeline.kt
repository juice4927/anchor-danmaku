package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.UserPreferences

class MessagePipeline(
    private val clock: Clock,
    preferences: UserPreferences = UserPreferences(),
    private val messageFilter: MessageFilter = MessageFilter(),
    private val messageDeduplicator: MessageDeduplicator = MessageDeduplicator(),
    private val danmakuCoalescer: DanmakuCoalescer = DanmakuCoalescer(),
    private val priorityEventBuffer: PriorityEventBuffer = PriorityEventBuffer(),
    private val pinnedMessagePolicy: PinnedMessagePolicy = PinnedMessagePolicy(clock),
    private val reminderPolicy: ReminderPolicy = ReminderPolicy(clock),
) {
    private val visibleEntries = ArrayDeque<VisibleEntry>()
    private var paused = false
    private var autoFollow = true
    private var skippedCount = 0
    private var newMessagesCount = 0
    private var receivedCount = 0

    var preferences: UserPreferences = preferences
        private set

    suspend fun ingest(message: LiveMessage): MessagePipelineResult {
        val nowMillis = effectiveTimestamp(message)
        receivedCount += 1
        if (!messageDeduplicator.shouldAccept(message, nowMillis)) {
            return MessagePipelineResult(snapshot(), reminderTriggered = false)
        }

        val coalesced = danmakuCoalescer.coalesce(message, nowMillis)
        val candidate = when (coalesced) {
            is CoalesceResult.Append -> coalesced.message
            is CoalesceResult.Replace -> coalesced.message
        }
        val filterDecision = messageFilter.evaluate(candidate, preferences)
        if (!filterDecision.display && !filterDecision.important) {
            return MessagePipelineResult(snapshot(), reminderTriggered = false)
        }

        val pinned = pinnedMessagePolicy.add(candidate, filterDecision.important)
        val reminderTriggered = reminderPolicy.shouldTrigger(filterDecision.important, preferences)

        if (coalesced is CoalesceResult.Replace) {
            replaceVisibleMessage(coalesced.originalMessageId, candidate, filterDecision.important)
            return MessagePipelineResult(
                state = snapshot(pinned),
                reminderTriggered = reminderTriggered,
            )
        }

        if (paused) {
            if (!filterDecision.important) {
                skippedCount += 1
            }
            return MessagePipelineResult(
                state = snapshot(pinned),
                reminderTriggered = reminderTriggered,
            )
        }

        priorityEventBuffer.offer(candidate, filterDecision.important)
        priorityEventBuffer.drainAll().forEach { buffered ->
            appendVisible(buffered.message, buffered.important)
        }
        return MessagePipelineResult(
            state = snapshot(pinned),
            reminderTriggered = reminderTriggered,
        )
    }

    fun updatePreferences(preferences: UserPreferences) {
        this.preferences = preferences
        trimVisibleToCapacity()
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (!value) {
            skippedCount = 0
        }
    }

    fun setAutoFollow(value: Boolean) {
        autoFollow = value
        if (value) {
            newMessagesCount = 0
        }
    }

    suspend fun dismissPinned(messageId: String) {
        pinnedMessagePolicy.dismiss(messageId)
    }

    suspend fun clear() {
        visibleEntries.clear()
        skippedCount = 0
        newMessagesCount = 0
        priorityEventBuffer.clear()
        pinnedMessagePolicy.clear()
    }

    fun snapshot(): MessagePipelineState = snapshot(pinnedMessagePolicy.snapshot())

    private fun snapshot(pinnedMessages: List<PinnedMessage>): MessagePipelineState = MessagePipelineState(
        visibleMessages = visibleEntries.map { it.message },
        pinnedMessages = pinnedMessages,
        skippedCount = skippedCount,
        newMessagesCount = newMessagesCount,
        receivedCount = receivedCount,
        bufferStats = priorityEventBuffer.statsSnapshot(),
    )

    private fun appendVisible(message: LiveMessage, important: Boolean) {
        visibleEntries.addLast(VisibleEntry(message, important))
        if (!autoFollow) {
            newMessagesCount += 1
        }
        trimVisibleToCapacity()
    }

    private fun replaceVisibleMessage(originalMessageId: String, message: LiveMessage, important: Boolean) {
        val index = visibleEntries.indexOfFirst { it.message.id == originalMessageId }
        if (index >= 0) {
            visibleEntries[index] = VisibleEntry(message, important)
        } else {
            appendVisible(message, important)
        }
    }

    private fun trimVisibleToCapacity() {
        while (visibleEntries.size > preferences.maxMessageCount) {
            val oldestOrdinaryIndex = visibleEntries.indexOfFirst { !it.important }
            if (oldestOrdinaryIndex >= 0) {
                visibleEntries.removeAt(oldestOrdinaryIndex)
            } else {
                visibleEntries.removeFirst()
            }
        }
    }

    private fun effectiveTimestamp(message: LiveMessage): Long =
        message.receivedAtMillis.takeIf { it > 0L } ?: clock.nowMillis()
}

data class MessagePipelineResult(
    val state: MessagePipelineState,
    val reminderTriggered: Boolean,
)

data class MessagePipelineState(
    val visibleMessages: List<LiveMessage>,
    val pinnedMessages: List<PinnedMessage>,
    val skippedCount: Int,
    val newMessagesCount: Int,
    val receivedCount: Int = 0,
    val bufferStats: BufferStats,
)

private data class VisibleEntry(
    val message: LiveMessage,
    val important: Boolean,
)
