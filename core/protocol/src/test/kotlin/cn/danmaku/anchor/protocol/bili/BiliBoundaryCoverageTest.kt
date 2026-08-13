package cn.danmaku.anchor.protocol.bili

import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream

/** Boundary cases for protocol branches that are not represented by live fixtures. */
class BiliBoundaryCoverageTest {
    private val roomId = 987654L
    private val receivedAt = 1_723_420_999_000L

    @Test
    fun commandMapperHandlesShapeAndOptionalFieldBoundaries() {
        val diagnostics = SafeDiagnostics()
        val mapper = BiliCommandMapper(diagnostics = diagnostics)

        assertThat(mapper.map(JsonPrimitive("text"), roomId, receivedAt)).isEmpty()
        assertThat(mapper.map(testJson.parseToJsonElement("{\"cmd\":null}"), roomId, receivedAt)).isEmpty()

        val sparseDanmaku = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"DANMU_MSG:4","info":[null,"hello",[123,"alice"],null]}""",
            ),
            roomId,
            receivedAt,
        ).single() as LiveMessage.DanmakuMessage
        assertThat(sparseDanmaku.serverTimestampMillis).isNull()
        assertThat(sparseDanmaku.medalName).isNull()
        assertThat(sparseDanmaku.medalLevel).isNull()

        val sparseGift = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"SEND_GIFT","data":{"uid":123,"uname":"alice","giftName":"rose","num":2,"coin_type":"silver"}}""",
            ),
            roomId,
            receivedAt,
        ).single() as LiveMessage.GiftMessage
        assertThat(sparseGift.totalCoin).isEqualTo(0L)
        assertThat(sparseGift.serverTimestampMillis).isNull()
        assertThat(sparseGift.estimatedCny).isNull()
        assertThat(sparseGift.id).contains("gift:$roomId:123:rose:0")

        val sparseGuard = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"GUARD_BUY","data":{"uid":123,"username":"alice","guard_level":3,"num":1}}""",
            ),
            roomId,
            receivedAt,
        ).single() as LiveMessage.GuardMessage
        assertThat(sparseGuard.serverTimestampMillis).isNull()
        assertThat(sparseGuard.id).contains("guard:$roomId:123:0")

        val malformedPayloads = listOf(
            """{"cmd":"DANMU_MSG","info":"bad"}""",
            """{"cmd":"DANMU_MSG","info":[null,"text",[null,"alice"]]}""",
            """{"cmd":"SUPER_CHAT_MESSAGE","data":{}}""",
            """{"cmd":"SEND_GIFT","data":{"uid":1,"uname":"u","num":1,"coin_type":"gold"}}""",
            """{"cmd":"GUARD_BUY","data":{"uid":1,"username":"u","num":1}}""",
        )
        malformedPayloads.forEach { payload ->
            assertThat(mapper.map(testJson.parseToJsonElement(payload), roomId, receivedAt)).isEmpty()
        }
        assertThat(diagnostics.snapshot().filterKeys { it.startsWith("malformed_command:") }.values.sum()).isAtLeast(5L)
    }

    @Test
    fun snapshotJsonEmitsNullableAndMoneyVariants() {
        val danmaku = LiveMessage.DanmakuMessage(
            id = "d",
            roomId = roomId,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = receivedAt,
            text = "hello",
            medalName = null,
            medalLevel = null,
        )
        val superChat = LiveMessage.SuperChatMessage(
            id = "sc",
            roomId = roomId,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = receivedAt,
            message = "hello",
            priceCny = Money.ZERO,
            startTimeMillis = null,
            endTimeMillis = null,
        )
        val gift = LiveMessage.GiftMessage(
            id = "g",
            roomId = roomId,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = receivedAt,
            giftName = "rose",
            count = 1,
            totalCoin = 0L,
            coinType = "silver",
            estimatedCny = null,
        )
        val guard = LiveMessage.GuardMessage(
            id = "guard",
            roomId = roomId,
            uid = null,
            userName = null,
            serverTimestampMillis = null,
            receivedAtMillis = receivedAt,
            guardLevel = 3,
            count = 1,
        )

        listOf(danmaku, superChat, gift, guard).forEach { value ->
            assertThat(value.toSnapshotJson()["roomId"]).isNotNull()
            assertThat(value.toSnapshotJson()["uid"]).isNull()
            assertThat(value.toSnapshotJson()["userName"]).isNull()
        }
        assertThat(gift.toSnapshotJson()["estimatedCny"]).isEqualTo(JsonNull)
        assertThat(superChat.toSnapshotJson()["priceCny"]?.toString()).isEqualTo("\"0\"")
    }

    @Test
    fun packetCodecRejectsMalformedHeadersAndHandlesNestedJsonFallback() {
        val diagnostics = SafeDiagnostics()
        val codec = BiliPacketCodec(diagnostics = diagnostics)

        assertThat(codec.decode(ByteArray(3)).packets).isEmpty()
        assertThat(diagnostics.snapshot()["tail_bytes_discarded"]).isEqualTo(3L)
        assertThat(runCatching { codec.parsePopularity(BiliPacket(16, 16, 1, 5, 1, byteArrayOf(0, 0, 0, 0))) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)

        val malformedHeaders = listOf(
            rawFrame(packetLength = 15, headerLength = 16),
            rawFrame(packetLength = 16, headerLength = 15),
            rawFrame(packetLength = 16, headerLength = 20),
            rawFrame(packetLength = 64, headerLength = 16),
        )
        malformedHeaders.forEach { frame ->
            assertThat(runCatching { codec.decode(frame) }.exceptionOrNull())
                .isInstanceOf(MalformedPacketException::class.java)
        }

        val valid = codec.encode(codec.authPacket(roomId, "token"))
        val truncatedTail = valid + rawFrame(packetLength = 100, headerLength = 16)
        assertThat(codec.decode(truncatedTail).packets).hasSize(1)
        assertThat(diagnostics.snapshot()["tail_truncated"]).isEqualTo(1L)

        val jsonPayload = """{"cmd":"DANMU_MSG","info":[]}""".toByteArray()
        val compressedJson = zlibCompress(jsonPayload)
        val outer = codec.encode(
            BiliPacket(16 + compressedJson.size, 16, 2, BiliPacket.OP_MESSAGE, 1, compressedJson),
        )
        val fallback = codec.decode(outer).packets.single()
        assertThat(fallback.body).isEqualTo(jsonPayload)

        val invalidNested = zlibCompress("not-json-and-long-enough".toByteArray())
        val invalidOuter = codec.encode(
            BiliPacket(16 + invalidNested.size, 16, 2, BiliPacket.OP_MESSAGE, 1, invalidNested),
        )
        assertThat(runCatching { codec.decode(invalidOuter) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)
    }

    @Test
    fun packetCodecCoversAuthAndHeartbeatFailureShapes() {
        val codec = BiliPacketCodec()
        val wrongOperation = BiliPacket(20, 16, 1, BiliPacket.OP_HEARTBEAT, 1, byteArrayOf(1, 2, 3, 4))
        assertThat(codec.isAuthSuccess(wrongOperation)).isFalse()
        assertThat(codec.isAuthSuccess(BiliPacket(25, 16, 1, BiliPacket.OP_AUTH_REPLY, 1, "bad".toByteArray()))).isFalse()
        assertThat(codec.isAuthSuccess(BiliPacket(25, 16, 1, BiliPacket.OP_AUTH_REPLY, 1, "{}".toByteArray()))).isFalse()
        assertThat(codec.isAuthSuccess(BiliPacket(28, 16, 1, BiliPacket.OP_AUTH_REPLY, 1, "{\"code\":\"x\"}".toByteArray()))).isFalse()
        assertThat(runCatching { codec.parsePopularity(wrongOperation.copy(operation = BiliPacket.OP_HEARTBEAT_REPLY, body = byteArrayOf(1, 2, 3))) }.exceptionOrNull())
            .isInstanceOf(MalformedPacketException::class.java)

        val heartbeat = codec.encode(codec.heartbeatPacket())
        assertThat(codec.decode(heartbeat).packets.single().operation).isEqualTo(BiliPacket.OP_HEARTBEAT)
        val v0 = codec.encode(BiliPacket(20, 16, 0, BiliPacket.OP_MESSAGE, 1, byteArrayOf(1, 2, 3, 4)))
        assertThat(codec.decode(v0).packets.single().protocolVersion).isEqualTo(0)
    }

    @Test
    fun roomApiMapsMissingDataTokensAndRestrictedRooms() = runBlocking {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\":-1,\"data\":null}"))
            assertThat(runCatching { roomApi(server).resolveRoom(roomId) }.exceptionOrNull())
                .isInstanceOf(RoomNotFoundFailure::class.java)

            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\":0,\"data\":{\"room_id\":987654,\"is_hidden\":true}}"))
            assertThat(runCatching { roomApi(server).resolveRoom(roomId) }.exceptionOrNull())
                .isInstanceOf(RoomRestrictedFailure::class.java)

            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\":0,\"data\":{\"room_id\":987654,\"encrypted\":true,\"pwd_verified\":false}}"))
            assertThat(runCatching { roomApi(server).resolveRoom(roomId) }.exceptionOrNull())
                .isInstanceOf(RoomRestrictedFailure::class.java)

            server.enqueue(wbiNavResponse())
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"code":0,"data":{"token":"   ","host_list":[]}}""",
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"code":0,"data":{"token":"   ","host_list":[]}}""",
                ),
            )
            assertThat(runCatching { roomApi(server).getDanmuInfo(roomId) }.exceptionOrNull())
                .isInstanceOf(EndpointUnavailableFailure::class.java)
        }
    }

    @Test
    fun roomApiMapsHttpStatusesAndSparseDtoDefaults() = runBlocking {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            assertThat(runCatching { roomApi(server).resolveRoom(roomId) }.exceptionOrNull())
                .isInstanceOf(RoomNotFoundFailure::class.java)
            server.enqueue(wbiNavResponse())
            server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
            assertThat(runCatching { roomApi(server).getDanmuInfo(roomId) }.exceptionOrNull())
                .isInstanceOf(RateLimitedFailure::class.java)
        }
        assertThat(testJson.decodeFromString<BiliAuthReply>("{}").code).isNull()
        assertThat(testJson.decodeFromString<BiliAuthReply>("{\"code\":0,\"unknown\":true}").code).isEqualTo(0)
        assertThat(testJson.decodeFromString<BiliRoomInitResponse>("{\"code\":0}").data).isNull()
        assertThat(testJson.decodeFromString<BiliDanmuInfoResponse>("{\"code\":0,\"data\":{}}").data?.hostList).isEmpty()
    }

    private fun rawFrame(
        packetLength: Int,
        headerLength: Int,
        protocolVersion: Int = 1,
        operation: Int = BiliPacket.OP_MESSAGE,
        sequence: Int = 1,
        body: ByteArray = byteArrayOf(),
    ): ByteArray = ByteBuffer.allocate(16 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
        putInt(packetLength)
        putShort(headerLength.toShort())
        putShort(protocolVersion.toShort())
        putInt(operation)
        putInt(sequence)
        put(body)
    }.array()

    private fun zlibCompress(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        DeflaterOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private fun roomApi(server: MockWebServer): BiliRoomApi = BiliRoomApi(
        client = OkHttpClient.Builder().build(),
        baseHttpUrl = server.url("/"),
        wbiNavUrl = server.url("/x/web-interface/nav"),
    )

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            runCatching { server.shutdown() }
        }
    }
}
