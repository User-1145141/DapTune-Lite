package com.weich.daptune.domain

import java.util.Locale

object ProfileNames {
    private val CopySuffix = Regex("^(.*) 副本(?: \\((\\d+)\\))?$")

    fun uniqueCopyName(
        preferredName: String,
        existingNames: Collection<String>,
        maxLength: Int = 40,
    ): String {
        require(maxLength >= MinimumCopyNameLength)
        val preferred = preferredName.trim().take(maxLength)
        require(preferred.isNotEmpty()) { "配置名称不能为空" }
        val occupied = existingNames.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        if (preferred.lowercase(Locale.ROOT) !in occupied) return preferred

        val parsed = CopySuffix.matchEntire(preferred)
        val root = (parsed?.groupValues?.get(1) ?: preferred).trimEnd()
        val firstCopyNumber = when {
            parsed == null -> 1
            parsed.groupValues[2].isBlank() -> 2
            else -> parsed.groupValues[2].toIntOrNull()?.plus(1) ?: 2
        }
        return generateSequence(firstCopyNumber) { it + 1 }
            .map { copyNumber ->
                val suffix = if (copyNumber == 1) " 副本" else " 副本 ($copyNumber)"
                root.take((maxLength - suffix.length).coerceAtLeast(1)).trimEnd() + suffix
            }
            .first { it.lowercase(Locale.ROOT) !in occupied }
    }

    private const val MinimumCopyNameLength = 4
}
