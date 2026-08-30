package cn.danmaku.anchor

import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.RoomMetadata
import cn.danmaku.anchor.testutil.FakePreferencesStore
import cn.danmaku.anchor.testutil.FakeRoomMetadataSource
import cn.danmaku.anchor.testutil.FakeSessionCoordinator
import cn.danmaku.anchor.testutil.MainDispatcherRule
import cn.danmaku.anchor.ui.connect.ConnectViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        preferences: AnchorUserPreferences = AnchorUserPreferences(),
        metadata: Map<Long, RoomMetadata> = emptyMap(),
    ): ConnectViewModel = ConnectViewModel(
        preferencesRepository = FakePreferencesStore(preferences),
        sessionRepository = FakeSessionCoordinator(),
        roomMetadataSource = FakeRoomMetadataSource(metadata),
    )

    @Test
    fun invalidRoomShowsChineseError() = runTest {
        val viewModel = viewModel()
        viewModel.updateInput("0000")
        assertNull(viewModel.buildConnectRequest(useDemo = false))
        assertEquals("请输入正确的直播间号", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun updateInputKeepsDigitsOnlyAndClampsToTwelveCharacters() = runTest {
        val viewModel = viewModel()

        viewModel.updateInput("ab12 34567890123456")

        assertEquals("123456789012", viewModel.uiState.value.roomInput)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun validRoomBuildsConnectRequestWithDemoFlag() = runTest {
        val viewModel = viewModel()

        viewModel.updateInput("987654")

        val request = assertNotNull(viewModel.buildConnectRequest(useDemo = true))
        assertEquals(987654L, request.roomId)
        assertEquals(true, request.useDemo)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun recentRoomPrefillsInput() = runTest {
        val viewModel = viewModel(
            preferences = AnchorUserPreferences(recentRooms = listOf(987654L, 1234L)),
        )
        assertEquals("987654", viewModel.uiState.value.roomInput)
    }

    @Test
    fun recentRoomsResolveOwnerNameAndLiveStatus() = runTest {
        val viewModel = viewModel(
            preferences = AnchorUserPreferences(recentRooms = listOf(987654L, 1234L)),
            metadata = mapOf(
                987654L to RoomMetadata(roomId = 987654L, ownerName = "永雏塔菲", liveStatus = LiveStatus.LIVE),
                1234L to RoomMetadata(roomId = 1234L, ownerName = "某主播", liveStatus = LiveStatus.NOT_LIVE),
            ),
        )
        advanceUntilIdle()

        val rooms = viewModel.uiState.value.recentRooms
        assertEquals(2, rooms.size)
        assertEquals("永雏塔菲", rooms[0].ownerName)
        assertEquals(LiveStatus.LIVE, rooms[0].liveStatus)
        assertEquals(LiveStatus.NOT_LIVE, rooms[1].liveStatus)
    }

    @Test
    fun recentRoomMetadataFailureDegradesToRoomNumberOnly() = runTest {
        val viewModel = ConnectViewModel(
            preferencesRepository = FakePreferencesStore(
                AnchorUserPreferences(recentRooms = listOf(987654L)),
            ),
            sessionRepository = FakeSessionCoordinator(),
            roomMetadataSource = FakeRoomMetadataSource(failureFor = 987654L),
        )
        advanceUntilIdle()

        val rooms = viewModel.uiState.value.recentRooms
        assertEquals(1, rooms.size)
        assertNull(rooms[0].ownerName)
        assertEquals(LiveStatus.UNKNOWN, rooms[0].liveStatus)
    }
}
