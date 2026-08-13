package cn.danmaku.anchor.domain.session

import cn.danmaku.anchor.domain.gateway.GatewayDiagnostics
import cn.danmaku.anchor.domain.gateway.GatewayEvent
import cn.danmaku.anchor.domain.gateway.GatewaySession
import cn.danmaku.anchor.domain.gateway.LiveGateway
import cn.danmaku.anchor.domain.time.Clock
import cn.danmaku.anchor.model.ConnectionFailure
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.RoomInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerSes01ToSes12Test {

    @Test
    fun `SES-01 normal lifecycle reaches connected state in order`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(session)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )
        val states = mutableListOf<SessionState>()
        val collectJob = backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            controller.state.drop(1).take(4).toList(states)
        }

        controller.start(1234L)
        scheduler.drainCurrent()

        assertThat(states.map { it::class.simpleName }).containsExactly(
            "Resolving",
            "Connecting",
            "Authenticating",
            "Connected",
        ).inOrder()
        collectJob.cancel()
    }

    @Test
    fun `SES-02 starting another room stops previous session before reconnecting`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler, realRoomId = 555L)
        val gateway = QueueGateway(ArrayDeque(listOf(first, second)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        controller.start(5678L)
        scheduler.drainCurrent()

        assertThat(first.closed).isTrue()
        assertThat(gateway.createdRoomIds).containsExactly(1234L, 5678L).inOrder()
        assertThat((controller.state.value as SessionState.Connected).roomInfo.roomId).isEqualTo(555L)
    }

    @Test
    fun `SES-03 stage timeout transitions into reconnecting without orphan jobs`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val hungSession = FakeGatewaySession(scheduler) {
            awaitCancellation()
        }
        val followUp = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(hungSession, followUp)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(10_000L)
        scheduler.drainCurrent()

        val reconnecting = controller.state.value as SessionState.Reconnecting
        assertThat(reconnecting.failure).isEqualTo(ConnectionFailure.UnknownRecoverable("stage-timeout"))
        assertThat(hungSession.closed).isTrue()

        scheduler.advanceTimeBy(1_000L)
        scheduler.drainCurrent()
        assertThat(gateway.createdRoomIds.size).isEqualTo(2)
    }

    @Test
    fun `SES-04 connected sessions send heartbeats every 30 seconds and stop cleanly`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val controller = SessionController(
            gateway = QueueGateway(ArrayDeque(listOf(session))),
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(60_000L)
        scheduler.drainCurrent()

        assertThat(session.heartbeatAtMillis).containsExactly(30_000L, 60_000L).inOrder()

        controller.stop()
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(60_000L)
        scheduler.drainCurrent()

        assertThat(session.heartbeatAtMillis).containsExactly(30_000L, 60_000L).inOrder()
    }

    @Test
    fun `SES-05 idle sessions reconnect after 90 seconds without any frames`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val controller = SessionController(
            gateway = QueueGateway(ArrayDeque(listOf(first, second))),
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(89_999L)
        scheduler.drainCurrent()
        assertThat(first.closed).isFalse()

        scheduler.advanceTimeBy(1L)
        scheduler.drainCurrent()

        assertThat(first.closed).isTrue()
        assertThat((controller.state.value as SessionState.Reconnecting).failure).isEqualTo(ConnectionFailure.ConnectionLost)
    }

    @Test
    fun `SES-06 reconnect backoff follows frozen sequence`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val sessions = List(8) { connectedSession(scheduler) }
        val gateway = QueueGateway(ArrayDeque(sessions))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()

        val expectedDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L)
        expectedDelays.forEachIndexed { index, expected ->
            sessions[index].emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
            scheduler.drainCurrent()
            assertThat((controller.state.value as SessionState.Reconnecting).currentDelayMillis).isEqualTo(expected)
            scheduler.advanceTimeBy(expected - 1L)
            scheduler.drainCurrent()
            assertThat(gateway.createdRoomIds.size).isEqualTo(index + 1)
            scheduler.advanceTimeBy(1L)
            scheduler.drainCurrent()
            assertThat(gateway.createdRoomIds.size).isEqualTo(index + 2)
        }
    }

    @Test
    fun `SES-07 connectivity loss pauses backoff and recovery retries immediately`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val connectivity = FakeConnectivityObserver()
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(first, second)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = connectivity,
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        first.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        scheduler.drainCurrent()
        connectivity.setConnected(false)
        scheduler.advanceTimeBy(60_000L)
        scheduler.drainCurrent()

        assertThat(gateway.createdRoomIds.size).isEqualTo(1)

        connectivity.setConnected(true)
        scheduler.drainCurrent()

        assertThat(gateway.createdRoomIds.size).isEqualTo(2)
    }

    @Test
    fun `SES-08 stable 30 second connection resets reconnect attempts`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val third = connectedSession(scheduler)
        val controller = SessionController(
            gateway = QueueGateway(ArrayDeque(listOf(first, second, third))),
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        first.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(1_000L)
        scheduler.drainCurrent()

        scheduler.advanceTimeBy(30_000L)
        scheduler.drainCurrent()
        second.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        scheduler.drainCurrent()

        assertThat((controller.state.value as SessionState.Reconnecting).currentDelayMillis).isEqualTo(1_000L)
    }

    @Test
    fun `SES-09 retry now cancels countdown and creates a new session immediately`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(first, second)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        first.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        scheduler.drainCurrent()

        controller.retryNow()
        scheduler.drainCurrent()

        assertThat(gateway.createdRoomIds.size).isEqualTo(2)
        assertThat(controller.state.value).isInstanceOf(SessionState.Connected::class.java)
    }

    @Test
    fun `SES-10 stop cancels session work within one logical second`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val session = connectedSession(scheduler)
        val controller = SessionController(
            gateway = QueueGateway(ArrayDeque(listOf(session))),
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        controller.stop()
        scheduler.advanceTimeBy(1_000L)
        scheduler.drainCurrent()

        assertThat(session.closed).isTrue()
        assertThat(controller.state.value).isEqualTo(SessionState.Stopped(roomId = 1234L))
    }

    @Test
    fun `SES-11 reconnect state exposes disconnect duration and missed message hint`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val controller = SessionController(
            gateway = QueueGateway(ArrayDeque(listOf(first, second))),
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        first.emit(GatewayEvent.Disconnected(ConnectionFailure.ConnectionLost))
        scheduler.drainCurrent()
        val reconnecting = controller.state.value as SessionState.Reconnecting
        scheduler.advanceTimeBy(2_500L)

        assertThat(reconnecting.mayHaveMissedMessages).isTrue()
        assertThat(reconnecting.disconnectedForMillis(scheduler.currentTime)).isEqualTo(2_500L)
    }

    @Test
    fun `SES-12 host exhaustion waits for global backoff and does not busy loop`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val first = connectedSession(scheduler)
        val second = connectedSession(scheduler)
        val gateway = QueueGateway(ArrayDeque(listOf(first, second)))
        val controller = SessionController(
            gateway = gateway,
            connectivity = FakeConnectivityObserver(),
            clock = SchedulerClock(scheduler),
            dispatcher = dispatcher,
        )

        controller.start(1234L)
        scheduler.drainCurrent()
        first.emit(GatewayEvent.Disconnected(ConnectionFailure.HostRejected(host = "broadcastlv.chat.bilibili.com")))
        scheduler.drainCurrent()
        scheduler.advanceTimeBy(999L)
        scheduler.drainCurrent()

        assertThat(gateway.createdRoomIds.size).isEqualTo(1)

        scheduler.advanceTimeBy(1L)
        scheduler.drainCurrent()
        assertThat(gateway.createdRoomIds.size).isEqualTo(2)
    }

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

        override val events = flow

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
