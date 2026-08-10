package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqTransformsTest {
    @Test
    fun peakToZero_placesMaximumAtZeroAndKeepsRangeValid() {
        val source = EqCurve.ofQ4(List(DapBandPlan.bandCount) { index -> index * 8 - 80 })

        val result = EqTransforms.peakToZero(source)

        assertEquals(0, result.toQ4List().max())
        assertTrue(result.toQ4List().min() >= -EqCurve.MAX_GAIN_Q4)
    }

    @Test
    fun meanToZero_hasAtMostOneQ4RoundingError() {
        val source = EqCurve.ofQ4(List(DapBandPlan.bandCount) { index -> (index - 7) * 5 })

        val result = EqTransforms.meanToZero(source)

        assertTrue(kotlin.math.abs(result.toQ4List().sum()) <= DapBandPlan.bandCount)
    }

    @Test
    fun fit_preservesShapeAndLimitsExtremes() {
        val source = List(DapBandPlan.bandCount) { index -> -20.0 + index * 2.0 }

        val conversion = EqTransforms.quantize(source, OverflowMode.FIT)

        assertTrue(conversion.adjusted)
        assertEquals(-EqCurve.MAX_GAIN_Q4, conversion.curve.toQ4List().min())
        assertTrue(conversion.curve.toQ4List().max() <= EqCurve.MAX_GAIN_Q4)
    }
}
