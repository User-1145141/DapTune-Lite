package com.weich.daptune.data

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurveBlobCodecTest {
    @Test
    fun encodeDecode_roundTripsSignedQ4Values() {
        val values = IntArray(DapBandPlan.bandCount) { index -> -160 + index * 16 }
        val curve = EqCurve.ofQ4(values)

        val encoded = CurveBlobCodec.encode(curve)
        val decoded = CurveBlobCodec.decode(encoded)

        assertEquals(curve, decoded)
        assertEquals(DapBandPlan.bandCount * Short.SIZE_BYTES, encoded.size)
        assertEquals(values.first().toShort(), ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).short)
    }

    @Test
    fun decode_rejectsWrongBlobLength() {
        assertThrows(IllegalArgumentException::class.java) {
            CurveBlobCodec.decode(ByteArray(3))
        }
    }
}
