package com.seenly.app.data.repository

import com.seenly.app.core.common.AppClock
import com.seenly.app.data.remote.cinemeta.CinemetaProvider
import com.seenly.app.data.remote.jikan.JikanProvider
import com.seenly.app.data.remote.ratings.MyAnimeListClient
import com.seenly.app.data.remote.ratings.RottenTomatoesClient
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Best-effort external ratings (IMDb, MyAnimeList, Rotten Tomatoes).
 *
 * The three lookups run concurrently and every failure is swallowed: a missing
 * rating must never fail the details fetch or the import it belongs to. Fields
 * already supplied by the provider are never looked up again.
 *
 * Anime providers only expose a MyAnimeList id, so IMDb is resolved through a
 * conservative exact-title search on Cinemeta (IMDb's mirror). A title that
 * does not match exactly and within a year stays without an IMDb rating.
 */
class MetadataEnricher(
    private val cinemeta: CinemetaProvider,
    private val jikan: JikanProvider,
    private val myAnimeList: MyAnimeListClient,
    private val rottenTomatoes: RottenTomatoesClient,
    private val clock: AppClock,
) {

    suspend fun enrich(
        media: ProviderMedia,
        timeoutMillis: Long = ENRICH_TIMEOUT_MS,
        /**
         * MyAnimeList and Rotten Tomatoes use blocking HTML scrapes. Previews
         * disable them so a slow page can never hold a bottom sheet open; the
         * background import/refresh still fills them in.
         */
        includeScrapedRatings: Boolean = true,
    ): ProviderMedia = coroutineScope {
        val isSeries = media.type.hasEpisodes
        val imdb = async {
            if (media.imdbId != null && media.imdbRating != null) return@async null
            var imdbId = media.imdbId
            var imdbRating = media.imdbRating
            if (imdbId == null) {
                val resolved = guarded(timeoutMillis) { resolveImdb(media) }
                imdbId = resolved?.first
                imdbRating = imdbRating ?: resolved?.second
            }
            if (imdbId != null && imdbRating == null) {
                imdbRating = guarded(timeoutMillis) { cinemeta.imdbRating(imdbId, isSeries) }
            }
            if (imdbId == null) null else imdbId to imdbRating
        }
        val malRating = async {
            val malId = media.malId ?: return@async null
            if (media.malRating != null) return@async null
            // Jikan is the official API but is often unreachable for long
            // stretches. Do not let its connect timeout consume the whole
            // ratings budget: fall back to the public MAL page quickly.
            guarded(MAL_JIKAN_TIMEOUT_MS) { jikan.malScore(malId) }
                ?: if (includeScrapedRatings) {
                    guarded(timeoutMillis) { myAnimeList.score(malId) }
                } else {
                    null
                }
        }
        val tomatoes = async {
            if (!includeScrapedRatings) return@async null
            if (media.rottenTomatoesRating != null) return@async null
            val releaseDate = media.releaseDate
            // Unreleased titles cannot have a Tomatometer yet; skip the request.
            if (releaseDate != null && releaseDate.isAfter(clock.today().plusMonths(1))) {
                return@async null
            }
            guarded(timeoutMillis) {
                rottenTomatoes.tomatometerScore(media.title, releaseDate?.year, isSeries)
            }
        }
        val imdbResult = imdb.await()
        media.copy(
            imdbId = media.imdbId ?: imdbResult?.first,
            imdbRating = media.imdbRating ?: imdbResult?.second,
            malRating = media.malRating ?: malRating.await(),
            rottenTomatoesRating = media.rottenTomatoesRating ?: tomatoes.await(),
        )
    }

    /**
     * Fills missing anime episode titles and air dates from the IMDb mirror.
     * AniList never publishes per-episode titles and Jikan is optional, so
     * Cinemeta is the dependable fallback. Season start dates anchor split
     * cours when IMDb groups two AniList cours into one television season.
     */
    suspend fun enrichEpisodes(details: ProviderMediaDetails): ProviderMediaDetails {
        if (details.media.type.category != MediaCategory.ANIME) return details
        val needsEnrichment = details.seasons.any { bundle ->
            bundle.episodes.any { it.title.isNullOrBlank() || it.airDate == null }
        }
        if (!needsEnrichment) return details
        val imdbId = details.media.imdbId
            ?: guarded(IMDB_RESOLVE_TIMEOUT_MS) { resolveImdb(details.media) }?.first
            ?: return details
        val cinema = guarded(CINEMETA_EPISODES_TIMEOUT_MS) {
            cinemeta.details("series/$imdbId")
        } ?: return details
        // Season start dates anchor split cours even when AniList and IMDb
        // disagree on an episode count (e.g. an extra recap episode).
        val byAbsolute = CinemetaEpisodeMapping.map(
            localSeasons = details.seasons,
            cinemaSeasons = cinema.seasons,
        )
        if (byAbsolute.isEmpty()) return details.copy(
            media = details.media.copy(
                imdbId = imdbId,
                imdbRating = details.media.imdbRating ?: cinema.media.imdbRating,
            ),
        )
        var sequential = 0
        val seasons = details.seasons.map { bundle ->
            bundle.copy(
                episodes = bundle.episodes.map { episode ->
                    val absolute = episode.absoluteNumber?.takeIf { it > 0 } ?: (sequential + 1)
                    sequential = maxOf(sequential, absolute)
                    val remote = byAbsolute[absolute]
                    if (remote == null) {
                        episode
                    } else {
                        episode.copy(
                            title = episode.title?.takeIf { it.isNotBlank() }
                                ?: remote.title?.takeIf { it.isNotBlank() },
                            airDate = episode.airDate ?: remote.airDate,
                            stillUrl = episode.stillUrl ?: remote.stillUrl,
                            synopsis = episode.synopsis ?: remote.synopsis,
                        )
                    }
                },
            )
        }
        return details.copy(
            media = details.media.copy(
                imdbId = details.media.imdbId ?: imdbId,
                imdbRating = details.media.imdbRating ?: cinema.media.imdbRating,
            ),
            seasons = seasons,
        )
    }

    /** Exact normalized title plus a release year within one year. */
    private suspend fun resolveImdb(media: ProviderMedia): Pair<String, Double?>? {
        val type = if (media.type.hasEpisodes) MediaType.TV_SERIES else MediaType.MOVIE
        val targetYear = media.releaseDate?.year
        val titles = (listOf(media.title) + media.alternateTitles)
            .mapNotNull(::normalizeTitle)
            .distinct()
            .take(MAX_IMDB_TITLE_LOOKUPS)
        titles.forEach { title ->
            val candidates = safe { cinemeta.search(title, type, page = 1).items }.orEmpty()
            val match = candidates.firstOrNull { candidate ->
                normalizeTitle(candidate.title) == title &&
                    yearsCompatible(targetYear, candidate.releaseDate?.year)
            } ?: return@forEach
            val imdbId = match.imdbId ?: return@forEach
            return imdbId to match.imdbRating
        }
        return null
    }

    private fun yearsCompatible(first: Int?, second: Int?): Boolean {
        if (first == null || second == null) return true
        return abs(first - second) <= 1
    }

    private fun normalizeTitle(title: String): String? =
        title.lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .takeIf { it.isNotEmpty() }

    private suspend fun <T> guarded(timeoutMillis: Long, block: suspend () -> T): T? =
        withTimeoutOrNull(timeoutMillis) { safe(block) }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val ENRICH_TIMEOUT_MS = 4_000L
        const val MAL_JIKAN_TIMEOUT_MS = 1_500L
        const val IMDB_RESOLVE_TIMEOUT_MS = 3_000L
        const val CINEMETA_EPISODES_TIMEOUT_MS = 4_000L
        const val MAX_IMDB_TITLE_LOOKUPS = 3
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    }
}
