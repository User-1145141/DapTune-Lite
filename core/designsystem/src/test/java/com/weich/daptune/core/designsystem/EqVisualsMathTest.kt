package com.weich.daptune.core.designsystem

import com.weich.daptune.core.model.EqCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqVisualsMathTest {
    @Test
    fun `track endpoints and center map to exact gains`() {
        assertEquals(EqCurve.MAX_GAIN_Q4, gainAt(y = 12f))
        assertEquals(0, gainAt(y = 150f))
        assertEquals(-EqCurve.MAX_GAIN_Q4, gainAt(y = 288f))
    }

    @Test
    fun `track position is always clamped and snapped to half decibel`() {
        assertEquals(EqCurve.MAX_GAIN_Q4, gainAt(y = -1_000f))
        assertEquals(-EqCurve.MAX_GAIN_Q4, gainAt(y = 1_000f))
        assertEquals(0, gainAt(y = 153f) % (EqCurve.Q4_PER_DB / 2))
    }

    @Test
    fun `gain to track position is the inverse at every step`() {
        for (gainQ4 in -EqCurve.MAX_GAIN_Q4..EqCurve.MAX_GAIN_Q4 step EqCurve.Q4_PER_DB / 2) {
            val y = trackYForGainQ4(
                gainQ4 = gainQ4,
                trackHeightPx = TrackHeight,
                verticalInsetPx = TrackInset,
            )
            assertEquals(gainQ4, gainAt(y))
        }
    }

    @Test
    fun `curve point hit selects its exact band`() {
        val band = 12
        val x = HorizontalInset +
            (ChartWidth - HorizontalInset * 2f) * band / 19f
        assertEquals(
            band,
            nearestCurveBandAt(
                curve = EqCurve.flat(),
                tapX = x,
                tapY = ChartHeight / 2f,
                widthPx = ChartWidth,
                heightPx = ChartHeight,
                horizontalInsetPx = HorizontalInset,
                verticalInsetPx = VerticalInset,
                hitRadiusPx = HitRadius,
            ),
        )
    }

    @Test
    fun `curve tap away from every point does not select`() {
        assertNull(
            nearestCurveBandAt(
                curve = EqCurve.flat(),
                tapX = ChartWidth / 2f,
                tapY = 0f,
                widthPx = ChartWidth,
                heightPx = ChartHeight,
                horizontalInsetPx = HorizontalInset,
                verticalInsetPx = VerticalInset,
                hitRadiusPx = HitRadius,
            ),
        )
    }

    private fun gainAt(y: Float): Int = gainQ4ForTrackPosition(
        yPx = y,
        trackHeightPx = TrackHeight,
        verticalInsetPx = TrackInset,
    )

    private companion object {
        const val TrackHeight = 300f
        const val TrackInset = 12f
        const val ChartWidth = 360f
        const val ChartHeight = 212f
        const val HorizontalInset = 4f
        const val VerticalInset = 8f
        const val HitRadius = 24f
    }
}
