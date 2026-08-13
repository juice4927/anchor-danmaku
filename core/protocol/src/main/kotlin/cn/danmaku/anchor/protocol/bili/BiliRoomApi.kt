package cn.danmaku.anchor.protocol.bili

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class BiliFailure(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class RoomNotFoundFailure : BiliFailure("RoomNotFound", retryable = false, message = "Room not found")

class RoomRestrictedFailure : BiliFailure("RoomRestricted", retryable = false, message = "Room restricted")

class EndpointUnavailableFailure(message: String = "No valid danmaku host") :
    BiliFailure("EndpointUnavailable", retryable = true, message = message)

class RateLimitedFailure(message: String = "Rate limited") :
    BiliFailure("RateLimited", retryable = true, message = message)

class HostRejectedFailure(host: String, cause: Throwable? = null) :
    BiliFailure("HostRejected", retryable = true, message = "Host rejected: $host", cause = cause)

class AuthRejectedFailure(message: String = "Auth rejected") :
    BiliFailure("AuthRejected", retryable = true, message = message)

class ConnectionLostFailure(cause: Throwable? = null) :
    BiliFailure("ConnectionLost", retryable = true, message = "Connection lost", cause = cause)

class ProtocolUnsupportedFailure(cause: Throwable? = null) :
    BiliFailure("ProtocolUnsupported", retryable = false, message = "Protocol unsupported", cause = cause)

class UnknownRecoverableFailure(message: String, cause: Throwable? = null) :
    BiliFailure("UnknownRecoverable", retryable = true, message = message, cause = cause)

class HttpStatusFailure(val statusCode: Int) : BiliFailure(
    code = if (statusCode == 429) "RateLimited" else "EndpointUnavailable",
    retryable = statusCode != 404,
    message = "HTTP $statusCode",
)

data class ResolvedRoom(
    val inputRoomId: Long,
    val roomId: Long,
    val shortId: Long?,
    val ownerUid: Long?,
    val liveStatus: Int,
    val restricted: Boolean,
)

data class DanmuHost(
    val host: String,
    val wssPort: Int,
)

data class DanmuInfo(
    val roomId: Long,
    val token: String,
    val hostList: List<DanmuHost>,
)

class BiliRoomApi(
    client: OkHttpClient,
    private val diagnostics: SafeDiagnostics = SafeDiagnostics(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseHttpUrl: HttpUrl = "https://api.live.bilibili.com/".toHttpUrl(),
    private val wbiNavUrl: HttpUrl = "https://api.bilibili.com/x/web-interface/nav".toHttpUrl(),
) {
    private val client = client

    // WBI keys 按天轮换；缓存 30 分钟即可避免每次重连都多一次 nav 请求，
    // 失败时不写入缓存，保持现有回退语义。
    @Volatile
    private var wbiCache: Pair<Pair<String, String>, Long>? = null

    suspend fun resolveRoom(inputRoomId: Long): ResolvedRoom {
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("room/v1/Room/room_init")
            .addQueryParameter("id", inputRoomId.toString())
            .build()
        val response = execute(httpRequest(url, inputRoomId))
        val body = response.useBodyString()
        if (!response.isSuccessful) {
            throw mapHttpFailure(response.code)
        }
        val parsed = runCatching { json.decodeFromString<BiliRoomInitResponse>(body) }
            .getOrElse { throw UnknownRecoverableFailure("Invalid room_init JSON", it) }
        if (parsed.code != 0) {
            throw RoomNotFoundFailure()
        }
        val data = parsed.data ?: throw UnknownRecoverableFailure("Missing room_init data")
        val roomId = data.roomId ?: throw UnknownRecoverableFailure("Missing room_id")
        val restricted = data.isHidden == true || data.isLocked == true || (data.encrypted == true && data.pwdVerified != true)
        if (restricted) {
            throw RoomRestrictedFailure()
        }
        return ResolvedRoom(
            inputRoomId = inputRoomId,
            roomId = roomId,
            shortId = data.shortId,
            ownerUid = data.uid,
            liveStatus = data.liveStatus ?: 0,
            restricted = false,
        )
    }

    suspend fun getDanmuInfo(realRoomId: Long): DanmuInfo {
        val wbiKeys = runCatching { fetchWbiKeys() }.getOrNull()
        val v2Failure = try {
            if (wbiKeys == null) {
                EndpointUnavailableFailure("danmu info wbi keys unavailable")
            } else {
                return fetchDanmuInfoV2(realRoomId, wbiKeys.first, wbiKeys.second)
            }
        } catch (failure: EndpointUnavailableFailure) {
            failure
        }
        return if (v2Failure.message?.startsWith("danmu info") == true) {
            fetchDanmuConf(realRoomId)
        } else {
            throw v2Failure
        }
    }

    private suspend fun fetchDanmuInfoV2(realRoomId: Long, imgKey: String, subKey: String): DanmuInfo {
        val params = BiliWbiSigner.sign(
            params = linkedMapOf(
                "id" to realRoomId.toString(),
                "type" to "0",
                "web_location" to "444.8",
            ),
            imgKey = imgKey,
            subKey = subKey,
        )
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("xlive/web-room/v1/index/getDanmuInfo")
            .apply {
                params.forEach { (name, value) -> addQueryParameter(name, value) }
            }
            .build()
        val response = execute(httpRequest(url, realRoomId))
        val body = response.useBodyString()
        if (!response.isSuccessful) {
            throw mapHttpFailure(response.code)
        }
        val parsed = runCatching { json.decodeFromString<BiliDanmuInfoResponse>(body) }
            .getOrElse { throw UnknownRecoverableFailure("Invalid getDanmuInfo JSON", it) }
        if (parsed.code != 0) {
            throw EndpointUnavailableFailure("danmu info code=${parsed.code}")
        }
        val data = parsed.data ?: throw EndpointUnavailableFailure("Missing danmu info data")
        val token = data.token?.takeIf { it.isNotBlank() } ?: throw EndpointUnavailableFailure("Missing danmu token")
        return DanmuInfo(
            roomId = realRoomId,
            token = token,
            hostList = toDanmuHosts(data.hostList),
        )
    }

    private suspend fun fetchDanmuConf(realRoomId: Long): DanmuInfo {
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("room/v1/Danmu/getConf")
            .addQueryParameter("room_id", realRoomId.toString())
            .addQueryParameter("platform", "pc")
            .addQueryParameter("player", "web")
            .build()
        val response = execute(httpRequest(url, realRoomId))
        val body = response.useBodyString()
        if (!response.isSuccessful) {
            throw mapHttpFailure(response.code)
        }
        val parsed = runCatching { json.decodeFromString<BiliDanmuConfResponse>(body) }
            .getOrElse { throw UnknownRecoverableFailure("Invalid getConf JSON", it) }
        if (parsed.code != 0) {
            throw EndpointUnavailableFailure("danmu conf code=${parsed.code}")
        }
        val data = parsed.data ?: throw EndpointUnavailableFailure("Missing danmu conf data")
        val token = data.token?.takeIf { it.isNotBlank() } ?: throw EndpointUnavailableFailure("Missing danmu token")
        return DanmuInfo(
            roomId = realRoomId,
            token = token,
            hostList = toDanmuHosts(data.hostServerList),
        )
    }

    private suspend fun fetchWbiKeys(): Pair<String, String> {
        val now = System.currentTimeMillis()
        wbiCache?.let { (keys, fetchedAtMillis) ->
            if (now - fetchedAtMillis < WBI_CACHE_TTL_MILLIS) {
                return keys
            }
        }
        val response = execute(httpRequest(wbiNavUrl))
        val body = response.useBodyString()
        if (!response.isSuccessful) {
            throw mapHttpFailure(response.code)
        }
        val parsed = runCatching { json.decodeFromString<BiliWbiNavResponse>(body) }
            .getOrElse { throw UnknownRecoverableFailure("Invalid nav JSON", it) }
        if (parsed.code != 0) {
            throw UnknownRecoverableFailure("nav code=${parsed.code}")
        }
        val imgKey = parsed.data?.wbiImg?.imgUrl?.substringAfterLast('/')?.substringBefore('.')
        val subKey = parsed.data?.wbiImg?.subUrl?.substringAfterLast('/')?.substringBefore('.')
        if (imgKey.isNullOrBlank() || subKey.isNullOrBlank()) {
            throw UnknownRecoverableFailure("Missing wbi keys")
        }
        val keys = imgKey to subKey
        wbiCache = keys to now
        return keys
    }

    private fun toDanmuHosts(hostDtos: List<BiliHostDto>): List<DanmuHost> {
        val hosts = hostDtos.mapNotNull { host ->
            val normalized = host.host?.let(BiliHostValidator::normalize)
            val port = host.wssPort
            when {
                normalized == null || port == null || port !in 1..65535 -> {
                    diagnostics.increment("invalid_host_entry")
                    null
                }

                else -> DanmuHost(normalized, port)
            }
        }
        if (hosts.isEmpty()) {
            throw EndpointUnavailableFailure()
        }
        return hosts
    }

    fun newWebSocketRequest(url: HttpUrl, realRoomId: Long): Request =
        Request.Builder()
            .url(url)
            .headers(commonHeaders(realRoomId).newBuilder().add("Origin", "https://live.bilibili.com").build())
            .build()

    private fun httpRequest(url: HttpUrl, realRoomId: Long? = null): Request =
        Request.Builder()
            .url(url)
            .headers(commonHeaders(realRoomId ?: 0L))
            .get()
            .build()

    private fun commonHeaders(realRoomId: Long): Headers =
        Headers.Builder()
            .add("User-Agent", DEFAULT_USER_AGENT)
            .add("Referer", "https://live.bilibili.com/$realRoomId")
            .build()

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) {
                        return
                    }
                    continuation.resumeWithException(UnknownRecoverableFailure("HTTP call failed", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )
    }

    private fun mapHttpFailure(statusCode: Int): BiliFailure = when (statusCode) {
        404 -> RoomNotFoundFailure()
        429 -> RateLimitedFailure()
        else -> HttpStatusFailure(statusCode)
    }

    private fun Response.useBodyString(): String = use { response ->
        response.body?.string() ?: ""
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val WBI_CACHE_TTL_MILLIS: Long = 30 * 60 * 1000L
    }
}
