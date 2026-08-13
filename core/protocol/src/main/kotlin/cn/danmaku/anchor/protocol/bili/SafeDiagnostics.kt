package cn.danmaku.anchor.protocol.bili

import cn.danmaku.anchor.domain.gateway.GatewayDiagnostics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SafeDiagnostics {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun increment(key: String, delta: Long = 1L): Long {
        require(delta >= 0L) { "delta must be non-negative" }
        return counters.computeIfAbsent(key) { AtomicLong() }.addAndGet(delta)
    }

    fun snapshot(): Map<String, Long> = counters
        .mapValues { (_, value) -> value.get() }
        .toSortedMap()

    fun toGatewayDiagnostics(): GatewayDiagnostics {
        val snapshot = snapshot()
        return GatewayDiagnostics(
            unknownCommandCount = snapshot.countPrefix("unknown_command"),
            malformedFrameCount = snapshot.countPrefix("malformed"),
            oversizedFrameCount = snapshot.countPrefix("oversized"),
            unsupportedOperationCount = snapshot.countPrefix("unsupported"),
        )
    }

    private fun Map<String, Long>.countPrefix(prefix: String): Int = entries
        .filter { (key, _) -> key.startsWith(prefix) }
        .sumOf { (_, value) -> value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
}
