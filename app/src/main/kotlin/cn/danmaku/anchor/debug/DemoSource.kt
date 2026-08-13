package cn.danmaku.anchor.debug

import cn.danmaku.anchor.AnchorConnectionState
import cn.danmaku.anchor.model.LiveMessage

sealed interface DemoScriptEvent {
    data class Delay(val millis: Long) : DemoScriptEvent
    data class Connection(val state: AnchorConnectionState) : DemoScriptEvent
    data class Message(val message: LiveMessage) : DemoScriptEvent
}

interface DemoSource {
    val isAvailable: Boolean
    val entryLabel: String?
    fun scriptFor(roomId: Long): List<DemoScriptEvent>
}
