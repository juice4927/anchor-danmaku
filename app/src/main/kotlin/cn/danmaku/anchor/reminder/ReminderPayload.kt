package cn.danmaku.anchor.reminder

import cn.danmaku.anchor.displayName
import cn.danmaku.anchor.model.LiveMessage

data class ReminderPayload(
    val key: String,
    val title: String,
    val content: String,
) {
    companion object {
        fun fromMessage(message: LiveMessage): ReminderPayload = when (message) {
            is LiveMessage.SuperChatMessage -> ReminderPayload(
                key = message.id,
                title = "醒目留言",
                content = "${message.displayName()} · ¥${message.priceCny.toDisplayString()}",
            )
            is LiveMessage.GuardMessage -> ReminderPayload(
                key = message.id,
                title = "舰队支持",
                content = "${message.displayName()} 上舰 ×${message.count}",
            )
            is LiveMessage.GiftMessage -> ReminderPayload(
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
