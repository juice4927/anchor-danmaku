package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.model.LiveMessage

class DanmakuCoalescer(
    private val windowMillis: Long = WINDOW_MILLIS,
    private val maxWindows: Int = MAX_WINDOWS,
) {
    private val windows = linkedMapOf<CoalesceKey, CoalesceWindow>()

    fun coalesce(message: LiveMessage, nowMillis: Long): CoalesceResult {
        if (message !is LiveMessage.DanmakuMessage || message.uid == null) {
            return CoalesceResult.Append(message)
        }

        val uid = message.uid ?: return CoalesceResult.Append(message)
        val key = CoalesceKey(
            roomId = message.roomId,
            uid = uid,
            text = message.text,
        )
        val existing = windows[key]
        if (existing != null && nowMillis - existing.lastSeenAtMillis <= windowMillis) {
            val merged = existing.message.copy(
                receivedAtMillis = message.receivedAtMillis,
                serverTimestampMillis = message.serverTimestampMillis ?: existing.message.serverTimestampMillis,
                repeatCount = existing.message.repeatCount + 1,
            )
            windows[key] = existing.copy(
                message = merged,
                lastSeenAtMillis = nowMillis,
            )
            return CoalesceResult.Replace(existing.message.id, merged)
        }
        // 一次性消息的 (uid, text) 组合只进不出，高流量直播间会无限累积；
        // 仅在新窗口加入前清理过期项（先过期后淘汰最旧），保证存活窗口不被误杀。
        if (windows.size >= maxWindows) {
            pruneExpired(nowMillis)
            evictOldest()
        }
        windows[key] = CoalesceWindow(
            message = message,
            lastSeenAtMillis = nowMillis,
        )
        return CoalesceResult.Append(message)
    }

    private fun pruneExpired(nowMillis: Long) {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().value.lastSeenAtMillis > windowMillis) {
                iterator.remove()
            }
        }
    }

    private fun evictOldest() {
        while (windows.size >= maxWindows) {
            val iterator = windows.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    private data class CoalesceKey(
        val roomId: Long,
        val uid: Long,
        val text: String,
    )

    private data class CoalesceWindow(
        val message: LiveMessage.DanmakuMessage,
        val lastSeenAtMillis: Long,
    )

    companion object {
        const val WINDOW_MILLIS: Long = 3_000L

        /** 并发窗口上限：防止长时间运行中一次性弹幕的 (uid, text) 组合无界增长。 */
        const val MAX_WINDOWS: Int = 4_096
    }
}

sealed interface CoalesceResult {
    data class Append(
        val message: LiveMessage,
    ) : CoalesceResult

    data class Replace(
        val originalMessageId: String,
        val message: LiveMessage.DanmakuMessage,
    ) : CoalesceResult
}
