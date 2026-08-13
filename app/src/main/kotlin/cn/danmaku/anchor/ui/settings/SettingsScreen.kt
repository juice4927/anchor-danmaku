package cn.danmaku.anchor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column {
                    Text("设置", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "显示、提醒、关键词和最近房间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .testTag(UiTags.SettingsRoot),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingSection(title = "字号") {
                    OptionRow(
                        values = AnchorUserPreferences.FONT_SIZE_OPTIONS,
                        selected = state.preferences.fontSizeSp,
                        label = { "${it}sp" },
                        onClick = onFontSizeChanged,
                    )
                }
            }
            item {
                SettingSection(title = "最大消息数") {
                    OptionRow(
                        values = AnchorUserPreferences.MAX_MESSAGES_OPTIONS,
                        selected = state.preferences.maxMessages,
                        label = Int::toString,
                        onClick = onMaxMessagesChanged,
                    )
                }
            }
            item {
                SettingSection(title = "礼物阈值") {
                    Text(
                        "最低礼物展示金额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OptionRow(
                        values = AnchorUserPreferences.MIN_GIFT_OPTIONS,
                        selected = state.preferences.minGiftDisplayThresholdYuan,
                        label = { "${it}元" },
                        onClick = { onGiftThresholdChanged(it, state.preferences.highlightGiftThresholdYuan) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "高额礼物提醒金额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OptionRow(
                        values = AnchorUserPreferences.HIGHLIGHT_GIFT_OPTIONS,
                        selected = state.preferences.highlightGiftThresholdYuan,
                        label = { "${it}元" },
                        onClick = { onGiftThresholdChanged(state.preferences.minGiftDisplayThresholdYuan, it) },
                    )
                }
            }
            item {
                SettingSection(title = "开关") {
                    ToggleRow("保持屏幕常亮", state.preferences.keepScreenOn, onKeepScreenOnChanged)
                    ToggleRow("提示音", state.preferences.soundEnabled, onSoundChanged)
                    ToggleRow("震动", state.preferences.vibrationEnabled, onVibrationChanged)
                }
            }
            item {
                SettingSection(title = "屏幕方向") {
                    OptionRow(
                        values = AnchorUserPreferences.SCREEN_ORIENTATION_OPTIONS,
                        selected = state.preferences.screenOrientation,
                        label = { orientationLabel(it) },
                        onClick = onScreenOrientationChanged,
                    )
                }
            }
            item {
                SettingSection(title = "关键词黑名单") {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        label = { Text("新增关键词") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        onAddKeyword(keywordInput)
                        keywordInput = ""
                    }) {
                        Text("添加关键词")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowChips(
                        items = state.preferences.keywordBlacklist,
                        label = { it },
                        key = { it },
                        onRemove = onRemoveKeyword,
                    )
                }
            }
            item {
                SettingSection(title = "用户黑名单") {
                    if (state.preferences.blockedUsers.isEmpty()) {
                        Text(
                            "暂无已屏蔽用户",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowChips(
                            items = state.preferences.blockedUsers,
                            label = { "${it.userName} (${it.uid})" },
                            key = { it.uid.toString() },
                            onRemove = { onRemoveBlockedUser(it.uid) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onClearBlockedUsers) {
                            Text("清空用户黑名单")
                        }
                    }
                }
            }
            item {
                SettingSection(title = "最近房间") {
                    if (state.preferences.recentRooms.isEmpty()) {
                        Text(
                            "暂无最近房间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowChips(
                            items = state.preferences.recentRooms,
                            label = Long::toString,
                            key = Long::toString,
                            onRemove = onRemoveRecentRoom,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onClearRecentRooms) {
                            Text("清空最近房间")
                        }
                    }
                }
            }
            item {
                SettingSection(title = "说明") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onOpenPrivacy) { Text("隐私说明") }
                        OutlinedButton(onClick = onOpenAbout) { Text("关于") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable () -> Unit,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun orientationLabel(value: Int): String = when (value) {
    AnchorUserPreferences.SCREEN_ORIENTATION_AUTO -> "自动"
    AnchorUserPreferences.SCREEN_ORIENTATION_PORTRAIT -> "竖屏"
    AnchorUserPreferences.SCREEN_ORIENTATION_LANDSCAPE -> "横屏"
    else -> "自动"
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onClick: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val isSelected = value == selected
            AssistChip(
                onClick = { onClick(value) },
                label = { Text(label(value)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    labelColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            )
        }
    }
}

@Composable
private fun <T> FlowChips(
    items: List<T>,
    label: (T) -> String,
    key: (T) -> String,
    onRemove: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            AssistChip(
                onClick = { onRemove(item) },
                label = { Text(label(item)) },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
