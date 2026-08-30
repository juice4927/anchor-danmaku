package cn.danmaku.anchor

import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.testutil.FakePreferencesStore
import cn.danmaku.anchor.testutil.FakeSessionCoordinator
import cn.danmaku.anchor.testutil.MainDispatcherRule
import cn.danmaku.anchor.ui.room.RoomViewModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pauseSkipsOrdinaryFeedMessages() = runTest {
        val session = FakeSessionCoordinator(
            initialState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
        )
        val viewModel = RoomViewModel(session, FakePreferencesStore(AnchorUserPreferences(maxMessages = 100)), mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        viewModel.togglePause()
        session.emitMessage(
            LiveMessage.DanmakuMessage(
                id = "d1",
                roomId = 987654L,
                uid = 10001L,
                userName = "普通观众",
                serverTimestampMillis = 1L,
                receivedAtMillis = 1L,
                text = "hello",
                medalName = null,
                medalLevel = null,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(1, viewModel.uiState.value.skippedCount)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.pinnedMessages.isEmpty())
    }

    @Test
    fun pauseKeepsImportantPinsWithoutIncrementingSkippedCount() = runTest {
        val session = FakeSessionCoordinator(
            initialState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
        )
        val viewModel = RoomViewModel(session, FakePreferencesStore(AnchorUserPreferences(maxMessages = 100)), mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        viewModel.togglePause()
        session.emitMessage(
            LiveMessage.SuperChatMessage(
                id = "sc1",
                roomId = 987654L,
                uid = 20002L,
                userName = "SC用户",
                serverTimestampMillis = 2L,
                receivedAtMillis = 2L,
                message = "加油",
                priceCny = Money.fromWholeCny(30),
                startTimeMillis = 2L,
                endTimeMillis = 60_002L,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(0, viewModel.uiState.value.skippedCount)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(listOf("sc1"), viewModel.uiState.value.pinnedMessages.map { it.message.id })
    }

    @Test
    fun clearFeedRemovesMessagesWithoutChangingConnectionState() = runTest {
        val session = FakeSessionCoordinator(
            initialState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
        )
        val viewModel = RoomViewModel(session, FakePreferencesStore(), mainDispatcherRule.dispatcher)
        advanceUntilIdle()
        session.emitMessage(
            LiveMessage.DanmakuMessage(
                id = "d1",
                roomId = 987654L,
                uid = 10001L,
                userName = "普通观众",
                serverTimestampMillis = 1L,
                receivedAtMillis = 1L,
                text = "hello",
                medalName = null,
                medalLevel = null,
            ),
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)

        viewModel.clearFeed()
        advanceUntilIdle()

        assertEquals(ConnectionPhase.Connected, viewModel.uiState.value.connectionState.phase)
        assertEquals(987654L, viewModel.uiState.value.connectionState.roomId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.pinnedMessages.isEmpty())
        assertEquals(0, viewModel.uiState.value.skippedCount)
        assertEquals(0, viewModel.uiState.value.newMessageCount)
    }

    @Test
    fun jumpToBottomReenablesAutoFollowAndClearsUnreadCount() = runTest {
        val session = FakeSessionCoordinator(
            initialState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
        )
        val viewModel = RoomViewModel(session, FakePreferencesStore(), mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        viewModel.onAutoFollowDisabled()
        session.emitMessage(
            LiveMessage.DanmakuMessage(
                id = "d1",
                roomId = 987654L,
                uid = 10001L,
                userName = "普通观众",
                serverTimestampMillis = 1L,
                receivedAtMillis = 1L,
                text = "hello",
                medalName = null,
                medalLevel = null,
            ),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.autoFollow)
        assertEquals(1, viewModel.uiState.value.newMessageCount)
        assertTrue(viewModel.uiState.value.showJumpButton)

        viewModel.jumpToBottom()

        assertTrue(viewModel.uiState.value.autoFollow)
        assertEquals(0, viewModel.uiState.value.newMessageCount)
    }

    @Test
    fun switchingRoomClearsPreviousMessages() = runTest {
        val session = FakeSessionCoordinator(
            initialState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
        )
        val viewModel = RoomViewModel(session, FakePreferencesStore(AnchorUserPreferences(maxMessages = 100)), mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        // 房间 A 收到弹幕
        session.emitMessage(
            LiveMessage.DanmakuMessage(
                id = "a1",
                roomId = 987654L,
                uid = 10001L,
                userName = "观众A",
                serverTimestampMillis = 1L,
                receivedAtMillis = 1L,
                text = "房间A的弹幕",
                medalName = null,
                medalLevel = null,
            ),
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)

        // 切换到房间 B（房间号变化）
        session.emitState(AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 11111L))
        advanceUntilIdle()

        // 旧房间弹幕应被清空
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(0, viewModel.uiState.value.receivedCount)
        assertEquals(ConnectionPhase.Connected, viewModel.uiState.value.connectionState.phase)
        assertEquals(11111L, viewModel.uiState.value.connectionState.roomId)
    }
}
