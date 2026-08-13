package cn.danmaku.anchor.reminder

import cn.danmaku.anchor.AnchorMessage
import cn.danmaku.anchor.GiftMessage
import cn.danmaku.anchor.GuardMessage
import cn.danmaku.anchor.SuperChatMessage
import cn.danmaku.anchor.displayName

data class ReminderPayload(
    val key: String,
    val title: String,
    val content: String,
) {
    companion object {
        fun fromMessage(message: AnchorMessage): ReminderPayload = when (message) {
            is SuperChatMessage -> ReminderPayload(
                key = message.id,
                title = "醒目留言",
                content = "${message.displayName()} · ¥${"%.2f".format(message.priceCny)}",
            )
            is GuardMessage -> ReminderPayload(
                key = message.id,
                title = "舰队支持",
                content = "${message.displayName()} 上舰 ×${message.count}",
            )
            is GiftMessage -> ReminderPayload(
                key = message.id,
                title = "高额礼物",
                content = "${message.displayName()} · ${message.giftName} ×${message.count}",
            )
            else -> ReminderPayload(
                key = message.id,
                title = "重要消息",
                content = message.displayName(),
            )
        }
    }
}
