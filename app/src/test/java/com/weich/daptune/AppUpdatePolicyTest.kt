package com.weich.daptune

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun automaticCheck_isLimitedToOncePerDay() {
        val now = 10L * AutomaticUpdateCheckIntervalMillis

        assertTrue(
            shouldAutomaticallyCheckForUpdates(
                enabled = true,
                lastCheckAtEpochMillis = 0L,
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldAutomaticallyCheckForUpdates(
                enabled = true,
                lastCheckAtEpochMillis = now - AutomaticUpdateCheckIntervalMillis + 1L,
                nowEpochMillis = now,
            ),
        )
        assertTrue(
            shouldAutomaticallyCheckForUpdates(
                enabled = true,
                lastCheckAtEpochMillis = now - AutomaticUpdateCheckIntervalMillis,
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldAutomaticallyCheckForUpdates(
                enabled = false,
                lastCheckAtEpochMillis = 0L,
                nowEpochMillis = now,
            ),
        )
    }
}
