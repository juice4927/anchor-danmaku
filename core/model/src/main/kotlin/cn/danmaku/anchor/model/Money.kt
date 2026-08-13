package cn.danmaku.anchor.model

import java.math.BigDecimal
import java.math.RoundingMode

class Money private constructor(
    val milliYuan: Long,
) : Comparable<Money> {

    override fun compareTo(other: Money): Int = milliYuan.compareTo(other.milliYuan)

    fun toCanonicalString(): String = milliYuan.toYuanDecimal()
        .stripTrailingZeros()
        .toPlainString()
        .ifBlank { "0" }

    fun toDisplayString(): String = milliYuan.toYuanDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .ifBlank { "0" }

    override fun equals(other: Any?): Boolean = other is Money && milliYuan == other.milliYuan

    override fun hashCode(): Int = milliYuan.hashCode()

    override fun toString(): String = "Money(${toCanonicalString()} CNY)"

    companion object {
        val ZERO: Money = Money(0L)

        fun fromGoldCoin(totalCoin: Long): Money = Money(totalCoin.coerceAtLeast(0L))

        fun fromWholeCny(amount: Long): Money = Money(amount.coerceAtLeast(0L) * 1_000L)

        fun fromCny(value: String): Money {
            val parsed = value.trim().ifEmpty { "0" }.toBigDecimal()
            val milliYuan = parsed
                .movePointRight(3)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            return Money(milliYuan.coerceAtLeast(0L))
        }
    }
}

private fun Long.toYuanDecimal(): BigDecimal = BigDecimal.valueOf(this, 3)
