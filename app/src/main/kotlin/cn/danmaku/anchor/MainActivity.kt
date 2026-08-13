package cn.danmaku.anchor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.service.ConnectionForegroundService
import cn.danmaku.anchor.ui.AppNavigation
import cn.danmaku.anchor.ui.theme.AnchorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingConnectAction: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingConnectAction?.invoke()
            pendingConnectAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkRoomId = deepLinkRoomId(intent)
        applyScreenOrientation()
        setContent {
            AnchorTheme {
                AppNavigation(
                    container = appContainer,
                    initialRoomId = deepLinkRoomId,
                    onConnectRequest = { roomId, useDemo ->
                        requestNotificationPermissionThen {
                            startForegroundService(
                                this,
                                ConnectionForegroundService.connectIntent(
                                    context = this,
                                    roomId = roomId,
                                    demoMode = useDemo,
                                ),
                            )
                        }
                    },
                    onRetryRequest = {
                        startService(ConnectionForegroundService.retryIntent(this))
                    },
                    onStopRequest = {
                        startService(ConnectionForegroundService.stopIntent(this))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun applyScreenOrientation() {
        lifecycleScope.launch {
            appContainer.preferencesRepository.preferences.collect { preferences ->
                requestedOrientation = when (preferences.screenOrientation) {
                    AnchorUserPreferences.SCREEN_ORIENTATION_PORTRAIT ->
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                    AnchorUserPreferences.SCREEN_ORIENTATION_LANDSCAPE ->
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    private fun deepLinkRoomId(intent: Intent?): Long? {
        val data = intent?.data ?: return null
        if (data.scheme != "bilibili" || data.host != "live") return null
        return data.pathSegments.firstOrNull()?.toLongOrNull()
    }

    private fun requestNotificationPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
            return
        }
        pendingConnectAction = action
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
