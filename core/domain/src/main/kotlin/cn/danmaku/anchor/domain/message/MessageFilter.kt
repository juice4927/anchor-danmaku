package cn.danmaku.anchor.domain.message

import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import cn.danmaku.anchor.model.UserPreferences

/**
 * 单一的重要消息判定源：消息管线的置顶与提醒共用，避免多处判定漂移。
 * 只有金瓜子礼物且估值达到阈值才算重要；SC 与上舰恒为重要。
 */
fun LiveMessage.isImportant(highlightGiftThreshold: Money): Boolean = when (this) {
    is LiveMessage.SuperChatMessage -> true
    is LiveMessage.GuardMessage -> true
    is LiveMessage.GiftMessage -> {
        val estimated = estimatedCny
        coinType.equals("gold", ignoreCase = true) && estimated != null && estimated >= highlightGiftThreshold
    }
    is LiveMessage.DanmakuMessage -> false
}

class MessageFilter {
    fun evaluate(message: LiveMessage, preferences: UserPreferences): FilterDecision {
        return when (message) {
            is LiveMessage.DanmakuMessage -> evaluateDanmaku(message, preferences)
            is LiveMessage.SuperChatMessage -> FilterDecision(display = true, important = true)
            is LiveMessage.GuardMessage -> FilterDecision(display = true, important = true)
            is LiveMessage.GiftMessage -> evaluateGift(message, preferences)
        }
    }

    private fun evaluateDanmaku(
        message: LiveMessage.DanmakuMessage,
        preferences: UserPreferences,
    ): FilterDecision {
        val body = message.text.lowercase()
        if (preferences.keywordBlacklist.any { keyword -> body.contains(keyword) }) {
            return FilterDecision(display = false, important = false, reason = FilterReason.Keyword)
        }
        if (preferences.isBlocked(message.uid)) {
            return FilterDecision(display = false, important = false, reason = FilterReason.User)
        }
        return FilterDecision(display = true, important = false)
    }

    private fun evaluateGift(
        message: LiveMessage.GiftMessage,
        preferences: UserPreferences,
    ): FilterDecision {
        val isGold = message.coinType.equals("gold", ignoreCase = true)
        val estimated = message.estimatedCny
        if (!isGold || estimated == null) {
            val display = preferences.minimumGiftDisplay == Money.ZERO
            return FilterDecision(
                display = display,
                important = false,
                reason = if (display) null else FilterReason.GiftThreshold,
            )
        }

        val display = estimated >= preferences.minimumGiftDisplay
        val important = message.isImportant(preferences.highlightGiftThreshold)
        return FilterDecision(
            display = display,
            important = important,
            reason = if (display) null else FilterReason.GiftThreshold,
        )
    }
}

data class FilterDecision(
    val display: Boolean,
    val important: Boolean,
    val reason: FilterReason? = null,
)

enum class FilterReason {
    Keyword,
    User,
    GiftThreshold,
}
