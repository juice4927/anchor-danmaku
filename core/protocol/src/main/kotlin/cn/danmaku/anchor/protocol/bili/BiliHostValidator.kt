package cn.danmaku.anchor.protocol.bili

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

object BiliHostValidator {
    private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun normalize(rawHost: String): String? {
        val trimmed = rawHost.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.contains('/') || trimmed.contains('@') || trimmed.contains(':') || trimmed.startsWith("[")) {
            return null
        }
        if (!trimmed.all { it.code in 0x21..0x7E }) {
            return null
        }
        val lower = trimmed.lowercase(Locale.ROOT).removeSuffix(".")
        if (lower.isEmpty() || lower.startsWith('.') || lower.endsWith('.') || lower.contains("..")) {
            return null
        }
        if (!lower.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) {
            return null
        }
        if (ipv4Regex.matches(lower)) {
            return null
        }
        return if (lower == "bilibili.com" || lower.endsWith(".bilibili.com")) {
            lower
        } else {
            null
        }
    }

    fun isAllowed(rawHost: String): Boolean = normalize(rawHost) != null

    fun buildWssUrl(rawHost: String, port: Int, path: String = "/sub"): HttpUrl {
        val host = normalize(rawHost) ?: throw IllegalArgumentException("Host is not allowed: $rawHost")
        require(port in 1..65535) { "Port out of range: $port" }
        val normalizedPath = path.trim().ifEmpty { "/sub" }
        // OkHttp's HttpUrl represents a secure WebSocket endpoint as HTTPS;
        // Request/RealWebSocket performs the HTTP upgrade on this URL.
        return "https://$host:$port$normalizedPath".toHttpUrl()
    }
}
