package cn.danmaku.anchor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.data.AnchorUserPreferences
import cn.danmaku.anchor.data.PreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AnchorUserPreferences = AnchorUserPreferences(),
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesStore,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                state.value = SettingsUiState(preferences)
            }
        }
    }

    fun updateFontSize(value: Int) = launch { preferencesRepository.updateFontSize(value) }
    fun updateMaxMessages(value: Int) = launch { preferencesRepository.updateMaxMessages(value) }
    fun updateKeepScreenOn(value: Boolean) = launch { preferencesRepository.updateKeepScreenOn(value) }
    fun updateSoundEnabled(value: Boolean) = launch { preferencesRepository.updateSoundEnabled(value) }
    fun updateVibrationEnabled(value: Boolean) = launch { preferencesRepository.updateVibrationEnabled(value) }
    fun updateGiftThresholds(minGift: Int, highlightGift: Int) = launch {
        preferencesRepository.updateGiftThresholds(minGift, highlightGift)
    }
    fun addKeyword(keyword: String) = launch { preferencesRepository.addKeyword(keyword) }
    fun removeKeyword(keyword: String) = launch { preferencesRepository.removeKeyword(keyword) }
    fun removeBlockedUser(uid: Long) = launch { preferencesRepository.removeBlockedUser(uid) }
    fun clearBlockedUsers() = launch { preferencesRepository.clearBlockedUsers() }
    fun removeRecentRoom(roomId: Long) = launch { preferencesRepository.removeRecentRoom(roomId) }
    fun clearRecentRooms() = launch { preferencesRepository.clearRecentRooms() }
    fun updateScreenOrientation(value: Int) = launch { preferencesRepository.updateScreenOrientation(value) }
    fun updateBatchRefreshEnabled(value: Boolean) = launch { preferencesRepository.updateBatchRefreshEnabled(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
