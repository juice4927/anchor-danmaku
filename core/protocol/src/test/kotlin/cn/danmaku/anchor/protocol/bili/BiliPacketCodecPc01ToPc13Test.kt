package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.DeflaterOutputStream

class BiliPacketCodecPc01ToPc13Test {
    private val diagnostics = SafeDiagnostics()
    private val codec = BiliPacketCodec(diagnostics = diagnostics)

    @Test
    fun pc01_headerRoundTrip() {
        val packet = BiliPacket(
            packetLength = 20,
            headerLength = 16,
            protocolVersion = 1,
            operation = 5,
            sequence = 9,
            body = byteArrayOf(1, 2, 3, 4),
        )
        val decoded = codec.encode(packet)
        assertThat(ByteBuffer.wrap(decoded).order(ByteOrder.BIG_ENDIAN).int).isEqualTo(20)
        val roundTrip = codec.decode(decoded).packets.single()
        assertThat(roundTrip.packetLength).isEqualTo(packet.packetLength)
        assertThat(roundTrip.headerLength).isEqualTo(packet.headerLength)
        assertThat(roundTrip.protocolVersion).isEqualTo(packet.protocolVersion)
        assertThat(roundTrip.operation).isEqualTo(packet.operation)
        assertThat(roundTrip.sequence).isEqualTo(packet.sequence)
        assertThat(roundTrip.body).isEqualTo(packet.body)
    }

    @Test
    fun pc02_multiplePackets() {
        val frame = readBase64Fixture("ws/multiple-packets.b64")
        val result = codec.decode(frame)
        assertThat(result.packets).hasSize(2)
        assertThat(result.packets[0].operation).isEqualTo(BiliPacket.OP_MESSAGE)
        assertThat(result.packets[1].operation).isEqualTo(BiliPacket.OP_MESSAGE)
    }

    @Test
    fun pc03_plainMessage() {
        val result = codec.decode(readBase64Fixture("ws/danmaku-plain.b64"))
        assertThat(result.packets.single().protocolVersion).isEqualTo(1)
        assertThat(result.packets.single().body.decodeToString()).contains("测试弹幕")
    }

    @Test
    fun pc04_zlibNested() {
        val result = codec.decode(readBase64Fixture("ws/danmaku-zlib-nested.b64"))
        assertThat(result.packets.single().body.decodeToString()).contains("压缩弹幕-zlib")
    }

    @Test
    fun pc05_brotliNested() {
        val result = codec.decode(readBase64Fixture("ws/danmaku-brotli-nested.b64"))
        assertThat(result.packets.single().body.decodeToString()).contains("压缩弹幕-brotli")
    }

    @Test
    fun pc06_heartbeatAndPopularity() {
        val heartbeat = codec.heartbeatPacket()
        assertThat(String(heartbeat.body, StandardCharsets.UTF_8)).isEqualTo("[object Object]")
        val result = codec.decode(readBase64Fixture("ws/heartbeat-popularity.b64"))
        assertThat(result.popularityValues.single()).isEqualTo(54321L)
    }

    @Test
    fun pc07_authBody() {
        val context = BiliAuthContext(
            roomId = 987654,
            token = "fixture-token-not-secret",
            buvid3 = "fixture-buvid3",
        )
        val auth = codec.authPacket(context)
        val json = testJson.parseToJsonElement(auth.body.decodeToString()).jsonObject
        assertThat(json["uid"]?.jsonPrimitive?.long).isEqualTo(0L)
        assertThat(json["roomid"]?.jsonPrimitive?.long).isEqualTo(987654L)
        assertThat(json["protover"]?.jsonPrimitive?.int).isEqualTo(3)
        assertThat(json["buvid"]?.jsonPrimitive?.content).isEqualTo("fixture-buvid3")
        assertThat(json["support_ack"]?.jsonPrimitive?.boolean).isTrue()
        assertThat(json["scene"]?.jsonPrimitive?.content).isEqualTo("room")
        assertThat(json["platform"]?.jsonPrimitive?.content).isEqualTo("web")
        assertThat(json["type"]?.jsonPrimitive?.int).isEqualTo(2)
        assertThat(json["key"]?.jsonPrimitive?.content).isEqualTo("fixture-token-not-secret")
    }

