package cn.danmaku.anchor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import cn.danmaku.anchor.service.ConnectionForegroundService
import cn.danmaku.anchor.ui.AppNavigation
import cn.danmaku.anchor.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {
    private var pendingConnectAction: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingConnectAction?.invoke()
            pendingConnectAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnchorTheme {
                AppNavigation(
                    container = appContainer,
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
