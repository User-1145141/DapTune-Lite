package com.weich.daptune.platform.dap

import com.weich.daptune.core.model.DapBandPlan
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class DapProtocolTest {
    @Test
    fun profilePayload_matchesXiaomiDolbyLayout() {
        val curve = IntArray(DapBandPlan.bandCount) { it - 10 }

        val payload = DapProtocol.createProfileCurvePayload(profile = 3, gainsQ4 = curve)
        val words = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()

        assertEquals(DapProtocol.commandSetProfileParameter, words.get(0))
        assertEquals(21, words.get(1))
        assertEquals(3, words.get(2))
        assertEquals(DapProtocol.parameterGraphicEqualizerBandGains, words.get(3))
        curve.forEachIndexed { index, value -> assertEquals(value, words.get(index + 4)) }
    }

    @Test
    fun profileReadKey_packsParameterAndProfile() {
        assertEquals(0x016e0305, DapProtocol.profileParameterReadKey(profile = 3, parameterId = 110))
    }
}
