package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import kotlin.math.floor
import kotlin.math.roundToInt

enum class OverflowMode {
    /** Scale the entire curve when either edge of the ±36 dB range is exceeded. */
    FIT,

    /** Clamp values outside the supported -36 dB..+36 dB range. */
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
                    .coerceIn(EqCurve.MIN_GAIN_Q4.toLong(), EqCurve.MAX_BOOST_Q4.toLong())
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
     * The supported curve range is -36 dB..+36 dB, so the threshold must be inside
     * that range.
     */
    fun limitMaximum(curve: EqCurve, thresholdDb: Double): EqCurve {
        require(thresholdDb.isFinite()) { "Threshold must be finite" }
        require(thresholdDb in EqCurve.MIN_GAIN_DB.toDouble()..EqCurve.MAX_BOOST_DB.toDouble()) {
            "Threshold must be within ${EqCurve.MIN_GAIN_DB}..+${EqCurve.MAX_BOOST_DB} dB"
        }
        // Round toward attenuation so the represented Q4 value never exceeds the requested cap.
        val thresholdQ4 = floor(thresholdDb * EqCurve.Q4_PER_DB)
            .toLong()
            .coerceIn(EqCurve.MIN_GAIN_Q4.toLong(), EqCurve.MAX_BOOST_Q4.toLong())
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
        val maxLimit = EqCurve.MAX_BOOST_DB.toDouble()
        val minLimit = EqCurve.MIN_GAIN_DB.toDouble()
        val exceedsLimit = maximum > maxLimit || minimum < minLimit
        val adjusted = if (!exceedsLimit) {
            gainsDb
        } else {
            when (overflowMode) {
                OverflowMode.CLAMP -> gainsDb.map { it.coerceIn(minLimit, maxLimit) }
                OverflowMode.FIT -> {
                    val positiveFactor = if (maximum > maxLimit) maxLimit / maximum else 1.0
                    val negativeFactor = if (minimum < minLimit) minLimit / minimum else 1.0
                    val factor = minOf(positiveFactor, negativeFactor)
                    gainsDb.map { it * factor }
                }
            }
        }

        val q4 = adjusted.map { gain ->
            (gain * EqCurve.Q4_PER_DB)
                .roundToInt()
                .coerceIn(EqCurve.MIN_GAIN_Q4, EqCurve.MAX_BOOST_Q4)
        }
        return CurveConversion(
            curve = EqCurve.ofQ4(q4),
            adjusted = exceedsLimit,
            sourceMinimumDb = minimum,
            sourceMaximumDb = maximum,
        )
    }
}
