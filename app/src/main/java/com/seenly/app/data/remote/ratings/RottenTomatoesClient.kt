package com.seenly.app.data.remote.ratings

import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.network.ProviderException
import com.seenly.app.core.network.RateLimiter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Best-effort Rotten Tomatoes Tomatometer lookup.
 *
 * Rotten Tomatoes has no credential-free JSON API, so this reads the public
 * search page (and, only when a match has no score yet, the title page's
 * embedded JSON-LD). It is intentionally conservative:
 * - one request per lookup, spaced by a rate limiter;
 * - results cached in memory for the process lifetime;
 * - failures never surface as errors — the rating is simply absent.
 *
 * Tomatometer scores are critic scores 0..100. Nothing here is a user rating.
 */
class RottenTomatoesClient(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {

    private val limiter = RateLimiter(maxPerMinute = 12, minIntervalMillis = 1_200)

    private val cache = ConcurrentHashMap<String, Int>()

    suspend fun tomatometerScore(title: String, year: Int?, isSeries: Boolean?): Int? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        val cacheKey = "${cleanTitle.lowercase()}|${year ?: ""}|${isSeries ?: ""}"
        cache[cacheKey]?.let { return it }

        val row = withContext(dispatchers.io) {
            limiter.acquire()
            val html = fetch(searchUrl(cleanTitle)) ?: return@withContext null
            parseSearchResults(html, cleanTitle, year, isSeries)
        } ?: return null

        val score = row.score ?: withContext(dispatchers.io) {
            row.href?.let { url ->
                limiter.acquire()
                fetch(url)?.let(::parseAggregateRating)
            }
        }
        if (score != null) cache[cacheKey] = score
        return score
    }

    internal data class SearchRow(
        val title: String,
        val href: String?,
        val releaseYear: Int?,
        val score: Int?,
        val isSeries: Boolean,
    )

    private fun searchUrl(title: String): String =
        "https://www.rottentomatoes.com/search?search=" + URLEncoder.encode(title, "UTF-8")

    private suspend fun fetch(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ProviderException.Http(response.code)
            }
            response.body?.string()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: IOException) {
        throw ProviderException.Offline(failure)
    }

    private companion object {
        val rowRegex = Regex("<search-page-media-row\\b([^>]*)>(.*?)</search-page-media-row>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("data-qa=\"info-name\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("href=\"(https?://[^\"]+)\"")
        val scoreRegex = Regex("tomatometer-score=\"(\\d+)\"")
        val releaseYearRegex = Regex("release-year=\"(\\d{4})\"")
        val aggregateRatingRegex = Regex("\"aggregateRating\":\\{.{0,800}?\"ratingValue\":\"(\\d+)\"", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("<[^>]+>")
        val nonAlphanumeric = Regex("[^a-z0-9]+")
    }

    internal fun parseSearchResults(
        html: String,
        title: String,
        year: Int?,
        isSeries: Boolean?,
    ): SearchRow? {
        val wanted = normalize(title)
        val rows = rowRegex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val rowTitle = titleRegex.find(body)?.groupValues?.getOrNull(1)
                ?.let { tagRegex.replace(it, "") }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val href = hrefRegex.find(body)?.groupValues?.getOrNull(1)
            SearchRow(
                title = rowTitle,
                href = href,
                releaseYear = releaseYearRegex.find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                score = scoreRegex.find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                isSeries = href?.contains("/tv/") == true,
            )
        }.toList()
        if (rows.isEmpty()) return null

        val exact = rows.filter { normalize(it.title) == wanted }
        val candidates = exact.ifEmpty { rows.filter { normalize(it.title).contains(wanted) || wanted.contains(normalize(it.title)) } }
        if (candidates.isEmpty()) return null

        return candidates.minByOrNull { row ->
            // Prefer type match, then the closest release year, then scored rows.
            var penalty = 0
            if (isSeries != null && row.isSeries != isSeries) penalty += 4
            if (year != null && row.releaseYear != null) penalty += kotlin.math.abs(row.releaseYear - year)
            if (row.score == null) penalty += 2
            penalty
        }
    }

    internal fun parseAggregateRating(html: String): Int? =
        aggregateRatingRegex.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }

    private fun normalize(input: String): String =
        nonAlphanumeric.replace(input.lowercase(), " ").trim()
}
