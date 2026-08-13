package cn.danmaku.anchor.protocol.bili

data class BiliPacket(
    val packetLength: Int,
    val headerLength: Int,
    val protocolVersion: Int,
    val operation: Int,
    val sequence: Int,
    val body: ByteArray,
) {
    companion object {
        const val HEADER_LENGTH = 16
        const val OP_HEARTBEAT = 2
        const val OP_HEARTBEAT_REPLY = 3
        const val OP_MESSAGE = 5
        const val OP_AUTH = 7
        const val OP_AUTH_REPLY = 8
    }
}
