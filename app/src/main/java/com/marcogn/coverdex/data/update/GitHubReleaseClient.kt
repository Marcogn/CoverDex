package com.marcogn.coverdex.data.update

import com.marcogn.coverdex.BuildConfig
import com.marcogn.coverdex.domain.update.ReleaseInfo
import com.marcogn.coverdex.domain.update.UpdateFailure
import com.marcogn.coverdex.domain.update.parseLatestRelease
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val API_BASE_URL = "https://api.github.com"
// Pinned REST API version, as GitHub recommends for every request (an unpinned call gets the default
// version, which may change).
private const val API_VERSION = "2022-11-28"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

/**
 * Reads the latest published release of this app's own repository, unauthenticated: published
 * releases of a public repository need no token, and a startup check at most once per start is far
 * below the 60 requests/hour per-IP limit. Same hand-rolled `HttpURLConnection` style as
 * `TheGamesDbApiClient` — one GET endpoint does not justify an HTTP client dependency.
 *
 * Owner and repository come from `BuildConfig` (`app/build.gradle.kts`), the one place a sibling app
 * changes when it copies this package.
 */
@Singleton
class GitHubReleaseClient @Inject constructor() {

    /** The latest non-draft, non-prerelease release, or `null` when the repository has none (HTTP 404). */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            // No Accept-Encoding: setting it by hand turns off HttpURLConnection's transparent gzip
            // decoding, and the body would arrive compressed.
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", UPDATE_USER_AGENT)
        }
        try {
            val code = networkCall { connection.responseCode }
            when {
                code in 200..299 -> Unit
                code == HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                connection.isRateLimited(code) -> throw UpdateException(UpdateFailure.RateLimited)
                else -> throw UpdateException(UpdateFailure.Http(code))
            }
            val body = networkCall { connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            try {
                parseLatestRelease(body)
            } catch (e: IllegalArgumentException) {
                // kotlinx's SerializationException is an IllegalArgumentException.
                throw UpdateException(UpdateFailure.InvalidResponse(e.message), e)
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * GitHub answers an exhausted rate limit with 429, or with 403 plus `x-ratelimit-remaining: 0` (primary
 * limit) or a `retry-after` header (secondary limit) — REST docs, "Exceeding the rate limit". Any other
 * 403 (blocked repository, proxy) is a plain HTTP error: "try later" would not fix it.
 */
private fun HttpURLConnection.isRateLimited(code: Int): Boolean =
    code == 429 || code == HttpURLConnection.HTTP_FORBIDDEN &&
        (getHeaderField("x-ratelimit-remaining") == "0" || getHeaderField("retry-after") != null)
