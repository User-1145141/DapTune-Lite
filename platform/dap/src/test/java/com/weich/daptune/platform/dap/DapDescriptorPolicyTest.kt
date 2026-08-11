package com.weich.daptune.platform.dap

import com.weich.daptune.core.model.DapCurveReadbackSupport
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class DapDescriptorPolicyTest {
    @Test
    fun proxyDap_supportsCurveReadback() {
        val support = DapDescriptorPolicy.curveReadbackSupport(
            typeUuid = UUID.fromString("fa81dbde-588b-11ed-9b6a-0242ac120002"),
            effectName = "DAP",
        )

        assertEquals(DapCurveReadbackSupport.SUPPORTED, support)
    }

    @Test
    fun directOffloadDap_isSetterOnly() {
        val support = DapDescriptorPolicy.curveReadbackSupport(
            typeUuid = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587"),
            effectName = "DAP_offload",
        )

        assertEquals(DapCurveReadbackSupport.UNAVAILABLE, support)
    }

    @Test
    fun unknownDescriptor_defaultsToSetterOnly() {
        val support = DapDescriptorPolicy.curveReadbackSupport(
            typeUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            effectName = "DAP",
        )

        assertEquals(DapCurveReadbackSupport.UNAVAILABLE, support)
    }
}
