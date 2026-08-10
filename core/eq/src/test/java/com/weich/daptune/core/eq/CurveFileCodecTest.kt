package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveFileCodecTest {
    @Test
    fun graphicEq_usesLogFrequencyInterpolation() {
        val imported = CurveFileCodec.import(
            "GraphicEQ: 47 0; 470 10; 4700 0; 19688 -2",
            "sample.txt",
        )

        assertEquals(DapBandPlan.bandCount, imported.gainsDb.size)
        assertEquals(0.0, imported.gainsDb.first(), 0.0001)
        assertTrue(imported.gainsDb[4] > 9.9)
        assertEquals(-2.0, imported.gainsDb.last(), 0.0001)
    }

    @Test
    fun parametricPeak_hasRequestedGainNearCenter() {
        val imported = CurveFileCodec.import(
            "Preamp: -2 dB\nFilter 1: ON PK Fc 3000 Hz Gain 6 dB Q 1.000",
            "ParametricEQ.txt",
        )

        val centerIndex = DapBandPlan.frequenciesHz.indexOf(3000)
        assertEquals(4.0, imported.gainsDb[centerIndex], 0.01)
    }

    @Test
    fun nativeFormat_roundTripsQ4Exactly() {
        val curve = EqCurve.ofQ4(List(DapBandPlan.bandCount) { it * 7 - 60 })
        val encoded = CurveFileCodec.exportNative("测试", curve)

        val decoded = CurveFileCodec.import(encoded, "test.json")
        val converted = EqTransforms.quantize(decoded.gainsDb, OverflowMode.CLAMP).curve

        assertEquals(curve, converted)
    }
}