    @Test
    fun pc08_authResponse() {
        val ok = codec.decode(readBase64Fixture("ws/auth-ok.b64")).packets.single()
        assertThat(codec.isAuthSuccess(ok)).isTrue()
        val notOk = codec.encode(
            BiliPacket(
                packetLength = 26,
                headerLength = 16,
                protocolVersion = 1,
                operation = 8,
                sequence = 1,
                body = """{"code":1}""".toByteArray(),
            ),
        )
        assertThat(codec.isAuthSuccess(codec.decode(notOk).packets.single())).isFalse()
    }

    @Test
    fun pc09_malformedHeader() {
        assertThat(runCatching { codec.decode(readBase64Fixture("ws/malformed-header.b64")) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)
    }

    @Test
    fun pc10_oversizedDecompressedPayloadIsRejected() {
        val strictCodec = BiliPacketCodec(
            limits = BiliPacketCodec.Limits(maxDecompressedBytes = 4 * 1024 * 1024),
        )
        assertThat(runCatching { strictCodec.decode(readBase64Fixture("ws/oversized-decompressed.b64")) }.exceptionOrNull())
            .isInstanceOf(PayloadLimitExceededException::class.java)
    }

    @Test
    fun pc11_unknownOperationIsIgnoredAndCounted() {
        val localDiagnostics = SafeDiagnostics()
        val localCodec = BiliPacketCodec(diagnostics = localDiagnostics)
        val packet = BiliPacket(
            packetLength = 20,
            headerLength = 16,
            protocolVersion = 1,
            operation = 99,
            sequence = 1,
            body = byteArrayOf(1, 2, 3, 4),
        )
        val result = localCodec.decode(localCodec.encode(packet))
        assertThat(result.packets).isEmpty()
        assertThat(localDiagnostics.snapshot()["unknown_operation:99"]).isEqualTo(1L)
    }

    @Test
    fun pc12_unsupportedVersionThrowsProtocolUnsupported() {
        assertThat(runCatching { codec.decode(readBase64Fixture("ws/unsupported-protocol-version.b64")) }.exceptionOrNull())
            .isInstanceOf(ProtocolUnsupportedException::class.java)
    }

    @Test
    fun pc13_tailBytesAreNotParsedAsPacket() {
        val packet = codec.encode(codec.authPacket(1, "x"))
        val tail = packet + byteArrayOf(0x01, 0x02, 0x03)
        val result = codec.decode(tail)
        assertThat(result.packets).hasSize(1)
        assertThat(diagnostics.snapshot()["tail_bytes_discarded"]).isEqualTo(3L)
    }

    @Test
    fun pc10_depthLimitRejectsTooDeepNestedCompression() {
        val nested = nestedCompressedPacket(depth = 5)
        assertThat(runCatching { codec.decode(nested) }.exceptionOrNull())
            .isInstanceOf(NestingDepthExceededException::class.java)
    }

    @Test
    fun pc14_truncatedSecondPacketIsCountedAsTailTruncated() {
        val first = codec.encode(codec.authPacket(1, "x"))
        val second = codec.encode(
            BiliPacket(
                packetLength = 20,
                headerLength = 16,
                protocolVersion = 1,
                operation = BiliPacket.OP_MESSAGE,
                sequence = 2,
                body = byteArrayOf(1, 2, 3, 4),
            ),
        )
        val result = codec.decode(first + second.copyOfRange(0, 16))
        assertThat(result.packets).hasSize(1)
        assertThat(diagnostics.snapshot()["tail_truncated"]).isEqualTo(1L)
    }

    @Test
    fun pc15_jsonLikeCompressedPayloadFallsBackToRawMessage() {
        val payload = """{"cmd":"DANMU_MSG"}""".toByteArray()
        val compressed = zlibCompress(payload)
        val frame = codec.encode(
            BiliPacket(
                packetLength = 16 + compressed.size,
                headerLength = 16,
                protocolVersion = 2,
                operation = BiliPacket.OP_MESSAGE,
                sequence = 1,
                body = compressed,
            ),
        )
        val result = codec.decode(frame)
        assertThat(result.packets).hasSize(1)
        assertThat(result.packets.single().body).isEqualTo(payload)
    }

    @Test
    fun pc16_nonJsonCompressedPayloadThrowsMalformedPacketException() {
        val compressed = zlibCompress("not-json-and-long-enough".toByteArray())
        val frame = codec.encode(
            BiliPacket(
                packetLength = 16 + compressed.size,
                headerLength = 16,
                protocolVersion = 2,
                operation = BiliPacket.OP_MESSAGE,
                sequence = 1,
                body = compressed,
            ),
        )
        assertThat(runCatching { codec.decode(frame) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)
    }

    @Test
    fun pc17_shortHeartbeatReplyBodyIsRejected() {
        val packet = BiliPacket(
            packetLength = 19,
            headerLength = 16,
            protocolVersion = 1,
            operation = BiliPacket.OP_HEARTBEAT_REPLY,
            sequence = 1,
            body = byteArrayOf(1, 2, 3),
        )
        assertThat(runCatching { codec.parsePopularity(packet) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)
    }

    @Test
    fun pc18_nonAuthPacketIsNotAuthSuccess() {
        val packet = BiliPacket(
            packetLength = 20,
            headerLength = 16,
            protocolVersion = 1,
            operation = BiliPacket.OP_MESSAGE,
            sequence = 1,
            body = byteArrayOf(1, 2, 3, 4),
        )
        assertThat(codec.isAuthSuccess(packet)).isFalse()
    }

    @Test
    fun pc10_childPacketLimitRejectsTooManyPackets() {
        val strictCodec = BiliPacketCodec(
            limits = BiliPacketCodec.Limits(maxChildPackets = 1_000),
        )
        val childBody = """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215,1723420800],"x",[1,"u"]]}""".toByteArray()
        val child = strictCodec.encode(
            BiliPacket(
                packetLength = 16 + childBody.size,
                headerLength = 16,
                protocolVersion = 1,
                operation = 5,
                sequence = 1,
                body = childBody,
            ),
        )
        val combined = ByteArrayOutputStream().use { out ->
            repeat(1001) { out.write(child) }
            out.toByteArray()
        }
        val compressed = zlibCompress(combined)
        val frame = strictCodec.encode(
            BiliPacket(
                packetLength = 16 + compressed.size,
                headerLength = 16,
                protocolVersion = 2,
                operation = 5,
                sequence = 1,
                body = compressed,
            ),
        )
        assertThat(runCatching { strictCodec.decode(frame) }.exceptionOrNull())
            .isInstanceOf(ChildPacketLimitExceededException::class.java)
    }

    private fun nestedCompressedPacket(depth: Int): ByteArray {
        val body = """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215,1723420800],"深度",[1,"u"]]}""".toByteArray()
        var payload = codec.encode(
            BiliPacket(
                packetLength = 16 + body.size,
                headerLength = 16,
                protocolVersion = 1,
                operation = 5,
                sequence = 1,
                body = body,
            ),
        )
        repeat(depth) {
            val compressed = zlibCompress(payload)
            payload = codec.encode(
                BiliPacket(
                    packetLength = 16 + compressed.size,
                    headerLength = 16,
                    protocolVersion = 2,
                    operation = 5,
                    sequence = 1,
                    body = compressed,
                ),
            )
        }
        return payload
    }

    private fun zlibCompress(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
