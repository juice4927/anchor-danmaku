package cn.danmaku.anchor.protocol.bili

import cn.danmaku.anchor.domain.gateway.GatewayEvent
import cn.danmaku.anchor.domain.gateway.GatewaySession
import cn.danmaku.anchor.domain.gateway.LiveGateway
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.RoomInfo
import cn.danmaku.anchor.model.LiveStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class BiliLiveGateway(
    private val client: OkHttpClient,
    private val roomApi: BiliRoomApi,
    private val diagnostics: SafeDiagnostics = SafeDiagnostics(),
    private val packetCodec: BiliPacketCodec = BiliPacketCodec(diagnostics = diagnostics),
    private val commandMapper: BiliCommandMapper = BiliCommandMapper(diagnostics = diagnostics),
    private val webSocketUrlBuilder: (String, Int) -> HttpUrl = { host, port -> BiliHostValidator.buildWssUrl(host, port) },
    private val sessionLifetimeMillis: Long = DEFAULT_SESSION_LIFETIME_MILLIS,
) : LiveGateway {
    // 超时配置由装配方（AppContainer）统一注入：WS 建立后 readTimeout 会在静默期误断，
    // 低流量直播间两次心跳间隔 30 秒，期间可能无任何帧；存活检测交给应用层 idle 检测
    // 与 op=2 心跳，因此 WS client 使用 readTimeout=0。
    override fun createSession(inputRoomId: Long): GatewaySession = Session(inputRoomId)

    inner class Session(
        private val inputRoomId: Long,
    ) : GatewaySession {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutableEvents = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 128)
        private var socketSession: BiliWebSocketSession? = null
        private var readerJob: Job? = null
        private var lifetimeJob: Job? = null
        private var disconnectEmitted = false

        override val events: Flow<GatewayEvent> = mutableEvents.asSharedFlow()

        override suspend fun start() {
            mutableEvents.emit(GatewayEvent.Resolving)
            val room = roomApi.resolveRoom(inputRoomId)
            val roomInfo = room.toRoomInfo()
            mutableEvents.emit(GatewayEvent.RoomResolved(roomInfo))
            val danmuInfo = roomApi.getDanmuInfo(room.roomId)

            var lastFailure: BiliFailure? = null
            for ((index, host) in danmuInfo.hostList.withIndex()) {
                mutableEvents.emit(GatewayEvent.HostConnecting(host.host, host.wssPort, index + 1))
                val url = try {
                    webSocketUrlBuilder(host.host, host.wssPort)
                } catch (error: IllegalArgumentException) {
                    diagnostics.increment("host_builder_rejected")
                    lastFailure = HostRejectedFailure(host.host, error)
                    continue
                }
                try {
                    val session = BiliWebSocketSession.connect(client, roomApi.newWebSocketRequest(url, room.roomId))
                    socketSession = session
                    val authenticated = CompletableDeferred<Unit>()
                    readerJob = scope.launch {
                        collectFrames(
                            roomId = room.roomId,
                            session = session,
                            authenticated = authenticated,
                        )
                    }
                    mutableEvents.emit(GatewayEvent.Authenticating)
                    if (!session.send(packetCodec.encodeAuthPacket(room.roomId, danmuInfo.token))) {
                        throw HostRejectedFailure(host.host)
                    }
                    withTimeout(10_000) {
                        authenticated.await()
                    }
                    mutableEvents.emit(GatewayEvent.DiagnosticsUpdated(diagnostics.toGatewayDiagnostics()))
                    scheduleLifetimeReconnect()
                    return
                } catch (failure: BiliFailure) {
                    readerJob?.cancel()
                    socketSession?.close()
                    socketSession = null
                    lastFailure = failure
                    if (!failure.retryable) {
                        emitDisconnectedOnce(failure.toConnectionFailure())
                        throw failure
                    }
                } catch (failure: ProtocolUnsupportedException) {
                    val typed = ProtocolUnsupportedFailure(failure)
                    emitDisconnectedOnce(typed.toConnectionFailure())
                    throw typed
                } catch (failure: Throwable) {
                    readerJob?.cancel()
                    socketSession?.close()
                    socketSession = null
                    lastFailure = HostRejectedFailure(host.host, failure)
                }
            }
            val terminal = lastFailure ?: EndpointUnavailableFailure()
            emitDisconnectedOnce(terminal.toConnectionFailure())
            throw terminal
        }

        override suspend fun sendHeartbeat() {
            val payload = packetCodec.encodeHeartbeatPacket()
            val socket = socketSession ?: throw ConnectionLostFailure()
            if (!socket.send(payload)) {
                throw ConnectionLostFailure()
            }
        }

        override suspend fun close() {
            lifetimeJob?.cancel()
            socketSession?.close()
            readerJob?.let { job ->
                runCatching {
                    withTimeout(1_000) {
                        job.join()
                    }
                }.onFailure {
                    job.cancel()
                }
            }
            socketSession?.cancel()
            socketSession = null
            scope.cancel()
        }

        /**
         * B站对超长连接会逐渐降级限流（实测 6 小时连接只剩 1/15 弹幕）。达到生命周期上限后
         * 主动断开，SessionController 会立即按退避序列重连，新连接恢复满速。
         */
        private fun scheduleLifetimeReconnect() {
            if (sessionLifetimeMillis <= 0L) return
            lifetimeJob?.cancel()
            lifetimeJob = scope.launch {
                delay(sessionLifetimeMillis)
                if (!disconnectEmitted) {
                    emitDisconnectedOnce(ConnectionLostFailure().toConnectionFailure())
                    socketSession?.close()
                }
            }
        }

        private suspend fun collectFrames(
            roomId: Long,
            session: BiliWebSocketSession,
            authenticated: CompletableDeferred<Unit>,
        ) {
            try {
                session.events.collect { event ->
                    when (event) {
                        BiliWebSocketEvent.Opened -> Unit
                        is BiliWebSocketEvent.BinaryFrame -> {
                            val result = try {
                                packetCodec.decode(event.payload)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: ProtocolUnsupportedException) {
                                throw error
                            } catch (error: MalformedPacketException) {
                                diagnostics.increment("frame_malformed")
                                return@collect
                            } catch (error: PayloadLimitExceededException) {
                                diagnostics.increment("frame_oversized")
                                return@collect
                            } catch (error: Throwable) {
                                diagnostics.increment("frame_decode_error")
                                return@collect
                            }
                            result.popularityValues.forEach { value ->
                                mutableEvents.emit(GatewayEvent.Popularity(value))
                            }
                            result.packets.forEach { packet ->
                                mutableEvents.emit(
                                    GatewayEvent.FrameReceived(receivedAtMillis = System.currentTimeMillis()),
                                )
                                when (packet.operation) {
                                    BiliPacket.OP_AUTH_REPLY -> {
                                        if (packetCodec.isAuthSuccess(packet)) {
                                            if (!authenticated.isCompleted) {
                                                authenticated.complete(Unit)
                                            }
                                        } else {
                                            val failure = AuthRejectedFailure()
                                            if (!authenticated.isCompleted) {
                                                authenticated.completeExceptionally(failure)
                                            }
                                            throw failure
                                        }
                                    }

                                    BiliPacket.OP_MESSAGE -> {
                                        val messages = commandMapper.map(packet, roomId, System.currentTimeMillis())
                                        messages.forEach { mutableEvents.emit(GatewayEvent.Message(it)) }
                                    }
                                }
                            }
                        }

                        is BiliWebSocketEvent.Closing,
                        is BiliWebSocketEvent.Closed,
                        -> {
                            val failure = ConnectionLostFailure()
                            if (!authenticated.isCompleted) {
                                authenticated.completeExceptionally(failure)
                            }
                            emitDisconnectedOnce(failure.toConnectionFailure())
                        }

                        is BiliWebSocketEvent.Failure -> {
                            val failure = ConnectionLostFailure(event.throwable)
                            if (!authenticated.isCompleted) {
                                authenticated.completeExceptionally(failure)
                            }
                            emitDisconnectedOnce(failure.toConnectionFailure())
                        }
                    }
                }
            } catch (failure: BiliFailure) {
                if (!authenticated.isCompleted) {
                    authenticated.completeExceptionally(failure)
                }
                emitDisconnectedOnce(failure.toConnectionFailure())
                throw failure
            } catch (failure: ProtocolUnsupportedException) {
                val typed = ProtocolUnsupportedFailure(failure)
                if (!authenticated.isCompleted) {
                    authenticated.completeExceptionally(typed)
                }
                emitDisconnectedOnce(typed.toConnectionFailure())
                throw typed
            }
        }

        private suspend fun emitDisconnectedOnce(failure: ConnectionFailure) {
            if (disconnectEmitted) {
                return
            }
            disconnectEmitted = true
            mutableEvents.emit(GatewayEvent.Disconnected(failure))
        }
    }

    private fun ResolvedRoom.toRoomInfo(): RoomInfo = RoomInfo(
        inputRoomId = inputRoomId,
        roomId = roomId,
        liveStatus = when (liveStatus) {
            0 -> LiveStatus.NOT_LIVE
            1 -> LiveStatus.LIVE
            2 -> LiveStatus.ROUND_PLAY
            else -> LiveStatus.UNKNOWN
        },
    )

    private fun BiliFailure.toConnectionFailure(): ConnectionFailure = when (this) {
        is RoomNotFoundFailure -> ConnectionFailure.RoomNotFound
        is RoomRestrictedFailure -> ConnectionFailure.RoomRestricted
        is EndpointUnavailableFailure -> ConnectionFailure.EndpointUnavailable(reason = message)
        is RateLimitedFailure -> ConnectionFailure.RateLimited()
        is HostRejectedFailure -> ConnectionFailure.HostRejected(reason = message)
        is AuthRejectedFailure -> ConnectionFailure.AuthRejected(reason = message)
        is ConnectionLostFailure -> ConnectionFailure.ConnectionLost
        is ProtocolUnsupportedFailure -> ConnectionFailure.ProtocolUnsupported()
        is UnknownRecoverableFailure -> ConnectionFailure.UnknownRecoverable(reason = message)
        is HttpStatusFailure -> if (statusCode == 429) ConnectionFailure.RateLimited(statusCode) else ConnectionFailure.EndpointUnavailable(statusCode = statusCode)
    }

    companion object {
        /** 单条连接的存活上限：B站对超长连接逐渐降级限流，到期主动重连恢复满速。 */
        const val DEFAULT_SESSION_LIFETIME_MILLIS: Long = 60 * 60 * 1000L
    }
}
