package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import cn.danmaku.anchor.model.LiveStatus
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

class BiliRoomApiNet01ToNet06Test {
    @Test
    fun net01_resolveRoomReturnsHttpStatusFailureForServerError() = runBlockingTest {
        withServer { server ->
            server.enqueueHttpResponse("/room/v1/Room/room_init", 500, """{"message":"oops"}""")
            val api = roomApi(server)
            val failure = runCatching { api.resolveRoom(1234L) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(HttpStatusFailure::class.java)
            assertThat((failure as HttpStatusFailure).statusCode).isEqualTo(500)
        }
    }

    @Test
    fun net02_resolveRoomRejectsMissingRoomId() = runBlockingTest {
        withServer { server ->
            server.enqueueHttpResponse(
                "/room/v1/Room/room_init",
                200,
                """{"code":0,"message":"OK","ttl":1,"data":{"short_id":1234}}""",
            )
            val api = roomApi(server)
            val failure = runCatching { api.resolveRoom(1234L) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(UnknownRecoverableFailure::class.java)
            assertThat(failure).hasMessageThat().contains("Missing room_id")
        }
    }

    @Test
    fun net03_getDanmuInfoFiltersInvalidHostsAndReturnsOnlyValidEntries() = runBlockingTest {
        withServer { server ->
            server.enqueueHttpResponse(
                "/xlive/web-room/v1/index/getDanmuInfo",
                200,
                """
                {"code":0,"message":"OK","ttl":1,"data":{
                  "token":"fixture-token-not-secret",
                  "host_list":[
                    {"host":"invalid host","wss_port":443},
                    {"host":"broadcastlv.chat.bilibili.com","wss_port":443}
                  ]
                }}
                """.trimIndent(),
            )
            val diagnostics = SafeDiagnostics()
            val api = roomApi(server, diagnostics)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.hostList).hasSize(1)
            assertThat(info.hostList.single().host).isEqualTo("broadcastlv.chat.bilibili.com")
            assertThat(diagnostics.snapshot()["invalid_host_entry"]).isEqualTo(1L)
        }
    }

    @Test
    fun net04_getDanmuInfoRejectsWhenNoValidHostsRemain() = runBlockingTest {
        withServer { server ->
            server.enqueueHttpResponse("/xlive/web-room/v1/index/getDanmuInfo", 200, readFixtureText("http/danmu-info-no-host.json"))
            val api = roomApi(server)
            val failure = runCatching { api.getDanmuInfo(987654L) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(EndpointUnavailableFailure::class.java)
        }
    }

    @Test
    fun net05_deserializesSparseRoomInitResponse() {
        val parsed = testJson.decodeFromString<BiliRoomInitResponse>(
            """{"code":0,"data":{"room_id":987654}}""",
        )
        assertThat(parsed.code).isEqualTo(0)
        assertThat(parsed.data?.roomId).isEqualTo(987654L)
        assertThat(parsed.data?.shortId).isNull()
        assertThat(parsed.data?.pwdVerified).isNull()
    }

    @Test
    fun net06_deserializesSparseDanmuInfoResponse() {
        val parsed = testJson.decodeFromString<BiliDanmuInfoResponse>(
            """{"code":0,"data":{"token":"fixture-token-not-secret","host_list":[{"host":"broadcastlv.chat.bilibili.com"},{"wss_port":443}]}}""",
        )
        assertThat(parsed.data?.hostList).hasSize(2)
        assertThat(parsed.data?.hostList?.first()?.host).isEqualTo("broadcastlv.chat.bilibili.com")
        assertThat(parsed.data?.hostList?.first()?.wssPort).isNull()
        assertThat(parsed.data?.hostList?.last()?.host).isNull()
        assertThat(parsed.data?.hostList?.last()?.wssPort).isEqualTo(443)
    }

    @Test
    fun net07_getDanmuInfoFallsBackToGetConfWhenV2IsRiskBlocked() = runBlockingTest {
        withServer { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.startsWith("/x/web-interface/nav") -> wbiNavResponse()

                        path.startsWith("/xlive/web-room/v1/index/getDanmuInfo") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"code":-352,"message":"-352","ttl":1}""")

                        path.startsWith("/room/v1/Danmu/getConf") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","data":{
                                      "token":"fixture-token-not-secret",
                                      "host_server_list":[
                                        {"host":"broadcastlv.chat.bilibili.com","wss_port":443}
                                      ]
                                    }}
                                    """.trimIndent(),
                                )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.hostList).hasSize(1)
            assertThat(info.hostList.single().host).isEqualTo("broadcastlv.chat.bilibili.com")
            assertThat(info.hostList.single().wssPort).isEqualTo(443)
        }
    }

    @Test
    fun net08_getDanmuInfoSignsV2RequestWithWbiParams() = runBlockingTest {
        withServer { server ->
            var capturedPath: String? = null
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.startsWith("/x/web-interface/nav") -> wbiNavResponse()

                        path.startsWith("/xlive/web-room/v1/index/getDanmuInfo") -> {
                            capturedPath = path
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","ttl":1,"data":{
                                      "token":"fixture-token-not-secret",
                                      "host_list":[{"host":"broadcastlv.chat.bilibili.com","wss_port":443}]
                                    }}
                                    """.trimIndent(),
                                )
                        }

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.hostList).hasSize(1)

            val path = capturedPath ?: throw AssertionError("getDanmuInfo was not called")
            assertThat(path).contains("id=987654")
            assertThat(path).contains("type=0")
            assertThat(path).contains("web_location=444.8")
            val wts = Regex("wts=(\\d+)").find(path)?.groupValues?.get(1)
                ?: throw AssertionError("wts missing from request")
            val wRid = Regex("w_rid=([0-9a-f]{32})").find(path)?.groupValues?.get(1)
                ?: throw AssertionError("w_rid missing from request")
            val expected = BiliWbiSigner.sign(
                params = mapOf(
                    "id" to "987654",
                    "type" to "0",
                    "web_location" to "444.8",
                ),
                imgKey = "7cd084941338484aae1ad9425b84077c",
                subKey = "4932caff0ff746eab6f01bf08b70ac45",
                wtsSeconds = wts.toLong(),
            )
            assertThat(wRid).isEqualTo(expected["w_rid"])
        }
    }

    @Test
    fun net09_getDanmuInfoFallsBackToGetConfWhenWbiKeysUnavailable() = runBlockingTest {
        withServer { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.startsWith("/x/web-interface/nav") -> MockResponse().setResponseCode(404)

                        path.startsWith("/room/v1/Danmu/getConf") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","data":{
                                      "token":"fixture-token-not-secret",
                                      "host_server_list":[
                                        {"host":"broadcastlv.chat.bilibili.com","wss_port":443}
                                      ]
                                    }}
                                    """.trimIndent(),
                                )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.hostList).hasSize(1)
            assertThat(info.hostList.single().host).isEqualTo("broadcastlv.chat.bilibili.com")
        }
    }

    @Test
    fun net10_anonymousIdentityCookieIsSentOnDanmuRequestsAndCached() = runBlockingTest {
        withServer { server ->
            var spiRequests = 0
            val requestPaths = mutableListOf<String>()
            val requestCookies = mutableListOf<String?>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    requestPaths += path
                    requestCookies += request.getHeader("Cookie")
                    return when {
                        path.startsWith("/x/frontend/finger/spi") -> {
                            spiRequests += 1
                            fingerSpiResponse()
                        }

                        path.startsWith("/x/web-interface/nav") -> wbiNavResponse()

                        path.startsWith("/xlive/web-room/v1/index/getDanmuInfo") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","ttl":1,"data":{
                                      "token":"fixture-token-not-secret",
                                      "host_list":[{"host":"broadcastlv.chat.bilibili.com","wss_port":443}]
                                    }}
                                    """.trimIndent(),
                                )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server, fingerprintInjected = false)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.anonymousIdentity).isEqualTo(TEST_IDENTITY)

            api.getDanmuInfo(987654L)
            assertThat(spiRequests).isEqualTo(1)
            val navCookie = requestCookies.filterIndexed { index, _ -> requestPaths[index].startsWith("/x/web-interface/nav") }
            val danmuCookie = requestCookies.filterIndexed { index, _ ->
                requestPaths[index].startsWith("/xlive/web-room/v1/index/getDanmuInfo")
            }
            assertThat(navCookie.single()).isEqualTo(TEST_IDENTITY.cookieHeader())
            assertThat(danmuCookie.last()).isEqualTo(TEST_IDENTITY.cookieHeader())
        }
    }

    @Test
    fun net11_fingerprintFailureStaysRecoverableAndNeverMapsToRoomNotFound() = runBlockingTest {
        withServer { server ->
            server.enqueueHttpResponse("/x/frontend/finger/spi", 500, """{"message":"oops"}""")
            val api = roomApi(server, fingerprintInjected = false)
            val failure = runCatching { api.getDanmuInfo(987654L) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(EndpointUnavailableFailure::class.java)
            assertThat(failure).isNotInstanceOf(RoomNotFoundFailure::class.java)

            server.enqueueHttpResponse("/x/frontend/finger/spi", 429, "{}")
            val rateLimited = runCatching { roomApi(server, fingerprintInjected = false).getDanmuInfo(987654L) }.exceptionOrNull()
            assertThat(rateLimited).isInstanceOf(RateLimitedFailure::class.java)

            server.enqueueHttpResponse("/x/frontend/finger/spi", 200, """{"code":0,"data":{"b_3":"only-three"}}""")
            val missingField = runCatching { roomApi(server, fingerprintInjected = false).getDanmuInfo(987654L) }.exceptionOrNull()
            assertThat(missingField).isInstanceOf(UnknownRecoverableFailure::class.java)
            assertThat(missingField).hasMessageThat().contains("buvid4")
        }
    }

    @Test
    fun net12_getRoomMetadataResolvesOwnerNameAndLiveStatus() = runBlockingTest {
        withServer { server ->
            var capturedPaths = mutableListOf<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    capturedPaths += path
                    return when {
                        path.startsWith("/room/v1/Room/get_info") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","data":{
                                      "room_id":23058,"uid":11153765,"live_status":1,"title":"哔哩哔哩音悦台"
                                    }}
                                    """.trimIndent(),
                                )

                        path.startsWith("/live_user/v1/Master/info") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"data":{"info":{
                                      "uid":11153765,"uname":"某主播","face":"https://example.com/face.jpg"
                                    }}}
                                    """.trimIndent(),
                                )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server)
            val meta = api.getRoomMetadata(23058L)
            assertThat(meta.roomId).isEqualTo(23058L)
            assertThat(meta.ownerName).isEqualTo("某主播")
            assertThat(meta.liveStatus).isEqualTo(LiveStatus.LIVE)
            val getInfoPath = capturedPaths.first { it.startsWith("/room/v1/Room/get_info") }
            assertThat(getInfoPath).contains("room_id=23058")
        }
    }

    @Test
    fun net13_getRoomMetadataDegradesWhenMasterInfoUnavailable() = runBlockingTest {
        withServer { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.startsWith("/room/v1/Room/get_info") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {"code":0,"message":"OK","data":{
                                      "room_id":23058,"uid":11153765,"live_status":0,"title":"哔哩哔哩音悦台"
                                    }}
                                    """.trimIndent(),
                                )

                        path.startsWith("/live_user/v1/Master/info") -> MockResponse().setResponseCode(500)

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val api = roomApi(server)
            val meta = api.getRoomMetadata(23058L)
            assertThat(meta.roomId).isEqualTo(23058L)
            assertThat(meta.ownerName).isNull()
            assertThat(meta.liveStatus).isEqualTo(LiveStatus.NOT_LIVE)
        }
    }

    private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private fun roomApi(
        server: MockWebServer,
        diagnostics: SafeDiagnostics = SafeDiagnostics(),
        fingerprintInjected: Boolean = true,
    ): BiliRoomApi =
        BiliRoomApi(
            client = OkHttpClient.Builder().build(),
            diagnostics = diagnostics,
            baseHttpUrl = server.url("/"),
            wbiNavUrl = server.url("/x/web-interface/nav"),
            fingerprintUrl = server.url("/x/frontend/finger/spi"),
            anonymousIdentityProvider = if (fingerprintInjected) TEST_IDENTITY_PROVIDER else null,
        )

    private fun MockWebServer.enqueueHttpResponse(pathPrefix: String, code: Int, body: String) {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                return when {
                    path.startsWith("/x/web-interface/nav") -> wbiNavResponse()
                    path.startsWith(pathPrefix) ->
                        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun withServer(block: suspend (MockWebServer) -> Unit) = kotlinx.coroutines.runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            server.shutdown()
        }
    }
}
