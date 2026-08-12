package com.weich.daptune.data

import android.content.Context
import android.util.AtomicFile
import com.weich.daptune.core.model.AutoEqForm
import com.weich.daptune.core.model.AutoEqProfile
import com.weich.daptune.domain.AutoEqRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AutoEqRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AutoEqRepository {
    private val cacheFile = File(context.cacheDir, "autoeq/recommended-results.md")
    private val catalogMutex = Mutex()

    @Volatile
    private var memoryCatalog: AutoEqCatalog? = null

    override suspend fun search(query: String, limit: Int): List<AutoEqProfile> =
        withContext(Dispatchers.IO) {
            require(limit in 1..MAX_SEARCH_RESULTS) { "搜索结果数量无效" }
            val cleanQuery = query.trim()
            require(cleanQuery.length <= MAX_SEARCH_QUERY_LENGTH) { "搜索词过长" }
            if (cleanQuery.isEmpty()) emptyList() else loadCatalog().search(cleanQuery, limit)
        }

    override suspend fun downloadGraphicEq(profile: AutoEqProfile): String =
        withContext(Dispatchers.IO) {
            val profileUrl = AutoEqIndexParser.graphicEqUrl(profile)
            downloadText(profileUrl, MAX_PROFILE_BYTES, "AutoEq 配置")
        }

    private suspend fun loadCatalog(): AutoEqCatalog {
        memoryCatalog?.let { return it }
        return catalogMutex.withLock {
            memoryCatalog?.let { return@withLock it }

            val cachedCatalog = readCachedCatalog()
            val cacheIsFresh = cacheFile.isFile &&
                System.currentTimeMillis() - cacheFile.lastModified() <= CACHE_MAX_AGE_MILLIS
            val loaded = if (cachedCatalog != null && cacheIsFresh) {
                cachedCatalog
            } else {
                runCatching {
                    val markdown = downloadText(INDEX_URL, MAX_INDEX_BYTES, "AutoEq 索引")
                    parseCatalog(markdown).also { writeCache(markdown) }
                }.getOrElse { error ->
                    cachedCatalog ?: throw error.asAutoEqFailure("无法载入 AutoEq 数据库")
                }
            }
            memoryCatalog = loaded
            loaded
        }
    }

    private fun readCachedCatalog(): AutoEqCatalog? {
        if (!cacheFile.isFile || cacheFile.length() !in 1..MAX_INDEX_BYTES.toLong()) return null
        return runCatching { parseCatalog(cacheFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun parseCatalog(markdown: String): AutoEqCatalog {
        val profiles = AutoEqIndexParser.parse(markdown)
        if (profiles.size < MINIMUM_VALID_INDEX_SIZE) {
            throw AutoEqRepositoryException("AutoEq 返回了不完整的数据")
        }
        return AutoEqCatalog(profiles)
    }

    private fun writeCache(markdown: String) {
        val parent = cacheFile.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) return
        val atomicFile = AtomicFile(cacheFile)
        var output = runCatching { atomicFile.startWrite() }.getOrNull() ?: return
        try {
            output.write(markdown.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (_: IOException) {
            atomicFile.failWrite(output)
        }
    }

    private fun downloadText(url: String, maximumBytes: Int, label: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            useCaches = true
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "DapTune-Android")
        }
        return try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw AutoEqRepositoryException("$label 下载失败（HTTP $status）")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maximumBytes) {
                throw AutoEqRepositoryException("$label 过大")
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(
                    declaredLength.coerceIn(0, maximumBytes.toLong()).toInt(),
                )
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maximumBytes) throw AutoEqRepositoryException("$label 过大")
                    output.write(buffer, 0, count)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
        } catch (error: AutoEqRepositoryException) {
            throw error
        } catch (error: IOException) {
            throw AutoEqRepositoryException("无法连接 AutoEq", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun Throwable.asAutoEqFailure(fallback: String): AutoEqRepositoryException =
        this as? AutoEqRepositoryException ?: AutoEqRepositoryException(fallback, this)

    private companion object {
        const val INDEX_URL =
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/README.md"
        const val MAX_INDEX_BYTES = 2_000_000
        const val MAX_PROFILE_BYTES = 128_000
        const val MINIMUM_VALID_INDEX_SIZE = 1_000
        const val MAX_SEARCH_RESULTS = 200
        const val MAX_SEARCH_QUERY_LENGTH = 120
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val CACHE_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

class AutoEqRepositoryException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal object AutoEqIndexParser {
    private const val RAW_RESULTS_BASE =
        "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    private val entryRegex = Regex("^- \\[(.*)]\\((\\./.*)\\)\\s*$")

    fun parse(markdown: String): List<AutoEqProfile> = markdown.lineSequence()
        .mapNotNull(::parseLine)
        .distinctBy(AutoEqProfile::relativePath)
        .toList()

    fun graphicEqUrl(profile: AutoEqProfile): String {
        requireValidPath(profile.relativePath, profile.name)
        val folder = profile.relativePath.removePrefix("./")
        val fileName = URLEncoder
            .encode("${profile.name} GraphicEQ.txt", StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "$RAW_RESULTS_BASE/$folder/$fileName"
    }

    private fun parseLine(line: String): AutoEqProfile? {
        val match = entryRegex.matchEntire(line.trimEnd()) ?: return null
        val name = match.groupValues[1].trim()
        val relativePath = match.groupValues[2]
        if (name.isEmpty() || name.length > MAX_UPSTREAM_NAME_LENGTH) return null
        val segments = validSegments(relativePath) ?: return null
        val decodedName = decodePathSegment(segments.last()) ?: return null
        if (decodedName != name) return null

        val source = decodePathSegment(segments.first())?.takeIf(String::isNotBlank) ?: return null
        val measurement = segments.drop(1).dropLast(1)
            .mapNotNull(::decodePathSegment)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val form = when {
            "over-ear" in measurement -> AutoEqForm.OVER_EAR
            "in-ear" in measurement -> AutoEqForm.IN_EAR
            "earbud" in measurement -> AutoEqForm.EARBUD
            else -> AutoEqForm.UNKNOWN
        }
        return AutoEqProfile(
            name = name,
            relativePath = relativePath,
            measurementSource = source,
            form = form,
        )
    }

    private fun requireValidPath(path: String, expectedName: String) {
        val segments = validSegments(path)
            ?: throw AutoEqRepositoryException("AutoEq 配置路径无效")
        val pathName = decodePathSegment(segments.last())
        if (pathName != expectedName) throw AutoEqRepositoryException("AutoEq 配置名称不匹配")
    }

    private fun validSegments(path: String): List<String>? {
        if (!path.startsWith("./") || path.length > MAX_UPSTREAM_PATH_LENGTH) return null
        if ('\\' in path || '?' in path || '#' in path) return null
        val segments = path.removePrefix("./").split('/')
        if (segments.size < 3 || segments.any(String::isBlank)) return null
        val decoded = segments.map { decodePathSegment(it) ?: return null }
        if (decoded.any { it == "." || it == ".." || '/' in it || '\\' in it }) return null
        return segments
    }

    /** URLDecoder is used only after protecting literal '+' characters in GitHub paths. */
    private fun decodePathSegment(value: String): String? = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrNull()

    private const val MAX_UPSTREAM_NAME_LENGTH = 160
    private const val MAX_UPSTREAM_PATH_LENGTH = 600
}

internal class AutoEqCatalog(profiles: List<AutoEqProfile>) {
    private val searchable = profiles.map { profile ->
        SearchableProfile(profile, normalizeForSearch(profile.name))
    }

    fun search(query: String, limit: Int): List<AutoEqProfile> {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        val tokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        return searchable.asSequence()
            .mapNotNull { candidate -> candidate.match(normalizedQuery, tokens) }
            .sortedWith(
                compareBy<SearchMatch>(SearchMatch::tier)
                    .thenBy(SearchMatch::boundaryPenalty)
                    .thenBy(SearchMatch::positionSum)
                    .thenBy { it.candidate.normalizedName.length }
                    .thenBy { it.candidate.profile.name.lowercase(Locale.ROOT) },
            )
            .take(limit)
            .map { it.candidate.profile }
            .toList()
    }

    private fun SearchableProfile.match(query: String, tokens: List<String>): SearchMatch? {
        val positions = tokens.map(normalizedName::indexOf)
        if (positions.any { it < 0 }) return null
        val tier = when {
            normalizedName == query -> 0
            normalizedName.startsWith(query) -> 1
            normalizedName.split(' ').any { it.startsWith(query) } -> 2
            query in normalizedName -> 3
            else -> 4
        }
        val boundaryPenalty = positions.count { position ->
            position > 0 && normalizedName[position - 1] != ' '
        }
        return SearchMatch(this, tier, boundaryPenalty, positions.sum())
    }

    private data class SearchableProfile(
        val profile: AutoEqProfile,
        val normalizedName: String,
    )

    private data class SearchMatch(
        val candidate: SearchableProfile,
        val tier: Int,
        val boundaryPenalty: Int,
        val positionSum: Int,
    )
}

private fun normalizeForSearch(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")
