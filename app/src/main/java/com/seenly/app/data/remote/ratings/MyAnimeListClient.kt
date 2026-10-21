package com.seenly.app.data.remote.ratings

import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.network.ProviderException
import com.seenly.app.core.network.RateLimiter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MyAnimeList community score fallback.
 *
 * Jikan is the preferred MAL API, but it is frequently unreachable. The public
 * MAL page still exposes the same score in its `ratingValue` metadata, so this
 * conservatively reads that one value. It is background-only: previews disable
 * scraped ratings so a slow page can never hold a bottom sheet open.
 */
class MyAnimeListClient(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {

    private val limiter = RateLimiter(maxPerMinute = 20, minIntervalMillis = 1_000)
    private val cache = ConcurrentHashMap<Int, Double>()

    suspend fun score(malId: Int): Double? {
        if (malId <= 0) return null
        cache[malId]?.let { return it }
        val score = withContext(dispatchers.io) {
            limiter.acquire()
            try {
                val request = Request.Builder()
                    .url("https://myanimelist.net/anime/$malId")
                    .header("User-Agent", "Seenly/1.1")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ProviderException.Http(response.code)
                    response.body?.string()?.let(::parseScore)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: IOException) {
                throw ProviderException.Offline(failure)
            }
        }
        if (score != null) cache[malId] = score
        return score
    }

    internal companion object {
        private val ratingValueRegex = Regex(
            "itemprop=[\"']ratingValue[\"'][^>]*>\\s*([0-9]+(?:\\.[0-9]+)?)",
            RegexOption.IGNORE_CASE,
        )

        internal fun parseScore(html: String): Double? {
            val match = ratingValueRegex.find(html) ?: return null
            return match.groupValues.getOrNull(1)
                ?.toDoubleOrNull()
                ?.takeIf { it > 0.0 && it <= 10.0 }
        }
    }
}
