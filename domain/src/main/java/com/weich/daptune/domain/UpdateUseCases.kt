package com.weich.daptune.domain

import com.weich.daptune.core.model.UpdateCheckResult
import java.math.BigInteger
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    suspend operator fun invoke(currentVersionName: String): UpdateCheckResult {
        val current = SemanticVersion.parse(currentVersionName)
            ?: throw UpdateCheckException("当前应用版本格式无效")
        val latestRelease = updateRepository.latestRelease()
        val latest = SemanticVersion.parse(latestRelease.versionName)
            ?: throw UpdateCheckException("发布版本格式无效")
        return UpdateCheckResult(
            latestRelease = latestRelease,
            updateAvailable = latest > current,
        )
    }
}

class UpdateCheckException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal fun isVersionNewer(candidate: String, current: String): Boolean? {
    val candidateVersion = SemanticVersion.parse(candidate) ?: return null
    val currentVersion = SemanticVersion.parse(current) ?: return null
    return candidateVersion > currentVersion
}

private data class SemanticVersion(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }

        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
        preRelease.zip(other.preRelease).forEach { (left, right) ->
            comparePreReleaseIdentifier(left, right).takeIf { it != 0 }?.let { return it }
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    companion object {
        private val Pattern = Regex(
            """^[vV]?([0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
        )

        fun parse(value: String): SemanticVersion? {
            val match = Pattern.matchEntire(value.trim()) ?: return null
            val preRelease = match.groupValues[4]
                .takeIf(String::isNotEmpty)
                ?.split('.')
                .orEmpty()
            if (preRelease.any { identifier ->
                    identifier.all(Char::isDigit) &&
                        identifier.length > 1 &&
                        identifier.startsWith('0')
                }
            ) {
                return null
            }
            return SemanticVersion(
                major = match.groupValues[1].toBigInteger(),
                minor = match.groupValues[2].toBigInteger(),
                patch = match.groupValues[3].toBigInteger(),
                preRelease = preRelease,
            )
        }
    }
}

private fun comparePreReleaseIdentifier(left: String, right: String): Int {
    val leftNumeric = left.all(Char::isDigit)
    val rightNumeric = right.all(Char::isDigit)
    return when {
        leftNumeric && rightNumeric -> left.toBigInteger().compareTo(right.toBigInteger())
        leftNumeric -> -1
        rightNumeric -> 1
        else -> left.compareTo(right)
    }
}
