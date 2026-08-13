package cn.danmaku.anchor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PreferencesStore {
    val preferences: StateFlow<AnchorUserPreferences>

    suspend fun currentSnapshot(): AnchorUserPreferences
    suspend fun addRecentRoom(roomId: Long)
    suspend fun removeRecentRoom(roomId: Long)
    suspend fun clearRecentRooms()
    suspend fun updateFontSize(fontSizeSp: Int)
    suspend fun updateMaxMessages(maxMessages: Int)
    suspend fun updateKeepScreenOn(enabled: Boolean)
    suspend fun updateSoundEnabled(enabled: Boolean)
    suspend fun updateVibrationEnabled(enabled: Boolean)
    suspend fun updateGiftThresholds(minGift: Int, highlightGift: Int)
    suspend fun addKeyword(raw: String)
    suspend fun removeKeyword(keyword: String)
    suspend fun addBlockedUser(uid: Long, userName: String)
    suspend fun removeBlockedUser(uid: Long)
    suspend fun clearBlockedUsers()
}

class PreferencesRepository(
    context: Context,
    scope: CoroutineScope,
) : PreferencesStore {
    private val store: DataStore<Preferences> = sharedStore(context, scope)
    private val keywordGuard = Mutex()

    override val preferences: StateFlow<AnchorUserPreferences> = store.data
        .map(::decode)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AnchorUserPreferences(),
        )

    override suspend fun currentSnapshot(): AnchorUserPreferences = preferences.first()

    override suspend fun addRecentRoom(roomId: Long) {
        if (roomId <= 0L) return
        store.edit { prefs ->
            val rooms = decodeLongList(prefs[RECENT_ROOMS]).toMutableList()
            rooms.remove(roomId)
            rooms.add(0, roomId)
            prefs[RECENT_ROOMS] = rooms.take(10).joinToString(",")
        }
    }

    override suspend fun removeRecentRoom(roomId: Long) {
        store.edit { prefs ->
            prefs[RECENT_ROOMS] = decodeLongList(prefs[RECENT_ROOMS])
                .filterNot { it == roomId }
                .joinToString(",")
        }
    }

    override suspend fun clearRecentRooms() {
        store.edit { it.remove(RECENT_ROOMS) }
    }

    override suspend fun updateFontSize(fontSizeSp: Int) {
        store.edit { it[FONT_SIZE] = normalizeFontSize(fontSizeSp) }
    }

    override suspend fun updateMaxMessages(maxMessages: Int) {
        store.edit { it[MAX_MESSAGES] = normalizeMaxMessages(maxMessages) }
    }

    override suspend fun updateKeepScreenOn(enabled: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun updateSoundEnabled(enabled: Boolean) {
        store.edit { it[SOUND_ENABLED] = enabled }
    }

    override suspend fun updateVibrationEnabled(enabled: Boolean) {
        store.edit { it[VIBRATION_ENABLED] = enabled }
    }

    override suspend fun updateGiftThresholds(minGift: Int, highlightGift: Int) {
        store.edit { prefs ->
            val normalizedMin = normalizeMinGift(minGift)
            prefs[MIN_GIFT] = normalizedMin
            prefs[HIGHLIGHT_GIFT] = normalizeHighlightGift(highlightGift, normalizedMin)
        }
    }

    override suspend fun addKeyword(raw: String) {
        val keyword = normalizeKeyword(raw) ?: return
        keywordGuard.withLock {
            store.edit { prefs ->
                val keywords = decodeKeywords(prefs[KEYWORDS]).toMutableList()
                keywords.remove(keyword)
                keywords.add(keyword)
                prefs[KEYWORDS] = keywords.takeLast(50).joinToString("|")
            }
        }
    }

    override suspend fun removeKeyword(keyword: String) {
        store.edit { prefs ->
            prefs[KEYWORDS] = decodeKeywords(prefs[KEYWORDS])
                .filterNot { it == keyword.trim() }
                .joinToString("|")
        }
    }

    override suspend fun addBlockedUser(uid: Long, userName: String) {
        if (uid <= 0L) return
        store.edit { prefs ->
            val blocked = decodeBlockedUsers(prefs[BLOCKED_USERS]).toMutableList()
            blocked.removeAll { it.uid == uid }
            blocked.add(BlockedUser(uid = uid, userName = userName.ifBlank { "匿名用户" }.sanitize()))
            prefs[BLOCKED_USERS] = blocked.takeLast(200).joinToString("|") {
                "${it.uid}:${it.userName.sanitize()}"
            }
        }
    }

    override suspend fun removeBlockedUser(uid: Long) {
        store.edit { prefs ->
            prefs[BLOCKED_USERS] = decodeBlockedUsers(prefs[BLOCKED_USERS])
                .filterNot { it.uid == uid }
                .joinToString("|") { "${it.uid}:${it.userName.sanitize()}" }
        }
    }

    override suspend fun clearBlockedUsers() {
        store.edit { it.remove(BLOCKED_USERS) }
    }

    private fun decode(prefs: Preferences): AnchorUserPreferences {
        val minGift = normalizeMinGift(prefs[MIN_GIFT] ?: 0)
        return AnchorUserPreferences(
            recentRooms = decodeLongList(prefs[RECENT_ROOMS]).take(10),
            fontSizeSp = normalizeFontSize(prefs[FONT_SIZE] ?: AnchorUserPreferences.DEFAULT_FONT_SIZE),
            maxMessages = normalizeMaxMessages(prefs[MAX_MESSAGES] ?: AnchorUserPreferences.DEFAULT_MAX_MESSAGES),
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: true,
            soundEnabled = prefs[SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[VIBRATION_ENABLED] ?: true,
            minGiftDisplayThresholdYuan = minGift,
            highlightGiftThresholdYuan = normalizeHighlightGift(
                prefs[HIGHLIGHT_GIFT] ?: 100,
                minGift,
            ),
            keywordBlacklist = decodeKeywords(prefs[KEYWORDS]).takeLast(50),
            blockedUsers = decodeBlockedUsers(prefs[BLOCKED_USERS]).takeLast(200),
        )
    }

    private fun normalizeFontSize(value: Int): Int =
        AnchorUserPreferences.FONT_SIZE_OPTIONS.minWithOrNull(
            compareBy<Int> { abs(it - value) }.thenByDescending { it },
        )
            ?: AnchorUserPreferences.DEFAULT_FONT_SIZE

    private fun normalizeMaxMessages(value: Int): Int =
        if (value in AnchorUserPreferences.MAX_MESSAGES_OPTIONS) value else AnchorUserPreferences.DEFAULT_MAX_MESSAGES

    private fun normalizeMinGift(value: Int): Int =
        if (value in AnchorUserPreferences.MIN_GIFT_OPTIONS) value else 0

    private fun normalizeHighlightGift(value: Int, minGift: Int): Int {
        val candidate = AnchorUserPreferences.HIGHLIGHT_GIFT_OPTIONS.minWithOrNull(
            compareBy<Int> { abs(it - value) }.thenByDescending { it },
        ) ?: 100
        return candidate.coerceAtLeast(minGift)
    }

    private fun normalizeKeyword(value: String): String? =
        value.trim().takeIf { it.length in 1..32 }

    private fun decodeLongList(raw: String?): List<Long> =
        raw.orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
            .distinct()

    private fun decodeKeywords(raw: String?): List<String> =
        raw.orEmpty()
            .split('|')
            .mapNotNull(::normalizeKeyword)
            .distinct()

    private fun decodeBlockedUsers(raw: String?): List<BlockedUser> =
        raw.orEmpty()
            .split('|')
            .mapNotNull { token ->
                val parts = token.split(':', limit = 2)
                val uid = parts.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
                val name = parts.getOrNull(1).orEmpty().ifBlank { "匿名用户" }
                BlockedUser(uid = uid, userName = name.sanitize())
            }
            .distinctBy { it.uid }

    private fun String.sanitize(): String = replace("|", "").replace(":", "").trim().ifBlank { "匿名用户" }

    private companion object {
        private val stores = mutableMapOf<String, DataStore<Preferences>>()
        private val storeLock = Any()

        private fun sharedStore(context: Context, scope: CoroutineScope): DataStore<Preferences> {
            val file = context.preferencesDataStoreFile("anchor-preferences")
            return synchronized(storeLock) {
                stores.getOrPut(file.absolutePath) {
                    PreferenceDataStoreFactory.create(scope = scope) { file }
                }
            }
        }

        val RECENT_ROOMS = stringPreferencesKey("recent_rooms")
        val FONT_SIZE = intPreferencesKey("font_size")
        val MAX_MESSAGES = intPreferencesKey("max_messages")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val MIN_GIFT = intPreferencesKey("min_gift")
        val HIGHLIGHT_GIFT = intPreferencesKey("highlight_gift")
        val KEYWORDS = stringPreferencesKey("keywords")
        val BLOCKED_USERS = stringPreferencesKey("blocked_users")
    }
}
