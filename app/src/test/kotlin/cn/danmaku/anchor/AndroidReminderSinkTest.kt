package cn.danmaku.anchor

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.reminder.AndroidReminderSink
import cn.danmaku.anchor.reminder.ReminderPayload
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidReminderSinkTest {
    @Test
    @Config(sdk = [28])
    fun skipsHardwareFeedbackWhenSoundAndVibrationAreDisabled() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val invocations = mutableListOf<String>()
        val sink = AndroidReminderSink(
            context = context,
            nowProvider = { 1_000L },
            soundPlayer = { invocations += "sound" },
            vibrationPlayer = { invocations += "vibrate" },
        )

        val accepted = sink.remind(
            ReminderPayload("1", "SC", "a"),
            AnchorUserPreferences(soundEnabled = false, vibrationEnabled = false),
        )

        assertTrue(accepted)
        assertTrue(invocations.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun fallsBackToHardwareFeedbackWhenNotificationPermissionIsDenied() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val invocations = mutableListOf<String>()
        val sink = AndroidReminderSink(
            context = context,
            nowProvider = { 1_000L },
            soundPlayer = { invocations += "sound" },
            vibrationPlayer = { invocations += "vibrate" },
        )

        val accepted = sink.remind(
            ReminderPayload("1", "SC", "a"),
            AnchorUserPreferences(),
        )

        assertTrue(accepted)
        assertTrue(invocations.contains("sound"))
        assertTrue(invocations.contains("vibrate"))
        assertTrue(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED,
        )
    }

    @Test
    @Config(sdk = [33])
    fun postsReminderNotificationWhenPermissionIsGranted() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val sink = AndroidReminderSink(
            context = context,
            nowProvider = { 1_000L },
            soundPlayer = { error("sound should not run when notification channel is available") },
            vibrationPlayer = { error("vibration should not run when notification channel is available") },
        )

        val accepted = sink.remind(
            ReminderPayload("1", "醒目留言", "提醒内容"),
            AnchorUserPreferences(),
        )

        assertTrue(accepted)
        val manager = context.getSystemService(NotificationManager::class.java)!!
        assertTrue(shadowOf(manager).size() > 0)
    }
}
