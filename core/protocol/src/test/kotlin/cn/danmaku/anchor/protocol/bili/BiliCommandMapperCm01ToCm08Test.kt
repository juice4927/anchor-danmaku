package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import org.junit.Test

class BiliCommandMapperCm01ToCm08Test {
    private val diagnostics = SafeDiagnostics()
    private val mapper = BiliCommandMapper(diagnostics = diagnostics)
    private val roomId = 987654L
    private val receivedAtMillis = 1_723_420_999_000L

    @Test
    fun cm01_danmakuVariantWithSuffixIsRecognized() {
        val mapped = mapper.map(packet("ws/danmaku-plain.b64"), roomId, receivedAtMillis).single()
        assertThat(mapped.toSnapshotJson()).isEqualTo(readJson("expected/danmaku-plain.json"))
    }

    @Test
    fun cm02_missingDanmakuFieldsAreDroppedAndCounted() {
        val payload = """{"cmd":"DANMU_MSG:1","info":[[]]}""".toByteArray()
        val mapped = mapper.map(BiliPacket(16 + payload.size, 16, 1, 5, 1, payload), roomId, receivedAtMillis)
        assertThat(mapped).isEmpty()
        assertThat(diagnostics.snapshot()["malformed_command:DANMU_MSG"]).isEqualTo(1L)
    }

    @Test
    fun cm03_superChatFieldsAreMapped() {
        val mapped = mapper.map(packet("ws/super-chat.b64"), roomId, receivedAtMillis).single()
        assertThat(mapped.toSnapshotJson()).isEqualTo(readJson("expected/super-chat.json"))
    }

    @Test
    fun cm04_goldGiftUsesCnyEstimation() {
        val mapped = mapper.map(packet("ws/gift-gold.b64"), roomId, receivedAtMillis).single()
        assertThat(mapped.toSnapshotJson()).isEqualTo(readJson("expected/gift-gold.json"))
    }

    @Test
    fun cm05_silverGiftHasNoCnyEstimation() {
        val mapped = mapper.map(packet("ws/gift-silver.b64"), roomId, receivedAtMillis).single()
        assertThat(mapped.toSnapshotJson()).isEqualTo(readJson("expected/gift-silver.json"))
    }

    @Test
    fun cm06_guardBuyFieldsAreMapped() {
        val mapped = mapper.map(packet("ws/guard-buy.b64"), roomId, receivedAtMillis).single()
        assertThat(mapped.toSnapshotJson()).isEqualTo(readJson("expected/guard-buy.json"))
    }

    @Test
    fun cm07_unknownCommandIncrementsDiagnosticAndYieldsNoMessages() {
        val mapped = mapper.map(packet("ws/unknown-command.b64"), roomId, receivedAtMillis)
        assertThat(mapped).isEmpty()
        assertThat(diagnostics.snapshot()["unknown_command:INTERACT_WORD"]).isEqualTo(1L)
    }

    @Test
    fun cm08MalformedJsonIsIsolatedFromSubsequentValidPackets() {
        val valid = mapper.map(packet("ws/danmaku-plain.b64"), roomId, receivedAtMillis)
        val invalid = mapper.map(
            BiliPacket(
                packetLength = 24,
                headerLength = 16,
                protocolVersion = 1,
                operation = 5,
                sequence = 1,
                body = "not-json".toByteArray(),
            ),
            roomId,
            receivedAtMillis,
        )
        val again = mapper.map(packet("ws/gift-silver.b64"), roomId, receivedAtMillis)
        assertThat(valid).isNotEmpty()
        assertThat(invalid).isEmpty()
        assertThat(again).isNotEmpty()
        assertThat(diagnostics.snapshot()["malformed_json"] ?: 0L).isAtLeast(1L)
    }

    @Test
    fun cm09_nonMessagePacketsAreIgnored() {
        val packet = BiliPacket(
            packetLength = 20,
            headerLength = 16,
            protocolVersion = 1,
            operation = BiliPacket.OP_HEARTBEAT,
            sequence = 1,
            body = byteArrayOf(1, 2, 3, 4),
        )
        assertThat(mapper.map(packet, roomId, receivedAtMillis)).isEmpty()
    }

    @Test
    fun cm10_missingCmdIsCountedAndDropped() {
        val mapped = mapper.map(
            testJson.parseToJsonElement("""{"data":{}}"""),
            roomId,
            receivedAtMillis,
        )
        assertThat(mapped).isEmpty()
        assertThat(diagnostics.snapshot()["missing_cmd"]).isEqualTo(1L)
    }

    @Test
    fun cm11_guardBuyFallsBackToGeneratedIdWhenIdentifiersAreMissing() {
        val mapped = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"GUARD_BUY","data":{"uid":10001,"username":"user","guard_level":3,"num":1,"start_time":1723420860}}""",
            ),
            roomId,
            receivedAtMillis,
        ).single()
        assertThat(mapped.toSnapshotJson()["id"].toString()).contains("guard:$roomId:10001:1723420860000")
    }

    @Test
    fun cm12_superChatAcceptsMissingUserInfo() {
        val mapped = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"SUPER_CHAT_MESSAGE","data":{"id":70001,"price":30,"message":"hi","start_time":1723420830,"end_time":1723420890,"uid":10001}}""",
            ),
            roomId,
            receivedAtMillis,
        ).single()
        assertThat(mapped.toSnapshotJson()["userName"]).isNull()
    }

    @Test
    fun cm13_giftWithoutTidUsesGeneratedIdAndDefaultCoinValue() {
        val mapped = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"SEND_GIFT","data":{"uname":"user","giftName":"rose","num":2,"coin_type":"silver"}}""",
            ),
            roomId,
            receivedAtMillis,
        ).single()
        assertThat(mapped.toSnapshotJson()["id"].toString()).contains("gift:$roomId:0:rose:0")
        assertThat(mapped.toSnapshotJson()["estimatedCny"]).isEqualTo(JsonNull)
    }

    @Test
    fun cm14_superChatAcceptsStringPriceValues() {
        val mapped = mapper.map(
            testJson.parseToJsonElement(
                """{"cmd":"SUPER_CHAT_MESSAGE","data":{"id":"70001","price":"30","message":"hi","start_time":1723420830,"uid":10001}}""",
            ),
            roomId,
            receivedAtMillis,
        ).single()
        assertThat(mapped.toSnapshotJson()["priceCny"].toString()).contains("30")
    }

    private fun packet(path: String): BiliPacket = codec.decode(readBase64Fixture(path)).packets.single()

    private val codec = BiliPacketCodec(diagnostics = diagnostics)
}
