package cn.danmaku.anchor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.ui.room.RoomScreen
import cn.danmaku.anchor.ui.room.RoomUiState
import cn.danmaku.anchor.ui.theme.AnchorTheme
import cn.danmaku.anchor.ui.UiTags
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule

class AppUiSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomScreenDisplaysAllFourMessageKindsInReplayView() {
        composeRule.setContent {
            AnchorTheme {
                RoomScreen(
                    state = RoomUiState(
                        connectionState = AnchorConnectionState(
                            phase = ConnectionPhase.Reconnecting,
                            roomId = 987654L,
                            failureKind = AnchorFailureKind.NetworkUnavailable,
                            reconnectDelaySeconds = 15,
                        ),
                        messages = listOf(
                            LiveMessage.DanmakuMessage(
                                id = "d1",
                                roomId = 987654L,
                                uid = 10001L,
                                userName = "弹幕用户",
                                serverTimestampMillis = 1L,
                                receivedAtMillis = 1L,
                                text = "hello",
                                medalName = null,
                                medalLevel = null,
                            ),
                            LiveMessage.SuperChatMessage(
                                id = "sc1",
                                roomId = 987654L,
                                uid = 10002L,
                                userName = "SC用户",
                                serverTimestampMillis = 2L,
                                receivedAtMillis = 2L,
                                message = "加油",
                                priceCny = Money.fromWholeCny(30),
                                startTimeMillis = 2L,
                                endTimeMillis = 60_002L,
                            ),
                            LiveMessage.GiftMessage(
                                id = "g1",
                                roomId = 987654L,
                                uid = 10003L,
                                userName = "礼物用户",
                                serverTimestampMillis = 3L,
                                receivedAtMillis = 3L,
                                giftName = "小花花",
                                count = 1,
                                totalCoin = 100,
                                coinType = "gold",
                                estimatedCny = Money.fromWholeCny(1),
                            ),
                            LiveMessage.GuardMessage(
                                id = "gu1",
                                roomId = 987654L,
                                uid = 10004L,
                                userName = "舰队用户",
                                serverTimestampMillis = 4L,
                                receivedAtMillis = 4L,
                                guardLevel = 1,
                                count = 1,
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

        composeRule.onNodeWithText("弹幕").assertExists()
        composeRule.onNodeWithText("SC").assertExists()
        composeRule.onNodeWithText("礼物").assertExists()
        composeRule.onNodeWithText("舰队").assertExists()
        composeRule.onNodeWithText("重连中", substring = true).assertExists()
        composeRule.onNodeWithText("15s 后重试", substring = true).assertExists()
    }

    @Test
    fun roomScreenExposesAccessibleActionsAndRetryCallback() {
        var retryCalled = false
        composeRule.setContent {
            AnchorTheme {
                RoomScreen(
                    state = RoomUiState(
                        connectionState = AnchorConnectionState(
                            phase = ConnectionPhase.Reconnecting,
                            roomId = 987654L,
                            reconnectDelaySeconds = 15,
                        ),
                    ),
                    onBackConfirmed = {},
                    onOpenSettings = {},
                    onPauseToggle = {},
                    onClear = {},
                    onJumpToBottom = {},
                    onScrolledAway = {},
                    onRetry = { retryCalled = true },
                    onDismissPinned = {},
                )
            }
        }

        listOf(
            "返回并停止",
            "立即重试",
            "打开设置",
            "暂停",
            "清屏",
            "回到底部",
        ).forEach { label ->
            composeRule.onNodeWithContentDescription(label)
                .assertExists()
            composeRule.onNode(hasContentDescription(label).and(hasClickAction()))
                .assertWidthIsAtLeast(40.dp)
                .assertHeightIsAtLeast(40.dp)
        }

        composeRule.onNodeWithContentDescription("立即重试").performClick()
        composeRule.runOnIdle { assertTrue(retryCalled) }
    }

    @Test
    fun roomScreenAutoFollowPinsNewestMessageAndUserDragDisablesFollow() {
        val messages = mutableStateListOf<LiveMessage>()
        var scrolledAway = false
        composeRule.setContent {
            AnchorTheme {
                RoomScreen(
                    state = RoomUiState(
                        messages = messages,
                        autoFollow = true,
                        fontSizeSp = 16,
                    ),
                    onBackConfirmed = {},
                    onOpenSettings = {},
                    onPauseToggle = {},
                    onClear = {},
                    onJumpToBottom = {},
                    onScrolledAway = { scrolledAway = true },
                    onRetry = {},
                    onDismissPinned = {},
                )
            }
        }

        composeRule.runOnIdle {
            repeat(40) { index ->
                messages += LiveMessage.DanmakuMessage(
                    id = "m$index",
                    roomId = 987654L,
                    uid = 10000L + index,
                    userName = "用户$index",
                    serverTimestampMillis = index.toLong(),
                    receivedAtMillis = index.toLong(),
                    text = "消息 $index",
                    medalName = null,
                    medalLevel = null,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("消息 39").assertIsDisplayed()

        composeRule.runOnIdle {
            messages += LiveMessage.DanmakuMessage(
                id = "m40",
                roomId = 987654L,
                uid = 10040L,
                userName = "用户40",
                serverTimestampMillis = 40L,
                receivedAtMillis = 40L,
                text = "最新消息",
                medalName = null,
                medalLevel = null,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("最新消息").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse("新增消息不应关闭自动跟随", scrolledAway)
        }

        composeRule.onNodeWithTag(UiTags.RoomMessageList).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue("用户拖动应关闭自动跟随", scrolledAway)
        }
    }
}
