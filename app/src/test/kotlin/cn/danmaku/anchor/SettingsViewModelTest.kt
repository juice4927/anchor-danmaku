package cn.danmaku.anchor

import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.BlockedUser
import cn.danmaku.anchor.testutil.FakePreferencesStore
import cn.danmaku.anchor.testutil.MainDispatcherRule
import cn.danmaku.anchor.ui.settings.SettingsViewModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reflectsKeywordAndBlacklistChangesFromStore() = runTest {
        val store = FakePreferencesStore(
            AnchorUserPreferences(
                keywordBlacklist = listOf("spam"),
                blockedUsers = listOf(BlockedUser(uid = 10001L, userName = "测试观众")),
                recentRooms = listOf(987654L),
            ),
        )
        val viewModel = SettingsViewModel(store)

        viewModel.addKeyword("礼物")
        viewModel.removeKeyword("spam")
        viewModel.removeBlockedUser(10001L)
        viewModel.removeRecentRoom(987654L)
        advanceUntilIdle()

        assertEquals(listOf("礼物"), viewModel.uiState.value.preferences.keywordBlacklist)
        assertTrue(viewModel.uiState.value.preferences.blockedUsers.isEmpty())
        assertTrue(viewModel.uiState.value.preferences.recentRooms.isEmpty())
    }

    @Test
    fun forwardsAllValidPreferenceUpdatesToStore() = runTest {
        val store = FakePreferencesStore(AnchorUserPreferences())
        val viewModel = SettingsViewModel(store)

        viewModel.updateFontSize(24)
        viewModel.updateMaxMessages(500)
        viewModel.updateKeepScreenOn(false)
        viewModel.updateSoundEnabled(false)
        viewModel.updateVibrationEnabled(false)
        viewModel.updateGiftThresholds(10, 500)
        advanceUntilIdle()

        assertEquals(24, viewModel.uiState.value.preferences.fontSizeSp)
        assertEquals(500, viewModel.uiState.value.preferences.maxMessages)
        assertEquals(false, viewModel.uiState.value.preferences.keepScreenOn)
        assertEquals(false, viewModel.uiState.value.preferences.soundEnabled)
        assertEquals(false, viewModel.uiState.value.preferences.vibrationEnabled)
        assertEquals(10, viewModel.uiState.value.preferences.minGiftDisplayThresholdYuan)
        assertEquals(500, viewModel.uiState.value.preferences.highlightGiftThresholdYuan)
    }
}
