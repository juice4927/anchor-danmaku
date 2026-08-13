package cn.danmaku.anchor.model

data class RoomInfo(
    val inputRoomId: Long,
    val roomId: Long,
    val liveStatus: LiveStatus = LiveStatus.UNKNOWN,
)

enum class LiveStatus {
    LIVE,
    NOT_LIVE,
    ROUND_PLAY,
    UNKNOWN,
}
