package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.model.LiveMessage
import java.util.LinkedHashMap

class MessageDeduplicator(
    private val maxEntries: Int = MAX_ENTRIES,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    private val seen = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    fun shouldAccept(message: LiveMessage, nowMillis: Long): Boolean {
        // 只在接近容量时才做全量过期清理，避免每条消息都扫描整个表；
        // 命中已过期 id 时下方会直接覆盖为新的时间戳，语义不变。
        if (seen.size >= maxEntries) {
            pruneExpired(nowMillis)
        }
        val existing = seen[message.id]
        if (existing != null && nowMillis - existing <= windowMillis) {
            return false
        }
        seen[message.id] = nowMillis
        pruneOverflow()
        return true
    }

    private fun pruneExpired(nowMillis: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > windowMillis) {
                iterator.remove()
            }
        }
    }

    private fun pruneOverflow() {
        while (seen.size > maxEntries) {
            val eldestKey = seen.entries.firstOrNull()?.key ?: return
            seen.remove(eldestKey)
        }
    }

    companion object {
        const val MAX_ENTRIES: Int = 2_048

        /**
         * 协议层重复帧（同一消息被服务端在同一秒内重复下发）在此窗口内抑制。
         *
         * 窗口不能大于弹幕服务端时间戳的秒级粒度带来的副作用：弹幕 id 包含按秒的
         * 服务端时间戳，同一用户在同一秒内发送的两条相同文本会被视为同 id。这类
         * 重复通常由 3 秒合并器展示为 ×N，去重窗口过长会吞掉同秒连发的计数。
         * 10 秒覆盖了真实的重连/重发场景，同时把同秒误判限制在极小窗口内。
         */
        const val WINDOW_MILLIS: Long = 10_000L
    }
}
