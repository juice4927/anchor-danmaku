package cn.danmaku.anchor.protocol.bili

import cn.danmaku.anchor.model.LiveMessage
import cn.danmaku.anchor.model.Money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class BiliCommandMapper(
    private val diagnostics: SafeDiagnostics = SafeDiagnostics(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun map(packet: BiliPacket, roomId: Long, receivedAtMillis: Long): List<LiveMessage> {
        if (packet.operation != BiliPacket.OP_MESSAGE) {
            return emptyList()
        }
        val payload = runCatching { json.parseToJsonElement(packet.body.decodeToString()) }.getOrElse {
            diagnostics.increment("malformed_json")
            return emptyList()
        }
        return map(payload, roomId, receivedAtMillis)
    }

    fun map(payload: JsonElement, roomId: Long, receivedAtMillis: Long): List<LiveMessage> {
        val root = payload as? JsonObject ?: run {
            diagnostics.increment("malformed_json")
            return emptyList()
        }
        val cmd = root["cmd"]?.jsonPrimitive?.contentOrNull ?: run {
            diagnostics.increment("missing_cmd")
            return emptyList()
        }
        return when (val normalized = cmd.substringBefore(':')) {
            "DANMU_MSG" -> mapDanmaku(root, roomId, receivedAtMillis)?.let(::listOf).orEmpty()
            "SUPER_CHAT_MESSAGE" -> mapSuperChat(root, roomId, receivedAtMillis)?.let(::listOf).orEmpty()
            "SEND_GIFT" -> mapGift(root, roomId, receivedAtMillis)?.let(::listOf).orEmpty()
            "GUARD_BUY" -> mapGuard(root, roomId, receivedAtMillis)?.let(::listOf).orEmpty()
            else -> {
                diagnostics.increment("unknown_command:$normalized")
                emptyList()
            }
        }
    }

    private fun mapDanmaku(root: JsonObject, roomId: Long, receivedAtMillis: Long): LiveMessage.DanmakuMessage? {
        val info = root["info"] as? JsonArray ?: return malformed("DANMU_MSG")
        val text = info.getOrNull(1).stringValue()
        val user = info.getOrNull(2) as? JsonArray
        val uid = user?.getOrNull(0).longValue()
        val userName = user?.getOrNull(1).stringValue()
        if (text == null || uid == null || userName == null) {
            return malformed("DANMU_MSG")
        }
        val medal = info.getOrNull(3) as? JsonArray
        val medalLevel = medal?.getOrNull(0).intValue()
        val medalName = medal?.getOrNull(1).stringValue()
        val metadata = info.getOrNull(0) as? JsonArray
        val timestampMillis = metadata?.getOrNull(4).longValue()?.times(1_000)
        return LiveMessage.DanmakuMessage(
            id = "danmaku:$roomId:$uid:${timestampMillis ?: receivedAtMillis}:$text",
            roomId = roomId,
            uid = uid,
            userName = userName,
            serverTimestampMillis = timestampMillis,
            receivedAtMillis = receivedAtMillis,
            text = text,
            medalName = medalName,
            medalLevel = medalLevel,
            repeatCount = 1,
        )
    }

    private fun mapSuperChat(root: JsonObject, roomId: Long, receivedAtMillis: Long): LiveMessage.SuperChatMessage? {
        val data = root["data"] as? JsonObject ?: return malformed("SUPER_CHAT_MESSAGE")
        val id = data["id"].stringValue() ?: return malformed("SUPER_CHAT_MESSAGE")
        val price = data["price"].numberValue() ?: return malformed("SUPER_CHAT_MESSAGE")
        val message = data["message"].stringValue() ?: return malformed("SUPER_CHAT_MESSAGE")
        val start = data["start_time"].longValue()?.times(1_000)
        val end = data["end_time"].longValue()?.times(1_000)
        val uid = data["uid"].longValue()
        val userName = (data["user_info"] as? JsonObject)?.get("uname").stringValue()
        return LiveMessage.SuperChatMessage(
            id = id,
            roomId = roomId,
            uid = uid,
            userName = userName,
            serverTimestampMillis = start,
            receivedAtMillis = receivedAtMillis,
            message = message,
            priceCny = Money.fromCny(price),
            startTimeMillis = start,
            endTimeMillis = end,
        )
    }

    private fun mapGift(root: JsonObject, roomId: Long, receivedAtMillis: Long): LiveMessage.GiftMessage? {
        val data = root["data"] as? JsonObject ?: return malformed("SEND_GIFT")
        val uid = data["uid"].longValue()
        val userName = data["uname"].stringValue()
        val giftName = data["giftName"].stringValue() ?: return malformed("SEND_GIFT")
        val count = data["num"].intValue() ?: return malformed("SEND_GIFT")
        val totalCoin = data["total_coin"].longValue() ?: 0L
        val coinType = data["coin_type"].stringValue() ?: return malformed("SEND_GIFT")
        val timestampMillis = data["timestamp"].longValue()?.times(1_000)
        val id = data["tid"].stringValue() ?: "gift:$roomId:${uid ?: 0}:$giftName:${timestampMillis ?: 0L}"
        val estimated = if (coinType == "gold") {
            Money.fromGoldCoin(totalCoin)
        } else {
            null
        }
        return LiveMessage.GiftMessage(
            id = id,
            roomId = roomId,
            uid = uid,
            userName = userName,
            serverTimestampMillis = timestampMillis,
            receivedAtMillis = receivedAtMillis,
            giftName = giftName,
            count = count,
            totalCoin = totalCoin,
            coinType = coinType,
            estimatedCny = estimated,
        )
    }

    private fun mapGuard(root: JsonObject, roomId: Long, receivedAtMillis: Long): LiveMessage.GuardMessage? {
        val data = root["data"] as? JsonObject ?: return malformed("GUARD_BUY")
        val uid = data["uid"].longValue()
        val userName = data["username"].stringValue()
        val guardLevel = data["guard_level"].intValue() ?: return malformed("GUARD_BUY")
        val count = data["num"].intValue() ?: return malformed("GUARD_BUY")
        val timestampMillis = data["start_time"].longValue()?.times(1_000)
        val id = data["id"].stringValue() ?: data["gift_id"].stringValue() ?: "guard:$roomId:${uid ?: 0}:${timestampMillis ?: 0L}"
        return LiveMessage.GuardMessage(
            id = id,
            roomId = roomId,
            uid = uid,
            userName = userName,
            serverTimestampMillis = timestampMillis,
            receivedAtMillis = receivedAtMillis,
            guardLevel = guardLevel,
            count = count,
        )
    }

    private fun <T> malformed(command: String): T? {
        diagnostics.increment("malformed_command:$command")
        return null
    }

    private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.longValue(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.intValue(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.numberValue(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.longOrNull?.toString() ?: primitive.contentOrNull
    }
}

fun LiveMessage.toSnapshotJson(): JsonObject = when (this) {
    is LiveMessage.DanmakuMessage -> buildJsonObject {
        put("type", "danmaku")
        put("roomId", roomId)
        uid?.let { put("uid", it) }
        userName?.let { put("userName", it) }
        put("text", text)
        medalName?.let { put("medalName", it) }
        medalLevel?.let { put("medalLevel", it) }
        put("repeatCount", repeatCount)
        serverTimestampMillis?.let { put("serverTimestampMillis", it) }
    }

    is LiveMessage.SuperChatMessage -> buildJsonObject {
        put("type", "superChat")
        put("id", id)
        put("roomId", roomId)
        uid?.let { put("uid", it) }
        userName?.let { put("userName", it) }
        put("message", message)
        put("priceCny", priceCny.toCanonicalString())
        startTimeMillis?.let { put("startTimeMillis", it) }
        endTimeMillis?.let { put("endTimeMillis", it) }
    }

    is LiveMessage.GiftMessage -> buildJsonObject {
        put("type", "gift")
        put("id", id)
        put("roomId", roomId)
        uid?.let { put("uid", it) }
        userName?.let { put("userName", it) }
        put("giftName", giftName)
        put("count", count)
        put("totalCoin", totalCoin)
        put("coinType", coinType)
        val estimated = estimatedCny
        if (estimated == null) {
            put("estimatedCny", JsonNull)
        } else {
            put("estimatedCny", estimated.toCanonicalString())
        }
    }

    is LiveMessage.GuardMessage -> buildJsonObject {
        put("type", "guard")
        put("id", id)
        put("roomId", roomId)
        uid?.let { put("uid", it) }
        userName?.let { put("userName", it) }
        put("guardLevel", guardLevel)
        put("count", count)
        serverTimestampMillis?.let { put("serverTimestampMillis", it) }
    }
}
