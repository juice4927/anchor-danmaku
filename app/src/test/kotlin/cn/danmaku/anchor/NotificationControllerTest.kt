package cn.danmaku.anchor

import android.app.Notification
import android.app.NotificationManager
import cn.danmaku.anchor.service.ConnectionForegroundService
import cn.danmaku.anchor.service.NotificationController
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationControllerTest {
    @Test
    fun ensureChannelCreatesLowImportanceChannel() {
        val context = RuntimeEnvironment.getApplication()
        val controller = NotificationController(context)

        controller.ensureChannel()

        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationController.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun buildIncludesReconnectTextAndStopActionIntent() {
        val context = RuntimeEnvironment.getApplication()
        val controller = NotificationController(context)

        val notification = controller.build(
            AnchorConnectionState(
                phase = ConnectionPhase.Reconnecting,
                roomId = 987654L,
                failureKind = AnchorFailureKind.NetworkUnavailable,
                reconnectDelaySeconds = 15,
            ),
        )

        assertEquals("停止", notification.actions.first().title.toString())
        assertEquals(
            ConnectionForegroundService.stopIntent(context).action,
            shadowOf(notification.actions.first().actionIntent).savedIntent.action,
        )
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("15s 后重试"))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("重连中"))
    }

    @Test
    fun notifyPostsNotificationUnderFixedId() {
        val context = RuntimeEnvironment.getApplication()
        val controller = NotificationController(context)

        controller.notify(
            AnchorConnectionState(
                phase = ConnectionPhase.Connected,
                roomId = 987654L,
            ),
        )

        val posted = context.getSystemService(NotificationManager::class.java)
            ?.let { shadowOf(it).getNotification(NotificationController.NOTIFICATION_ID) }
        assertNotNull(posted)
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("已连接"))
    }
}
