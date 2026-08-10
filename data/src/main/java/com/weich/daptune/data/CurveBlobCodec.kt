package com.weich.daptune.data

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object CurveBlobCodec {
    private const val LegacyBytesPerGain = Short.SIZE_BYTES
    private const val BytesPerGain = Int.SIZE_BYTES
    private const val LegacySize = DapBandPlan.bandCount * LegacyBytesPerGain
    private const val ExpectedSize = DapBandPlan.bandCount * BytesPerGain

    fun encode(curve: EqCurve): ByteArray {
        val buffer = ByteBuffer.allocate(ExpectedSize).order(ByteOrder.LITTLE_ENDIAN)
        curve.toQ4Array().forEach(buffer::putInt)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): EqCurve {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val gains = when (bytes.size) {
            LegacySize -> IntArray(DapBandPlan.bandCount) { buffer.short.toInt() }
            ExpectedSize -> IntArray(DapBandPlan.bandCount) { buffer.int }
            else -> throw IllegalArgumentException("Invalid curve blob size: ${bytes.size}")
        }
        return EqCurve.ofQ4(gains)
    }
}
