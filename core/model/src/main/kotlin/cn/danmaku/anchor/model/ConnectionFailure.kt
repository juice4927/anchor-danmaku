package cn.danmaku.anchor.model

sealed interface ConnectionFailure {
    val recoverable: Boolean

    data object InvalidRoomInput : ConnectionFailure {
        override val recoverable: Boolean = false
    }

    data object RoomNotFound : ConnectionFailure {
        override val recoverable: Boolean = false
    }

    data object RoomRestricted : ConnectionFailure {
        override val recoverable: Boolean = false
    }

    data object NetworkUnavailable : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data class RateLimited(
        val statusCode: Int? = 429,
    ) : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data class EndpointUnavailable(
        val statusCode: Int? = null,
        val reason: String? = null,
    ) : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data class HostRejected(
        val host: String? = null,
        val reason: String? = null,
    ) : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data class AuthRejected(
        val statusCode: Int? = null,
        val reason: String? = null,
    ) : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data object ConnectionLost : ConnectionFailure {
        override val recoverable: Boolean = true
    }

    data class ProtocolUnsupported(
        val protocolVersion: Int? = null,
        val operation: Int? = null,
    ) : ConnectionFailure {
        override val recoverable: Boolean = false
    }

    data class UnknownRecoverable(
        val reason: String? = null,
    ) : ConnectionFailure {
        override val recoverable: Boolean = true
    }
}
