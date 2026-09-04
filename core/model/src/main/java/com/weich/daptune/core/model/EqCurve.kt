package com.weich.daptune.core.model

import kotlin.math.roundToInt

/**
 * Immutable 20-band curve using Dolby's signed Q4 dB representation.
 *
 * The device curve is represented as signed Q4 dB values (1/16 dB per unit).
 * The supported adjustment range is symmetric at -36 dB..+36 dB.
 */
class EqCurve private constructor(
    private val gainsQ4: IntArray,
) {
    init {
        require(gainsQ4.size == DapBandPlan.bandCount) {
            "Expected ${DapBandPlan.bandCount} gains, got ${gainsQ4.size}"
        }
        gainsQ4.forEachIndexed { index, gain ->
            require(gain in MIN_GAIN_Q4..MAX_BOOST_Q4) {
                "Band $index is outside ${MIN_GAIN_DB}..+${MAX_BOOST_DB} dB: $gain Q4"
            }
        }
    }

    operator fun get(index: Int): Int = gainsQ4[index]

    fun gainDb(index: Int): Double = gainsQ4[index].toDouble() / Q4_PER_DB

    fun toQ4Array(): IntArray = gainsQ4.copyOf()

    fun toQ4List(): List<Int> = gainsQ4.asList()

    fun toDbList(): List<Double> = gainsQ4.map { it.toDouble() / Q4_PER_DB }

    fun withGainQ4(index: Int, gainQ4: Int): EqCurve {
        require(index in 0 until DapBandPlan.bandCount)
        require(gainQ4 in MIN_GAIN_Q4..MAX_BOOST_Q4)
        if (gainsQ4[index] == gainQ4) return this
        return ofQ4(gainsQ4.copyOf().also { it[index] = gainQ4 })
    }

    fun stableHash(): Int = gainsQ4.contentHashCode()

    override fun equals(other: Any?): Boolean =
        this === other || other is EqCurve && gainsQ4.contentEquals(other.gainsQ4)

    override fun hashCode(): Int = gainsQ4.contentHashCode()

    override fun toString(): String = "EqCurve(${gainsQ4.joinToString()})"

    companion object {
        const val Q4_PER_DB = 16
        const val MAX_BOOST_DB = 36
        const val MIN_GAIN_DB = -36
        const val MAX_BOOST_Q4 = MAX_BOOST_DB * Q4_PER_DB
        const val MIN_GAIN_Q4 = MIN_GAIN_DB * Q4_PER_DB

        fun flat(): EqCurve = EqCurve(IntArray(DapBandPlan.bandCount))

        fun ofQ4(gains: IntArray): EqCurve = EqCurve(gains.copyOf())

        fun ofQ4(gains: List<Int>): EqCurve = EqCurve(gains.toIntArray())

        fun ofDb(gainsDb: List<Double>): EqCurve {
            require(gainsDb.size == DapBandPlan.bandCount)
            require(gainsDb.all(Double::isFinite)) { "Curve contains a non-finite gain" }
            return EqCurve(
                IntArray(gainsDb.size) { index ->
                    (gainsDb[index] * Q4_PER_DB)
                        .roundToInt()
                        .coerceIn(MIN_GAIN_Q4, MAX_BOOST_Q4)
                },
            )
        }
    }
}

object DapBandPlan {
    val frequenciesHz: IntArray = intArrayOf(
        47,
        141,
        234,
        328,
        469,
        656,
        844,
        1031,
        1313,
        1688,
        2250,
        3000,
        3750,
        4688,
        5813,
        7125,
        9000,
        11250,
        13875,
        19688,
    )

    const val id: String = "dolby-dap-20-v1"
    const val bandCount: Int = 20
}
