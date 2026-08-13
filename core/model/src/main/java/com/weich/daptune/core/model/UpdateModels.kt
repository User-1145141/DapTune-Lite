package com.weich.daptune.core.model

/** Metadata for one published DapTune GitHub Release. */
data class AppRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releasePageUrl: String,
    val publishedAtEpochMillis: Long?,
)

data class UpdateCheckResult(
    val latestRelease: AppRelease,
    val updateAvailable: Boolean,
)
