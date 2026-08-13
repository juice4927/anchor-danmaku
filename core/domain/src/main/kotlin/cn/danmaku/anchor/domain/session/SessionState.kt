package cn.danmaku.anchor.domain.session

import cn.danmaku.anchor.domain.gateway.GatewayDiagnostics
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.RoomInfo

sealed interface SessionState {
    data object Idle : SessionState

    data class Resolving(
        val inputRoomId: Long,
    ) : SessionState

    data class Connecting(
        val inputRoomId: Long,
        val roomInfo: RoomInfo? = null,
        val host: String? = null,
        val port: Int? = null,
        val attempt: Int = 1,
    ) : SessionState

    data class Authenticating(
        val roomInfo: RoomInfo? = null,
        val host: String? = null,
        val port: Int? = null,
    ) : SessionState

    data class Connected(
        val roomInfo: RoomInfo,
        val connectedAtMillis: Long,
        val lastFrameAtMillis: Long,
        val popularity: Long? = null,
        val reconnectAttempts: Int = 0,
        val diagnostics: GatewayDiagnostics = GatewayDiagnostics(),
    ) : SessionState

    data class Reconnecting(
        val inputRoomId: Long,
        val attempt: Int,
        val currentDelayMillis: Long,
        val retryAtMillis: Long,
        val failure: ConnectionFailure,
        val disconnectedAtMillis: Long,
        val roomInfo: RoomInfo? = null,
        val mayHaveMissedMessages: Boolean,
        val diagnostics: GatewayDiagnostics = GatewayDiagnostics(),
    ) : SessionState {
        fun disconnectedForMillis(nowMillis: Long): Long = (nowMillis - disconnectedAtMillis).coerceAtLeast(0L)
    }

    data class Stopped(
        val roomId: Long? = null,
    ) : SessionState

    data class Fatal(
        val inputRoomId: Long?,
        val failure: ConnectionFailure,
    ) : SessionState
}
