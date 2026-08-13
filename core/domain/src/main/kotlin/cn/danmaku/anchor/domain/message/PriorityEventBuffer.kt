package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.model.LiveMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PriorityEventBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val mutex = Mutex()
    private val entries = ArrayDeque<BufferedEvent>(capacity)
    private val wakeSignal = MutableStateFlow(0)
    private var droppedCount = 0
    private var criticalDropCount = 0
    @Volatile
    private var publishedStats = BufferStats()

    val updates: StateFlow<Int> = wakeSignal

    suspend fun offer(message: LiveMessage, important: Boolean) {
        mutex.withLock {
            if (entries.size < capacity) {
                entries.addLast(BufferedEvent(message, important))
                wakeSignal.value += 1
                return
            }

            val oldestOrdinaryIndex = entries.indexOfFirst { !it.important }
            when {
                !important && oldestOrdinaryIndex >= 0 -> {
                    entries.removeAt(oldestOrdinaryIndex)
                    droppedCount += 1
                    publishStats()
                    entries.addLast(BufferedEvent(message, false))
                }

                !important -> {
                    droppedCount += 1
                    publishStats()
                    return
                }

                oldestOrdinaryIndex >= 0 -> {
                    entries.removeAt(oldestOrdinaryIndex)
                    droppedCount += 1
                    publishStats()
                    entries.addLast(BufferedEvent(message, true))
                }

                else -> {
                    entries.removeFirst()
                    droppedCount += 1
                    criticalDropCount += 1
                    publishStats()
                    entries.addLast(BufferedEvent(message, true))
                }
            }
            wakeSignal.value += 1
        }
    }

    suspend fun drainAll(): List<BufferedEvent> = mutex.withLock {
        if (entries.isEmpty()) return emptyList()
        val drained = entries.toList()
        entries.clear()
        wakeSignal.value += 1
        drained
    }

    suspend fun snapshot(): List<BufferedEvent> = mutex.withLock { entries.toList() }

    suspend fun stats(): BufferStats = mutex.withLock {
        publishedStats
    }

    /** Returns the last lock-protected counters without suspending a UI snapshot. */
    fun statsSnapshot(): BufferStats = publishedStats

    suspend fun clear() {
        mutex.withLock {
            entries.clear()
            droppedCount = 0
            criticalDropCount = 0
            publishStats()
            wakeSignal.value += 1
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 512
    }

    private fun publishStats() {
        publishedStats = BufferStats(
            droppedCount = droppedCount,
            criticalDropCount = criticalDropCount,
        )
    }
}

data class BufferedEvent(
    val message: LiveMessage,
    val important: Boolean,
)

data class BufferStats(
    val droppedCount: Int = 0,
    val criticalDropCount: Int = 0,
)
