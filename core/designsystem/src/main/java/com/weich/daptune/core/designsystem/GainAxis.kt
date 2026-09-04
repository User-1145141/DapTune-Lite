package com.weich.daptune.core.designsystem

import com.weich.daptune.core.model.EqCurve
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
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
    gainAxisForMinimum(curve.toQ4List().min())

internal fun gainAxisForMinimum(minimumQ4: Int): GainAxis {
    val defaultMinimumQ4 = -EqCurve.MAX_BOOST_Q4
    val needsHeadroom = minimumQ4 < defaultMinimumQ4 + AxisHeadroomQ4
    val requiredMinimumQ4 = if (needsHeadroom) {
        (minimumQ4.toLong() - AxisHeadroomQ4)
            .coerceAtLeast(Int.MIN_VALUE.toLong())
    } else {
        defaultMinimumQ4.toLong()
    }
    val requiredRangeQ4 = EqCurve.MAX_BOOST_Q4.toLong() - requiredMinimumQ4
    val majorStepQ4 = niceMajorStepQ4(requiredRangeQ4)
    val alignedMinimumQ4 =
        (Math.floorDiv(requiredMinimumQ4, majorStepQ4.toLong()) * majorStepQ4.toLong())
            .coerceIn(EqCurve.MIN_GAIN_Q4.toLong(), Int.MAX_VALUE.toLong())
            .toInt()
    val minorStepQ4 =
        (ceil(majorStepQ4.toDouble() / 10.0 / GainStepQ4) * GainStepQ4)
            .roundToInt()
            .coerceAtLeast(GainStepQ4)
    return GainAxis(
        maximumQ4 = EqCurve.MAX_BOOST_Q4,
        minimumQ4 = alignedMinimumQ4,
        majorStepQ4 = majorStepQ4,
        minorStepQ4 = minorStepQ4,
    )
}

private fun niceMajorStepQ4(rangeQ4: Long): Int {
    val rawStepDb = rangeQ4.toDouble() / EqCurve.Q4_PER_DB / TargetMajorIntervals
    val magnitude = 10.0.pow(floor(log10(rawStepDb)))
    val normalized = rawStepDb / magnitude
    val factor = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return (factor * magnitude * EqCurve.Q4_PER_DB)
        .coerceIn(MinimumMajorStepQ4.toDouble(), Int.MAX_VALUE.toDouble())
        .roundToInt()
}

internal const val GainStepQ4 = 1
private const val AxisHeadroomQ4 = EqCurve.Q4_PER_DB
private const val MinimumMajorStepQ4 = 5 * EqCurve.Q4_PER_DB
private const val TargetMajorIntervals = 7.0
