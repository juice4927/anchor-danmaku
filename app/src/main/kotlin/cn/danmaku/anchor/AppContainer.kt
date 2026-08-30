package cn.danmaku.anchor

import android.content.Context
import cn.danmaku.anchor.domain.session.SessionController
import cn.danmaku.anchor.domain.time.SystemClock
import cn.danmaku.anchor.data.AndroidConnectivityObserver
import cn.danmaku.anchor.data.BiliRoomMetadataSource
import cn.danmaku.anchor.data.PreferencesRepository
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.data.RoomMetadataSource
import cn.danmaku.anchor.debug.VariantDemoSourceFactory
import cn.danmaku.anchor.protocol.bili.BiliLiveGateway
import cn.danmaku.anchor.protocol.bili.BiliRoomApi
import cn.danmaku.anchor.reminder.AndroidReminderSink
import cn.danmaku.anchor.reminder.ReminderSink
import cn.danmaku.anchor.service.NotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(
    context: Context,
) {
    val applicationContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val preferencesRepository: PreferencesStore = PreferencesRepository(applicationContext, appScope)
    val connectivityObserver = AndroidConnectivityObserver(applicationContext)
    val notificationController = NotificationController(applicationContext)
    val reminderSink: ReminderSink = AndroidReminderSink(applicationContext)
    val demoSource = VariantDemoSourceFactory.create()
    // HTTP 与 WebSocket 各自预配置超时，组件不再各自 newBuilder，避免多份连接池配置漂移。
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val webSocketClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // WS 静默期可能长达 30 秒（心跳间隔），readTimeout 交给应用层 idle 检测。
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val roomApi = BiliRoomApi(httpClient)
    val roomMetadataSource: RoomMetadataSource = BiliRoomMetadataSource(roomApi)
    private val liveGateway = BiliLiveGateway(webSocketClient, roomApi)
    private val coreSessionController = SessionController(
        gateway = liveGateway,
        connectivity = connectivityObserver,
        clock = SystemClock,
        dispatcher = Dispatchers.IO,
    )
    val sessionRepository = AnchorSessionRepository(
        scope = appScope,
        preferencesRepository = preferencesRepository,
        reminderSink = reminderSink,
        demoSource = demoSource,
        coreSessionController = coreSessionController,
    )
}
