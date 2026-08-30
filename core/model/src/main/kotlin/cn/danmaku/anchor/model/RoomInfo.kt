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
    UNKNOWN;

    companion object {
        /** 映射 B站 room/live 接口的 live_status 数值（0=未开播 1=直播中 2=轮播）。 */
        fun fromBiliLiveStatus(value: Int?): LiveStatus = when (value) {
            0 -> NOT_LIVE
            1 -> LIVE
            2 -> ROUND_PLAY
            else -> UNKNOWN
        }
    }
}
