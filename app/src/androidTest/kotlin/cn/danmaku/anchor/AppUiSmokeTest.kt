package cn.danmaku.anchor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.RoomMetadata
import cn.danmaku.anchor.ui.UiTags
import cn.danmaku.anchor.ui.about.AboutPage
import cn.danmaku.anchor.ui.about.AboutScreen
import cn.danmaku.anchor.ui.connect.ConnectScreen
import cn.danmaku.anchor.ui.connect.ConnectUiState
import cn.danmaku.anchor.ui.room.RoomScreen
import cn.danmaku.anchor.ui.room.RoomUiState
import cn.danmaku.anchor.ui.settings.SettingsScreen
import cn.danmaku.anchor.ui.settings.SettingsUiState
import cn.danmaku.anchor.ui.theme.AnchorTheme
import org.junit.Rule
import org.junit.Test

class AppUiSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectScreenRendersCoreNodes() {
        composeRule.setContent {
            AnchorTheme {
                ConnectScreen(
                    state = ConnectUiState(
                        roomInput = "987654",
                        recentRooms = listOf(RoomMetadata(roomId = 987654L, ownerName = "某主播", liveStatus = LiveStatus.LIVE)),
                        demoAvailable = true,
                        demoLabel = "回放演示",
                    ),
                    onInputChanged = {},
                    onRoomSelected = {},
                    onEnterRoom = {},
                    onOpenSettings = {},
                    onOpenAbout = {},
                )
            }
        }
        composeRule.onNodeWithTag(UiTags.ConnectRoomInput).assertExists()
        composeRule.onNodeWithText("进入弹幕台").assertIsDisplayed()
    }

    @Test
    fun roomScreenRendersMessageList() {
        composeRule.setContent {
            AnchorTheme {
                RoomScreen(
                    state = RoomUiState(
                        connectionState = AnchorConnectionState(phase = ConnectionPhase.Connected, roomId = 987654L),
                        messages = listOf(
                            LiveMessage.DanmakuMessage(
                                id = "1",
                                roomId = 987654L,
                                uid = 10001L,
                                userName = "测试观众",
                                serverTimestampMillis = 1L,
                                receivedAtMillis = 1L,
                                text = "hello",
                                medalName = null,
                                medalLevel = null,
                            ),
                        ),
                    ),
                    onBackConfirmed = {},
                    onOpenSettings = {},
                    onPauseToggle = {},
                    onClear = {},
                    onJumpToBottom = {},
                    onScrolledAway = {},
                    onRetry = {},
                    onDismissPinned = {},
                )
            }
        }
        composeRule.onNodeWithTag(UiTags.RoomMessageList).assertExists()
    }

    @Test
    fun settingsScreenRendersRoot() {
        composeRule.setContent {
            AnchorTheme {
                SettingsScreen(
                    state = SettingsUiState(AnchorUserPreferences()),
                    onNavigateUp = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    onFontSizeChanged = {},
                    onMaxMessagesChanged = {},
                    onKeepScreenOnChanged = {},
                    onSoundChanged = {},
                    onVibrationChanged = {},
                    onGiftThresholdChanged = { _, _ -> },
                    onAddKeyword = {},
                    onRemoveKeyword = {},
                    onRemoveBlockedUser = {},
                    onClearBlockedUsers = {},
                    onRemoveRecentRoom = {},
                    onClearRecentRooms = {},
                )
            }
        }
        composeRule.onNodeWithTag(UiTags.SettingsRoot).assertExists()
    }

    @Test
    fun aboutScreenRendersNonOfficialNotice() {
        composeRule.setContent {
            AnchorTheme {
                AboutScreen(page = AboutPage.About, onNavigateUp = {})
            }
        }
        composeRule.onNode(hasText("非官方第三方工具", substring = true)).assertExists()
    }
}
