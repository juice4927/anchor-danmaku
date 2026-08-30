package cn.danmaku.anchor.model

/**
 * 最近连接列表在某几个房间维度上的轻量聚合，供连接页在不落库的情况下展示主播名与开播状态。
 * 若批量接口不可达，ownerName 为 null、liveStatus 为 UNKNOWN，UI 需优雅降级为仅显示房间号。
 */
data class RoomMetadata(
    val roomId: Long,
    val ownerName: String? = null,
    val liveStatus: LiveStatus = LiveStatus.UNKNOWN,
)
