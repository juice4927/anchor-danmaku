package cn.danmaku.anchor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.ConnectionPhase
import cn.danmaku.anchor.MainActivity
import cn.danmaku.anchor.R

class NotificationController(
    private val context: Context,
) {
    private val manager = requireNotNull(context.getSystemService<NotificationManager>())

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "主播弹幕台连接",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示房间连接状态和停止按钮"
        }
        manager.createNotificationChannel(channel)
    }

    fun build(state: AnchorConnectionState): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("主播弹幕台")
            .setContentText(buildContentText(state))
            .setContentIntent(mainPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(state.phase != ConnectionPhase.Stopped)
            .addAction(0, "停止", stopPendingIntent())
            .build()

    /**
     * 前台服务已用 [build] 完成首次 startForeground 后，后续状态变化只更新
     * 通知内容。反复调用 startForeground 会带来高流量房间每秒数十次的
     * Binder 事务，[android.app.NotificationManager.notify] 是轻量替代。
     */
    fun notify(state: AnchorConnectionState) {
        manager.notify(NOTIFICATION_ID, build(state))
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    internal fun buildContentText(state: AnchorConnectionState): String {
        val roomLabel = state.roomId?.let { "房间 $it" } ?: "等待连接"
        val phaseLabel = when (state.phase) {
            ConnectionPhase.Reconnecting -> {
                val retry = state.reconnectDelaySeconds?.let { " · ${it}s 后重试" }.orEmpty()
                "${state.statusText}$retry"
            }
            ConnectionPhase.Fatal -> state.failureKind?.message ?: state.statusText
            else -> state.statusText
        }
        return "$roomLabel · $phaseLabel"
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_MAIN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            REQUEST_STOP,
            ConnectionForegroundService.stopIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_ID = "anchor_connection"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_MAIN = 2001
        private const val REQUEST_STOP = 2002
    }
}
