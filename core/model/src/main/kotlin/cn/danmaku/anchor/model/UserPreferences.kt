package cn.danmaku.anchor.model

class UserPreferences(
    danmakuTextSizeSp: Int = DEFAULT_DANMAKU_TEXT_SIZE_SP,
    maxMessageCount: Int = DEFAULT_MAX_MESSAGE_COUNT,
    keepScreenOn: Boolean = true,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    minimumGiftDisplay: Money = DEFAULT_MINIMUM_GIFT_DISPLAY,
    highlightGiftThreshold: Money = DEFAULT_HIGHLIGHT_GIFT_THRESHOLD,
    keywordBlacklist: Set<String> = emptySet(),
    blockedUsers: List<BlockedUser> = emptyList(),
    recentRoomIds: List<Long> = emptyList(),
) {
    val danmakuTextSizeSp: Int = normalizeTextSize(danmakuTextSizeSp)
    val maxMessageCount: Int = normalizeMaxMessageCount(maxMessageCount)
    val keepScreenOn: Boolean = keepScreenOn
    val soundEnabled: Boolean = soundEnabled
    val vibrationEnabled: Boolean = vibrationEnabled
    val minimumGiftDisplay: Money = normalizeMinimumGift(minimumGiftDisplay)
    val highlightGiftThreshold: Money = normalizeHighlightGift(highlightGiftThreshold, minimumGiftDisplay)
    val keywordBlacklist: Set<String> = normalizeKeywords(keywordBlacklist)
    val blockedUsers: List<BlockedUser> = normalizeBlockedUsers(blockedUsers)
    val recentRoomIds: List<Long> = normalizeRecentRooms(recentRoomIds)

    fun isBlocked(uid: Long?): Boolean = uid != null && blockedUsers.any { it.uid == uid }

    fun withRecentRoom(roomId: Long): UserPreferences {
        if (roomId <= 0L) return this
        return copy(recentRoomIds = listOf(roomId) + recentRoomIds.filterNot { it == roomId })
    }

    fun withoutRecentRoom(roomId: Long): UserPreferences = copy(recentRoomIds = recentRoomIds.filterNot { it == roomId })

    fun clearRecentRooms(): UserPreferences = copy(recentRoomIds = emptyList())

    fun withBlockedUser(uid: Long, latestName: String?): UserPreferences = copy(
        blockedUsers = blockedUsers.filterNot { it.uid == uid } + BlockedUser(uid = uid, latestName = latestName),
    )

    fun withoutBlockedUser(uid: Long): UserPreferences = copy(blockedUsers = blockedUsers.filterNot { it.uid == uid })

    fun copy(
        danmakuTextSizeSp: Int = this.danmakuTextSizeSp,
        maxMessageCount: Int = this.maxMessageCount,
        keepScreenOn: Boolean = this.keepScreenOn,
        soundEnabled: Boolean = this.soundEnabled,
        vibrationEnabled: Boolean = this.vibrationEnabled,
        minimumGiftDisplay: Money = this.minimumGiftDisplay,
        highlightGiftThreshold: Money = this.highlightGiftThreshold,
        keywordBlacklist: Set<String> = this.keywordBlacklist,
        blockedUsers: List<BlockedUser> = this.blockedUsers,
        recentRoomIds: List<Long> = this.recentRoomIds,
    ): UserPreferences = UserPreferences(
        danmakuTextSizeSp = danmakuTextSizeSp,
        maxMessageCount = maxMessageCount,
        keepScreenOn = keepScreenOn,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        minimumGiftDisplay = minimumGiftDisplay,
        highlightGiftThreshold = highlightGiftThreshold,
        keywordBlacklist = keywordBlacklist,
        blockedUsers = blockedUsers,
        recentRoomIds = recentRoomIds,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPreferences) return false
        return danmakuTextSizeSp == other.danmakuTextSizeSp &&
            maxMessageCount == other.maxMessageCount &&
            keepScreenOn == other.keepScreenOn &&
            soundEnabled == other.soundEnabled &&
            vibrationEnabled == other.vibrationEnabled &&
            minimumGiftDisplay == other.minimumGiftDisplay &&
            highlightGiftThreshold == other.highlightGiftThreshold &&
            keywordBlacklist == other.keywordBlacklist &&
            blockedUsers == other.blockedUsers &&
            recentRoomIds == other.recentRoomIds
    }

    override fun hashCode(): Int {
        var result = danmakuTextSizeSp
        result = 31 * result + maxMessageCount
        result = 31 * result + keepScreenOn.hashCode()
        result = 31 * result + soundEnabled.hashCode()
        result = 31 * result + vibrationEnabled.hashCode()
        result = 31 * result + minimumGiftDisplay.hashCode()
        result = 31 * result + highlightGiftThreshold.hashCode()
        result = 31 * result + keywordBlacklist.hashCode()
        result = 31 * result + blockedUsers.hashCode()
        result = 31 * result + recentRoomIds.hashCode()
        return result
    }

    override fun toString(): String = "UserPreferences(" +
        "danmakuTextSizeSp=$danmakuTextSizeSp, " +
        "maxMessageCount=$maxMessageCount, " +
        "keepScreenOn=$keepScreenOn, " +
        "soundEnabled=$soundEnabled, " +
        "vibrationEnabled=$vibrationEnabled, " +
        "minimumGiftDisplay=$minimumGiftDisplay, " +
        "highlightGiftThreshold=$highlightGiftThreshold, " +
        "keywordBlacklist=$keywordBlacklist, " +
        "blockedUsers=$blockedUsers, " +
        "recentRoomIds=$recentRoomIds)"

    companion object {
        const val DEFAULT_DANMAKU_TEXT_SIZE_SP: Int = 20
        const val DEFAULT_MAX_MESSAGE_COUNT: Int = 300
        val DEFAULT_MINIMUM_GIFT_DISPLAY: Money = Money.ZERO
        val DEFAULT_HIGHLIGHT_GIFT_THRESHOLD: Money = Money.fromWholeCny(100)

        private val VALID_TEXT_SIZES = (16..32 step 2).toSet()
        private val VALID_MAX_MESSAGE_COUNTS = setOf(100, 300, 500)
        private val VALID_MINIMUM_GIFT_AMOUNTS = setOf(
            Money.ZERO,
            Money.fromWholeCny(1),
            Money.fromWholeCny(10),
            Money.fromWholeCny(50),
        )
        private val VALID_HIGHLIGHT_AMOUNTS = setOf(
            Money.fromWholeCny(50),
            Money.fromWholeCny(100),
            Money.fromWholeCny(500),
        )

        private fun normalizeTextSize(raw: Int): Int = raw.takeIf { it in VALID_TEXT_SIZES } ?: DEFAULT_DANMAKU_TEXT_SIZE_SP

        private fun normalizeMaxMessageCount(raw: Int): Int =
            raw.takeIf { it in VALID_MAX_MESSAGE_COUNTS } ?: DEFAULT_MAX_MESSAGE_COUNT

        private fun normalizeMinimumGift(raw: Money): Money =
            raw.takeIf { it in VALID_MINIMUM_GIFT_AMOUNTS } ?: DEFAULT_MINIMUM_GIFT_DISPLAY

        private fun normalizeHighlightGift(raw: Money, minimumGiftDisplay: Money): Money {
            val normalizedMinimum = normalizeMinimumGift(minimumGiftDisplay)
            val candidate = raw.takeIf { it in VALID_HIGHLIGHT_AMOUNTS } ?: DEFAULT_HIGHLIGHT_GIFT_THRESHOLD
            return if (candidate < normalizedMinimum) {
                DEFAULT_HIGHLIGHT_GIFT_THRESHOLD
            } else {
                candidate
            }
        }

        private fun normalizeKeywords(raw: Set<String>): Set<String> {
            val normalized = linkedSetOf<String>()
            raw.forEach { candidate ->
                val value = candidate.trim().lowercase()
                if (value.length in 1..32 && value !in normalized && normalized.size < 50) {
                    normalized += value
                }
            }
            return normalized
        }

        private fun normalizeBlockedUsers(raw: List<BlockedUser>): List<BlockedUser> {
            val normalized = linkedMapOf<Long, BlockedUser>()
            raw.forEach { candidate ->
                val uid = candidate.uid
                if (uid > 0L) {
                    normalized[uid] = candidate.copy(latestName = candidate.latestName?.trim().orEmpty().ifBlank { null })
                }
            }
            return normalized.values.take(200)
        }

        private fun normalizeRecentRooms(raw: List<Long>): List<Long> {
            val normalized = ArrayList<Long>(10)
            raw.forEach { roomId ->
                if (roomId > 0L && roomId !in normalized && normalized.size < 10) {
                    normalized += roomId
                }
            }
            return normalized
        }
    }
}

data class BlockedUser(
    val uid: Long,
    val latestName: String?,
)
