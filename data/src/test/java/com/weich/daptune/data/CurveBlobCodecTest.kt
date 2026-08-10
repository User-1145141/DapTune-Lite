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
        val values = IntArray(DapBandPlan.bandCount) { index -> -40_000 + index * 2_000 }
        val curve = EqCurve.ofQ4(values)

        val encoded = CurveBlobCodec.encode(curve)
        val decoded = CurveBlobCodec.decode(encoded)

        assertEquals(curve, decoded)
        assertEquals(DapBandPlan.bandCount * Int.SIZE_BYTES, encoded.size)
        assertEquals(values.first(), ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun decode_supportsLegacySignedShortBlobs() {
        val values = IntArray(DapBandPlan.bandCount) { index -> -280 + index * 12 }
        val legacy = ByteBuffer.allocate(DapBandPlan.bandCount * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putShort(it.toShort()) } }
            .array()

        val decoded = CurveBlobCodec.decode(legacy)

        assertEquals(EqCurve.ofQ4(values), decoded)
    }

    @Test
    fun decode_rejectsWrongBlobLength() {
        assertThrows(IllegalArgumentException::class.java) {
            CurveBlobCodec.decode(ByteArray(3))
        }
    }
}
