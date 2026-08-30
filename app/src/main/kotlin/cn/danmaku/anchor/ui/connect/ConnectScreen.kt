package cn.danmaku.anchor.ui.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.R
import cn.danmaku.anchor.model.LiveStatus
import cn.danmaku.anchor.model.RoomMetadata
import cn.danmaku.anchor.ui.UiTags
import cn.danmaku.anchor.ui.theme.BiliPink
import cn.danmaku.anchor.ui.theme.BiliPinkLight

@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onInputChanged: (String) -> Unit,
    onRoomSelected: (Long) -> Unit,
    onEnterRoom: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp || maxWidth > maxHeight
            if (wide) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BrandHeader(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.08f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            PageIntro()
                            ConnectCard(state, onInputChanged, onEnterRoom)
                        }
                        RecentRoomsPanel(
                            rooms = state.recentRooms,
                            onRoomSelected = onRoomSelected,
                            fillHeight = true,
                            modifier = Modifier
                                .weight(0.92f)
                                .fillMaxSize(),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { BrandHeader(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout) }
                    item { PageIntro() }
                    item { ConnectCard(state, onInputChanged, onEnterRoom) }
                    item {
                        RecentRoomsPanel(
                            rooms = state.recentRooms,
                            onRoomSelected = onRoomSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIntro() {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 520.dp
        val intro: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "01  /  CONNECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = BiliPink,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = "进入一个直播间", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "把弹幕变成可扫描的现场信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro()
                GuestStatus()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                intro()
                GuestStatus()
            }
        }
    }
}

@Composable
private fun GuestStatus() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF153126),
        border = BorderStroke(1.dp, Color(0xFF28513E)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF44D483)),
            )
            Text(
                text = "游客连接 · 不需要登录",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8DE9B2),
            )
        }
    }
}

@Composable
private fun BrandHeader(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(BiliPinkLight, BiliPink),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppBrandMark(modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主播弹幕台",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "B站直播 · 非官方工具",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "打开设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenAbout) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "关于与隐私",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppBrandMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.075f
        val bubble = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.22f)
            lineTo(size.width * 0.82f, size.height * 0.22f)
            quadraticBezierTo(size.width * 0.90f, size.height * 0.22f, size.width * 0.90f, size.height * 0.32f)
            lineTo(size.width * 0.90f, size.height * 0.67f)
            quadraticBezierTo(size.width * 0.90f, size.height * 0.77f, size.width * 0.80f, size.height * 0.77f)
            lineTo(size.width * 0.59f, size.height * 0.77f)
            lineTo(size.width * 0.40f, size.height * 0.91f)
            lineTo(size.width * 0.45f, size.height * 0.77f)
            lineTo(size.width * 0.18f, size.height * 0.77f)
            quadraticBezierTo(size.width * 0.10f, size.height * 0.77f, size.width * 0.10f, size.height * 0.67f)
            lineTo(size.width * 0.10f, size.height * 0.32f)
            quadraticBezierTo(size.width * 0.10f, size.height * 0.22f, size.width * 0.18f, size.height * 0.22f)
            close()
        }
        drawPath(
            path = bubble,
            color = Color(0xFF11151B),
            style = Stroke(width = stroke),
        )
        val barWidth = size.width * 0.075f
        val baseY = size.height * 0.67f
        listOf(0.30f to 0.51f, 0.43f to 0.37f, 0.56f to 0.45f, 0.69f to 0.32f).forEach { (x, top) ->
            drawRoundRect(
                color = Color(0xFF11151B),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * x, size.height * top),
                size = androidx.compose.ui.geometry.Size(barWidth, baseY - size.height * top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun ConnectCard(
    state: ConnectUiState,
    onInputChanged: (String) -> Unit,
    onEnterRoom: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "连接直播间",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "支持短号或长号",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.roomInput,
                onValueChange = onInputChanged,
                label = { Text("直播间号") },
                supportingText = {
                    val error = state.errorMessage
                    if (error != null) {
                        Text(error)
                    } else {
                        Text("支持短号或长号，连接成功后会记住最近 10 个房间")
                    }
                },
                isError = state.errorMessage != null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.ConnectRoomInput),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            BiliPrimaryButton(
                text = "进入弹幕台",
                onClick = { onEnterRoom(false) },
                modifier = Modifier.testTag(UiTags.ConnectPrimaryButton),
            )
            if (state.demoAvailable && !state.demoLabel.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onEnterRoom(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag(UiTags.ConnectDemoButton),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(state.demoLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = "仅连接公开弹幕服务，不播放视频，不读取登录态。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentRoomsPanel(
    rooms: List<RoomMetadata>,
    onRoomSelected: (Long) -> Unit,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("最近连接", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${rooms.size} 个房间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rooms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(120.dp)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "暂无最近连接记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.heightIn(max = 280.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rooms) { room ->
                        RecentRoomRow(room = room, onClick = { onRoomSelected(room.roomId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRoomRow(
    room: RoomMetadata,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(UiTags.RecentRoomsList),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sensors),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.titleText(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LiveStatusDot(liveStatus = room.liveStatus)
                    Text(
                        text = room.liveStatus.toDisplayLabel() + " · 点击进入直播间",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun RoomMetadata.titleText(): String =
    ownerName?.takeIf { it.isNotBlank() }?.let { "$it 的直播间" } ?: "房间 $roomId"

private fun LiveStatus.toDisplayLabel(): String = when (this) {
    LiveStatus.LIVE -> "直播中"
    LiveStatus.NOT_LIVE -> "未开播"
    LiveStatus.ROUND_PLAY -> "轮播中"
    LiveStatus.UNKNOWN -> "状态未知"
}

@Composable
private fun LiveStatusDot(liveStatus: LiveStatus) {
    val color = when (liveStatus) {
        LiveStatus.LIVE -> Color(0xFF44D483)
        LiveStatus.NOT_LIVE -> Color(0xFF9AA5B1)
        LiveStatus.ROUND_PLAY -> Color(0xFFF5A623)
        LiveStatus.UNKNOWN -> Color(0xFF9AA5B1)
    }
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun BiliPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(BiliPink),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
