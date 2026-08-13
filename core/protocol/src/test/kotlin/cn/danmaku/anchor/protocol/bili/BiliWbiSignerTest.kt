package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BiliWbiSignerTest {
    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"

    @Test
    fun wbi01_computesOfficialMixinKeyVector() {
        assertThat(BiliWbiSigner.mixinKey(imgKey, subKey))
            .isEqualTo("ea1db124af3c7062474693fa704f4ff8")
    }

    @Test
    fun wbi02_signsParamsWithFixedTimestampVector() {
        val signed = BiliWbiSigner.sign(
            params = mapOf(
                "id" to "987654",
                "type" to "0",
                "web_location" to "444.8",
            ),
            imgKey = imgKey,
            subKey = subKey,
            wtsSeconds = 1_700_000_000L,
        )
        assertThat(signed["wts"]).isEqualTo("1700000000")
        assertThat(signed["w_rid"]).isEqualTo("fd64ad32eace4dd65e30433cdf5b9d9a")
        assertThat(signed["id"]).isEqualTo("987654")
        assertThat(signed["type"]).isEqualTo("0")
        assertThat(signed["web_location"]).isEqualTo("444.8")
    }

    @Test
    fun wbi03_encodesRfc3986AndFiltersForbiddenChars() {
        assertThat(BiliWbiSigner.encodeURIComponent("a b/中"))
            .isEqualTo("a%20b%2F%E4%B8%AD")
        assertThat(BiliWbiSigner.encodeURIComponent("a b!c").filterNot { it in "!'()*" })
            .isEqualTo("a%20bc")
    }
}
