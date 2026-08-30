package cn.danmaku.anchor.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.SessionCoordinator
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.data.RoomMetadataSource
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
    val roomTitle: String? = null,
    val roomOwnerName: String? = null,
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
    private val preferencesRepository: PreferencesStore,
    private val roomMetadataSource: RoomMetadataSource? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val state = MutableStateFlow(RoomUiState())
    private val messagePipeline = MessagePipeline(clock = SystemClock)
    private val pipelineMutex = Mutex()

    val uiState: StateFlow<RoomUiState> = state.asStateFlow()

    // RoomViewModel 以 Activity 作用域创建（AppNavigation 中 viewModel() 声明于 NavHost 外），
    // 跨导航条目复用。MessagePipeline 是进程内内存态，若连接目标房间变化而不清空，
    // 切换房间后上一房间的弹幕会残留。这里跟随连接状态里的房间号，变化即重置消息流水线。
    private var activeRoomId: Long? = null

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
                val roomId = sessionState.roomId
                if (roomId != null && roomId != activeRoomId) {
                    activeRoomId = roomId
                    resetPipelineOnRoomSwitch()
                    refreshRoomHeader(roomId)
                }
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

    private suspend fun resetPipelineOnRoomSwitch() {
        val pipelineState = pipelineMutex.withLock {
            messagePipeline.resetForNewRoom()
            messagePipeline.snapshot()
        }
        applyPipelineState(pipelineState)
    }

    /** 拉取房间标题与主播名用于弹幕台顶部；source 缺失或拉取失败时静默降级为仅房间号。 */
    private suspend fun refreshRoomHeader(roomId: Long) {
        val metadata = roomMetadataSource
            ?.let { runCatching { it.loadRoomMetadata(roomId) }.getOrNull() }
        state.update { current ->
            current.copy(
                roomTitle = metadata?.roomTitle?.takeIf { it.isNotBlank() },
                roomOwnerName = metadata?.ownerName?.takeIf { it.isNotBlank() },
            )
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

    fun blockUser(uid: Long?, userName: String?) {
        val id = uid ?: return
        viewModelScope.launch {
            preferencesRepository.addBlockedUser(id, userName.orEmpty())
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
