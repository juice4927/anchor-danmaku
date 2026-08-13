package cn.danmaku.anchor.protocol.bili

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class BiliCompression(
    private val maxDecompressedBytes: Int = 4 * 1024 * 1024,
) {
    fun inflateZlib(payload: ByteArray): ByteArray = inflateMultipleZlibStreams(payload)

    fun inflateBrotli(payload: ByteArray): ByteArray = readBounded(BrotliInputStream(ByteArrayInputStream(payload)))

    /**
     * 一个 WebSocket 帧的 body 可能由多个独立 zlib 流拼接而成（高流量直播间服务端会合并
     * 帧以减少往返）。[java.util.zip.InflaterInputStream] 只解压第一个流，之后的流会被静默
     * 丢弃，导致整段弹幕丢失。这里循环消费每个流，并在累计超过上限时抛边界异常。
     */
    private fun inflateMultipleZlibStreams(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        var offset = 0
        while (offset < payload.size) {
            val inflater = Inflater()
            try {
                inflater.setInput(payload, offset, payload.size - offset)
                while (true) {
                    val read = try {
                        inflater.inflate(buffer)
                    } catch (error: DataFormatException) {
                        break
                    }
                    if (read <= 0) break
                    total += read
                    if (total > maxDecompressedBytes) {
                        throw PayloadLimitExceededException("decompressedBytes", total, maxDecompressedBytes)
                    }
                    output.write(buffer, 0, read)
                }
                val consumed = (payload.size - offset) - inflater.remaining
                // 正常流 consumed > 0；异常时至少前进 1 字节避免死循环，交由调用方边界处理。
                offset += if (consumed > 0) consumed else 1
            } finally {
                inflater.end()
            }
        }
        return output.toByteArray()
    }

    private fun readBounded(input: InputStream): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) {
                    return output.toByteArray()
                }
                total += read
                if (total > maxDecompressedBytes) {
                    throw PayloadLimitExceededException("decompressedBytes", total, maxDecompressedBytes)
                }
                output.write(buffer, 0, read)
            }
        }
    }
}
