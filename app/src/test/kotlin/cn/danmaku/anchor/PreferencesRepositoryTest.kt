package cn.danmaku.anchor

import cn.danmaku.anchor.data.PreferencesRepository
import cn.danmaku.anchor.data.AnchorUserPreferences
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesRepositoryTest {
    @Test
    fun persistsAndNormalizesAcrossInstances() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PreferencesRepository(context, scope)
        try {
            repository.updateFontSize(31)
            repository.updateMaxMessages(200)
            repository.updateGiftThresholds(9, 20)
            repository.addRecentRoom(1234L)
            repository.addRecentRoom(1234L)
            repository.addKeyword("  test  ")
            repository.addBlockedUser(10001L, "测试观众")

            val snapshot = repository.currentSnapshot()
            assertEquals(32, snapshot.fontSizeSp)
            assertEquals(300, snapshot.maxMessages)
            assertEquals(0, snapshot.minGiftDisplayThresholdYuan)
            assertEquals(50, snapshot.highlightGiftThresholdYuan)
            assertEquals(listOf(1234L), snapshot.recentRooms)

            val reloaded = PreferencesRepository(context, scope).currentSnapshot()
            assertEquals(snapshot, reloaded)
        } finally {
            resetRepository(repository)
            scope.cancel()
        }
    }

    @Test
    fun storesAtMostTenRecentRooms() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PreferencesRepository(context, scope)
        try {
            repeat(12) { index -> repository.addRecentRoom((index + 1).toLong()) }
            val snapshot = repository.currentSnapshot()
            assertEquals(10, snapshot.recentRooms.size)
            assertEquals(listOf(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L), snapshot.recentRooms)
        } finally {
            resetRepository(repository)
            scope.cancel()
        }
    }

    @Test
    fun storesAtMostFiftyKeywords() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PreferencesRepository(context, scope)
        try {
            repeat(60) { index -> repository.addKeyword("kw${index + 1}") }
            val snapshot = repository.currentSnapshot()
            assertEquals(50, snapshot.keywordBlacklist.size)
            assertEquals("kw11", snapshot.keywordBlacklist.first())
            assertEquals("kw60", snapshot.keywordBlacklist.last())
        } finally {
            resetRepository(repository)
            scope.cancel()
        }
    }

    @Test
    fun storesAtMostTwoHundredBlockedUsers() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PreferencesRepository(context, scope)
        try {
            repeat(210) { index -> repository.addBlockedUser((index + 1).toLong(), "user${index + 1}") }
            val snapshot = repository.currentSnapshot()
            assertEquals(200, snapshot.blockedUsers.size)
            assertEquals(11L, snapshot.blockedUsers.first().uid)
            assertEquals(210L, snapshot.blockedUsers.last().uid)
            assertTrue(snapshot.blockedUsers.all { it.userName.isNotBlank() })
        } finally {
            resetRepository(repository)
            scope.cancel()
        }
    }

    private suspend fun resetRepository(repository: PreferencesRepository) {
        repository.currentSnapshot().keywordBlacklist.forEach { repository.removeKeyword(it) }
        repository.clearRecentRooms()
        repository.clearBlockedUsers()
        repository.updateFontSize(AnchorUserPreferences.DEFAULT_FONT_SIZE)
        repository.updateMaxMessages(AnchorUserPreferences.DEFAULT_MAX_MESSAGES)
        repository.updateKeepScreenOn(true)
        repository.updateSoundEnabled(true)
        repository.updateVibrationEnabled(true)
        repository.updateGiftThresholds(0, 100)
    }
}
