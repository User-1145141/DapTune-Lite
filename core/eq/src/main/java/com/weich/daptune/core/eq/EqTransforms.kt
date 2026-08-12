package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import kotlin.math.floor
import kotlin.math.roundToInt

enum class OverflowMode {
    /** Scale the entire curve when its positive peak exceeds +10 dB. */
    FIT,

    /** Clamp only values above +10 dB; attenuation remains unchanged. */
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
        val gainsQ4 = curve.toQ4List()
        val peakQ4 = gainsQ4.max()
        return EqCurve.ofQ4(
            gainsQ4.map { gainQ4 ->
                (gainQ4.toLong() - peakQ4.toLong())
                    .coerceAtLeast(Int.MIN_VALUE.toLong())
                    .toInt()
            },
        )
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

    /**
     * Hard-limits every band above [thresholdDb] without changing lower bands.
     *
     * The product has no artificial attenuation floor, so negative thresholds are valid.
     * Dolby's +10 dB boost ceiling remains the only user-facing upper bound.
     */
    fun limitMaximum(curve: EqCurve, thresholdDb: Double): EqCurve {
        require(thresholdDb.isFinite()) { "Threshold must be finite" }
        require(thresholdDb <= EqCurve.MAX_BOOST_DB) {
            "Threshold exceeds +${EqCurve.MAX_BOOST_DB} dB"
        }
        // Round toward attenuation so the represented Q4 value never exceeds the requested cap.
        val thresholdQ4 = floor(thresholdDb * EqCurve.Q4_PER_DB)
            .toLong()
            .coerceAtLeast(Int.MIN_VALUE.toLong())
            .toInt()
        return EqCurve.ofQ4(curve.toQ4List().map { it.coerceAtMost(thresholdQ4) })
    }

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
        val limit = EqCurve.MAX_BOOST_DB.toDouble()
        val exceedsLimit = maximum > limit
        val adjusted = if (!exceedsLimit) {
            gainsDb
        } else {
            when (overflowMode) {
                OverflowMode.CLAMP -> gainsDb.map { it.coerceAtMost(limit) }
                OverflowMode.FIT -> {
                    val factor = limit / maximum
                    gainsDb.map { it * factor }
                }
            }
        }

        val q4 = adjusted.map { gain ->
            (gain * EqCurve.Q4_PER_DB)
                .roundToInt()
                .coerceAtMost(EqCurve.MAX_BOOST_Q4)
        }
        return CurveConversion(
            curve = EqCurve.ofQ4(q4),
            adjusted = exceedsLimit,
            sourceMinimumDb = minimum,
            sourceMaximumDb = maximum,
        )
    }
}
