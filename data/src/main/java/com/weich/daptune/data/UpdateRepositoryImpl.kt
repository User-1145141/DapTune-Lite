package com.weich.daptune.data

import com.weich.daptune.core.model.AppRelease
import com.weich.daptune.domain.UpdateCheckException
import com.weich.daptune.domain.UpdateRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class UpdateRepositoryImpl @Inject constructor() : UpdateRepository {
    override suspend fun latestRelease(): AppRelease = withContext(Dispatchers.IO) {
        val connection = (URL(LatestReleaseEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DapTune-Android")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> readRelease(connection)
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    throw UpdateCheckException("暂未找到正式版本")
                }
                HttpURLConnection.HTTP_FORBIDDEN,
                429,
                -> {
                    throw UpdateCheckException("GitHub 请求过于频繁，请稍后重试")
                }
                else -> throw UpdateCheckException("检查更新失败（HTTP $status）")
            }
        } catch (error: UpdateCheckException) {
            throw error
        } catch (error: IOException) {
            throw UpdateCheckException("无法连接 GitHub", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun readRelease(connection: HttpURLConnection): AppRelease {
        val contentType = connection.contentType.orEmpty().lowercase()
        if (!contentType.startsWith("application/json")) {
            throw UpdateCheckException("GitHub 返回了无法识别的数据")
        }
        val contentLength = connection.contentLengthLong
        if (contentLength > MaxResponseBytes) {
            throw UpdateCheckException("GitHub 返回的数据过大")
        }
        val payload = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MaxResponseBytes) {
                    throw UpdateCheckException("GitHub 返回的数据过大")
                }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
        return try {
            GitHubReleaseParser.parse(payload)
        } catch (error: UpdateCheckException) {
            throw error
        } catch (error: RuntimeException) {
            throw UpdateCheckException("GitHub 返回了无效的版本信息", error)
        }
    }

    private companion object {
        const val LatestReleaseEndpoint =
            "https://api.github.com/repos/silverpoetry/DapTune/releases/latest"
        const val ConnectTimeoutMillis = 10_000
        const val ReadTimeoutMillis = 15_000
        const val MaxResponseBytes = 64 * 1024
    }
}

internal object GitHubReleaseParser {
    private val JsonParser = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): AppRelease {
        val release = JsonParser.parseToJsonElement(payload).jsonObject
        if (release["draft"]?.jsonPrimitive?.booleanOrNull == true ||
            release["prerelease"]?.jsonPrimitive?.booleanOrNull == true
        ) {
            throw UpdateCheckException("GitHub 返回的不是正式版本")
        }

        val tagName = release.requiredString("tag_name", maxLength = 64)
        val releasePageUrl = release.requiredString("html_url", maxLength = 512)
        validateReleasePageUrl(releasePageUrl)
        val title = release["name"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(120)
            ?: tagName
        val publishedAt = release["published_at"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { value ->
                runCatching { Instant.parse(value).toEpochMilli() }
                    .getOrElse { throw UpdateCheckException("发布时间格式无效", it) }
            }

        return AppRelease(
            tagName = tagName,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            title = title,
            releasePageUrl = releasePageUrl,
            publishedAtEpochMillis = publishedAt,
        )
    }

    private fun kotlinx.serialization.json.JsonObject.requiredString(
        name: String,
        maxLength: Int,
    ): String {
        val value = this[name]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw UpdateCheckException("GitHub 返回的数据缺少 $name")
        if (value.length > maxLength || value.any(Char::isISOControl)) {
            throw UpdateCheckException("GitHub 返回的 $name 无效")
        }
        return value
    }

    private fun validateReleasePageUrl(value: String) {
        val uri = runCatching { URI(value) }
            .getOrElse { throw UpdateCheckException("发布页地址无效", it) }
        val valid = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.path.startsWith("/silverpoetry/DapTune/releases/tag/")
        if (!valid) throw UpdateCheckException("发布页地址无效")
    }
}
