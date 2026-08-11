package com.weich.daptune.platform.dap

import com.weich.daptune.core.model.DapCurveReadbackSupport
import java.util.UUID

/**
 * Classifies behavior from the effect protocol exposed by the descriptor, not
 * from a phone model or firmware allow-list.
 *
 * Xiaomi's proxy DAP implements the synthetic parameter-110 read key used for
 * transactional verification. The direct DAP_offload effect accepts the same
 * setter payload but returns a zero-filled caller buffer for that read key.
 * Treat every unknown descriptor conservatively as setter-only: a false claim
 * of readback support would turn those zeroes into a destructive rollback.
 */
internal object DapDescriptorPolicy {
    private val ProxyDapTypeUuid = UUID.fromString("fa81dbde-588b-11ed-9b6a-0242ac120002")
    private val DirectOffloadTypeUuid = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587")

    fun curveReadbackSupport(typeUuid: UUID, effectName: String?): DapCurveReadbackSupport = when {
        typeUuid == ProxyDapTypeUuid -> DapCurveReadbackSupport.SUPPORTED
        typeUuid == DirectOffloadTypeUuid -> DapCurveReadbackSupport.UNAVAILABLE
        effectName.equals("DAP_offload", ignoreCase = true) -> DapCurveReadbackSupport.UNAVAILABLE
        else -> DapCurveReadbackSupport.UNAVAILABLE
    }
}
