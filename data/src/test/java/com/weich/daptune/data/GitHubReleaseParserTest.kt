package com.weich.daptune.data

import com.weich.daptune.domain.UpdateCheckException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun validStableRelease_isParsed() {
        val release = GitHubReleaseParser.parse(
            """
            {
              "tag_name": "v0.3.0",
              "name": "DapTune 0.3.0",
              "html_url": "https://github.com/silverpoetry/DapTune/releases/tag/v0.3.0",
              "published_at": "2026-08-13T08:00:00Z",
              "draft": false,
              "prerelease": false
            }
            """.trimIndent(),
        )

        assertEquals("v0.3.0", release.tagName)
        assertEquals("0.3.0", release.versionName)
        assertEquals("DapTune 0.3.0", release.title)
    }

    @Test
    fun externalReleaseUrl_isRejected() {
        assertThrows(UpdateCheckException::class.java) {
            GitHubReleaseParser.parse(
                """
                {
                  "tag_name": "v9.9.9",
                  "html_url": "https://example.com/download.apk",
                  "draft": false,
                  "prerelease": false
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun prerelease_isRejectedByStableChannel() {
        assertThrows(UpdateCheckException::class.java) {
            GitHubReleaseParser.parse(
                """
                {
                  "tag_name": "v0.4.0-beta.1",
                  "html_url": "https://github.com/silverpoetry/DapTune/releases/tag/v0.4.0-beta.1",
                  "draft": false,
                  "prerelease": true
                }
                """.trimIndent(),
            )
        }
    }
}
