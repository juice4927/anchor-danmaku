package cn.danmaku.anchor.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.SessionCoordinator
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.data.toCorePreferences
import cn.danmaku.anchor.domain.message.MessagePipeline
import cn.danmaku.anchor.domain.message.MessagePipelineState
import cn.danmaku.anchor.domain.time.SystemClock
import cn.danmaku.anchor.model.LiveMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PinnedUiMessage(
    val message: LiveMessage,
    val expiresAtMillis: Long,
    val label: String,
)

data class RoomUiState(
    val connectionState: AnchorConnectionState = AnchorConnectionState(),
    val messages: List<LiveMessage> = emptyList(),
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
                    messagePipeline.ingest(message)
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
        state.update { current ->
            current.copy(
                messages = pipelineState.visibleMessages,
                pinnedMessages = pipelineState.pinnedMessages.map { pinned ->
                    PinnedUiMessage(
                        message = pinned.message,
                        expiresAtMillis = pinned.expiresAtMillis,
                        label = pinned.message.pinLabel(),
                    )
                },
                skippedCount = pipelineState.skippedCount,
                newMessageCount = pipelineState.newMessagesCount,
                receivedCount = pipelineState.receivedCount,
                fontSizeSp = preferences?.fontSizeSp ?: current.fontSizeSp,
                keepScreenOn = preferences?.keepScreenOn ?: current.keepScreenOn,
                droppedMessageCount = pipelineState.bufferStats.droppedCount,
                criticalDroppedMessageCount = pipelineState.bufferStats.criticalDropCount,
            )
        }
    }
}

private fun LiveMessage.pinLabel(): String = when (this) {
    is LiveMessage.SuperChatMessage -> "SC"
    is LiveMessage.GuardMessage -> "舰队"
    is LiveMessage.GiftMessage -> "礼物"
    is LiveMessage.DanmakuMessage -> "消息"
}
