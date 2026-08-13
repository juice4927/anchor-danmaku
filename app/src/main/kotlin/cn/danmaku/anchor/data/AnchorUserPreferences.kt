package cn.danmaku.anchor.data

data class BlockedUser(
    val uid: Long,
    val userName: String,
)

data class AnchorUserPreferences(
    val recentRooms: List<Long> = emptyList(),
    val fontSizeSp: Int = 20,
    val maxMessages: Int = 300,
    val keepScreenOn: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val minGiftDisplayThresholdYuan: Int = 0,
    val highlightGiftThresholdYuan: Int = 100,
    val keywordBlacklist: List<String> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 20
        const val DEFAULT_MAX_MESSAGES = 300
        val FONT_SIZE_OPTIONS = (16..32 step 2).toList()
        val MAX_MESSAGES_OPTIONS = listOf(100, 300, 500)
        val MIN_GIFT_OPTIONS = listOf(0, 1, 10, 50)
        val HIGHLIGHT_GIFT_OPTIONS = listOf(50, 100, 500)
    }
}
