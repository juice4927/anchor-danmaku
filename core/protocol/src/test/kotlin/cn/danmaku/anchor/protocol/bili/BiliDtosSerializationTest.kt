package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class BiliDtosSerializationTest {
    @Test
    fun dto01_roomInitDataRoundTripsWithAllFieldsPresent() {
        val original = BiliRoomInitData(
            roomId = 987654L,
            shortId = 1234L,
            uid = 10001L,
            liveStatus = 1,
            isHidden = false,
            isLocked = false,
            encrypted = true,
            pwdVerified = true,
        )
        val decoded = testJson.decodeFromString<BiliRoomInitData>(testJson.encodeToString(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun dto02_roomInitResponseRoundTripsWithMissingOptionalFields() {
        val decoded = testJson.decodeFromString<BiliRoomInitResponse>(
            testJson.encodeToString(
                BiliRoomInitResponse(
                    code = 0,
                    data = BiliRoomInitData(roomId = 987654L),
                ),
            ),
        )
        assertThat(decoded.code).isEqualTo(0)
        assertThat(decoded.data?.roomId).isEqualTo(987654L)
        assertThat(decoded.data?.shortId).isNull()
        assertThat(decoded.data?.pwdVerified).isNull()
    }

    @Test
    fun dto03_danmuInfoResponseRoundTripsWithMixedHostEntries() {
        val original = BiliDanmuInfoResponse(
            code = 0,
            data = BiliDanmuInfoData(
                token = "fixture-token-not-secret",
                hostList = listOf(
                    BiliHostDto(host = "broadcastlv.chat.bilibili.com", wssPort = 443),
                    BiliHostDto(host = "tx-bj-live-comet-01.chat.bilibili.com", port = 8443, wsPort = 80),
                ),
            ),
        )
        val decoded = testJson.decodeFromString<BiliDanmuInfoResponse>(testJson.encodeToString(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun dto04_authReplyRoundTripsWithAndWithoutCode() {
        val encodedNull = testJson.encodeToString(BiliAuthReply())
        val encodedCode = testJson.encodeToString(BiliAuthReply(code = 0))
        assertThat(testJson.decodeFromString<BiliAuthReply>(encodedNull)).isEqualTo(BiliAuthReply())
        assertThat(testJson.decodeFromString<BiliAuthReply>(encodedCode)).isEqualTo(BiliAuthReply(code = 0))
    }

    @Test
    fun dto05_danmuConfResponseRoundTripsWithHostServerList() {
        val original = BiliDanmuConfResponse(
            code = 0,
            data = BiliDanmuConfData(
                token = "fixture-token-not-secret",
                hostServerList = listOf(
                    BiliHostDto(host = "broadcastlv.chat.bilibili.com", port = 2243, wsPort = 2244, wssPort = 443),
                ),
            ),
        )
        val decoded = testJson.decodeFromString<BiliDanmuConfResponse>(testJson.encodeToString(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun dto06_wbiNavResponseRoundTripsWithImageUrls() {
        val original = BiliWbiNavResponse(
            code = 0,
            data = BiliWbiNavData(
                wbiImg = BiliWbiImg(
                    imgUrl = "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
                    subUrl = "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png",
                ),
            ),
        )
        val decoded = testJson.decodeFromString<BiliWbiNavResponse>(testJson.encodeToString(original))
        assertThat(decoded).isEqualTo(original)
    }
}
