package cn.danmaku.anchor.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import cn.danmaku.anchor.data.AnchorUserPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ReminderSink {
    suspend fun remind(payload: ReminderPayload, preferences: AnchorUserPreferences): Boolean
}

class AndroidReminderSink(
    private val context: Context,
    private val nowProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val soundPlayer: (Context) -> Unit = { appContext ->
        RingtoneManager.getRingtone(
            appContext,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )?.play()
    },
    private val vibrationPlayer: (Context) -> Unit = { appContext ->
        appContext.getSystemService<Vibrator>()?.vibrate(
            VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    },
) : ReminderSink {
    private val mutex = Mutex()
    private var lastHardwareTriggerMillis: Long? = null

    override suspend fun remind(
        payload: ReminderPayload,
        preferences: AnchorUserPreferences,
    ): Boolean = mutex.withLock {
        if (!hasNotificationPermission()) return false
        val now = nowProvider()
        val lastTrigger = lastHardwareTriggerMillis
        if (lastTrigger != null && now - lastTrigger < THROTTLE_WINDOW_MILLIS) return false
        if (preferences.soundEnabled) {
            soundPlayer(context)
        }
        if (preferences.vibrationEnabled) {
            vibrationPlayer(context)
        }
        lastHardwareTriggerMillis = now
        true
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val THROTTLE_WINDOW_MILLIS = 750L
    }
}
