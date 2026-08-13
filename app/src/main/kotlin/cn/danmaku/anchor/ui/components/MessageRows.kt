package cn.danmaku.anchor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import cn.danmaku.anchor.AnchorMessage
import cn.danmaku.anchor.DanmakuMessage
import cn.danmaku.anchor.GiftMessage
import cn.danmaku.anchor.GuardMessage
import cn.danmaku.anchor.SuperChatMessage
import cn.danmaku.anchor.displayName

@Composable
fun MessageRow(
    message: AnchorMessage,
    fontSizeSp: Int,
) {
    val accent = when (message) {
        is SuperChatMessage -> Color(0xFFFFC857)
        is GuardMessage -> Color(0xFFA78BFA)
        is GiftMessage -> Color(0xFF4DD0E1)
        is DanmakuMessage -> MaterialTheme.colorScheme.tertiary
    }
    val label = when (message) {
        is SuperChatMessage -> "SC"
        is GuardMessage -> "舰队"
        is GiftMessage -> "礼物"
        is DanmakuMessage -> "弹幕"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    if (message is DanmakuMessage && message.medalName != null) {
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

private fun describeMessage(message: AnchorMessage): String = when (message) {
    is DanmakuMessage -> message.text + if (message.repeatCount > 1) " ×${message.repeatCount}" else ""
    is SuperChatMessage -> "¥${"%.2f".format(message.priceCny)} · ${message.message}"
    is GiftMessage -> {
        val amount = message.estimatedCny?.let { " · 约¥${"%.2f".format(it)}" }.orEmpty()
        "${message.giftName} ×${message.count}$amount"
    }
    is GuardMessage -> "等级 ${message.guardLevel} ×${message.count}"
}
