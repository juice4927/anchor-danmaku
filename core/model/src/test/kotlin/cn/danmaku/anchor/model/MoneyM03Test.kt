package cn.danmaku.anchor.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyM03Test {

    @Test
    fun `M-03 gold coin totals convert to exact cny without float math`() {
        val converted = Money.fromGoldCoin(99_999L)

        assertThat(converted.milliYuan).isEqualTo(99_999L)
        assertThat(converted.toCanonicalString()).isEqualTo("99.999")
        assertThat(converted.toDisplayString()).isEqualTo("100")
    }
}
