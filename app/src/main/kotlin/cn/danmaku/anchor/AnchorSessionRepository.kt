package cn.danmaku.anchor

import cn.danmaku.anchor.domain.session.SessionController
import cn.danmaku.anchor.domain.session.SessionState
import cn.danmaku.anchor.domain.message.isImportant
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.debug.DemoScriptEvent
import cn.danmaku.anchor.debug.DemoSource
import cn.danmaku.anchor.reminder.ReminderPayload
import cn.danmaku.anchor.reminder.ReminderSink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnchorSessionRepository(
    private val scope: CoroutineScope,
    private val preferencesRepository: PreferencesStore,
    private val reminderSink: ReminderSink,
    private val demoSource: DemoSource,
    private val coreSessionController: SessionController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SessionCoordinator {
    private val guard = Mutex()
    private val stateFlow = MutableStateFlow(AnchorConnectionState())
    private val messageFlow = MutableSharedFlow<LiveMessage>(extraBufferCapacity = 1024)

    private var activeBackend: SessionBackend? = null
    private var stateJob: Job? = null
    private var messageJob: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<LiveMessage> = messageFlow.asSharedFlow()
    override val demoAvailable: Boolean
        get() = demoSource.isAvailable
    override val demoEntryLabel: String?
        get() = demoSource.entryLabel

    override suspend fun connect(
        roomId: Long,
        demoMode: Boolean,
    ) {
        guard.withLock {
            stopLocked()
            val backend = if (demoMode && demoSource.isAvailable) {
                DemoSessionBackend(roomId = roomId, source = demoSource, dispatcher = dispatcher)
            } else {
                CoreSessionBackend(
                    controller = coreSessionController,
                    scope = scope,
                    dispatcher = dispatcher,
                )
            }
            activeBackend = backend
            stateFlow.value = backend.state.value
            bindBackend(backend)
            backend.start(roomId)
        }
    }

    override suspend fun retryNow() {
        activeBackend?.retryNow()
    }

    override suspend fun stop() {
        guard.withLock {
            stopLocked()
            stateFlow.value = AnchorConnectionState(phase = ConnectionPhase.Stopped)
        }
    }

    private fun bindBackend(backend: SessionBackend) {
        stateJob = scope.launch(dispatcher) {
            backend.state.collectLatest { newState ->
                stateFlow.value = newState
                if (newState.phase == ConnectionPhase.Connected) {
                    newState.roomId?.let { preferencesRepository.addRecentRoom(it) }
                }
            }
        }
        messageJob = scope.launch(dispatcher) {
            backend.messages.collect { message ->
                messageFlow.emit(message)
                val preferences = preferencesRepository.currentSnapshot()
                if (isImportant(message, preferences)) {
                    reminderSink.remind(ReminderPayload.fromMessage(message), preferences)
                }
            }
        }
    }

    private suspend fun stopLocked() {
        stateJob?.cancel()
        messageJob?.cancel()
        activeBackend?.stop()
        activeBackend = null
    }

    private fun isImportant(
        message: LiveMessage,
        preferences: AnchorUserPreferences,
    ): Boolean = message.isImportant(Money.fromWholeCny(preferences.highlightGiftThresholdYuan.toLong()))
}

interface SessionCoordinator {
    val state: StateFlow<AnchorConnectionState>
    val messages: Flow<LiveMessage>
    val demoAvailable: Boolean
    val demoEntryLabel: String?

    suspend fun connect(roomId: Long, demoMode: Boolean)
    suspend fun retryNow()
    suspend fun stop()
}

private interface SessionBackend {
    val state: StateFlow<AnchorConnectionState>
    val messages: Flow<LiveMessage>

    suspend fun start(roomId: Long)
    suspend fun retryNow()
    suspend fun stop()
}

private class DemoSessionBackend(
    private val roomId: Long,
    private val source: DemoSource,
    private val dispatcher: CoroutineDispatcher,
) : SessionBackend {
    private val stateFlow = MutableStateFlow(
        AnchorConnectionState(
            phase = ConnectionPhase.Idle,
            roomId = roomId,
            inputRoomId = roomId,
        ),
    )
    private val messageFlow = MutableSharedFlow<LiveMessage>(extraBufferCapacity = 64)
    private var job: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<LiveMessage> = messageFlow.asSharedFlow()

    override suspend fun start(roomId: Long) {
        job?.cancel()
        job = CoroutineScope(dispatcher).launch {
            source.scriptFor(roomId).forEach { step ->
                when (step) {
                    is DemoScriptEvent.Delay -> delay(step.millis)
                    is DemoScriptEvent.Connection -> stateFlow.value = step.state
                    is DemoScriptEvent.Message -> messageFlow.emit(step.message)
                }
            }
        }
    }

    override suspend fun retryNow() {
        start(roomId)
    }

    override suspend fun stop() {
        job?.cancel()
        stateFlow.value = AnchorConnectionState(
            phase = ConnectionPhase.Stopped,
            roomId = roomId,
            inputRoomId = roomId,
        )
    }
}

private class CoreSessionBackend(
    private val controller: SessionController,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) : SessionBackend {
    private val stateFlow = MutableStateFlow(controller.state.value.toAnchorState())
    private val messageFlow = MutableSharedFlow<LiveMessage>(extraBufferCapacity = 1024)
    private var stateJob: Job? = null
    private var messageJob: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<LiveMessage> = messageFlow.asSharedFlow()

    override suspend fun start(roomId: Long) {
        stateJob?.cancel()
        messageJob?.cancel()
        stateJob = scope.launch(dispatcher) {
            controller.state.collect { stateFlow.value = it.toAnchorState() }
        }
        messageJob = scope.launch(dispatcher) {
            controller.events.collect { messageFlow.emit(it) }
        }
        controller.start(roomId)
    }

    override suspend fun retryNow() {
        controller.retryNow()
    }

    override suspend fun stop() {
        controller.stop()
        stateJob?.cancel()
        messageJob?.cancel()
        stateJob = null
        messageJob = null
    }
}

private fun SessionState.toAnchorState(): AnchorConnectionState = when (this) {
    SessionState.Idle -> AnchorConnectionState(phase = ConnectionPhase.Idle)
    is SessionState.Resolving -> AnchorConnectionState(
        phase = ConnectionPhase.Resolving,
        inputRoomId = inputRoomId,
    )
    is SessionState.Connecting -> AnchorConnectionState(
        phase = ConnectionPhase.Connecting,
        roomId = roomInfo?.roomId,
        inputRoomId = inputRoomId,
        liveLabel = roomInfo?.liveStatus?.toDisplayLabel(),
    )
    is SessionState.Authenticating -> AnchorConnectionState(
        phase = ConnectionPhase.Authenticating,
        roomId = roomInfo?.roomId,
        inputRoomId = roomInfo?.inputRoomId,
        liveLabel = roomInfo?.liveStatus?.toDisplayLabel(),
    )
    is SessionState.Connected -> AnchorConnectionState(
        phase = ConnectionPhase.Connected,
        roomId = roomInfo.roomId,
        inputRoomId = roomInfo.inputRoomId,
        popularity = popularity?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
        liveLabel = roomInfo.liveStatus.toDisplayLabel(),
        mayHaveMissedMessages = false,
    )
    is SessionState.Reconnecting -> AnchorConnectionState(
        phase = ConnectionPhase.Reconnecting,
        roomId = roomInfo?.roomId,
        inputRoomId = inputRoomId,
        popularity = null,
        liveLabel = roomInfo?.liveStatus?.toDisplayLabel(),
        failureKind = failure.toAnchorFailureKind(),
        failureDetail = failure.detailText(),
        reconnectDelaySeconds = ((currentDelayMillis + 999L) / 1_000L).coerceAtLeast(0L).toInt(),
        disconnectedDurationSeconds = (disconnectedForMillis(System.currentTimeMillis()) / 1_000L)
            .coerceAtLeast(0L)
            .toInt(),
        mayHaveMissedMessages = mayHaveMissedMessages,
    )
    is SessionState.Stopped -> AnchorConnectionState(
        phase = ConnectionPhase.Stopped,
        roomId = roomId,
    )
    is SessionState.Fatal -> AnchorConnectionState(
        phase = ConnectionPhase.Fatal,
        inputRoomId = inputRoomId,
        failureKind = failure.toAnchorFailureKind(),
        failureDetail = failure.detailText(),
    )
}

private fun LiveStatus.toDisplayLabel(): String = when (this) {
    LiveStatus.LIVE -> "直播中"
    LiveStatus.NOT_LIVE -> "未开播"
    LiveStatus.ROUND_PLAY -> "轮播中"
    LiveStatus.UNKNOWN -> "状态未知"
}

private fun ConnectionFailure.toAnchorFailureKind(): AnchorFailureKind = when (this) {
    ConnectionFailure.InvalidRoomInput -> AnchorFailureKind.InvalidRoomInput
    ConnectionFailure.RoomNotFound -> AnchorFailureKind.RoomNotFound
    ConnectionFailure.RoomRestricted -> AnchorFailureKind.RoomRestricted
    ConnectionFailure.NetworkUnavailable -> AnchorFailureKind.NetworkUnavailable
    is ConnectionFailure.RateLimited -> AnchorFailureKind.RateLimited
    is ConnectionFailure.EndpointUnavailable -> AnchorFailureKind.EndpointUnavailable
    is ConnectionFailure.HostRejected -> AnchorFailureKind.HostRejected
    is ConnectionFailure.AuthRejected -> AnchorFailureKind.AuthRejected
    ConnectionFailure.ConnectionLost -> AnchorFailureKind.ConnectionLost
    is ConnectionFailure.ProtocolUnsupported -> AnchorFailureKind.ProtocolUnsupported
    is ConnectionFailure.UnknownRecoverable -> AnchorFailureKind.UnknownRecoverable
}

private fun ConnectionFailure.detailText(): String? = when (this) {
    is ConnectionFailure.RateLimited -> statusCode?.let { "HTTP $it" }
    is ConnectionFailure.EndpointUnavailable -> reason
    is ConnectionFailure.HostRejected -> reason
    is ConnectionFailure.AuthRejected -> reason
    is ConnectionFailure.ProtocolUnsupported -> protocolVersion?.let { "protocolVersion=$it" }
    is ConnectionFailure.UnknownRecoverable -> reason
    else -> null
}
