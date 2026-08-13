package cn.danmaku.anchor.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.ConnectionPhase
import cn.danmaku.anchor.R
import cn.danmaku.anchor.ui.UiTags
import cn.danmaku.anchor.ui.components.MessageRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/** 自动跟随的滚动信号：跟随开关、是否滚动中、数据总数与最后可见项索引。 */
private data class FollowSignal(
    val autoFollow: Boolean,
    val scrolling: Boolean,
    val total: Int,
    val lastVisible: Int,
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
) {
    var showConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val view = LocalView.current

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag(UiTags.RoomTopBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showConfirm = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回并停止",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.connectionState.roomId?.let { "房间 $it" } ?: "未连接",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(state)
                        val meta = buildMetaLine(state)
                        if (meta != null) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
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
                            if (state.isPaused) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play_arrow),
                                    contentDescription = "恢复",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pause),
                                    contentDescription = "暂停",
                                    tint = MaterialTheme.colorScheme.onSurface,
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
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
        ) {
            if (state.pinnedMessages.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.RoomPinnedList),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.pinnedMessages, key = { it.message.id }) { pinned ->
                        Card(
                            onClick = { onDismissPinned(pinned.message.id) },
                            shape = MaterialTheme.shapes.medium,
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                PinnedCountdownLabel(
                                    label = pinned.label,
                                    expiresAtMillis = pinned.expiresAtMillis,
                                )
                                MessageRow(message = pinned.message, fontSizeSp = state.fontSizeSp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (state.isPaused) {
                Text(
                    text = "已暂停，略过 ${state.skippedCount} 条",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (state.showJumpButton) {
                Text(
                    text = "回到底部 · ${state.newMessageCount} 条新消息",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag(UiTags.RoomJumpButton)
                        .clickable { onJumpToBottom() },
                )
            }
            if (state.criticalDroppedMessageCount > 0) {
                Text(
                    text = "消息过载，重要消息可能丢失（${state.criticalDroppedMessageCount} 条）",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag(UiTags.RoomDiagnosticBanner),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTags.RoomMessageList),
                color = Color.Transparent,
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(
                            message = message,
                            fontSizeSp = state.fontSizeSp,
                        )
                    }
                }
            }
        }
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
        text = "$label · 剩余 ${remainingSeconds}s",
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

private fun buildMetaLine(state: RoomUiState): String? {
    val popularity = state.connectionState.popularity?.let { "人气 $it" }
    val reconnect = state.connectionState.reconnectDelaySeconds?.let { "${it}s 后重试" }
    val received = "已收 ${state.receivedCount}"
    return listOfNotNull(popularity, reconnect, received).joinToString(" · ").ifBlank { null }
}
