package cn.danmaku.anchor.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import cn.danmaku.anchor.MainActivity
import cn.danmaku.anchor.R
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
    private val manager = context.getSystemService<NotificationManager>()
    private val mutex = Mutex()
    private var lastHardwareTriggerMillis: Long? = null

    override suspend fun remind(
        payload: ReminderPayload,
        preferences: AnchorUserPreferences,
    ): Boolean = mutex.withLock {
        val now = nowProvider()
        val lastTrigger = lastHardwareTriggerMillis
        if (lastTrigger != null && now - lastTrigger < THROTTLE_WINDOW_MILLIS) return false
        lastHardwareTriggerMillis = now
        if (hasNotificationPermission()) {
            postReminderNotification(payload)
        } else {
            // 无通知权限时直接硬件提醒兜底：渠道通知依赖系统权限，
            // 但声音/震动仍是用户可感知的提醒途径。
            if (preferences.soundEnabled) {
                soundPlayer(context)
            }
            if (preferences.vibrationEnabled) {
                vibrationPlayer(context)
            }
        }
        true
    }

    private fun postReminderNotification(payload: ReminderPayload) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_danmaku)
            .setContentTitle(payload.title)
            .setContentText(payload.content)
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        manager?.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "重要消息提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "醒目留言、上舰与高额礼物提醒"
        }
        manager?.createNotificationChannel(channel)
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_REMINDER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REMINDER_CHANNEL_ID = "anchor_important"
        const val REMINDER_NOTIFICATION_ID = 2001
        private const val REQUEST_REMINDER = 3001
        const val THROTTLE_WINDOW_MILLIS = 750L
    }
}
