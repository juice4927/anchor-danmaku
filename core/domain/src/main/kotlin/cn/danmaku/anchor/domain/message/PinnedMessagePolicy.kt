package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.LiveMessage
import kotlin.math.max
import kotlin.math.min

class PinnedMessagePolicy(
    private val clock: Clock,
) {
    private val pinned = ArrayDeque<PinnedMessage>()

    fun add(message: LiveMessage, important: Boolean): List<PinnedMessage> {
        pruneExpired()
        if (!important) return snapshot()

        val durationMillis = when (message) {
            is LiveMessage.SuperChatMessage -> superChatDurationMillis(message)
            is LiveMessage.GuardMessage -> 30_000L
            is LiveMessage.GiftMessage -> 15_000L
            is LiveMessage.DanmakuMessage -> return snapshot()
        }
        pinned.addFirst(
            PinnedMessage(
                message = message,
                expiresAtMillis = clock.nowMillis() + durationMillis,
            ),
        )
        while (pinned.size > MAX_PINNED_MESSAGES) {
            pinned.removeLast()
        }
        return snapshot()
    }

    fun dismiss(messageId: String): List<PinnedMessage> {
        pruneExpired()
        pinned.removeAll { it.message.id == messageId }
        return snapshot()
    }

    fun clear(): List<PinnedMessage> {
        pinned.clear()
        return emptyList()
    }

    fun snapshot(): List<PinnedMessage> {
        pruneExpired()
        return pinned.toList()
    }

    private fun pruneExpired() {
        val now = clock.nowMillis()
        pinned.removeAll { it.expiresAtMillis <= now }
    }

    private fun superChatDurationMillis(message: LiveMessage.SuperChatMessage): Long {
        val endTime = message.endTimeMillis ?: return 60_000L
        val remaining = endTime - clock.nowMillis()
        return min(120_000L, max(15_000L, remaining))
    }

    companion object {
        const val MAX_PINNED_MESSAGES: Int = 3
    }
}

data class PinnedMessage(
    val message: LiveMessage,
    val expiresAtMillis: Long,
)
