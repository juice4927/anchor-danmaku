package cn.danmaku.anchor

import android.content.Context
import cn.danmaku.anchor.domain.session.SessionController
import cn.danmaku.anchor.domain.time.SystemClock
import cn.danmaku.anchor.data.AndroidConnectivityObserver
import cn.danmaku.anchor.data.PreferencesRepository
import cn.danmaku.anchor.data.PreferencesStore
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
    private val httpClient = OkHttpClient.Builder().build()
    private val roomApi = BiliRoomApi(httpClient)
    private val liveGateway = BiliLiveGateway(httpClient, roomApi)
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
