package cn.danmaku.anchor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.ui.UiTags

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateUp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onMaxMessagesChanged: (Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onGiftThresholdChanged: (Int, Int) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onRemoveBlockedUser: (Long) -> Unit,
    onClearBlockedUsers: () -> Unit,
    onRemoveRecentRoom: (Long) -> Unit,
    onClearRecentRooms: () -> Unit,
    onScreenOrientationChanged: (Int) -> Unit = {},
) {
    var keywordInput by rememberSaveable { mutableStateOf("") }
    var currentSection by rememberSaveable { mutableStateOf(SettingsSection.Display) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(onNavigateUp = onNavigateUp)
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(UiTags.SettingsRoot),
        ) {
            val wide = maxWidth >= 760.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SettingsSidebar(
                        currentSection = currentSection,
                        onSectionSelected = { currentSection = it },
                        modifier = Modifier.width(172.dp),
                    )
                    SettingsPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        currentSection = currentSection,
                        state = state,
                        keywordInput = keywordInput,
                        onKeywordInputChanged = { keywordInput = it },
                        onFontSizeChanged = onFontSizeChanged,
                        onMaxMessagesChanged = onMaxMessagesChanged,
                        onKeepScreenOnChanged = onKeepScreenOnChanged,
                        onSoundChanged = onSoundChanged,
                        onVibrationChanged = onVibrationChanged,
                        onGiftThresholdChanged = onGiftThresholdChanged,
                        onAddKeyword = {
                            onAddKeyword(keywordInput)
                            keywordInput = ""
                        },
                        onRemoveKeyword = onRemoveKeyword,
                        onRemoveBlockedUser = onRemoveBlockedUser,
                        onClearBlockedUsers = onClearBlockedUsers,
                        onRemoveRecentRoom = onRemoveRecentRoom,
                        onClearRecentRooms = onClearRecentRooms,
                        onOpenPrivacy = onOpenPrivacy,
                        onOpenAbout = onOpenAbout,
                        onScreenOrientationChanged = onScreenOrientationChanged,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsTabStrip(
                        currentSection = currentSection,
                        onSectionSelected = { currentSection = it },
                    )
                    SettingsPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        currentSection = currentSection,
                        state = state,
                        keywordInput = keywordInput,
                        onKeywordInputChanged = { keywordInput = it },
                        onFontSizeChanged = onFontSizeChanged,
                        onMaxMessagesChanged = onMaxMessagesChanged,
                        onKeepScreenOnChanged = onKeepScreenOnChanged,
                        onSoundChanged = onSoundChanged,
                        onVibrationChanged = onVibrationChanged,
                        onGiftThresholdChanged = onGiftThresholdChanged,
                        onAddKeyword = {
                            onAddKeyword(keywordInput)
                            keywordInput = ""
                        },
                        onRemoveKeyword = onRemoveKeyword,
                        onRemoveBlockedUser = onRemoveBlockedUser,
                        onClearBlockedUsers = onClearBlockedUsers,
                        onRemoveRecentRoom = onRemoveRecentRoom,
                        onClearRecentRooms = onClearRecentRooms,
                        onOpenPrivacy = onOpenPrivacy,
                        onOpenAbout = onOpenAbout,
                        onScreenOrientationChanged = onScreenOrientationChanged,
                    )
                }
            }
        }
    }
}

private enum class SettingsSection(
    val title: String,
    val description: String,
) {
    Display("显示", "字号、容量、方向与常亮"),
    Alerts("提醒", "礼物阈值、声音与震动"),
    Filter("过滤", "关键词与用户屏蔽"),
    Data("数据", "最近房间、说明与清理"),
}

@Composable
private fun SettingsTopBar(
    onNavigateUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "观看密度、提醒阈值与数据整理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SaveChip()
    }
}

@Composable
private fun SaveChip() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = "自动保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SettingsSidebar(
    currentSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "分类",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            SettingsSection.values().forEach { section ->
                SettingsNavButton(
                    title = section.title,
                    subtitle = section.description,
                    selected = currentSection == section,
                    onClick = { onSectionSelected(section) },
                )
            }
        }
    }
}

@Composable
private fun SettingsTabStrip(
    currentSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsSection.values().forEach { section ->
                CompactSectionChip(
                    title = section.title,
                    selected = currentSection == section,
                    onClick = { onSectionSelected(section) },
                )
            }
        }
    }
}

