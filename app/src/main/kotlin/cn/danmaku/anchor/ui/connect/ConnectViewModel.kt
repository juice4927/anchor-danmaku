package cn.danmaku.anchor.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.danmaku.anchor.data.PreferencesStore
import cn.danmaku.anchor.data.RoomMetadataSource
import cn.danmaku.anchor.SessionCoordinator
import cn.danmaku.anchor.model.RoomMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val roomInput: String = "",
    val errorMessage: String? = null,
    val recentRooms: List<RoomMetadata> = emptyList(),
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
    private val roomMetadataSource: RoomMetadataSource,
    initialRoomId: Long? = null,
) : ViewModel() {
    private val initialPreferences = preferencesRepository.preferences.value
    private val state = MutableStateFlow(
        ConnectUiState(
            roomInput = initialRoomId?.toString()
                ?: initialPreferences.recentRooms.firstOrNull()?.toString().orEmpty(),
            recentRooms = initialPreferences.recentRooms.map { RoomMetadata(roomId = it) },
            demoAvailable = sessionRepository.demoAvailable,
            demoLabel = sessionRepository.demoEntryLabel,
        ),
    )
    private val metadataCache = mutableMapOf<Long, RoomMetadata>()

    val uiState: StateFlow<ConnectUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                state.update { current ->
                    current.copy(
                        roomInput = current.roomInput.ifBlank {
                            preferences.recentRooms.firstOrNull()?.toString().orEmpty()
                        },
                    )
                }
                refreshAllMetadata(preferences.recentRooms)
            }
        }
    }

    /** 顺序/并行拉取最近房间的展示元数据；失败项降级为仅房间号。 */
    private suspend fun refreshAllMetadata(roomIds: List<Long>) {
        if (roomIds.isEmpty()) {
            state.update { it.copy(recentRooms = emptyList()) }
            return
        }
        val resolved = coroutineScope {
            roomIds.map { roomId ->
                async {
                    metadataCache[roomId] ?: runCatching { roomMetadataSource.loadRoomMetadata(roomId) }
                        .getOrElse { RoomMetadata(roomId = roomId) }
                        .also { metadataCache[roomId] = it }
                }
            }.map { it.await() }
        }
        state.update { it.copy(recentRooms = resolved) }
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
