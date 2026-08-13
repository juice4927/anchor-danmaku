package cn.danmaku.anchor.debug

import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.AnchorMessage

sealed interface DemoScriptEvent {
    data class Delay(val millis: Long) : DemoScriptEvent
    data class Connection(val state: AnchorConnectionState) : DemoScriptEvent
    data class Message(val message: AnchorMessage) : DemoScriptEvent
}

interface DemoSource {
    val isAvailable: Boolean
    val entryLabel: String?
    fun scriptFor(roomId: Long): List<DemoScriptEvent>
}
