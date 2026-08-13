package cn.danmaku.anchor.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.AnchorMessage
import cn.danmaku.anchor.DanmakuMessage
import cn.danmaku.anchor.GiftMessage
import cn.danmaku.anchor.GuardMessage
import cn.danmaku.anchor.SessionCoordinator
import cn.danmaku.anchor.SuperChatMessage
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.domain.message.MessagePipeline
import cn.danmaku.anchor.domain.message.MessagePipelineState
import cn.danmaku.anchor.domain.time.SystemClock
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.model.UserPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import cn.danmaku.anchor.model.BlockedUser as CoreBlockedUser
import cn.danmaku.anchor.model.LiveMessage as CoreLiveMessage

data class PinnedUiMessage(
    val message: AnchorMessage,
    val expiresAtMillis: Long,
    val label: String,
)

data class RoomUiState(
    val connectionState: AnchorConnectionState = AnchorConnectionState(),
    val messages: List<AnchorMessage> = emptyList(),
    val pinnedMessages: List<PinnedUiMessage> = emptyList(),
    val isPaused: Boolean = false,
    val skippedCount: Int = 0,
    val autoFollow: Boolean = true,
    val newMessageCount: Int = 0,
    val receivedCount: Int = 0,
    val fontSizeSp: Int = 20,
    val keepScreenOn: Boolean = true,
    val droppedMessageCount: Int = 0,
    val criticalDroppedMessageCount: Int = 0,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val showJumpButton: Boolean
        get() = !autoFollow || newMessageCount > 0
}

class RoomViewModel(
    sessionRepository: SessionCoordinator,
    preferencesRepository: PreferencesStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val state = MutableStateFlow(RoomUiState())
    private val messagePipeline = MessagePipeline(clock = SystemClock)
    private val pipelineMutex = Mutex()

    val uiState: StateFlow<RoomUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                val pipelineState = pipelineMutex.withLock {
                    messagePipeline.updatePreferences(preferences.toCorePreferences())
                    messagePipeline.snapshot()
                }
                applyPipelineState(pipelineState = pipelineState, preferences = preferences)
            }
        }
        viewModelScope.launch {
            sessionRepository.state.collect { sessionState ->
                state.update { it.copy(connectionState = sessionState) }
            }
        }
        viewModelScope.launch(dispatcher) {
            sessionRepository.messages.collect { message ->
                val result = pipelineMutex.withLock {
                    messagePipeline.ingest(message.toCoreMessage())
                }
                applyPipelineState(result.state)
            }
        }
    }

    fun togglePause() {
        val paused = !state.value.isPaused
        messagePipeline.setPaused(paused)
        state.update { it.copy(isPaused = paused) }
        applyPipelineState(messagePipeline.snapshot())
    }

    fun clearFeed() {
        state.update {
            it.copy(
                messages = emptyList(),
                pinnedMessages = emptyList(),
                skippedCount = 0,
                newMessageCount = 0,
            )
        }
        viewModelScope.launch {
            val pipelineState = pipelineMutex.withLock {
                messagePipeline.clear()
                messagePipeline.snapshot()
            }
            applyPipelineState(pipelineState)
        }
    }

    fun jumpToBottom() {
        messagePipeline.setAutoFollow(true)
        state.update { it.copy(autoFollow = true) }
        applyPipelineState(messagePipeline.snapshot())
    }

    fun onAutoFollowDisabled() {
        messagePipeline.setAutoFollow(false)
        state.update { it.copy(autoFollow = false) }
        applyPipelineState(messagePipeline.snapshot())
    }

    fun dismissPinned(id: String) {
        viewModelScope.launch {
            val pipelineState = pipelineMutex.withLock {
                messagePipeline.dismissPinned(id)
                messagePipeline.snapshot()
            }
            applyPipelineState(pipelineState)
        }
    }

    private fun applyPipelineState(
        pipelineState: MessagePipelineState,
        preferences: AnchorUserPreferences? = null,
    ) {
        val now = System.currentTimeMillis()
        state.update { current ->
            current.copy(
                messages = pipelineState.visibleMessages.map(CoreLiveMessage::toAnchorMessage),
                pinnedMessages = pipelineState.pinnedMessages.map { pinned ->
                    val message = pinned.message.toAnchorMessage()
                    PinnedUiMessage(
                        message = message,
                        expiresAtMillis = pinned.expiresAtMillis,
                        label = message.pinLabel(),
                    )
                },
                skippedCount = pipelineState.skippedCount,
                newMessageCount = pipelineState.newMessagesCount,
                receivedCount = pipelineState.receivedCount,
                fontSizeSp = preferences?.fontSizeSp ?: current.fontSizeSp,
                keepScreenOn = preferences?.keepScreenOn ?: current.keepScreenOn,
                droppedMessageCount = pipelineState.bufferStats.droppedCount,
                criticalDroppedMessageCount = pipelineState.bufferStats.criticalDropCount,
                nowMillis = now,
            )
        }
    }
}

