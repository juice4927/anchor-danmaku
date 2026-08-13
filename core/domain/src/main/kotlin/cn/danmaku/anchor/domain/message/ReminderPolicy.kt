package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.UserPreferences

class ReminderPolicy(
    private val clock: Clock,
) {
    private var lastReminderAtMillis: Long? = null

    fun shouldTrigger(important: Boolean, preferences: UserPreferences): Boolean {
        if (!important) return false
        if (!preferences.soundEnabled && !preferences.vibrationEnabled) return false

        val now = clock.nowMillis()
        val last = lastReminderAtMillis
        if (last != null && now - last < THROTTLE_WINDOW_MILLIS) {
            return false
        }
        lastReminderAtMillis = now
        return true
    }

    companion object {
        const val THROTTLE_WINDOW_MILLIS: Long = 750L
    }
}
