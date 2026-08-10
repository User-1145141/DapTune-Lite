package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltInPresetsTest {
    @Test
    fun turnerSpeakerPreset_keepsMeasuredTwentyBandConversion() {
        val preset = BuiltInPresets.all.single { it.id == "builtin.turner_speaker" }

        assertEquals("79 扬声器", preset.name)
        assertEquals(5, preset.sortOrder)
        assertEquals(DapBandPlan.bandCount, preset.curve.toQ4Array().size)
        assertArrayEquals(
            intArrayOf(
                160, 160, 160, 130, 49, -34, -50, -7, 35, 40,
                -23, -103, -121, -112, -67, 14, 64, 64, 64, 64,
            ),
            preset.curve.toQ4Array(),
        )
    }
}