private fun AnchorUserPreferences.toCorePreferences(): UserPreferences = UserPreferences(
    danmakuTextSizeSp = fontSizeSp,
    maxMessageCount = maxMessages,
    keepScreenOn = keepScreenOn,
    soundEnabled = soundEnabled,
    vibrationEnabled = vibrationEnabled,
    minimumGiftDisplay = Money.fromWholeCny(minGiftDisplayThresholdYuan.toLong()),
    highlightGiftThreshold = Money.fromWholeCny(highlightGiftThresholdYuan.toLong()),
    keywordBlacklist = keywordBlacklist.toSet(),
    blockedUsers = blockedUsers.map { CoreBlockedUser(uid = it.uid, latestName = it.userName) },
)

private fun AnchorMessage.toCoreMessage(): CoreLiveMessage = when (this) {
    is DanmakuMessage -> CoreLiveMessage.DanmakuMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        text = text,
        medalName = medalName,
        medalLevel = medalLevel,
        repeatCount = repeatCount,
    )
    is SuperChatMessage -> CoreLiveMessage.SuperChatMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        message = message,
        priceCny = Money.fromCny(priceCny.toString()),
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
    )
    is GiftMessage -> CoreLiveMessage.GiftMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        giftName = giftName,
        count = count,
        totalCoin = totalCoin,
        coinType = coinType,
        estimatedCny = estimatedCny?.let { Money.fromCny(it.toString()) },
    )
    is GuardMessage -> CoreLiveMessage.GuardMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        guardLevel = guardLevel,
        count = count,
    )
}

private fun CoreLiveMessage.toAnchorMessage(): AnchorMessage = when (this) {
    is CoreLiveMessage.DanmakuMessage -> DanmakuMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        text = text,
        medalName = medalName,
        medalLevel = medalLevel,
        repeatCount = repeatCount,
    )
    is CoreLiveMessage.SuperChatMessage -> SuperChatMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        message = message,
        priceCny = priceCny.milliYuan / 1_000.0,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
    )
    is CoreLiveMessage.GiftMessage -> GiftMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        giftName = giftName,
        count = count,
        totalCoin = totalCoin,
        coinType = coinType,
        estimatedCny = estimatedCny?.let { it.milliYuan / 1_000.0 },
    )
    is CoreLiveMessage.GuardMessage -> GuardMessage(
        id = id,
        roomId = roomId,
        uid = uid,
        userName = userName,
        serverTimestampMillis = serverTimestampMillis,
        receivedAtMillis = receivedAtMillis,
        guardLevel = guardLevel,
        count = count,
    )
}

private fun AnchorMessage.pinLabel(): String = when (this) {
    is SuperChatMessage -> "SC"
    is GuardMessage -> "舰队"
    is GiftMessage -> "礼物"
    is DanmakuMessage -> "消息"
}
