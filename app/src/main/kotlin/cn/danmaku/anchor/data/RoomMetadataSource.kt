package cn.danmaku.anchor.data

import cn.danmaku.anchor.model.RoomMetadata
import cn.danmaku.anchor.protocol.bili.BiliRoomApi

/** 读侧只读数据的轻量接入点：为"最近连接"列表拉取主播名与开播状态。 */
interface RoomMetadataSource {
    /** 拉取单个房间的展示元数据；网络/服务不可达时抛异常，由调用方决定降级。 */
    suspend fun loadRoomMetadata(roomId: Long): RoomMetadata
}

class BiliRoomMetadataSource(
    private val api: BiliRoomApi,
) : RoomMetadataSource {
    override suspend fun loadRoomMetadata(roomId: Long): RoomMetadata = api.getRoomMetadata(roomId)
}
