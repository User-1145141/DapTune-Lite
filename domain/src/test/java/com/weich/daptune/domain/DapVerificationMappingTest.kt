package com.weich.daptune.domain

import com.weich.daptune.core.model.DapApplyVerification
import com.weich.daptune.core.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Test

class DapVerificationMappingTest {
    @Test
    fun curveReadback_mapsToVerifiedSnapshot() {
        assertEquals(
            VerificationState.VERIFIED,
            DapApplyVerification.CURVE_READBACK.toSnapshotVerification(),
        )
    }

    @Test
    fun setterOnlyWrite_isNeverPersistedAsVerified() {
        assertEquals(
            VerificationState.WRITE_ACCEPTED,
            DapApplyVerification.WRITE_ACCEPTED.toSnapshotVerification(),
        )
    }
}
