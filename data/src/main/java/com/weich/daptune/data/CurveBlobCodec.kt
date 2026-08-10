package com.weich.daptune.data

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object CurveBlobCodec {
    private const val BytesPerGain = Short.SIZE_BYTES
    private const val ExpectedSize = DapBandPlan.bandCount * BytesPerGain

    fun encode(curve: EqCurve): ByteArray {
        val buffer = ByteBuffer.allocate(ExpectedSize).order(ByteOrder.LITTLE_ENDIAN)
        curve.toQ4Array().forEach { buffer.putShort(it.toShort()) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): EqCurve {
        require(bytes.size == ExpectedSize) { "Invalid curve blob size: ${bytes.size}" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return EqCurve.ofQ4(IntArray(DapBandPlan.bandCount) { buffer.short.toInt() })
    }
}
