package cn.danmaku.anchor.debug

class NoDemoSource : DemoSource {
    override val isAvailable: Boolean = false
    override val entryLabel: String? = null

    override fun scriptFor(roomId: Long): List<DemoScriptEvent> = emptyList()
}