@Composable
private fun SettingsNavButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactSectionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(9.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
        ) {}
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier,
    currentSection: SettingsSection,
    state: SettingsUiState,
    keywordInput: String,
    onKeywordInputChanged: (String) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onMaxMessagesChanged: (Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onGiftThresholdChanged: (Int, Int) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onRemoveBlockedUser: (Long) -> Unit,
    onClearBlockedUsers: () -> Unit,
    onRemoveRecentRoom: (Long) -> Unit,
    onClearRecentRooms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onScreenOrientationChanged: (Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PanelHeader(section = currentSection)
            when (currentSection) {
                SettingsSection.Display -> DisplaySettingsContent(
                    state = state,
                    onFontSizeChanged = onFontSizeChanged,
                    onMaxMessagesChanged = onMaxMessagesChanged,
                    onKeepScreenOnChanged = onKeepScreenOnChanged,
                    onScreenOrientationChanged = onScreenOrientationChanged,
                )
                SettingsSection.Alerts -> AlertSettingsContent(
                    state = state,
                    onSoundChanged = onSoundChanged,
                    onVibrationChanged = onVibrationChanged,
                    onGiftThresholdChanged = onGiftThresholdChanged,
                )
                SettingsSection.Filter -> FilterSettingsContent(
                    state = state,
                    keywordInput = keywordInput,
                    onKeywordInputChanged = onKeywordInputChanged,
                    onAddKeyword = onAddKeyword,
                    onRemoveKeyword = onRemoveKeyword,
                    onRemoveBlockedUser = onRemoveBlockedUser,
                )
                SettingsSection.Data -> DataSettingsContent(
                    state = state,
                    onRemoveRecentRoom = onRemoveRecentRoom,
                    onClearRecentRooms = onClearRecentRooms,
                    onClearBlockedUsers = onClearBlockedUsers,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(section: SettingsSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = section.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisplaySettingsContent(
    state: SettingsUiState,
    onFontSizeChanged: (Int) -> Unit,
    onMaxMessagesChanged: (Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onScreenOrientationChanged: (Int) -> Unit,
) {
    CompactGroup(title = "显示", description = "控制弹幕流的字号、容量和跟随方式。") {
        ChoiceSettingRow(
            title = "弹幕字号",
            subtitle = "当前预览 ${state.preferences.fontSizeSp}sp",
            values = AnchorUserPreferences.FONT_SIZE_OPTIONS,
            selected = state.preferences.fontSizeSp,
            label = { "${it}sp" },
            onClick = onFontSizeChanged,
        )
        ChoiceSettingRow(
            title = "最大消息数",
            subtitle = "超过后优先淘汰普通弹幕",
            values = AnchorUserPreferences.MAX_MESSAGES_OPTIONS,
            selected = state.preferences.maxMessages,
            label = Int::toString,
            onClick = onMaxMessagesChanged,
        )
        SwitchSettingRow(
            title = "保持屏幕常亮",
            subtitle = "适合把设备作为直播副屏",
            checked = state.preferences.keepScreenOn,
            onCheckedChange = onKeepScreenOnChanged,
        )
        ChoiceSettingRow(
            title = "屏幕方向",
            subtitle = "锁定观看方向或跟随系统",
            values = AnchorUserPreferences.SCREEN_ORIENTATION_OPTIONS,
            selected = state.preferences.screenOrientation,
            label = ::orientationLabel,
            onClick = onScreenOrientationChanged,
        )
    }
}

@Composable
private fun AlertSettingsContent(
    state: SettingsUiState,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onGiftThresholdChanged: (Int, Int) -> Unit,
) {
    CompactGroup(title = "礼物提醒", description = "用阈值保留真正重要的礼物事件。") {
        ChoiceSettingRow(
            title = "最低展示金额",
            subtitle = "低于此金额的礼物不进入消息流",
            values = AnchorUserPreferences.MIN_GIFT_OPTIONS,
            selected = state.preferences.minGiftDisplayThresholdYuan,
            label = { "${it}元" },
            onClick = {
                onGiftThresholdChanged(it, state.preferences.highlightGiftThresholdYuan)
            },
        )
        ChoiceSettingRow(
            title = "高额礼物提醒",
            subtitle = "触发声音、震动和重点保留",
            values = AnchorUserPreferences.HIGHLIGHT_GIFT_OPTIONS,
            selected = state.preferences.highlightGiftThresholdYuan,
            label = { "${it}元" },
            onClick = {
                onGiftThresholdChanged(state.preferences.minGiftDisplayThresholdYuan, it)
            },
        )
    }
    CompactGroup(title = "即时反馈", description = "提醒开关会即时生效。") {
        SwitchSettingRow(
            title = "提示音",
            subtitle = "收到高额礼物或重点事件时播放声音",
            checked = state.preferences.soundEnabled,
            onCheckedChange = onSoundChanged,
        )
        SwitchSettingRow(
            title = "震动",
            subtitle = "适合静音环境下保留触觉提醒",
            checked = state.preferences.vibrationEnabled,
            onCheckedChange = onVibrationChanged,
        )
    }
}

@Composable
private fun FilterSettingsContent(
    state: SettingsUiState,
    keywordInput: String,
    onKeywordInputChanged: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onRemoveBlockedUser: (Long) -> Unit,
) {
    CompactGroup(title = "关键词黑名单", description = "屏蔽固定词条，减少高频噪音。") {
        OutlinedTextField(
            value = keywordInput,
            onValueChange = onKeywordInputChanged,
            label = { Text("新增关键词") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onAddKeyword) {
                Text("添加关键词")
            }
        }
        ChipList(
            items = state.preferences.keywordBlacklist,
            label = { it },
            key = { it },
            emptyLabel = "暂无关键词黑名单",
            onRemove = onRemoveKeyword,
        )
    }
    CompactGroup(title = "用户黑名单", description = "屏蔽指定用户的发言，保留逐个移除能力。") {
        ChipList(
            items = state.preferences.blockedUsers,
            label = { "${it.userName} (${it.uid})" },
            key = { it.uid.toString() },
            emptyLabel = "暂无已屏蔽用户",
            onRemove = { onRemoveBlockedUser(it.uid) },
        )
    }
}

@Composable
private fun DataSettingsContent(
    state: SettingsUiState,
    onRemoveRecentRoom: (Long) -> Unit,
    onClearRecentRooms: () -> Unit,
    onClearBlockedUsers: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    CompactGroup(title = "最近房间", description = "保留常用房间号，便于快速回到工作现场。") {
        ChipList(
            items = state.preferences.recentRooms,
            label = Long::toString,
            key = Long::toString,
            emptyLabel = "暂无最近房间",
            onRemove = onRemoveRecentRoom,
        )
    }
    CompactGroup(title = "说明", description = "查看隐私与应用信息。") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.weight(1f),
            ) {
                Text("隐私说明")
            }
            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.weight(1f),
            ) {
                Text("关于")
            }
        }
    }
    DangerGroup(
        hasBlockedUsers = state.preferences.blockedUsers.isNotEmpty(),
        hasRecentRooms = state.preferences.recentRooms.isNotEmpty(),
        onClearBlockedUsers = onClearBlockedUsers,
        onClearRecentRooms = onClearRecentRooms,
    )
}

@Composable
private fun CompactGroup(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ChoiceSettingRow(
    title: String,
    subtitle: String,
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onClick: (Int) -> Unit,
) {
    CompactSettingRow(title = title, subtitle = subtitle) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                SelectChip(
                    label = label(value),
                    selected = value == selected,
                    onClick = { onClick(value) },
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CompactSettingRow(title = title, subtitle = subtitle) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CompactSettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 560.dp
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingCopy(title = title, subtitle = subtitle)
                trailing()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingCopy(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }
        }
    }
}

@Composable
private fun SettingCopy(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(8.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

@Composable
private fun <T> ChipList(
    items: List<T>,
    label: (T) -> String,
    key: (T) -> String,
    emptyLabel: String,
    onRemove: (T) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = emptyLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.distinctBy(key).forEach { item ->
            AssistChip(
                onClick = { onRemove(item) },
                modifier = Modifier.widthIn(max = 420.dp),
                label = {
                    Text(
                        text = label(item),
                        maxLines = 1,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun DangerGroup(
    hasBlockedUsers: Boolean,
    hasRecentRooms: Boolean,
    onClearBlockedUsers: () -> Unit,
    onClearRecentRooms: () -> Unit,
) {
    CompactGroup(title = "危险操作", description = "清理后立即生效，且无法撤销。") {
        if (!hasBlockedUsers && !hasRecentRooms) {
            Text(
                text = "当前没有可清理的数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (hasBlockedUsers) {
                OutlinedButton(onClick = onClearBlockedUsers, modifier = Modifier.fillMaxWidth()) {
                    Text("清空用户黑名单")
                }
            }
            if (hasRecentRooms) {
                OutlinedButton(onClick = onClearRecentRooms, modifier = Modifier.fillMaxWidth()) {
                    Text("清空最近房间")
                }
            }
        }
    }
}

private fun orientationLabel(value: Int): String = when (value) {
    AnchorUserPreferences.SCREEN_ORIENTATION_AUTO -> "自动"
    AnchorUserPreferences.SCREEN_ORIENTATION_PORTRAIT -> "竖屏"
    AnchorUserPreferences.SCREEN_ORIENTATION_LANDSCAPE -> "横屏"
    else -> "自动"
}
