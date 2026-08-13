package com.weich.daptune.domain

import com.weich.daptune.core.model.AppRelease
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUseCasesTest {
    @Test
    fun semanticVersionComparison_handlesStableAndPrereleaseVersions() {
        assertTrue(isVersionNewer("0.3.0", "0.2.0") == true)
        assertTrue(isVersionNewer("1.0.0", "1.0.0-beta.2") == true)
        assertFalse(isVersionNewer("1.0.0-beta.2", "1.0.0") == true)
    }

    @Test
    fun checkUseCase_reportsOnlyStrictlyNewerRelease() = runBlocking {
        val useCase = CheckForUpdateUseCase(
            updateRepository = object : UpdateRepository {
                override suspend fun latestRelease() = AppRelease(
                    tagName = "v0.3.0",
                    versionName = "0.3.0",
                    title = "DapTune 0.3.0",
                    releasePageUrl =
                        "https://github.com/silverpoetry/DapTune/releases/tag/v0.3.0",
                    publishedAtEpochMillis = null,
                )
            },
        )

        assertTrue(useCase("0.2.0").updateAvailable)
        assertFalse(useCase("0.3.0").updateAvailable)
    }
}
