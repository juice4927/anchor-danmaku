package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class BiliCompressionMultiStreamTest {
    private val compression = BiliCompression()

    @Test
    fun inflateZlib_singleStream_stillWorks() {
        val text = "单流弹幕".toByteArray(Charsets.UTF_8)
        assertThat(compression.inflateZlib(zlib(text)).decodeToString()).isEqualTo("单流弹幕")
    }

    @Test
    fun inflateZlib_concatenatedStreams_decompressesEveryStream() {
        val first = "第一段弹幕".toByteArray(Charsets.UTF_8)
        val second = "第二段弹幕".toByteArray(Charsets.UTF_8)
        val payload = zlib(first) + zlib(second)
        assertThat(compression.inflateZlib(payload).decodeToString())
            .isEqualTo("第一段弹幕第二段弹幕")
    }

    @Test
    fun inflateZlib_manyStreams_stillWithinLimits() {
        val payload = ByteArrayOutputStream().use { out ->
            repeat(4) { out.write(zlib("块$it".toByteArray(Charsets.UTF_8))) }
            out.toByteArray()
        }
        assertThat(compression.inflateZlib(payload).decodeToString()).isEqualTo("块0块1块2块3")
    }

    private fun zlib(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
