package cn.danmaku.anchor.model

/**
 * 房间在展示层的聚合：供"最近连接"列表与弹幕台顶部状态条不落库地呈现房间标题、主播名与开播状态。
 * 若接口不可达，title/ownerName 为 null、liveStatus 为 UNKNOWN，UI 需优雅降级为仅显示房间号。
 */
data class RoomMetadata(
    val roomId: Long,
    val roomTitle: String? = null,
    val ownerName: String? = null,
    val liveStatus: LiveStatus = LiveStatus.UNKNOWN,
)
