package com.weich.daptune.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProfileOrderingTest {
    @Test
    fun customProfilesArePlacedBeforeBuiltIns() {
        val profiles = listOf(
            profile("builtin.flat", isBuiltIn = true),
            profile("custom.reference", isBuiltIn = false),
            profile("builtin.warm", isBuiltIn = true),
            profile("custom.speaker", isBuiltIn = false),
        )

        assertEquals(
            listOf("custom.reference", "custom.speaker", "builtin.flat", "builtin.warm"),
            profiles.withCustomProfilesFirst().map(EqProfile::id),
        )
    }

    @Test
    fun orderingInsideEachGroupIsPreserved() {
        val profiles = listOf(
            profile("builtin.second", isBuiltIn = true),
            profile("custom.second", isBuiltIn = false),
            profile("custom.first", isBuiltIn = false),
            profile("builtin.first", isBuiltIn = true),
        )

        assertEquals(
            listOf("custom.second", "custom.first", "builtin.second", "builtin.first"),
            profiles.withCustomProfilesFirst().map(EqProfile::id),
        )
    }

    @Test
    fun alreadyOrderedProfilesAreReturnedWithoutAllocation() {
        val profiles = listOf(
            profile("custom.reference", isBuiltIn = false),
            profile("builtin.flat", isBuiltIn = true),
        )

        assertSame(profiles, profiles.withCustomProfilesFirst())
    }

    private fun profile(id: String, isBuiltIn: Boolean) = EqProfile(
        id = id,
        name = id,
        curve = EqCurve.flat(),
        isBuiltIn = isBuiltIn,
        source = if (isBuiltIn) ProfileSource.BUILT_IN else ProfileSource.MANUAL,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
