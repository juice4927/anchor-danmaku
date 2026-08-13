package cn.danmaku.anchor.domain.session

class ReconnectPolicy(
    private val backoffDelaysMillis: LongArray = DEFAULT_BACKOFF_DELAYS_MILLIS,
) {
    fun delayMillisForAttempt(attempt: Int): Long {
        if (attempt <= 0) return backoffDelaysMillis.first()
        val index = (attempt - 1).coerceAtMost(backoffDelaysMillis.lastIndex)
        return backoffDelaysMillis[index]
    }

    companion object {
        const val STAGE_TIMEOUT_MILLIS: Long = 10_000L
        const val HEARTBEAT_INTERVAL_MILLIS: Long = 30_000L
        const val IDLE_TIMEOUT_MILLIS: Long = 90_000L
        const val STABLE_CONNECTION_RESET_MILLIS: Long = 30_000L

        val DEFAULT_BACKOFF_DELAYS_MILLIS = longArrayOf(
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            32_000L,
            60_000L,
        )
    }
}
