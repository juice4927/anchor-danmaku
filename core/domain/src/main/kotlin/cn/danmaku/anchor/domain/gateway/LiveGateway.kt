package cn.danmaku.anchor.domain.gateway

import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.RoomInfo
import kotlinx.coroutines.flow.Flow

interface LiveGateway {
    fun createSession(inputRoomId: Long): GatewaySession
}

interface GatewaySession {
    val events: Flow<GatewayEvent>

    suspend fun start()

    suspend fun sendHeartbeat()

    suspend fun close()
}

class GatewayFailureException(
    val failure: ConnectionFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.toString(), cause)

sealed interface GatewayEvent {
    data object Resolving : GatewayEvent

    data class RoomResolved(
        val roomInfo: RoomInfo,
    ) : GatewayEvent

    data class HostConnecting(
        val host: String,
        val port: Int,
        val attempt: Int,
    ) : GatewayEvent

    data object Authenticating : GatewayEvent

    data class FrameReceived(
        val receivedAtMillis: Long,
    ) : GatewayEvent

    data class Message(
        val value: LiveMessage,
    ) : GatewayEvent

    data class Popularity(
        val value: Long,
    ) : GatewayEvent

    data class DiagnosticsUpdated(
        val diagnostics: GatewayDiagnostics,
    ) : GatewayEvent

    data class Disconnected(
        val failure: ConnectionFailure,
    ) : GatewayEvent
}

data class GatewayDiagnostics(
    val unknownCommandCount: Int = 0,
    val malformedFrameCount: Int = 0,
    val oversizedFrameCount: Int = 0,
    val unsupportedOperationCount: Int = 0,
)
