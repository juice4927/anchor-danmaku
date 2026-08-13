package cn.danmaku.anchor.protocol.bili

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface BiliWebSocketEvent {
    data object Opened : BiliWebSocketEvent
    data class BinaryFrame(val payload: ByteArray) : BiliWebSocketEvent
    data class Closing(val code: Int, val reason: String) : BiliWebSocketEvent
    data class Closed(val code: Int, val reason: String) : BiliWebSocketEvent
    data class Failure(val throwable: Throwable, val response: Response?) : BiliWebSocketEvent
}

class BiliWebSocketSession private constructor(
    val request: Request,
    private val webSocket: WebSocket,
    private val eventChannel: Channel<BiliWebSocketEvent>,
) {
    val events: Flow<BiliWebSocketEvent> = eventChannel.receiveAsFlow()

    fun send(payload: ByteArray): Boolean = webSocket.send(ByteString.of(*payload))

    fun close(code: Int = 1000, reason: String = "normal"): Boolean = webSocket.close(code, reason)

    fun cancel() {
        webSocket.cancel()
    }

    companion object {
        /**
         * 事件通道容量上限。消费端（解码协程）暂时落后时不再无界堆积原始帧：
         * 直播场景下最新内容优先，超限时丢弃最旧事件。若关闭/失败事件恰好被丢弃，
         * SessionController 的 90 秒 idle 看门狗仍会兜底触发重连，不会永久悬挂。
         */
        private const val EVENT_BUFFER_CAPACITY = 256

        suspend fun connect(client: OkHttpClient, request: Request): BiliWebSocketSession {
            val events = Channel<BiliWebSocketEvent>(
                capacity = EVENT_BUFFER_CAPACITY,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            return suspendCancellableCoroutine { continuation ->
                val openSignal = CompletableDeferred<Unit>()
                var socketRef: WebSocket? = null
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socketRef = webSocket
                        events.trySend(BiliWebSocketEvent.Opened)
                        if (!openSignal.isCompleted) {
                            openSignal.complete(Unit)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        events.trySend(BiliWebSocketEvent.BinaryFrame(bytes.toByteArray()))
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        events.trySend(BiliWebSocketEvent.Closing(code, reason))
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        events.trySend(BiliWebSocketEvent.Closed(code, reason))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        events.trySend(BiliWebSocketEvent.Failure(t, response))
                        if (!openSignal.isCompleted) {
                            openSignal.completeExceptionally(t)
                        }
                    }
                }
                val socket = client.newWebSocket(request, listener)
                continuation.invokeOnCancellation {
                    socket.cancel()
                }
                openSignal.invokeOnCompletion { error ->
                    if (error != null) {
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(error)
                        }
                    } else if (!continuation.isCompleted) {
                        continuation.resume(BiliWebSocketSession(request, socket, events))
                    }
                }
            }
        }
    }
}
