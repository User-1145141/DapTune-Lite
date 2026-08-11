package com.weich.daptune.core.model

/**
 * Presents user-created profiles before built-in presets while preserving the
 * repository-defined order inside each group.
 */
fun List<EqProfile>.withCustomProfilesFirst(): List<EqProfile> {
    if (size < 2) return this
    val firstBuiltIn = indexOfFirst(EqProfile::isBuiltIn)
    val customAfterBuiltIn = if (firstBuiltIn < 0) {
        false
    } else {
        subList(firstBuiltIn + 1, size).any { !it.isBuiltIn }
    }
    if (!customAfterBuiltIn) return this
    return buildList(size) {
        this@withCustomProfilesFirst.filterTo(this) { !it.isBuiltIn }
        this@withCustomProfilesFirst.filterTo(this, EqProfile::isBuiltIn)
    }
}
