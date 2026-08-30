package cn.danmaku.anchor.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.ConnectionPhase
import cn.danmaku.anchor.R
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.ui.UiTags
import cn.danmaku.anchor.ui.components.MessageRow
import cn.danmaku.anchor.ui.components.describeMessage
import cn.danmaku.anchor.ui.components.displayName
import cn.danmaku.anchor.ui.theme.BiliBlue
import cn.danmaku.anchor.ui.theme.BiliGiftGold
import cn.danmaku.anchor.ui.theme.BiliGold
import cn.danmaku.anchor.ui.theme.BiliPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/** 自动跟随的滚动信号：跟随开关、是否滚动中、数据总数与最后可见项索引。 */
private data class FollowSignal(
    val autoFollow: Boolean,
    val scrolling: Boolean,
    val total: Int,
    val lastVisible: Int,
)

private data class FocusEventUi(
    val id: String,
    val label: String,
    val title: String,
    val detail: String,
    val accent: Color,
    val meta: String? = null,
)

@Composable
fun RoomScreen(
    state: RoomUiState,
    onBackConfirmed: () -> Unit,
    onOpenSettings: () -> Unit,
    onPauseToggle: () -> Unit,
    onClear: () -> Unit,
    onJumpToBottom: () -> Unit,
    onScrolledAway: () -> Unit,
    onRetry: () -> Unit,
    onDismissPinned: (String) -> Unit,
    onBlockUser: (Long?, String?) -> Unit = { _, _ -> },
) {
    var showConfirm by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<LiveMessage?>(null) }
    val listState = rememberLazyListState()
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current

    // LaunchedEffect(listState) 的闭包只捕获首帧组合时的 state 实例，后续重组不会刷新。
    // rememberUpdatedState 提供对最新值的引用，且是可跟踪的 MutableState，供 snapshotFlow 订阅。
    val currentAutoFollow by rememberUpdatedState(state.autoFollow)

    BackHandler { showConfirm = true }

    LaunchedEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
    }

    // 自动跟随核心：跟随开启时，消息追加或滚动结束后始终对齐到底部最新弹幕。
    // - 用 snapshotFlow 监听「跟随开关 + 是否滚动中 + 数据总数 + 最后可见项」，
    //   不依赖消息 id 重启 effect——高流量下滚动不会被新消息取消；
    // - lastVisible < total - 1 短路：已在底部时不再发起无效滚动，消除滚动抖动。
    LaunchedEffect(listState) {
        snapshotFlow {
            FollowSignal(
                autoFollow = currentAutoFollow,
                scrolling = listState.isScrollInProgress,
                total = listState.layoutInfo.totalItemsCount,
                lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .collect { (autoFollow, scrolling, total, lastVisible) ->
                if (autoFollow && !scrolling && total > 0 && lastVisible < total - 1) {
                    listState.scrollToItem(total - 1)
                }
            }
    }

    // 手势驱动跟随开关（保留回看功能）：
    // - 用户开始拖动即视为离开最新位置，关闭跟随（可回看历史），顶部显示「回到底部 · N 条新消息」；
    // - 松手时若已滚回到底部，恢复跟随。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    if (currentAutoFollow) {
                        onScrolledAway()
                    }
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    if (!currentAutoFollow) {
                        val total = listState.layoutInfo.totalItemsCount
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        if (total > 0 && lastVisible >= total - 1) {
                            onJumpToBottom()
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("停止连接并返回") },
            text = { Text("当前连接将被停止，并返回连接页。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onBackConfirmed()
                }) {
                    Text("停止并返回")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("继续观看")
                }
            },
        )
    }

    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text(message.displayName()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(describeMessage(message))
                    message.uid?.let { uid ->
                        Text(
                            text = "用户ID：$uid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(describeMessage(message)))
                    actionMessage = null
                }) {
                    Text("复制")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { actionMessage = null }) {
                        Text("取消")
                    }
                    if (message.uid != null) {
                        TextButton(onClick = {
                            onBlockUser(message.uid, message.userName)
                            actionMessage = null
                        }) {
                            Text("拉黑该用户")
                        }
                    }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RoomStatusStrip(
                state = state,
                onBack = { showConfirm = true },
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val wideLayout = maxWidth >= 900.dp
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoomMainPanel(
                        state = state,
                        listState = listState,
                        onPauseToggle = onPauseToggle,
                        onClear = onClear,
                        onJumpToBottom = onJumpToBottom,
                        onActionMessage = { actionMessage = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    FocusRail(
                        state = state,
                        onDismissPinned = onDismissPinned,
                        modifier = Modifier
                            .width(236.dp)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoomMainPanel(
                        state = state,
                        listState = listState,
                        onPauseToggle = onPauseToggle,
                        onClear = onClear,
                        onJumpToBottom = onJumpToBottom,
                        onActionMessage = { actionMessage = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    FocusRail(
                        state = state,
                        onDismissPinned = onDismissPinned,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomStatusStrip(
    state: RoomUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(UiTags.RoomTopBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回并停止",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = roomHeaderText(state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(state)
                    state.connectionState.popularity?.let {
                        StatusMetricChip(text = "人气 $it", color = BiliPink)
                    }
                    state.connectionState.liveLabel?.takeIf { it.isNotBlank() }?.let {
                        StatusMetricChip(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.connectionState.reconnectDelaySeconds?.let {
                        StatusMetricChip(
                            text = "${it}s 后重试",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusMetricChip(
                        text = "已收 ${state.receivedCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isPaused) {
                        StatusMetricChip(text = "暂停中", color = BiliPink)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "立即重试",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "打开设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMetricChip(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.26f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 弹幕台顶部标题：优先组合房间标题、主播名与房间号，缺失部分自动省略。 */
private fun roomHeaderText(state: RoomUiState): String {
    val roomId = state.connectionState.roomId
    val parts = listOfNotNull(
        state.roomTitle?.takeIf { it.isNotBlank() },
        state.roomOwnerName?.takeIf { it.isNotBlank() },
        roomId?.let { "房间 $it" },
    )
    return parts.joinToString(separator = " · ").ifBlank { "未连接" }
}

@Composable
private fun RoomMainPanel(
    state: RoomUiState,
    listState: LazyListState,
    onPauseToggle: () -> Unit,
    onClear: () -> Unit,
    onJumpToBottom: () -> Unit,
    onActionMessage: (LiveMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "主消息流",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.autoFollow) "自动跟随最新消息" else "回看模式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${state.messages.size} 条可见消息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isPaused) {
                InfoBanner(
                    text = "已暂停，略过 ${state.skippedCount} 条",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.showJumpButton) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = onJumpToBottom,
                        shape = RoundedCornerShape(50),
                        color = BiliPink,
                        modifier = Modifier.testTag(UiTags.RoomJumpButton),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_south),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "回到底部 · ${state.newMessageCount} 条新消息",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (state.criticalDroppedMessageCount > 0) {
                InfoBanner(
                    text = "消息过载，重要消息可能丢失（${state.criticalDroppedMessageCount} 条）",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(UiTags.RoomDiagnosticBanner),
                )
            }
            if (state.connectionState.phase == ConnectionPhase.Reconnecting &&
                state.connectionState.mayHaveMissedMessages
            ) {
                InfoBanner(
                    text = "连接中断，期间消息可能遗漏，重连后请留意",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(UiTags.RoomMessageList),
                color = Color.Transparent,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(
                            message = message,
                            fontSizeSp = state.fontSizeSp,
                            onLongClick = { onActionMessage(message) },
                        )
                    }
                }
            }
            ConsoleToolbar(
                isPaused = state.isPaused,
                onPauseToggle = onPauseToggle,
                onClear = onClear,
                onJumpToBottom = onJumpToBottom,
            )
        }
    }
}

@Composable
private fun ConsoleToolbar(
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onClear: () -> Unit,
    onJumpToBottom: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier.testTag(UiTags.RoomPauseButton),
                ) {
                    val tint = if (isPaused) BiliPink else MaterialTheme.colorScheme.onSurface
                    if (isPaused) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = "恢复",
                            tint = tint,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = "暂停",
                            tint = tint,
                        )
                    }
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.testTag(UiTags.RoomClearButton),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cleaning_services),
                        contentDescription = "清屏",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = onJumpToBottom,
                    modifier = Modifier.testTag(UiTags.RoomJumpButton),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_south),
                        contentDescription = "回到底部",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusRail(
    state: RoomUiState,
    onDismissPinned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryEvents = state.messages
        .asReversed()
        .filterNot { it is LiveMessage.DanmakuMessage }
        .distinctBy { it.id }
        .take(6)
        .map { it.toFocusEvent() }

    Surface(
        modifier = modifier.heightIn(min = 180.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "重点事件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "优先展示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.pinnedMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.RoomPinnedList),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.pinnedMessages.forEach { pinned ->
                        Surface(
                            onClick = { onDismissPinned(pinned.message.id) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    EventLabelChip(
                                        label = pinnedFocusLabel(pinned.message, pinned.label),
                                        accent = messageAccent(pinned.message),
                                    )
                                    PinnedCountdownLabel(
                                        label = "剩余",
                                        expiresAtMillis = pinned.expiresAtMillis,
                                    )
                                }
                                Text(
                                    text = pinned.message.displayName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = describeMessage(pinned.message),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (secondaryEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    secondaryEvents.forEach { event ->
                        FocusEventCard(event = event)
                    }
                }
            } else if (state.pinnedMessages.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = "暂无 pinned / SC / 舰队 / 礼物事件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventLabelChip(
    label: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FocusEventCard(
    event: FocusEventUi,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventLabelChip(
                    label = event.label,
                    accent = event.accent,
                )
                event.meta?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun messageAccent(message: LiveMessage): Color = when (message) {
    is LiveMessage.SuperChatMessage -> BiliGold
    is LiveMessage.GuardMessage -> BiliBlue
    is LiveMessage.GiftMessage -> BiliGiftGold
    is LiveMessage.DanmakuMessage -> BiliPink
}

private fun pinnedFocusLabel(message: LiveMessage, fallback: String): String = when (message) {
    is LiveMessage.SuperChatMessage -> "SC ¥${message.priceCny.toDisplayString()}"
    is LiveMessage.GuardMessage -> "舰队"
    is LiveMessage.GiftMessage -> message.estimatedCny?.let { "礼物 ¥${it.toDisplayString()}" } ?: "礼物"
    is LiveMessage.DanmakuMessage -> fallback
}

private fun LiveMessage.toFocusEvent(): FocusEventUi = when (this) {
    is LiveMessage.SuperChatMessage -> FocusEventUi(
        id = id,
        label = "SC ¥${priceCny.toDisplayString()}",
        title = displayName(),
        detail = message,
        accent = BiliGold,
        meta = "醒目留言",
    )
    is LiveMessage.GuardMessage -> FocusEventUi(
        id = id,
        label = "舰队",
        title = displayName(),
        detail = "加入或续费舰队，等级 ${guardLevel} ×${count}",
        accent = BiliBlue,
        meta = "等级 ${guardLevel}",
    )
    is LiveMessage.GiftMessage -> FocusEventUi(
        id = id,
        label = estimatedCny?.let { "礼物 ¥${it.toDisplayString()}" } ?: "礼物",
        title = displayName(),
        detail = "${giftName} ×${count}",
        accent = BiliGiftGold,
        meta = estimatedCny?.let { "约¥${it.toDisplayString()}" },
    )
    is LiveMessage.DanmakuMessage -> FocusEventUi(
        id = id,
        label = "消息",
        title = displayName(),
        detail = text,
        accent = BiliPink,
        meta = medalName,
    )
}

@Composable
private fun InfoBanner(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PinnedCountdownLabel(
    label: String,
    expiresAtMillis: Long,
) {
    // 置顶倒计时需要独立于消息流的 1 秒 ticker：安静直播间没有消息事件，
    // 依赖 RoomUiState.nowMillis 会让剩余秒数冻结。
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val remainingSeconds = ((expiresAtMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
    Text(
        text = "$label ${remainingSeconds}s",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusPill(state: RoomUiState) {
    val phase = state.connectionState.phase
    val color = when (phase) {
        ConnectionPhase.Connected -> Color(0xFF4ADE80)
        ConnectionPhase.Reconnecting, ConnectionPhase.Connecting, ConnectionPhase.Authenticating ->
            Color(0xFFFBBF24)
        ConnectionPhase.Fatal -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = state.connectionState.statusText,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
