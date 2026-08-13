package cn.danmaku.anchor

import cn.danmaku.anchor.domain.session.SessionController
import cn.danmaku.anchor.domain.session.SessionState
import cn.danmaku.anchor.model.ConnectionFailure as CoreConnectionFailure
import cn.danmaku.anchor.model.LiveMessage as CoreLiveMessage
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
    private val messageFlow = MutableSharedFlow<AnchorMessage>(extraBufferCapacity = 1024)

    private var activeBackend: SessionBackend? = null
    private var stateJob: Job? = null
    private var messageJob: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<AnchorMessage> = messageFlow.asSharedFlow()
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
        message: AnchorMessage,
        preferences: AnchorUserPreferences,
    ): Boolean = when (message) {
        is SuperChatMessage -> true
        is GuardMessage -> true
        is GiftMessage -> (message.estimatedCny ?: Double.NEGATIVE_INFINITY) >= preferences.highlightGiftThresholdYuan
        is DanmakuMessage -> false
    }
}

interface SessionCoordinator {
    val state: StateFlow<AnchorConnectionState>
    val messages: Flow<AnchorMessage>
    val demoAvailable: Boolean
    val demoEntryLabel: String?

    suspend fun connect(roomId: Long, demoMode: Boolean)
    suspend fun retryNow()
    suspend fun stop()
}

private interface SessionBackend {
    val state: StateFlow<AnchorConnectionState>
    val messages: Flow<AnchorMessage>

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
    private val messageFlow = MutableSharedFlow<AnchorMessage>(extraBufferCapacity = 64)
    private var job: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<AnchorMessage> = messageFlow.asSharedFlow()

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
    private val messageFlow = MutableSharedFlow<AnchorMessage>(extraBufferCapacity = 1024)
    private var stateJob: Job? = null
    private var messageJob: Job? = null

    override val state: StateFlow<AnchorConnectionState> = stateFlow.asStateFlow()
    override val messages: Flow<AnchorMessage> = messageFlow.asSharedFlow()

    override suspend fun start(roomId: Long) {
        stateJob?.cancel()
        messageJob?.cancel()
        stateJob = scope.launch(dispatcher) {
            controller.state.collect { stateFlow.value = it.toAnchorState() }
        }
        messageJob = scope.launch(dispatcher) {
            controller.events.collect { messageFlow.emit(it.toAnchorMessage()) }
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

private fun CoreConnectionFailure.toAnchorFailureKind(): AnchorFailureKind = when (this) {
    CoreConnectionFailure.InvalidRoomInput -> AnchorFailureKind.InvalidRoomInput
    CoreConnectionFailure.RoomNotFound -> AnchorFailureKind.RoomNotFound
    CoreConnectionFailure.RoomRestricted -> AnchorFailureKind.RoomRestricted
    CoreConnectionFailure.NetworkUnavailable -> AnchorFailureKind.NetworkUnavailable
    is CoreConnectionFailure.RateLimited -> AnchorFailureKind.RateLimited
    is CoreConnectionFailure.EndpointUnavailable -> AnchorFailureKind.EndpointUnavailable
    is CoreConnectionFailure.HostRejected -> AnchorFailureKind.HostRejected
    is CoreConnectionFailure.AuthRejected -> AnchorFailureKind.AuthRejected
    CoreConnectionFailure.ConnectionLost -> AnchorFailureKind.ConnectionLost
    is CoreConnectionFailure.ProtocolUnsupported -> AnchorFailureKind.ProtocolUnsupported
    is CoreConnectionFailure.UnknownRecoverable -> AnchorFailureKind.UnknownRecoverable
}

private fun CoreConnectionFailure.detailText(): String? = when (this) {
    is CoreConnectionFailure.RateLimited -> statusCode?.let { "HTTP $it" }
    is CoreConnectionFailure.EndpointUnavailable -> reason
    is CoreConnectionFailure.HostRejected -> reason
    is CoreConnectionFailure.AuthRejected -> reason
    is CoreConnectionFailure.ProtocolUnsupported -> protocolVersion?.let { "protocolVersion=$it" }
    is CoreConnectionFailure.UnknownRecoverable -> reason
    else -> null
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
        priceCny = priceCny.toDoubleYuan(),
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
        estimatedCny = estimatedCny?.toDoubleYuan(),
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

private fun Money.toDoubleYuan(): Double = milliYuan / 1_000.0
