package cn.danmaku.anchor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import cn.danmaku.anchor.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConnectionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var notificationForegroundPosted = false
    private var lastNotificationText: String? = null

    override fun onCreate() {
        super.onCreate()
        appContainer.notificationController.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationController.NOTIFICATION_ID,
            appContainer.notificationController.build(appContainer.sessionRepository.state.value),
        )
        // 首次 startForeground 已发生，且后续相同内容的更新不需要重复 post；
        // 状态内容变化时只走轻量 notify()。
        notificationForegroundPosted = true
        lastNotificationText =
            appContainer.notificationController.buildContentText(appContainer.sessionRepository.state.value)
        ensureNotificationSync()
        when (intent?.action) {
            ACTION_CONNECT -> {
                val roomId = intent.getLongExtra(EXTRA_ROOM_ID, -1L)
                val demoMode = intent.getBooleanExtra(EXTRA_DEMO_MODE, false)
                serviceScope.launch {
                    appContainer.sessionRepository.connect(roomId = roomId, demoMode = demoMode)
                }
            }
            ACTION_RETRY -> {
                serviceScope.launch { appContainer.sessionRepository.retryNow() }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    appContainer.sessionRepository.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    appContainer.notificationController.cancel()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationSync() {
        if (notificationJob != null) return
        notificationJob = serviceScope.launch {
            appContainer.sessionRepository.state.collectLatest { state ->
                val controller = appContainer.notificationController
                val content = controller.buildContentText(state)
                if (content == lastNotificationText) {
                    return@collectLatest
                }
                lastNotificationText = content
                if (notificationForegroundPosted) {
                    controller.notify(state)
                } else {
                    startForeground(
                        NotificationController.NOTIFICATION_ID,
                        controller.build(state),
                    )
                    notificationForegroundPosted = true
                }
            }
        }
    }

    companion object {
        private const val ACTION_CONNECT = "cn.danmaku.anchor.action.CONNECT"
        private const val ACTION_RETRY = "cn.danmaku.anchor.action.RETRY"
        private const val ACTION_STOP = "cn.danmaku.anchor.action.STOP"
        private const val EXTRA_ROOM_ID = "room_id"
        private const val EXTRA_DEMO_MODE = "demo_mode"

        fun connectIntent(
            context: Context,
            roomId: Long,
            demoMode: Boolean,
        ): Intent = Intent(context, ConnectionForegroundService::class.java)
            .setAction(ACTION_CONNECT)
            .putExtra(EXTRA_ROOM_ID, roomId)
            .putExtra(EXTRA_DEMO_MODE, demoMode)

        fun retryIntent(context: Context): Intent =
            Intent(context, ConnectionForegroundService::class.java).setAction(ACTION_RETRY)

        fun stopIntent(context: Context): Intent =
            Intent(context, ConnectionForegroundService::class.java).setAction(ACTION_STOP)
    }
}
