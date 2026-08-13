package cn.danmaku.anchor.domain.session

import cn.danmaku.anchor.domain.gateway.GatewayDiagnostics
import cn.danmaku.anchor.domain.gateway.GatewayEvent
import cn.danmaku.anchor.domain.gateway.GatewayFailureException
import cn.danmaku.anchor.domain.gateway.GatewaySession
import cn.danmaku.anchor.domain.gateway.LiveGateway
import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.RoomInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerEdgeCasesTest {

    @Test
    fun `session controller marks invalid room input as fatal without creating a session`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val gateway = QueueGateway(ArrayDeque<FakeGatewaySession>())
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(0L)
        scheduler.drainCurrent()

        assertThat(controller.state.value).isEqualTo(
            SessionState.Fatal(
                inputRoomId = 0L,
                failure = ConnectionFailure.InvalidRoomInput,
            ),
        )
        assertThat(gateway.createdRoomIds).isEmpty()
    }

    @Test
    fun `session controller ignores retry now after stop`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(session)))
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(1234L)
        scheduler.drainCurrent()
        controller.stop()
        scheduler.drainCurrent()
        controller.retryNow()
        scheduler.drainCurrent()

        assertThat(gateway.createdRoomIds).containsExactly(1234L).inOrder()
        assertThat(controller.state.value).isEqualTo(SessionState.Stopped(roomId = 1234L))
    }

    @Test
    fun `session controller maps gateway factory failures into recoverable failures`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val gateway = object : LiveGateway {
            override fun createSession(inputRoomId: Long): GatewaySession {
                throw GatewayFailureException(ConnectionFailure.HostRejected(host = "test-host"))
            }
        }
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(1234L)
        scheduler.drainCurrent()

        assertThat(controller.state.value).isEqualTo(
            SessionState.Fatal(
                inputRoomId = 1234L,
                failure = ConnectionFailure.UnknownRecoverable(reason = "session-create:HostRejected(host=test-host, reason=null)"),
            ),
        )
    }

    @Test
    fun `session controller maps session start failures into unknown recoverable failures`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val failed = FakeGatewaySession(scheduler) {
            throw IllegalStateException("boom")
        }
        val followUp = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(failed, followUp)))
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(1234L)
        scheduler.drainCurrent()

        assertThat(controller.state.value).isInstanceOf(SessionState.Reconnecting::class.java)
        assertThat((controller.state.value as SessionState.Reconnecting).failure).isEqualTo(
            ConnectionFailure.UnknownRecoverable(reason = "boom"),
        )
    }

    @Test
    fun `session controller keeps connected state updated from post connect gateway events`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(session)))
        val controller = controller(scheduler, dispatcher, gateway)
        val messages = mutableListOf<LiveMessage>()
        val collectJob = backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            controller.events.take(1).toList(messages)
        }

        controller.start(1234L)
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(5_000L)

        session.emit(GatewayEvent.HostConnecting(host = "late", port = 443, attempt = 2))
        session.emit(GatewayEvent.Authenticating)
        session.emit(GatewayEvent.FrameReceived(receivedAtMillis = 0L))
        session.emit(GatewayEvent.Message(value = danmakuMessage(receivedAtMillis = 0L)))
        session.emit(GatewayEvent.Popularity(value = 42L))
        session.emit(GatewayEvent.DiagnosticsUpdated(GatewayDiagnostics(unknownCommandCount = 2)))
        scheduler.drainCurrent()

        val state = controller.state.value as SessionState.Connected
        assertThat(state.lastFrameAtMillis).isEqualTo(5_000L)
        assertThat(state.popularity).isEqualTo(42L)
        assertThat(state.diagnostics.unknownCommandCount).isEqualTo(2)
        assertThat(messages).hasSize(1)
        collectJob.cancel()
    }

    @Test
    fun `session controller ignores resolving events after connection`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(session)))
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(1234L)
        scheduler.drainCurrent()
        session.emit(GatewayEvent.Resolving)
        scheduler.drainCurrent()

        assertThat(controller.state.value).isInstanceOf(SessionState.Connected::class.java)
    }

    @Test
    fun `session controller refreshes reconnecting diagnostics after disconnect`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(session)))
        val controller = controller(scheduler, dispatcher, gateway)

        controller.start(1234L)
        scheduler.drainCurrent()
        session.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        session.emit(GatewayEvent.DiagnosticsUpdated(GatewayDiagnostics(unknownCommandCount = 7)))
        scheduler.drainCurrent()

        val reconnecting = controller.state.value as SessionState.Reconnecting
        assertThat(reconnecting.diagnostics.unknownCommandCount).isEqualTo(7)
    }

    @Test
    fun `reconnect policy returns the first delay for non positive attempts`() {
        val policy = ReconnectPolicy()

        assertThat(policy.delayMillisForAttempt(0)).isEqualTo(1_000L)
        assertThat(policy.delayMillisForAttempt(-1)).isEqualTo(1_000L)
    }

    private fun controller(
        scheduler: TestCoroutineScheduler,
        dispatcher: CoroutineDispatcher,
        gateway: LiveGateway,
    ) = SessionController(
        gateway = gateway,
        connectivity = FakeConnectivityObserver(),
        clock = SchedulerClock(scheduler),
        dispatcher = dispatcher,
    )

    private fun TestCoroutineScheduler.drainCurrent(passes: Int = 6) {
        repeat(passes) {
            runCurrent()
        }
    }

    private fun connectedSession(
        scheduler: TestCoroutineScheduler,
        realRoomId: Long = 987654L,
    ): FakeGatewaySession = FakeGatewaySession(scheduler) {
        emit(GatewayEvent.RoomResolved(RoomInfo(inputRoomId = 1234L, roomId = realRoomId, liveStatus = LiveStatus.LIVE)))
        emit(GatewayEvent.HostConnecting(host = "broadcastlv.chat.bilibili.com", port = 443, attempt = 1))
        emit(GatewayEvent.Authenticating)
    }

    private fun danmakuMessage(receivedAtMillis: Long): LiveMessage.DanmakuMessage = LiveMessage.DanmakuMessage(
        id = "message-1",
        roomId = 987654L,
        uid = 1L,
        userName = "u1",
        serverTimestampMillis = null,
        receivedAtMillis = receivedAtMillis,
        text = "hello",
        medalName = null,
        medalLevel = null,
        repeatCount = 1,
    )

    private class QueueGateway(
        private val sessions: ArrayDeque<FakeGatewaySession>,
    ) : LiveGateway {
        val createdRoomIds = mutableListOf<Long>()

        override fun createSession(inputRoomId: Long): GatewaySession {
            createdRoomIds += inputRoomId
            check(sessions.isNotEmpty()) { "No scripted session left for roomId=$inputRoomId, created=$createdRoomIds" }
            return sessions.removeFirst()
        }
    }

    private class FakeGatewaySession(
        private val scheduler: TestCoroutineScheduler,
        private val onStart: suspend FakeGatewaySession.() -> Unit,
    ) : GatewaySession {
        private val flow = MutableSharedFlow<GatewayEvent>(replay = 8, extraBufferCapacity = 32)
        val heartbeatAtMillis = mutableListOf<Long>()
        var closed = false

        override val events: Flow<GatewayEvent> = flow

        override suspend fun start() {
            onStart()
        }

        override suspend fun sendHeartbeat() {
            heartbeatAtMillis += scheduler.currentTime
        }

        override suspend fun close() {
            closed = true
        }

        suspend fun emit(event: GatewayEvent) {
            flow.emit(event)
        }
    }

    private class FakeConnectivityObserver(
        initialConnected: Boolean = true,
    ) : ConnectivityObserver {
        private val mutableState = MutableStateFlow(initialConnected)
        override val isConnected: StateFlow<Boolean> = mutableState

        fun setConnected(value: Boolean) {
            mutableState.value = value
        }
    }

    private class SchedulerClock(
        private val scheduler: TestCoroutineScheduler,
    ) : Clock {
        override fun nowMillis(): Long = scheduler.currentTime
    }
}
