package cn.danmaku.anchor

import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.testutil.FakePreferencesStore
import cn.danmaku.anchor.testutil.FakeSessionCoordinator
import cn.danmaku.anchor.testutil.MainDispatcherRule
import cn.danmaku.anchor.ui.connect.ConnectViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ConnectViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidRoomShowsChineseError() = runTest {
        val viewModel = ConnectViewModel(
            preferencesRepository = FakePreferencesStore(),
            sessionRepository = FakeSessionCoordinator(),
        )
        viewModel.updateInput("0000")
        assertNull(viewModel.buildConnectRequest(useDemo = false))
        assertEquals("请输入正确的直播间号", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun updateInputKeepsDigitsOnlyAndClampsToTwelveCharacters() = runTest {
        val viewModel = ConnectViewModel(
            preferencesRepository = FakePreferencesStore(),
            sessionRepository = FakeSessionCoordinator(),
        )

        viewModel.updateInput("ab12 34567890123456")

        assertEquals("123456789012", viewModel.uiState.value.roomInput)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun validRoomBuildsConnectRequestWithDemoFlag() = runTest {
        val viewModel = ConnectViewModel(
            preferencesRepository = FakePreferencesStore(),
            sessionRepository = FakeSessionCoordinator(),
        )

        viewModel.updateInput("987654")

        val request = assertNotNull(viewModel.buildConnectRequest(useDemo = true))
        assertEquals(987654L, request.roomId)
        assertEquals(true, request.useDemo)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun recentRoomPrefillsInput() = runTest {
        val viewModel = ConnectViewModel(
            preferencesRepository = FakePreferencesStore(
                AnchorUserPreferences(recentRooms = listOf(987654L, 1234L)),
            ),
            sessionRepository = FakeSessionCoordinator(),
        )
        assertEquals("987654", viewModel.uiState.value.roomInput)
    }
}
