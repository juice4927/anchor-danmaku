package cn.danmaku.anchor.protocol.bili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeDiagnosticsSd01ToSd03Test {
    @Test
    fun sd01_negativeDeltaIsRejected() {
        assertThat(runCatching { SafeDiagnostics().increment("x", -1) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun sd02_prefixCountsAggregateMatchingKeys() {
        val diagnostics = SafeDiagnostics()
        diagnostics.increment("unknown_command:ONE", 2)
        diagnostics.increment("unknown_command:TWO", 3)
        diagnostics.increment("malformed_frame", 4)
        val snapshot = diagnostics.toGatewayDiagnostics()
        assertThat(snapshot.unknownCommandCount).isEqualTo(5)
        assertThat(snapshot.malformedFrameCount).isEqualTo(4)
    }

    @Test
    fun sd03_prefixCountsClampLargeValues() {
        val diagnostics = SafeDiagnostics()
        diagnostics.increment("oversized_payload", Int.MAX_VALUE.toLong() + 1)
        assertThat(diagnostics.toGatewayDiagnostics().oversizedFrameCount).isEqualTo(Int.MAX_VALUE)
    }
}
