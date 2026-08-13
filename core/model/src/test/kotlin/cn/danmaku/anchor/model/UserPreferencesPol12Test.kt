package cn.danmaku.anchor.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserPreferencesPol12Test {

    @Test
    fun `POL-12 invalid persisted values normalize to frozen defaults and limits`() {
        val preferences = UserPreferences(
            danmakuTextSizeSp = 17,
            maxMessageCount = 42,
            keepScreenOn = true,
            soundEnabled = false,
            vibrationEnabled = false,
            minimumGiftDisplay = Money.fromCny("3"),
            highlightGiftThreshold = Money.fromCny("1"),
            keywordBlacklist = setOf("", "  ", "Spam", "spam", "x".repeat(33), "有效词"),
            blockedUsers = listOf(
                BlockedUser(uid = 0L, latestName = "bad"),
                BlockedUser(uid = 42L, latestName = " 屏蔽用户 "),
                BlockedUser(uid = 42L, latestName = "覆盖名字"),
            ),
            recentRoomIds = listOf(-1L, 1L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L),
        )

        assertThat(preferences.danmakuTextSizeSp).isEqualTo(UserPreferences.DEFAULT_DANMAKU_TEXT_SIZE_SP)
        assertThat(preferences.maxMessageCount).isEqualTo(UserPreferences.DEFAULT_MAX_MESSAGE_COUNT)
        assertThat(preferences.minimumGiftDisplay.toCanonicalString()).isEqualTo("0")
        assertThat(preferences.highlightGiftThreshold.toCanonicalString()).isEqualTo("100")
        assertThat(preferences.keywordBlacklist).containsExactly("spam", "有效词")
        assertThat(preferences.blockedUsers).containsExactly(
            BlockedUser(uid = 42L, latestName = "覆盖名字"),
        )
        assertThat(preferences.recentRoomIds).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            .inOrder()
    }
}
