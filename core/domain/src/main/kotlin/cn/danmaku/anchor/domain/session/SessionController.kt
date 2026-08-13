package cn.danmaku.anchor.domain.session

import cn.danmaku.anchor.domain.gateway.GatewayDiagnostics
import cn.danmaku.anchor.domain.gateway.GatewayEvent
import cn.danmaku.anchor.domain.gateway.GatewayFailureException
import cn.danmaku.anchor.domain.gateway.GatewaySession
import cn.danmaku.anchor.domain.gateway.LiveGateway
import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.RoomInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

interface ConnectivityObserver {
    val isConnected: StateFlow<Boolean>
}

class SessionController(
    private val gateway: LiveGateway,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    dispatcher: CoroutineDispatcher,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val controlMutex = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Idle)
    private val mutableEvents = MutableSharedFlow<LiveMessage>(extraBufferCapacity = 1024)

    private var sessionLoopJob: Job? = null
    private var activeSession: GatewaySession? = null
    private var activeInputRoomId: Long? = null
    private var lastStateWriteMillis = 0L
    private var stateWritePending = false

    val state: StateFlow<SessionState> = mutableState.asStateFlow()
    val events: Flow<LiveMessage> = mutableEvents.asSharedFlow()

    suspend fun start(inputRoomId: Long) {
        controlMutex.withLock {
            stopLocked(nextState = null)
            activeInputRoomId = inputRoomId
            if (inputRoomId <= 0L) {
                mutableState.value = SessionState.Fatal(inputRoomId = inputRoomId, failure = ConnectionFailure.InvalidRoomInput)
                return
            }
            sessionLoopJob = scope.launch {
                runSessionLoop(inputRoomId)
            }
        }
    }

    suspend fun retryNow() {
        val roomId = controlMutex.withLock { activeInputRoomId }
        if (roomId != null) {
            start(roomId)
        }
    }

    suspend fun stop() {
        controlMutex.withLock {
            val roomId = activeInputRoomId
            stopLocked(nextState = SessionState.Stopped(roomId))
            activeInputRoomId = null
        }
    }

    private suspend fun runSessionLoop(inputRoomId: Long) {
        var attempt = 0
        var latestRoomInfo: RoomInfo? = null
        var lastDiagnostics = GatewayDiagnostics()
        var latestPopularity: Long? = null

        while (currentCoroutineContext().isActive && activeInputRoomId == inputRoomId) {
            if (!connectivity.isConnected.value) {
                val now = clock.nowMillis()
                mutableState.value = SessionState.Reconnecting(
                    inputRoomId = inputRoomId,
                    attempt = attempt.coerceAtLeast(1),
                    currentDelayMillis = 0L,
                    retryAtMillis = now,
                    failure = ConnectionFailure.NetworkUnavailable,
                    disconnectedAtMillis = now,
                    roomInfo = latestRoomInfo,
                    mayHaveMissedMessages = false,
                    diagnostics = lastDiagnostics,
                )
                connectivity.isConnected.filter { it }.first()
            }

            val session = try {
                gateway.createSession(inputRoomId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Factory failures have no protocol-level event to classify. Surface a
                // typed failure and stop this attempt so a bad factory cannot hot-loop.
                if (activeInputRoomId == inputRoomId && currentCoroutineContext().isActive) {
                    mutableState.value = SessionState.Fatal(
                        inputRoomId = inputRoomId,
                        failure = ConnectionFailure.UnknownRecoverable(
                            reason = "session-create:${error.message ?: error::class.simpleName}",
                        ),
                    )
                }
                break
            }
            activeSession = session
            mutableState.value = SessionState.Resolving(inputRoomId)
            yield()

            val disconnectSignal = CompletableDeferred<ConnectionFailure>()
            var lastFrameAtMillis = clock.nowMillis()
            var connectedAtMillis: Long? = null
            var latestHost: String? = null
            var latestPort: Int? = null
            var disconnectState: SessionState.Reconnecting? = null

            val collectorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.events.collect { event ->
                    when (event) {
                        GatewayEvent.Resolving -> {
                            if (mutableState.value !is SessionState.Connected) {
                                mutableState.value = SessionState.Resolving(inputRoomId)
                            }
                            yield()
                        }

                        is GatewayEvent.RoomResolved -> {
                            latestRoomInfo = event.roomInfo
                            yield()
                        }

                        is GatewayEvent.HostConnecting -> {
                            latestHost = event.host
                            latestPort = event.port
                            if (mutableState.value !is SessionState.Connected) {
                                mutableState.value = SessionState.Connecting(
                                    inputRoomId = inputRoomId,
                                    roomInfo = latestRoomInfo,
                                    host = event.host,
                                    port = event.port,
                                    attempt = event.attempt,
                                )
                            }
                            yield()
                        }

                        GatewayEvent.Authenticating -> {
                            if (mutableState.value !is SessionState.Connected) {
                                mutableState.value = SessionState.Authenticating(
                                    roomInfo = latestRoomInfo,
                                    host = latestHost,
                                    port = latestPort,
                                )
                            }
                            yield()
                        }

                        is GatewayEvent.FrameReceived -> {
                            lastFrameAtMillis = event.receivedAtMillis.takeIf { it > 0L } ?: clock.nowMillis()
                            updateConnectedLastFrame(lastFrameAtMillis, lastDiagnostics)
                            yield()
                        }

                        is GatewayEvent.Message -> {
                            lastFrameAtMillis = effectiveReceivedAt(event.value)
                            updateConnectedLastFrame(lastFrameAtMillis, lastDiagnostics)
                            mutableEvents.emit(event.value)
                            yield()
                        }

                        is GatewayEvent.Popularity -> {
                            latestPopularity = event.value
                            // op=3 心跳回复证明连接存活，必须刷新最近帧时间，否则 idle 检测
                            // 会在“无弹幕但有正常心跳”的低流量直播间误判断线。
                            lastFrameAtMillis = clock.nowMillis()
                            updateConnectedState { current ->
                                current.copy(
                                    lastFrameAtMillis = lastFrameAtMillis,
                                    popularity = event.value,
                                    diagnostics = lastDiagnostics,
                                )
                            }
                            yield()
                        }

                        is GatewayEvent.DiagnosticsUpdated -> {
                            lastDiagnostics = event.diagnostics
                            val current = mutableState.value
                            mutableState.value = when (current) {
                                is SessionState.Connected -> current.copy(diagnostics = event.diagnostics)
                                is SessionState.Reconnecting -> current.copy(diagnostics = event.diagnostics)
                                else -> current
                            }
                            yield()
                        }

                        is GatewayEvent.Disconnected -> {
                            val now = clock.nowMillis()
                            val connected = mutableState.value as? SessionState.Connected
                            val nextAttempt = if (
                                connected != null &&
                                    now - connected.connectedAtMillis >= ReconnectPolicy.STABLE_CONNECTION_RESET_MILLIS
                            ) {
                                1
                            } else {
                                attempt + 1
                            }
                            attempt = nextAttempt
                            val delayMillis = reconnectPolicy.delayMillisForAttempt(nextAttempt)
                            disconnectState = SessionState.Reconnecting(
                                inputRoomId = inputRoomId,
                                attempt = nextAttempt,
                                currentDelayMillis = delayMillis,
                                retryAtMillis = now + delayMillis,
                                failure = event.failure,
                                disconnectedAtMillis = now,
                                roomInfo = connected?.roomInfo ?: latestRoomInfo,
                                mayHaveMissedMessages = connected != null,
                                diagnostics = lastDiagnostics,
                            )
                            mutableState.value = disconnectState!!
                            if (!disconnectSignal.isCompleted) {
                                disconnectSignal.complete(event.failure)
                            }
                            yield()
                        }
                    }
                }
            }

            var heartbeatJob: Job? = null
            var idleWatchJob: Job? = null
            var terminalFailure: ConnectionFailure? = null
            try {
                val startSucceeded = try {
                    withTimeoutOrNull(ReconnectPolicy.STAGE_TIMEOUT_MILLIS) {
                        session.start()
                        true
                    } == true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    terminalFailure = mapStartFailure(error)
                    false
                }

                if (!startSucceeded) {
                    if (terminalFailure == null) {
                        terminalFailure = ConnectionFailure.UnknownRecoverable("stage-timeout")
                    }
                } else {
                    // A gateway may buffer its startup callbacks. Give the collector a
                    // bounded number of dispatcher turns to publish all stage metadata.
                    repeat(8) { yield() }
                    if (disconnectSignal.isCompleted) {
                        terminalFailure = disconnectSignal.await()
                    }
                }

                if (terminalFailure == null) {
                    connectedAtMillis = clock.nowMillis()
                    mutableState.value = SessionState.Connected(
                        roomInfo = latestRoomInfo ?: RoomInfo(inputRoomId = inputRoomId, roomId = inputRoomId),
                        connectedAtMillis = connectedAtMillis,
                        lastFrameAtMillis = lastFrameAtMillis,
                        popularity = latestPopularity,
                        reconnectAttempts = attempt,
                        diagnostics = lastDiagnostics,
                    )
                    heartbeatJob = scope.launch {
                        while (isActive) {
                            delay(ReconnectPolicy.HEARTBEAT_INTERVAL_MILLIS)
                            try {
                                session.sendHeartbeat()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                if (!disconnectSignal.isCompleted) {
                                    disconnectSignal.complete(mapStartFailure(error))
                                }
                                break
                            }
                        }
                    }
                    idleWatchJob = scope.launch {
                        while (isActive) {
                            delay(1_000L)
                            flushPendingStateWrite(lastFrameAtMillis, latestPopularity, lastDiagnostics)
                            val now = clock.nowMillis()
                            if (now - lastFrameAtMillis >= ReconnectPolicy.IDLE_TIMEOUT_MILLIS) {
                                if (!disconnectSignal.isCompleted) {
                                    disconnectSignal.complete(ConnectionFailure.ConnectionLost)
                                }
                                session.close()
                                break
                            }
                        }
                    }
                    terminalFailure = disconnectSignal.await()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                terminalFailure = mapStartFailure(error)
            } finally {
                heartbeatJob?.cancelAndJoin()
                idleWatchJob?.cancelAndJoin()
                collectorJob.cancelAndJoin()
                session.close()
                if (activeSession === session) {
                    activeSession = null
                }
            }

            val failure = terminalFailure ?: ConnectionFailure.UnknownRecoverable()
            if (!failure.recoverable || activeInputRoomId != inputRoomId || !currentCoroutineContext().isActive) {
                if (activeInputRoomId == inputRoomId && currentCoroutineContext().isActive) {
                    mutableState.value = SessionState.Fatal(inputRoomId = inputRoomId, failure = failure)
                }
                break
            }

            val reconnecting = disconnectState ?: run {
                val now = clock.nowMillis()
                val wasConnected = connectedAtMillis != null
                attempt = if (wasConnected && now - connectedAtMillis!! >= ReconnectPolicy.STABLE_CONNECTION_RESET_MILLIS) {
                    1
                } else {
                    attempt + 1
                }
                val delayMillis = reconnectPolicy.delayMillisForAttempt(attempt)
                SessionState.Reconnecting(
                    inputRoomId = inputRoomId,
                    attempt = attempt,
                    currentDelayMillis = delayMillis,
                    retryAtMillis = now + delayMillis,
                    failure = failure,
                    disconnectedAtMillis = now,
                    roomInfo = latestRoomInfo,
                    mayHaveMissedMessages = wasConnected,
                    diagnostics = lastDiagnostics,
                ).also { mutableState.value = it }
            }
            waitForReconnectWindow(reconnecting.currentDelayMillis)
        }
    }

    private suspend fun waitForReconnectWindow(delayMillis: Long) {
        if (delayMillis <= 0L) return
        if (!connectivity.isConnected.value) {
            connectivity.isConnected.filter { it }.first()
            return
        }
        val disconnectedDuringWait = withTimeoutOrNull(delayMillis) {
            connectivity.isConnected.filter { connected -> !connected }.first()
        } != null
        if (disconnectedDuringWait) {
            connectivity.isConnected.filter { it }.first()
        }
    }

    private suspend fun stopLocked(nextState: SessionState?) {
        val loopJob = sessionLoopJob
        sessionLoopJob = null
        stateWritePending = false
        activeSession?.close()
        activeSession = null
        loopJob?.cancelAndJoin()
        if (nextState != null) {
            mutableState.value = nextState
        }
    }

    /**
     * 高流量直播间每个数据包都会触发 Connected 状态写入；这里对 UI 可见的
     * 状态更新做节流（本地帧时间变量仍每次刷新，idle 检测保持精确）。
     * 节流期间置 pending，由 idle 看门狗的秒级 tick 兜底 flush，
     * 保证突发流量结束后最终值一定收敛（如人气值）。
     */
    private fun updateConnectedState(transform: (SessionState.Connected) -> SessionState.Connected) {
        val now = clock.nowMillis()
        if (now - lastStateWriteMillis < STATE_WRITE_THROTTLE_MILLIS) {
            stateWritePending = true
            return
        }
        lastStateWriteMillis = now
        stateWritePending = false
        val current = mutableState.value
        if (current is SessionState.Connected) {
            mutableState.value = transform(current)
        }
    }

    private fun flushPendingStateWrite(
        lastFrameAtMillis: Long,
        latestPopularity: Long?,
        lastDiagnostics: GatewayDiagnostics,
    ) {
        if (!stateWritePending) return
        val now = clock.nowMillis()
        if (now - lastStateWriteMillis < STATE_WRITE_THROTTLE_MILLIS) return
        stateWritePending = false
        lastStateWriteMillis = now
        val current = mutableState.value
        if (current is SessionState.Connected) {
            mutableState.value = current.copy(
                lastFrameAtMillis = lastFrameAtMillis,
                popularity = latestPopularity ?: current.popularity,
                diagnostics = lastDiagnostics,
            )
        }
    }

    private fun updateConnectedLastFrame(lastFrameAtMillis: Long, diagnostics: GatewayDiagnostics) {
        updateConnectedState { current ->
            current.copy(
                lastFrameAtMillis = lastFrameAtMillis,
                diagnostics = diagnostics,
            )
        }
    }

    private fun effectiveReceivedAt(message: LiveMessage): Long =
        message.receivedAtMillis.takeIf { it > 0L } ?: clock.nowMillis()

    private fun mapStartFailure(error: Throwable): ConnectionFailure = when (error) {
        is GatewayFailureException -> error.failure
        else -> ConnectionFailure.UnknownRecoverable(
            reason = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName,
        )
    }

    private companion object {
        const val STATE_WRITE_THROTTLE_MILLIS: Long = 200L
    }
}
