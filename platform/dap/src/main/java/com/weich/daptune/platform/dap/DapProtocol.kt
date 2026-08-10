package com.weich.daptune.platform.dap

import com.weich.daptune.core.model.DapBandPlan
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object DapProtocol {
    const val effectParameterCommand = 5
    const val commandSetProfileParameter = 0x01000000
    const val commandGetProfileCount = 0x03000000
    const val commandSaveSettings = 0x09000000
    const val commandGetCurrentProfile = 0x0a000000
    const val commandGetDapOn = 0x00000000
    const val parameterGraphicEqualizerBandGains = 110

    fun queryKey(command: Int): Int = command + effectParameterCommand

    fun profileParameterReadKey(profile: Int, parameterId: Int): Int =
        commandSetProfileParameter + effectParameterCommand + (parameterId shl 16) + (profile shl 8)

    fun createProfileCurvePayload(profile: Int, gainsQ4: IntArray): ByteArray {
        require(profile >= 0)
        require(gainsQ4.size == DapBandPlan.bandCount)
        return ByteBuffer.allocate((gainsQ4.size + 4) * Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(commandSetProfileParameter)
                putInt(gainsQ4.size + 1)
                putInt(profile)
                putInt(parameterGraphicEqualizerBandGains)
                gainsQ4.forEach(::putInt)
            }
            .array()
    }

    fun createSavePayload(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(commandSaveSettings)
        .array()

    fun createQueryBuffer(command: Int): ByteArray = ByteBuffer.allocate(3 * Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(command)
        .array()

    fun createCurveReadBuffer(): ByteArray = ByteArray((DapBandPlan.bandCount + 2) * Int.SIZE_BYTES)

    fun readFirstInt(bytes: ByteArray): Int = ByteBuffer.wrap(bytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int

    fun readCurve(bytes: ByteArray): IntArray {
        require(bytes.size >= DapBandPlan.bandCount * Int.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(DapBandPlan.bandCount) { buffer.int }
    }
}
