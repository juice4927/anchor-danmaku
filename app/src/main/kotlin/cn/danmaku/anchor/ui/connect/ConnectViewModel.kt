package cn.danmaku.anchor.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.SessionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val roomInput: String = "",
    val errorMessage: String? = null,
    val recentRooms: List<Long> = emptyList(),
    val demoAvailable: Boolean = false,
    val demoLabel: String? = null,
) 

data class ConnectRequest(
    val roomId: Long,
    val useDemo: Boolean,
)

class ConnectViewModel(
    preferencesRepository: PreferencesStore,
    sessionRepository: SessionCoordinator,
    initialRoomId: Long? = null,
) : ViewModel() {
    private val initialPreferences = preferencesRepository.preferences.value
    private val state = MutableStateFlow(
        ConnectUiState(
            roomInput = initialRoomId?.toString()
                ?: initialPreferences.recentRooms.firstOrNull()?.toString().orEmpty(),
            recentRooms = initialPreferences.recentRooms,
            demoAvailable = sessionRepository.demoAvailable,
            demoLabel = sessionRepository.demoEntryLabel,
        ),
    )

    val uiState: StateFlow<ConnectUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                state.update { current ->
                    current.copy(
                        roomInput = current.roomInput.ifBlank {
                            preferences.recentRooms.firstOrNull()?.toString().orEmpty()
                        },
                        recentRooms = preferences.recentRooms,
                    )
                }
            }
        }
    }

    fun updateInput(input: String) {
        state.update {
            it.copy(
                roomInput = input.filter(Char::isDigit).take(12),
                errorMessage = null,
            )
        }
    }

    fun setInput(input: String) {
        state.update { it.copy(roomInput = input, errorMessage = null) }
    }

    fun buildConnectRequest(useDemo: Boolean): ConnectRequest? {
        val normalized = state.value.roomInput.trim()
        val roomId = normalized.toLongOrNull()
        if (!ROOM_ID_REGEX.matches(normalized) || roomId == null) {
            state.update { it.copy(errorMessage = "请输入正确的直播间号") }
            return null
        }
        state.update { it.copy(errorMessage = null) }
        return ConnectRequest(roomId = roomId, useDemo = useDemo)
    }

    private companion object {
        val ROOM_ID_REGEX = Regex("[1-9][0-9]{0,11}")
    }
}
