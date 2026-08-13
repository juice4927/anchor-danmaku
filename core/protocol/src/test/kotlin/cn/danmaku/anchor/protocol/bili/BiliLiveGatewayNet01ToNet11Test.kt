package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.junit.Test
import java.util.concurrent.TimeUnit

class BiliLiveGatewayNet01ToNet11Test {
    private val codec = BiliPacketCodec()

    @Test
    fun net01_shortRoomIdResolvesToRealRoomIdAndUsesItForSubsequentRequests() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(1_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.RoomResolved }) {
                    delay(10)
                }
            }
            val roomInit = server.takeRequest()
            val wbiNav = server.takeRequest()
            val danmuInfo = server.takeRequest()
            val wsRequest = server.takeRequest()
            assertThat(roomInit.path).isEqualTo("/room/v1/Room/room_init?id=1234")
            assertThat(wbiNav.path).isEqualTo("/x/web-interface/nav")
            assertThat(danmuInfo.path).startsWith("/xlive/web-room/v1/index/getDanmuInfo?id=987654&type=0&web_location=444.8&wts=")
            assertThat(danmuInfo.path).contains("&w_rid=")
            assertThat(wsRequest.path).isEqualTo("/ws/broadcastlv.chat.bilibili.com/443")
            assertThat(wsRequest.getHeader("Referer")).isEqualTo("https://live.bilibili.com/987654")
            assertThat(wsRequest.getHeader("Cookie")).isNull()
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net02_missingRoomMapsToRoomNotFoundAndDoesNotOpenWebSocket() = runBlocking {
        withServer { server ->
            server.enqueueHttpResponse("/room/v1/Room/room_init", 404, readFixtureText("http/room-init-not-found.json"))
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            assertThat(runCatching { session.start() }.exceptionOrNull())
                .isInstanceOf(RoomNotFoundFailure::class.java)
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun net03_restrictedRoomStopsBeforeWebSocketAndDoesNotRetry() = runBlocking {
        withServer { server ->
            server.enqueueHttpResponse("/room/v1/Room/room_init", 200, readFixtureText("http/room-init-restricted.json"))
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            assertThat(runCatching { session.start() }.exceptionOrNull())
                .isInstanceOf(RoomRestrictedFailure::class.java)
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun net04_notLiveRoomStillConnects() = runBlocking {
        withServer { server ->
            val roomInitBody = readFixtureText("http/room-init-not-live.json")
            server.enqueueHttpFixtures(roomInitBody = roomInitBody)
            server.enqueueWsScript(
                path = "/ws/broadcastlv.chat.bilibili.com/443",
                roomInitBody = roomInitBody,
            ) { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(1_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.RoomResolved }) {
                    delay(10)
                }
            }
            assertThat(events.any { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.RoomResolved }).isTrue()
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net05_getDanmuInfoParsesTokenAndHosts() = runBlocking {
        withServer { server ->
            server.enqueueHttpResponse("/xlive/web-room/v1/index/getDanmuInfo", 200, readFixtureText("http/danmu-info-valid.json"))
            val api = roomApi(server)
            val info = api.getDanmuInfo(987654L)
            assertThat(info.token).isEqualTo("fixture-token-not-secret")
            assertThat(info.hostList).hasSize(2)
            assertThat(info.hostList.first().host).isEqualTo("broadcastlv.chat.bilibili.com")
            assertThat(info.hostList.first().wssPort).isEqualTo(443)
        }
    }

    @Test
    fun net06_firstHostFailureFallsBackToSecondHost() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinary(codec.encode(
                    BiliPacket(
                        packetLength = 26,
                        headerLength = 16,
                        protocolVersion = 1,
                        operation = 8,
                        sequence = 1,
                        body = """{"code":1}""".toByteArray(),
                    ),
                ))
                ws.close(1000, "done")
            }
            server.enqueueWsScript("/ws/tx-bj-live-comet-01.chat.bilibili.com/8443") { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server) { host, port -> server.url("/ws/$host/$port") }
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(1_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Authenticating }) {
                    delay(10)
                }
            }
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.HostConnecting>()).hasSize(2)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net07_nonBilibiliHostsAreRejected() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures(
                danmuInfoBody = """{"code":0,"message":"OK","ttl":1,"data":{"token":"fixture-token-not-secret","host_list":[{"host":"evil.example.com","wss_port":443}]}}""",
            )
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            assertThat(runCatching { session.start() }.exceptionOrNull())
                .isInstanceOf(EndpointUnavailableFailure::class.java)
        }
    }

    @Test
    fun net08_authOkThenFixtureMessagesBecomeDomainEvents() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, clientMessage ->
                if (clientMessage.size > 0) {
                    ws.sendBinaryFixture("ws/auth-ok.b64")
                    ws.sendBinaryFixture("ws/heartbeat-popularity.b64")
                    ws.sendBinaryFixture("ws/danmaku-plain.b64")
                    ws.sendBinaryFixture("ws/super-chat.b64")
                    ws.sendBinaryFixture("ws/gift-gold.b64")
                    ws.sendBinaryFixture("ws/guard-buy.b64")
                    ws.close(1000, "done")
                }
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(2_000) {
                while (events.count { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Message } < 4) {
                    delay(10)
                }
            }
            assertThat(events.any { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Popularity }).isTrue()
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.Message>()).hasSize(4)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net09_serverCloseEmitsDisconnectedOnce() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, clientMessage ->
                if (clientMessage.size > 0) {
                    ws.sendBinaryFixture("ws/auth-ok.b64")
                    ws.close(1000, "bye")
                }
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(2_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Disconnected }) {
                    delay(10)
                }
            }
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.Disconnected>()).hasSize(1)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net10_httpFailuresMapToRecoverableFailuresWithoutLeakingToken() = runBlocking {
        withServer { server ->
            server.enqueueHttpResponse("/room/v1/Room/room_init", 429, """{"code":-412,"message":"too many"}""")
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val failure = runCatching { session.start() }.exceptionOrNull()
            assertThat(failure).isInstanceOf(RateLimitedFailure::class.java)
            assertThat(server.takeRequest().path).contains("id=1234")
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun net11_closeStopsFurtherEvents() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, clientMessage ->
                if (clientMessage.size > 0) {
                    ws.sendBinaryFixture("ws/auth-ok.b64")
                    ws.close(1000, "done")
                }
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            session.close()
            val countAfterClose = events.size
            delay(100)
            assertThat(events.size).isEqualTo(countAfterClose)
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net12_invalidWebSocketUrlBuilderFallsBackToLaterHosts() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures(
                danmuInfoBody = """
                    {"code":0,"message":"OK","ttl":1,"data":{"token":"fixture-token-not-secret","host_list":[
                      {"host":"invalid.example.com","wss_port":443},
                      {"host":"broadcastlv.chat.bilibili.com","wss_port":443}
                    ]}}
                """.trimIndent(),
            )
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server) { host, port ->
                if (host == "invalid.example.com") {
                    throw IllegalArgumentException("reject")
                }
                server.url("/ws/$host/$port")
            }
            val session = gateway.createSession(1234L)
            session.start()
            assertThat(server.requestCount).isEqualTo(4)
            session.close()
        }
    }

    @Test
    fun net13_authFailureRetriesTheNextHostAndEventuallyConnects() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures(
                danmuInfoBody = """
                    {"code":0,"message":"OK","ttl":1,"data":{"token":"fixture-token-not-secret","host_list":[
                      {"host":"broadcastlv.chat.bilibili.com","wss_port":443},
                      {"host":"tx-bj-live-comet-01.chat.bilibili.com","wss_port":8443}
                    ]}}
                """.trimIndent(),
            )
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinary(
                    codec.encode(
                        BiliPacket(
                            packetLength = 26,
                            headerLength = 16,
                            protocolVersion = 1,
                            operation = BiliPacket.OP_AUTH_REPLY,
                            sequence = 1,
                            body = """{"code":1}""".toByteArray(),
                        ),
                    ),
                )
                ws.close(1000, "auth failed")
            }
            server.enqueueWsScript("/ws/tx-bj-live-comet-01.chat.bilibili.com/8443") { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server) { host, port -> server.url("/ws/$host/$port") }
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.HostConnecting>()).hasSize(2)
            assertThat(server.requestCount).isEqualTo(5)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net14_protocolUnsupportedFrameFailsFast() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures(
                danmuInfoBody = """
                    {"code":0,"message":"OK","ttl":1,"data":{"token":"fixture-token-not-secret","host_list":[
                      {"host":"broadcastlv.chat.bilibili.com","wss_port":443}
                    ]}}
                """.trimIndent(),
            )
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinary(
                    codec.encode(
                        BiliPacket(
                            packetLength = 20,
                            headerLength = 16,
                            protocolVersion = 9,
                            operation = BiliPacket.OP_MESSAGE,
                            sequence = 1,
                            body = byteArrayOf(1, 2, 3, 4),
                        ),
                    ),
                )
            }
            val gateway = gateway(server) { host, port -> server.url("/ws/$host/$port") }
            val session = gateway.createSession(1234L)
            try {
                val failure = runCatching { session.start() }.exceptionOrNull()
                assertThat(failure).isInstanceOf(java.io.IOException::class.java)
                assertThat(server.requestCount).isEqualTo(4)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun net15_sendHeartbeatRejectsClosedWebSocket() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "bye")
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            session.start()
            session.close()
            assertThat(runCatching { session.sendHeartbeat() }.exceptionOrNull())
                .isInstanceOf(ConnectionLostFailure::class.java)
        }
    }

    @Test
    fun net16_unknownLiveStatusMapsToLiveStatusUnknown() = runBlocking {
        withServer { server ->
            val roomInitBody = """{"code":0,"message":"OK","ttl":1,"data":{"room_id":987654,"live_status":99}}"""
            server.enqueueHttpFixtures(roomInitBody = roomInitBody)
            server.enqueueWsScript(
                path = "/ws/broadcastlv.chat.bilibili.com/443",
                roomInitBody = roomInitBody,
            ) { ws, _ ->
                ws.sendBinaryFixture("ws/auth-ok.b64")
                ws.close(1000, "done")
            }
            val gateway = gateway(server)
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(1_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.RoomResolved }) {
                    delay(10)
                }
            }
            val roomResolved = events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.RoomResolved>().single()
            assertThat(roomResolved.roomInfo.liveStatus).isEqualTo(cn.danmaku.anchor.model.LiveStatus.UNKNOWN)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net17_malformedFrameIsSkippedWithoutKillingStream() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, clientMessage ->
                if (clientMessage.size > 0) {
                    ws.sendBinaryFixture("ws/auth-ok.b64")
                    ws.sendBinary(byteArrayOf(0, 0, 0, 10, 0, 16, 0, 1, 0, 0, 0, 5, 0, 0, 0, 1, 0, 0, 0, 0))
                    ws.sendBinaryFixture("ws/danmaku-plain.b64")
                    ws.close(1000, "done")
                }
            }
            val diagnostics = SafeDiagnostics()
            val gateway = BiliLiveGateway(
                client = OkHttpClient.Builder().build(),
                roomApi = roomApi(server),
                diagnostics = diagnostics,
                webSocketUrlBuilder = { host, port -> server.url("/ws/$host/$port") },
            )
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(2_000) {
                while (events.count { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Message } < 1) {
                    delay(10)
                }
            }
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.Message>()).hasSize(1)
            assertThat(diagnostics.snapshot()["frame_malformed"]).isEqualTo(1L)
            session.close()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun net18_sessionLifetimeExpiryEmitsDisconnectedForReconnect() = runBlocking {
        withServer { server ->
            server.enqueueHttpFixtures()
            server.enqueueWsScript("/ws/broadcastlv.chat.bilibili.com/443") { ws, clientMessage ->
                if (clientMessage.size > 0) {
                    ws.sendBinaryFixture("ws/auth-ok.b64")
                }
            }
            val gateway = BiliLiveGateway(
                client = OkHttpClient.Builder().build(),
                roomApi = roomApi(server),
                webSocketUrlBuilder = { host, port -> server.url("/ws/$host/$port") },
                sessionLifetimeMillis = 300L,
            )
            val session = gateway.createSession(1234L)
            val events = mutableListOf<cn.danmaku.anchor.domain.gateway.GatewayEvent>()
            val collector = launch { session.events.collect { events += it } }
            session.start()
            withTimeout(3_000) {
                while (events.none { it is cn.danmaku.anchor.domain.gateway.GatewayEvent.Disconnected }) {
                    delay(10)
                }
            }
            assertThat(events.filterIsInstance<cn.danmaku.anchor.domain.gateway.GatewayEvent.Disconnected>()).hasSize(1)
            session.close()
            collector.cancelAndJoin()
        }
    }

    private fun gateway(server: MockWebServer, wsBuilder: (String, Int) -> okhttp3.HttpUrl = { host, port -> server.url("/ws/$host/$port") }): BiliLiveGateway {
        val client = OkHttpClient.Builder().build()
        return BiliLiveGateway(
            client = client,
            roomApi = roomApi(server),
            webSocketUrlBuilder = wsBuilder,
        )
    }

    private fun roomApi(server: MockWebServer): BiliRoomApi =
        BiliRoomApi(
            client = OkHttpClient.Builder().build(),
            baseHttpUrl = server.url("/"),
            wbiNavUrl = server.url("/x/web-interface/nav"),
        )

    private fun MockWebServer.enqueueHttpFixtures(
        roomInitBody: String = readFixtureText("http/room-init-valid-short.json"),
        danmuInfoBody: String = readFixtureText("http/danmu-info-valid.json"),
    ) {
        dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when {
                        request.path?.startsWith("/x/web-interface/nav") == true -> wbiNavResponse()

                        request.path?.startsWith("/room/v1/Room/room_init") == true -> MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(roomInitBody)

                        request.path?.startsWith("/xlive/web-room/v1/index/getDanmuInfo") == true -> MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(danmuInfoBody)

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    private fun MockWebServer.enqueueHttpResponse(pathPrefix: String, code: Int, body: String) {
        dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return if (request.path?.startsWith(pathPrefix) == true) {
                        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
                    } else if (request.path?.startsWith("/x/web-interface/nav") == true) {
                        wbiNavResponse()
                    } else {
                        MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    private fun MockWebServer.enqueueWsScript(
        path: String,
        roomInitBody: String = readFixtureText("http/room-init-valid-short.json"),
        danmuInfoBody: String = readFixtureText("http/danmu-info-valid.json"),
        script: (WebSocket, ByteString) -> Unit,
    ) {
        dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when {
                        request.path?.startsWith(path) == true -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                                Unit
                            }

                            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                                script(webSocket, bytes)
                            }
                        })

                        request.path?.startsWith("/x/web-interface/nav") == true -> wbiNavResponse()

                        request.path?.startsWith("/room/") == true || request.path?.startsWith("/xlive/") == true -> MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                if (request.path?.startsWith("/room/") == true) {
                                    roomInitBody
                                } else {
                                    danmuInfoBody
                                },
                            )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
    }

    private fun WebSocket.sendBinaryFixture(path: String) {
        send(ByteString.of(*readBase64Fixture(path)))
    }

    private fun WebSocket.sendBinary(payload: ByteArray) {
        send(ByteString.of(*payload))
    }

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            server.forceCloseActiveConnections()
            server.drainRecordedRequests()
            runCatching { server.shutdown() }
        }
    }

    private fun MockWebServer.drainRecordedRequests() {
        while (runCatching { takeRequest(50, TimeUnit.MILLISECONDS) }.getOrNull() != null) {
            Unit
        }
    }

    private fun MockWebServer.forceCloseActiveConnections() {
        forceCloseField("openClientSockets")
        forceCloseField("openConnections")
    }

    private fun MockWebServer.forceCloseField(fieldName: String) {
        runCatching {
            val field = MockWebServer::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(this)
            when (value) {
                is Collection<*> -> {
                    value.forEach { closeSilently(it) }
                    runCatching { (value as MutableCollection<*>).clear() }
                }
                else -> closeSilently(value)
            }
        }
    }

    private fun closeSilently(value: Any?) {
        when (value) {
            is java.io.Closeable -> runCatching { value.close() }
            is AutoCloseable -> runCatching { value.close() }
            is java.net.Socket -> runCatching { value.close() }
            null -> Unit
            else -> runCatching {
                value.javaClass.getMethod("close").invoke(value)
            }
        }
    }
}
