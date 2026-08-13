package cn.danmaku.anchor

import android.Manifest
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
    fun returnsFalseWhenNotificationPermissionIsDenied() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val sink = AndroidReminderSink(
            context = context,
            nowProvider = { 1_000L },
            soundPlayer = { error("sound should not run") },
            vibrationPlayer = { error("vibration should not run") },
        )

        val accepted = sink.remind(
            ReminderPayload("1", "SC", "a"),
            AnchorUserPreferences(),
        )

        assertFalse(accepted)
        assertTrue(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED,
        )
    }
}
