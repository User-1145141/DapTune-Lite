package com.weich.daptune.core.designsystem

import com.weich.daptune.core.model.EqCurve
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared vertical scale for the overview and all 20 band controls. */
class GainAxis internal constructor(
    val maximumQ4: Int,
    val minimumQ4: Int,
    val majorStepQ4: Int,
    val minorStepQ4: Int,
) {
    init {
        require(maximumQ4 > minimumQ4)
        require(majorStepQ4 > 0)
        require(minorStepQ4 > 0)
    }

    val rangeQ4: Long
        get() = maximumQ4.toLong() - minimumQ4.toLong()

    val accessibilitySteps: Int
        get() = (rangeQ4 / GainStepQ4 - 1L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun fractionFor(gainQ4: Int): Float =
        ((maximumQ4.toDouble() - gainQ4.toDouble()) / rangeQ4.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()

    fun majorTicksQ4(): List<Int> = buildList {
        var positive = maximumQ4.toLong()
        while (positive > 0L) {
            add(positive.toInt())
            positive -= majorStepQ4.toLong()
        }
        if (lastOrNull() != 0) add(0)

        var negative = -majorStepQ4.toLong()
        while (negative > minimumQ4.toLong()) {
            add(negative.toInt())
            negative -= majorStepQ4.toLong()
        }
        if (lastOrNull() != minimumQ4) add(minimumQ4)
    }
}

fun gainAxisFor(curve: EqCurve): GainAxis =
    gainAxisForExtremes(
        minimumQ4 = curve.toQ4List().min(),
        maximumQ4 = curve.toQ4List().max(),
    )

internal fun gainAxisForMinimum(minimumQ4: Int): GainAxis =
    gainAxisForExtremes(minimumQ4 = minimumQ4, maximumQ4 = 0)

private fun gainAxisForExtremes(
    minimumQ4: Int,
    maximumQ4: Int,
): GainAxis {
    val maximumGainQ4 = EqCurve.MAX_BOOST_Q4
    val minimumGainQ4 = EqCurve.MIN_GAIN_Q4
    val maxAbsQ4 = maxOf(
        kotlin.math.abs(minimumQ4.toLong()),
        kotlin.math.abs(maximumQ4.toLong()),
    ).coerceAtMost(maximumGainQ4.toLong())

    // Keep the default view compact and symmetric. Expand in 6 dB increments when the
    // actual curve needs more room, while never exceeding the real ±36 dB edit range.
    val minimumVisibleDb = DefaultVisibleAxisDb
    val stepDb = AxisExpansionDb
    val requiredVisibleDb = kotlin.math.ceil(
        maxOf(minimumVisibleDb.toDouble(), maxAbsQ4.toDouble() / EqCurve.Q4_PER_DB) / stepDb,
    ).toInt() * stepDb
    val visibleDb = requiredVisibleDb.coerceIn(minimumVisibleDb, EqCurve.MAX_BOOST_DB)
    val maximumQ4 = visibleDb * EqCurve.Q4_PER_DB
    val minimumQ4 = -maximumQ4

    return GainAxis(
        maximumQ4 = maximumQ4,
        minimumQ4 = minimumQ4.coerceAtLeast(minimumGainQ4),
        majorStepQ4 = stepDb * EqCurve.Q4_PER_DB,
        minorStepQ4 = EqCurve.Q4_PER_DB,
    )
}

private const val DefaultVisibleAxisDb = 6
private const val AxisExpansionDb = 6

internal const val GainStepQ4 = 1
