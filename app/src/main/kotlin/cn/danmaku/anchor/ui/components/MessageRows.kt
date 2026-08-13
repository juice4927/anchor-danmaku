package cn.danmaku.anchor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.danmaku.anchor.displayName
import cn.danmaku.anchor.model.LiveMessage

@Composable
fun MessageRow(
    message: LiveMessage,
    fontSizeSp: Int,
) {
    val accent = when (message) {
        is LiveMessage.SuperChatMessage -> Color(0xFFFFC857)
        is LiveMessage.GuardMessage -> Color(0xFFA78BFA)
        is LiveMessage.GiftMessage -> Color(0xFF4DD0E1)
        is LiveMessage.DanmakuMessage -> MaterialTheme.colorScheme.tertiary
    }
    val label = when (message) {
        is LiveMessage.SuperChatMessage -> "SC"
        is LiveMessage.GuardMessage -> "舰队"
        is LiveMessage.GiftMessage -> "礼物"
        is LiveMessage.DanmakuMessage -> "弹幕"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // 高流量滚动下避免 IntrinsicSize 的二次测量：Box 以内容（Column）定高，
        // 色条用 matchParentSize 跟随，无需额外测量 pass。
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .width(4.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = message.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (message is LiveMessage.DanmakuMessage && message.medalName != null) {
                        Text(
                            text = buildString {
                                message.medalLevel?.let { append(it) }
                                append(" ")
                                append(message.medalName)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = describeMessage(message),
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 8).sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

fun LiveMessage.displayName(): String = userName?.takeIf { it.isNotBlank() } ?: "匿名用户"

private fun describeMessage(message: LiveMessage): String = when (message) {
    is LiveMessage.DanmakuMessage -> message.text + if (message.repeatCount > 1) " ×${message.repeatCount}" else ""
    is LiveMessage.SuperChatMessage -> "¥${message.priceCny.toDisplayString()} · ${message.message}"
    is LiveMessage.GiftMessage -> {
        val amount = message.estimatedCny?.let { " · 约¥${it.toDisplayString()}" }.orEmpty()
        "${message.giftName} ×${message.count}$amount"
    }
    is LiveMessage.GuardMessage -> "等级 ${message.guardLevel} ×${message.count}"
}
