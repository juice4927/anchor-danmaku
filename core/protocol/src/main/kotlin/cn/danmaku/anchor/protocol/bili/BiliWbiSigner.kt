package cn.danmaku.anchor.protocol.bili

import java.security.MessageDigest

/**
 * Bilibili WBI 签名（w_rid + wts）。
 *
 * 实现对齐 bilibili-API-collect 的官方文档：
 * - mixin_key 由 img_key + sub_key 按 MIXIN_KEY_ENC_TAB 重排后取前 32 位
 * - 参数按键名 ASCII 排序，值按 encodeURIComponent 编码并过滤 !'()*
 * - w_rid = MD5(query + mixin_key)，小写十六进制
 */
internal object BiliWbiSigner {
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun mixinKey(imgKey: String, subKey: String): String {
        val combined = imgKey + subKey
        return buildString(32) {
            for (index in 0 until 32) {
                append(combined[MIXIN_KEY_ENC_TAB[index]])
            }
        }
    }

    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        wtsSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): Map<String, String> {
        val withTimestamp = LinkedHashMap(params)
        withTimestamp["wts"] = wtsSeconds.toString()
        val sorted = withTimestamp.toSortedMap()
        val query = sorted.entries.joinToString("&") { (name, value) ->
            val encodedValue = encodeURIComponent(value).filterNot { it in "!'()*" }
            encodeURIComponent(name) + "=" + encodedValue
        }
        val wRid = md5Hex(query + mixinKey(imgKey, subKey))
        return sorted + ("w_rid" to wRid)
    }

    internal fun encodeURIComponent(value: String): String {
        val encoded = StringBuilder(value.length * 3)
        for (ch in value) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                    ch in "-_.!~*'()" -> encoded.append(ch)

                else -> {
                    for (byte in ch.toString().toByteArray(Charsets.UTF_8)) {
                        encoded.append('%')
                        encoded.append(HEX_CHARS[(byte.toInt() ushr 4) and 0x0F])
                        encoded.append(HEX_CHARS[byte.toInt() and 0x0F])
                    }
                }
            }
        }
        return encoded.toString()
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
