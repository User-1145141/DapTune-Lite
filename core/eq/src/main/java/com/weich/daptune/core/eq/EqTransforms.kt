package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import kotlin.math.abs
import kotlin.math.roundToInt

enum class OverflowMode {
    FIT,
    CLAMP,
}

data class CurveConversion(
    val curve: EqCurve,
    val adjusted: Boolean,
    val sourceMinimumDb: Double,
    val sourceMaximumDb: Double,
)

object EqTransforms {
    fun eliminatePositiveGain(curve: EqCurve): EqCurve {
        val gains = curve.toDbList()
        val shift = -maxOf(0.0, gains.max())
        return quantize(gains.map { it + shift }, OverflowMode.FIT).curve
    }

    fun peakToZero(curve: EqCurve): EqCurve {
        val gains = curve.toDbList()
        val peak = gains.max()
        return quantize(gains.map { it - peak }, OverflowMode.FIT).curve
    }

    fun meanToZero(curve: EqCurve): EqCurve {
        val gains = curve.toDbList()
        val mean = gains.average()
        return quantize(gains.map { it - mean }, OverflowMode.FIT).curve
    }

    fun shift(curve: EqCurve, offsetDb: Double, overflowMode: OverflowMode): EqCurve =
        quantize(curve.toDbList().map { it + offsetDb }, overflowMode).curve

    fun scale(curve: EqCurve, factor: Double): EqCurve {
        require(factor.isFinite() && factor >= 0.0)
        return quantize(curve.toDbList().map { it * factor }, OverflowMode.FIT).curve
    }

    fun invert(curve: EqCurve): EqCurve =
        EqCurve.ofQ4(curve.toQ4Array().map { -it })

    fun smooth(curve: EqCurve, passes: Int = 1): EqCurve {
        require(passes in 1..8)
        var values = curve.toDbList().toDoubleArray()
        repeat(passes) {
            val next = values.copyOf()
            for (index in values.indices) {
                val left = values[maxOf(0, index - 1)]
                val center = values[index]
                val right = values[minOf(values.lastIndex, index + 1)]
                next[index] = (left + center * 2.0 + right) / 4.0
            }
            values = next
        }
        return quantize(values.asList(), OverflowMode.FIT).curve
    }

    fun quantize(gainsDb: List<Double>, overflowMode: OverflowMode): CurveConversion {
        require(gainsDb.size == DapBandPlan.bandCount) {
            "Expected ${DapBandPlan.bandCount} gains, got ${gainsDb.size}"
        }
        require(gainsDb.all(Double::isFinite)) { "Curve contains a non-finite gain" }

        val minimum = gainsDb.min()
        val maximum = gainsDb.max()
        val limit = EqCurve.MAX_GAIN_DB.toDouble()
        val exceedsLimit = minimum < -limit || maximum > limit
        val adjusted = if (!exceedsLimit) {
            gainsDb
        } else {
            when (overflowMode) {
                OverflowMode.CLAMP -> gainsDb.map { it.coerceIn(-limit, limit) }
                OverflowMode.FIT -> {
                    val factor = limit / maxOf(abs(minimum), abs(maximum))
                    gainsDb.map { it * factor }
                }
            }
        }

        val q4 = adjusted.map { gain ->
            (gain * EqCurve.Q4_PER_DB)
                .roundToInt()
                .coerceIn(-EqCurve.MAX_GAIN_Q4, EqCurve.MAX_GAIN_Q4)
        }
        return CurveConversion(
            curve = EqCurve.ofQ4(q4),
            adjusted = exceedsLimit,
            sourceMinimumDb = minimum,
            sourceMaximumDb = maximum,
        )
    }
}
