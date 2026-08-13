package cn.danmaku.anchor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.danmaku.anchor.AppContainer
import cn.danmaku.anchor.ui.about.AboutPage
import cn.danmaku.anchor.ui.about.AboutScreen
import cn.danmaku.anchor.ui.connect.ConnectScreen
import cn.danmaku.anchor.ui.connect.ConnectViewModel
import cn.danmaku.anchor.ui.room.RoomScreen
import cn.danmaku.anchor.ui.room.RoomViewModel
import cn.danmaku.anchor.ui.settings.SettingsScreen
import cn.danmaku.anchor.ui.settings.SettingsViewModel

@Composable
fun AppNavigation(
    container: AppContainer,
    onConnectRequest: (roomId: Long, useDemo: Boolean) -> Unit,
    onRetryRequest: () -> Unit,
    onStopRequest: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val connectViewModel: ConnectViewModel = viewModel(
        factory = container.factory {
            ConnectViewModel(
                preferencesRepository = container.preferencesRepository,
                sessionRepository = container.sessionRepository,
            )
        },
    )
    val roomViewModel: RoomViewModel = viewModel(
        factory = container.factory {
            RoomViewModel(
                sessionRepository = container.sessionRepository,
                preferencesRepository = container.preferencesRepository,
            )
        },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = container.factory {
            SettingsViewModel(container.preferencesRepository)
        },
    )

    NavHost(
        navController = navController,
        startDestination = Routes.Connect,
    ) {
        composable(Routes.Connect) {
            val state by connectViewModel.uiState.collectAsStateWithLifecycle()
            ConnectScreen(
                state = state,
                onInputChanged = connectViewModel::updateInput,
                onRoomSelected = { roomId ->
                    connectViewModel.setInput(roomId.toString())
                },
                onEnterRoom = { useDemo ->
                    connectViewModel.buildConnectRequest(useDemo)?.let { request ->
                        onConnectRequest(request.roomId, request.useDemo)
                        navController.navigate(Routes.Room)
                    }
                },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenAbout = { navController.navigate(Routes.About) },
            )
        }
        composable(Routes.Room) {
            val state by roomViewModel.uiState.collectAsStateWithLifecycle()
            RoomScreen(
                state = state,
                onBackConfirmed = {
                    onStopRequest()
                    navController.popBackStack(Routes.Connect, inclusive = false)
                },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onPauseToggle = roomViewModel::togglePause,
                onClear = roomViewModel::clearFeed,
                onJumpToBottom = roomViewModel::jumpToBottom,
                onScrolledAway = roomViewModel::onAutoFollowDisabled,
                onRetry = onRetryRequest,
                onDismissPinned = roomViewModel::dismissPinned,
            )
        }
        composable(Routes.Settings) {
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onNavigateUp = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.Privacy) },
                onOpenAbout = { navController.navigate(Routes.About) },
                onFontSizeChanged = settingsViewModel::updateFontSize,
                onMaxMessagesChanged = settingsViewModel::updateMaxMessages,
                onKeepScreenOnChanged = settingsViewModel::updateKeepScreenOn,
                onSoundChanged = settingsViewModel::updateSoundEnabled,
                onVibrationChanged = settingsViewModel::updateVibrationEnabled,
                onGiftThresholdChanged = settingsViewModel::updateGiftThresholds,
                onAddKeyword = settingsViewModel::addKeyword,
                onRemoveKeyword = settingsViewModel::removeKeyword,
                onRemoveBlockedUser = settingsViewModel::removeBlockedUser,
                onClearBlockedUsers = settingsViewModel::clearBlockedUsers,
                onRemoveRecentRoom = settingsViewModel::removeRecentRoom,
                onClearRecentRooms = settingsViewModel::clearRecentRooms,
            )
        }
        composable(Routes.About) {
            AboutScreen(
                page = AboutPage.About,
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(Routes.Privacy) {
            AboutScreen(
                page = AboutPage.Privacy,
                onNavigateUp = { navController.popBackStack() },
            )
        }
    }
}

private fun AppContainer.factory(
    producer: () -> ViewModel,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = producer() as T
}

private object Routes {
    const val Connect = "connect"
    const val Room = "room"
    const val Settings = "settings"
    const val About = "about"
    const val Privacy = "privacy"
}
