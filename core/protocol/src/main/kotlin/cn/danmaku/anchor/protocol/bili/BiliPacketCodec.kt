package cn.danmaku.anchor.protocol.bili

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

open class BiliProtocolException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class MalformedPacketException(message: String) : BiliProtocolException(message)

class ProtocolUnsupportedException(val protocolVersion: Int) :
    BiliProtocolException("Unsupported protocol version: $protocolVersion")

open class PayloadLimitExceededException(
    val limitName: String,
    val actual: Int,
    val limit: Int,
) : BiliProtocolException("$limitName exceeded: actual=$actual limit=$limit")

class ChildPacketLimitExceededException(actual: Int, limit: Int) :
    PayloadLimitExceededException("childPackets", actual, limit)

class NestingDepthExceededException(actual: Int, limit: Int) :
    PayloadLimitExceededException("nestingDepth", actual, limit)

class BiliPacketCodec(
    private val diagnostics: SafeDiagnostics = SafeDiagnostics(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    compression: BiliCompression? = null,
    private val limits: Limits = Limits(),
) {
    private val decompressor: BiliCompression = compression ?: BiliCompression(limits.maxDecompressedBytes)

    data class Limits(
        val maxDecompressedBytes: Int = 32 * 1024 * 1024,
        val maxDepth: Int = 4,
        val maxChildPackets: Int = 20_000,
    )

    data class DecodeResult(
        val packets: List<BiliPacket>,
        val popularityValues: List<Long>,
    )

    @Serializable
    private data class AuthRequest(
        val uid: Long,
        val roomid: Long,
        val protover: Int,
        val buvid: String,
        val platform: String,
        val type: Int,
        val key: String,
    )

    fun encode(packet: BiliPacket): ByteArray {
        val buffer = ByteBuffer.allocate(packet.packetLength).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(packet.packetLength)
        buffer.putShort(packet.headerLength.toShort())
        buffer.putShort(packet.protocolVersion.toShort())
        buffer.putInt(packet.operation)
        buffer.putInt(packet.sequence)
        buffer.put(packet.body)
        return buffer.array()
    }

    fun authPacket(realRoomId: Long, token: String): BiliPacket {
        val payload = AuthRequest(
            uid = 0,
            roomid = realRoomId,
            protover = 3,
            buvid = "",
            platform = "web",
            type = 2,
            key = token,
        )
        val body = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        return BiliPacket(
            packetLength = BiliPacket.HEADER_LENGTH + body.size,
            headerLength = BiliPacket.HEADER_LENGTH,
            protocolVersion = 1,
            operation = BiliPacket.OP_AUTH,
            sequence = 1,
            body = body,
        )
    }

    fun heartbeatPacket(): BiliPacket {
        val body = "[object Object]".toByteArray(StandardCharsets.UTF_8)
        return BiliPacket(
            packetLength = BiliPacket.HEADER_LENGTH + body.size,
            headerLength = BiliPacket.HEADER_LENGTH,
            protocolVersion = 1,
            operation = BiliPacket.OP_HEARTBEAT,
            sequence = 1,
            body = body,
        )
    }

    fun encodeAuthPacket(realRoomId: Long, token: String): ByteArray = encode(authPacket(realRoomId, token))

    fun encodeHeartbeatPacket(): ByteArray = encode(heartbeatPacket())

    fun decode(frame: ByteArray): DecodeResult {
        val context = DecodeContext()
        return decode(frame = frame, depth = 0, context = context)
    }

    fun parsePopularity(packet: BiliPacket): Long {
        if (packet.operation != BiliPacket.OP_HEARTBEAT_REPLY || packet.body.size < 4) {
            throw MalformedPacketException("Popularity packet body must contain 4 bytes")
        }
        return readUnsignedInt(packet.body, 0)
    }

    fun isAuthSuccess(packet: BiliPacket): Boolean {
        if (packet.operation != BiliPacket.OP_AUTH_REPLY) {
            return false
        }
        val element = runCatching { json.parseToJsonElement(packet.body.decodeToString()) }.getOrNull()
        return (element as? kotlinx.serialization.json.JsonObject)
            ?.get("code")
            ?.jsonPrimitive
            ?.intOrNull == 0
    }

    private fun decode(frame: ByteArray, depth: Int, context: DecodeContext): DecodeResult {
        if (depth > limits.maxDepth) {
            throw NestingDepthExceededException(depth, limits.maxDepth)
        }
        var cursor = 0
        val packets = mutableListOf<BiliPacket>()
        val popularityValues = mutableListOf<Long>()
        while (cursor + BiliPacket.HEADER_LENGTH <= frame.size) {
            context.childPackets += 1
            if (context.childPackets > limits.maxChildPackets) {
                diagnostics.increment("oversized_child_packets")
                throw ChildPacketLimitExceededException(context.childPackets, limits.maxChildPackets)
            }
            val packetLength = readInt(frame, cursor)
            val headerLength = readUnsignedShort(frame, cursor + 4)
            val version = readUnsignedShort(frame, cursor + 6)
            val operation = readInt(frame, cursor + 8)
            val sequence = readInt(frame, cursor + 12)
            if (packetLength < BiliPacket.HEADER_LENGTH || headerLength < BiliPacket.HEADER_LENGTH || headerLength > packetLength) {
                diagnostics.increment("malformed_frame")
                throw MalformedPacketException(
                    "Invalid packet header packetLength=$packetLength headerLength=$headerLength",
                )
            }
            if (cursor + packetLength > frame.size) {
                if (cursor == 0) {
                    diagnostics.increment("malformed_frame")
                    throw MalformedPacketException("Packet length $packetLength exceeds available bytes ${frame.size - cursor}")
                }
                diagnostics.increment("tail_truncated")
                break
            }
            val body = frame.copyOfRange(cursor + headerLength, cursor + packetLength)
            when (operation) {
                BiliPacket.OP_MESSAGE -> {
                    when (version) {
                        0, 1 -> {
                            packets += BiliPacket(packetLength, headerLength, version, operation, sequence, body)
                        }

                        2, 3 -> {
                            val inflated = try {
                                when (version) {
                                    2 -> decompressor.inflateZlib(body)
                                    else -> decompressor.inflateBrotli(body)
                                }
                            } catch (failure: PayloadLimitExceededException) {
                                diagnostics.increment("oversized_frame")
                                throw failure
                            }
                            if (inflated.size > limits.maxDecompressedBytes) {
                                diagnostics.increment("oversized_frame")
                                throw PayloadLimitExceededException(
                                    limitName = "decompressedBytes",
                                    actual = inflated.size,
                                    limit = limits.maxDecompressedBytes,
                                )
                            }
                            val nested = runCatching { decode(inflated, depth + 1, context) }
                            val nestedResult = nested.getOrElse { error ->
                                if (error is MalformedPacketException && looksLikeJson(inflated)) {
                                    DecodeResult(
                                        packets = listOf(
                                            BiliPacket(
                                                packetLength = BiliPacket.HEADER_LENGTH + inflated.size,
                                                headerLength = BiliPacket.HEADER_LENGTH,
                                                protocolVersion = 1,
                                                operation = BiliPacket.OP_MESSAGE,
                                                sequence = sequence,
                                                body = inflated,
                                            ),
                                        ),
                                        popularityValues = emptyList(),
                                    )
                                } else {
                                    throw error
                                }
                            }
                            packets += nestedResult.packets
                            popularityValues += nestedResult.popularityValues
                        }

                        else -> {
                            diagnostics.increment("unsupported_protocol")
                            throw ProtocolUnsupportedException(version)
                        }
                    }
                }

                BiliPacket.OP_HEARTBEAT_REPLY -> {
                    val packet = BiliPacket(packetLength, headerLength, version, operation, sequence, body)
                    popularityValues += parsePopularity(packet)
                }

                BiliPacket.OP_AUTH_REPLY,
                BiliPacket.OP_HEARTBEAT,
                BiliPacket.OP_AUTH,
                -> packets += BiliPacket(packetLength, headerLength, version, operation, sequence, body)

                else -> diagnostics.increment("unknown_operation:$operation")
            }
            cursor += packetLength
        }
        if (cursor < frame.size) {
            diagnostics.increment("tail_bytes_discarded", (frame.size - cursor).toLong())
        }
        return DecodeResult(packets = packets, popularityValues = popularityValues)
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val text = bytes.decodeToString().trim()
        if (!(text.startsWith("{") || text.startsWith("["))) {
            return false
        }
        return runCatching { json.parseToJsonElement(text) }.isSuccess
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long =
        readInt(bytes, offset).toLong() and 0xFFFF_FFFFL

    private data class DecodeContext(
        var childPackets: Int = 0,
    )
}
