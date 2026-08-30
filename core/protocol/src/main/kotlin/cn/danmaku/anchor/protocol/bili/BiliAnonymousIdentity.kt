package cn.danmaku.anchor.protocol.bili

/**
 * Anonymous browser identity used by Bilibili's public live endpoints.
 * It is deliberately separate from account credentials and login cookies.
 */
data class BiliAnonymousIdentity(
    val buvid3: String,
    val buvid4: String,
) {
    init {
        require(isValidCookieValue(buvid3)) { "Invalid buvid3" }
        require(isValidCookieValue(buvid4)) { "Invalid buvid4" }
    }

    fun cookieHeader(): String = "buvid3=$buvid3; buvid4=$buvid4"

    private companion object {
        fun isValidCookieValue(value: String): Boolean =
            value.isNotBlank() && value.all { it.code in 0x21..0x7E && it != ';' && it != ',' }
    }
}

fun interface BiliAnonymousIdentityProvider {
    suspend fun load(): BiliAnonymousIdentity
}
