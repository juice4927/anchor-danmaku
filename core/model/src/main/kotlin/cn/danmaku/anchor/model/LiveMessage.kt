package cn.danmaku.anchor.model

sealed interface LiveMessage {
    val id: String
    val roomId: Long
    val uid: Long?
    val userName: String?
    val serverTimestampMillis: Long?
    val receivedAtMillis: Long

    data class DanmakuMessage(
        override val id: String,
        override val roomId: Long,
        override val uid: Long?,
        override val userName: String?,
        override val serverTimestampMillis: Long?,
        override val receivedAtMillis: Long,
        val text: String,
        val medalName: String?,
        val medalLevel: Int?,
        val repeatCount: Int = 1,
    ) : LiveMessage

    data class SuperChatMessage(
        override val id: String,
        override val roomId: Long,
        override val uid: Long?,
        override val userName: String?,
        override val serverTimestampMillis: Long?,
        override val receivedAtMillis: Long,
        val message: String,
        val priceCny: Money,
        val startTimeMillis: Long?,
        val endTimeMillis: Long?,
    ) : LiveMessage

    data class GiftMessage(
        override val id: String,
        override val roomId: Long,
        override val uid: Long?,
        override val userName: String?,
        override val serverTimestampMillis: Long?,
        override val receivedAtMillis: Long,
        val giftName: String,
        val count: Int,
        val totalCoin: Long,
        val coinType: String,
        val estimatedCny: Money?,
    ) : LiveMessage

    data class GuardMessage(
        override val id: String,
        override val roomId: Long,
        override val uid: Long?,
        override val userName: String?,
        override val serverTimestampMillis: Long?,
        override val receivedAtMillis: Long,
        val guardLevel: Int,
        val count: Int,
    ) : LiveMessage
}
