package cn.danmaku.anchor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.ui.theme.BiliBlue
import cn.danmaku.anchor.ui.theme.BiliGiftGold
import cn.danmaku.anchor.ui.theme.BiliGold
import cn.danmaku.anchor.ui.theme.BiliPink

/** 消息类型对应的 B 站风格强调色：弹幕粉、SC 金、舰队蓝、礼物金。 */
private val messageAccent: (LiveMessage) -> Color = { message ->
    when (message) {
        is LiveMessage.SuperChatMessage -> BiliGold
        is LiveMessage.GuardMessage -> BiliBlue
        is LiveMessage.GiftMessage -> BiliGiftGold
        is LiveMessage.DanmakuMessage -> BiliPink
    }
}

private val messageLabel: (LiveMessage) -> String = { message ->
    when (message) {
        is LiveMessage.SuperChatMessage -> "SC"
        is LiveMessage.GuardMessage -> "舰队"
        is LiveMessage.GiftMessage -> "礼物"
        is LiveMessage.DanmakuMessage -> "弹幕"
    }
}

private fun messageMeta(message: LiveMessage): String? = when (message) {
    is LiveMessage.DanmakuMessage -> buildString {
        message.medalLevel?.let { append(it) }
        message.medalName?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" ")
            append(it)
        }
        if (message.repeatCount > 1) {
            if (isNotEmpty()) append(" · ")
            append("合并 ${message.repeatCount} 条")
        }
    }.ifBlank { null }
    is LiveMessage.SuperChatMessage -> "¥${message.priceCny.toDisplayString()}"
    is LiveMessage.GiftMessage -> buildString {
        append(message.giftName)
        append(" ×")
        append(message.count)
        message.estimatedCny?.let {
            append(" · 约¥")
            append(it.toDisplayString())
        }
    }
    is LiveMessage.GuardMessage -> "等级 ${message.guardLevel} ×${message.count}"
}

private fun messageBody(message: LiveMessage): String = when (message) {
    is LiveMessage.DanmakuMessage -> message.text
    is LiveMessage.SuperChatMessage -> message.message
    is LiveMessage.GiftMessage -> describeMessage(message)
    is LiveMessage.GuardMessage -> "加入或续费舰队"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: LiveMessage,
    fontSizeSp: Int,
    onLongClick: (() -> Unit)? = null,
) {
    val accent = messageAccent(message)
    val label = messageLabel(message)
    val meta = messageMeta(message)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // 直接在内容层绘制色条，避免 matchParentSize 将窄条扩成整行背景。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val inset = 8.dp.toPx()
                    drawRoundRect(
                        color = accent,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, inset),
                        size = androidx.compose.ui.geometry.Size(
                            width = 3.5.dp.toPx(),
                            height = (size.height - inset * 2).coerceAtLeast(0f),
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = messageBody(message),
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 7).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

fun LiveMessage.displayName(): String = userName?.takeIf { it.isNotBlank() } ?: "匿名用户"

/** 消息展示文案（长按菜单等 UI 复用）。 */
internal fun describeMessage(message: LiveMessage): String = when (message) {
    is LiveMessage.DanmakuMessage -> message.text + if (message.repeatCount > 1) " ×${message.repeatCount}" else ""
    is LiveMessage.SuperChatMessage -> "¥${message.priceCny.toDisplayString()} · ${message.message}"
    is LiveMessage.GiftMessage -> {
        val amount = message.estimatedCny?.let { " · 约¥${it.toDisplayString()}" }.orEmpty()
        "${message.giftName} ×${message.count}$amount"
    }
    is LiveMessage.GuardMessage -> "等级 ${message.guardLevel} ×${message.count}"
}
