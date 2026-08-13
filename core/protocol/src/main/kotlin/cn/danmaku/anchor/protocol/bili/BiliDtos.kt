package cn.danmaku.anchor.protocol.bili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BiliRoomInitResponse(
    val code: Int,
    val message: String? = null,
    val ttl: Int? = null,
    val data: BiliRoomInitData? = null,
)

@Serializable
data class BiliRoomInitData(
    @SerialName("room_id")
    val roomId: Long? = null,
    @SerialName("short_id")
    val shortId: Long? = null,
    val uid: Long? = null,
    @SerialName("live_status")
    val liveStatus: Int? = null,
    @SerialName("is_hidden")
    val isHidden: Boolean? = null,
    @SerialName("is_locked")
    val isLocked: Boolean? = null,
    val encrypted: Boolean? = null,
    @SerialName("pwd_verified")
    val pwdVerified: Boolean? = null,
)

@Serializable
data class BiliDanmuInfoResponse(
    val code: Int,
    val message: String? = null,
    val ttl: Int? = null,
    val data: BiliDanmuInfoData? = null,
)

@Serializable
data class BiliDanmuInfoData(
    val token: String? = null,
    @SerialName("host_list")
    val hostList: List<BiliHostDto> = emptyList(),
)

@Serializable
data class BiliDanmuConfResponse(
    val code: Int,
    val message: String? = null,
    val data: BiliDanmuConfData? = null,
)

@Serializable
data class BiliDanmuConfData(
    val token: String? = null,
    @SerialName("host_server_list")
    val hostServerList: List<BiliHostDto> = emptyList(),
)

@Serializable
data class BiliWbiNavResponse(
    val code: Int,
    val message: String? = null,
    val ttl: Int? = null,
    val data: BiliWbiNavData? = null,
)

@Serializable
data class BiliWbiNavData(
    @SerialName("wbi_img")
    val wbiImg: BiliWbiImg? = null,
)

@Serializable
data class BiliWbiImg(
    @SerialName("img_url")
    val imgUrl: String? = null,
    @SerialName("sub_url")
    val subUrl: String? = null,
)

@Serializable
data class BiliHostDto(
    val host: String? = null,
    val port: Int? = null,
    @SerialName("ws_port")
    val wsPort: Int? = null,
    @SerialName("wss_port")
    val wssPort: Int? = null,
)

@Serializable
data class BiliAuthReply(
    val code: Int? = null,
)
