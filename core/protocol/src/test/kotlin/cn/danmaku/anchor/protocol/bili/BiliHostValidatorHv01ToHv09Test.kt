package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BiliHostValidatorHv01ToHv09Test {
    @Test
    fun hv01_normalizeAcceptsBilibiliSubdomainAndCanonicalizesIt() {
        assertThat(BiliHostValidator.normalize("  BroadcastLV.Chat.Bilibili.com. "))
            .isEqualTo("broadcastlv.chat.bilibili.com")
        assertThat(BiliHostValidator.normalize("bilibili.com")).isEqualTo("bilibili.com")
    }

    @Test
    fun hv02_normalizeRejectsBlankAndDelimitedHosts() {
        listOf("", "   ", "a/b", "a@b", "a:b", "[::1]").forEach {
            assertThat(BiliHostValidator.normalize(it)).isNull()
        }
    }

    @Test
    fun hv03_normalizeRejectsNonAsciiAndInvalidPunctuation() {
        listOf("直播.bilibili.com", "_chat.bilibili.com", "chat..bilibili.com").forEach {
            assertThat(BiliHostValidator.normalize(it)).isNull()
        }
    }

    @Test
    fun hv04_normalizeRejectsLeadingOrTrailingDots() {
        assertThat(BiliHostValidator.normalize(".chat.bilibili.com")).isNull()
        assertThat(BiliHostValidator.normalize("chat.bilibili.com.")).isEqualTo("chat.bilibili.com")
        assertThat(BiliHostValidator.normalize("chat.bilibili.com..")).isNull()
    }

    @Test
    fun hv05_normalizeRejectsIpv4AndNonBilibiliDomains() {
        assertThat(BiliHostValidator.normalize("127.0.0.1")).isNull()
        assertThat(BiliHostValidator.normalize("example.com")).isNull()
    }

    @Test
    fun hv06_isAllowedReflectsNormalizationResult() {
        assertThat(BiliHostValidator.isAllowed("chat.bilibili.com")).isTrue()
        assertThat(BiliHostValidator.isAllowed("example.com")).isFalse()
    }

    @Test
    fun hv07_buildWssUrlUsesDefaultPathAndCanonicalHost() {
        val url = BiliHostValidator.buildWssUrl("broadcastlv.chat.bilibili.com", 443)
        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("broadcastlv.chat.bilibili.com")
        assertThat(url.port).isEqualTo(443)
        assertThat(url.encodedPath).isEqualTo("/sub")
    }

    @Test
    fun hv08_buildWssUrlTrimsCustomPath() {
        val url = BiliHostValidator.buildWssUrl("broadcastlv.chat.bilibili.com", 8443, "  /custom ")
        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("broadcastlv.chat.bilibili.com")
        assertThat(url.port).isEqualTo(8443)
        assertThat(url.encodedPath).isEqualTo("/custom")
        assertThat(BiliHostValidator.buildWssUrl("broadcastlv.chat.bilibili.com", 443, "   ").encodedPath)
            .isEqualTo("/sub")
    }

    @Test
    fun hv09_buildWssUrlRejectsInvalidHostAndPort() {
        assertThat(runCatching { BiliHostValidator.buildWssUrl("example.com", 443) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { BiliHostValidator.buildWssUrl("chat.bilibili.com", 0) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { BiliHostValidator.buildWssUrl("chat.bilibili.com", 65536) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
