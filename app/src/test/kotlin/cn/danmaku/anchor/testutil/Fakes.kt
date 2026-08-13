package cn.danmaku.anchor.testutil

import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.ConnectionPhase
import cn.danmaku.anchor.SessionCoordinator
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.BlockedUser
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.model.LiveMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesStore(
    initial: AnchorUserPreferences = AnchorUserPreferences(),
) : PreferencesStore {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<AnchorUserPreferences> = state.asStateFlow()

    override suspend fun currentSnapshot(): AnchorUserPreferences = state.value
    override suspend fun addRecentRoom(roomId: Long) {
        state.value = state.value.copy(
            recentRooms = listOf(roomId) + state.value.recentRooms.filterNot { it == roomId }.take(9),
        )
    }
    override suspend fun removeRecentRoom(roomId: Long) {
        state.value = state.value.copy(recentRooms = state.value.recentRooms.filterNot { it == roomId })
    }
    override suspend fun clearRecentRooms() {
        state.value = state.value.copy(recentRooms = emptyList())
    }
    override suspend fun updateFontSize(fontSizeSp: Int) {
        state.value = state.value.copy(fontSizeSp = fontSizeSp)
    }
    override suspend fun updateMaxMessages(maxMessages: Int) {
        state.value = state.value.copy(maxMessages = maxMessages)
    }
    override suspend fun updateKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(keepScreenOn = enabled)
    }
    override suspend fun updateSoundEnabled(enabled: Boolean) {
        state.value = state.value.copy(soundEnabled = enabled)
    }
    override suspend fun updateVibrationEnabled(enabled: Boolean) {
        state.value = state.value.copy(vibrationEnabled = enabled)
    }
    override suspend fun updateGiftThresholds(minGift: Int, highlightGift: Int) {
        state.value = state.value.copy(
            minGiftDisplayThresholdYuan = minGift,
            highlightGiftThresholdYuan = highlightGift,
        )
    }
    override suspend fun addKeyword(raw: String) {
        state.value = state.value.copy(keywordBlacklist = state.value.keywordBlacklist + raw)
    }
    override suspend fun removeKeyword(keyword: String) {
        state.value = state.value.copy(keywordBlacklist = state.value.keywordBlacklist.filterNot { it == keyword })
    }
    override suspend fun addBlockedUser(uid: Long, userName: String) {
        state.value = state.value.copy(blockedUsers = state.value.blockedUsers + BlockedUser(uid, userName))
    }
    override suspend fun removeBlockedUser(uid: Long) {
        state.value = state.value.copy(blockedUsers = state.value.blockedUsers.filterNot { it.uid == uid })
    }
    override suspend fun clearBlockedUsers() {
        state.value = state.value.copy(blockedUsers = emptyList())
    }
}

class FakeSessionCoordinator(
    initialState: AnchorConnectionState = AnchorConnectionState(),
) : SessionCoordinator {
    private val stateFlow = MutableStateFlow(initialState)
    private val messageFlow = MutableSharedFlow<LiveMessage>()

    var connectCalls: Int = 0
        private set
    var retryCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set
    var lastConnectedRoomId: Long? = null
        private set
    var lastConnectedDemoMode: Boolean? = null
        private set

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<LiveMessage> = messageFlow.asSharedFlow()
    override val demoAvailable: Boolean = true
    override val demoEntryLabel: String? = "回放演示"

    suspend fun emitState(state: AnchorConnectionState) {
        stateFlow.emit(state)
    }

    suspend fun emitMessage(message: LiveMessage) {
        messageFlow.emit(message)
    }

    override suspend fun connect(roomId: Long, demoMode: Boolean) {
        connectCalls += 1
        lastConnectedRoomId = roomId
        lastConnectedDemoMode = demoMode
    }

    override suspend fun retryNow() {
        retryCalls += 1
    }

    override suspend fun stop() {
        stopCalls += 1
    }
}
