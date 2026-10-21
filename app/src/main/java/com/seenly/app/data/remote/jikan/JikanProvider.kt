package com.seenly.app.data.remote.jikan

import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.network.ProviderException
import com.seenly.app.core.network.RateLimiter
import com.seenly.app.core.network.rateLimitedCall
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.MetadataProvider
import com.seenly.app.domain.providers.Paged
import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderRelation
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.LocalDate

object JikanMapper {

    /** Jikan falls back to "Episode N" placeholders when no real title exists. */
    private val placeholderTitle = Regex("^Episode \\d+$", RegexOption.IGNORE_CASE)

    fun parseRuntimeMinutes(duration: String?): Int? {
        if (duration.isNullOrBlank()) return null
        val text = duration.lowercase()
        val hours = Regex("(\\d+)\\s*hr").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)\\s*min").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    fun isoDateOrNull(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    fun typeOf(anime: JikanAnime): MediaType =
        if (anime.type.equals("Movie", ignoreCase = true)) MediaType.ANIME_MOVIE else MediaType.ANIME_SERIES

    fun toProviderMedia(anime: JikanAnime): ProviderMedia? {
        if (anime.malId <= 0) return null
        if (anime.rating?.contains("Hentai", ignoreCase = true) == true) return null
        val title = listOfNotNull(anime.titleEnglish, anime.title, anime.titleJapanese).firstOrNull { it.isNotBlank() }
            ?: return null
        val alternates = buildList {
            addAll(anime.titles.mapNotNull { it.title })
            add(anime.titleJapanese)
            add(anime.titleEnglish)
        }.filterNotNull().filter { it.isNotBlank() && it != title }.distinct()
        return ProviderMedia(
            provider = ProviderId.JIKAN,
            externalId = anime.malId.toString(),
            type = typeOf(anime),
            title = title,
            originalTitle = anime.titleJapanese,
            alternateTitles = alternates,
            synopsis = anime.synopsis?.takeIf { it.isNotBlank() },
            posterUrl = anime.images?.jpg?.largeImageUrl ?: anime.images?.jpg?.imageUrl,
            backdropUrl = null,
            releaseDate = isoDateOrNull(anime.aired?.from) ?: anime.year?.let { LocalDate.of(it, 1, 1) },
            endDate = isoDateOrNull(anime.aired?.to),
            runtimeMinutes = parseRuntimeMinutes(anime.duration),
            totalSeasons = null,
            totalEpisodes = anime.episodes,
            studio = anime.studios.firstOrNull()?.name,
            genres = anime.genres.mapNotNull { it.name }.distinct(),
            tags = emptyList(),
            score = anime.score,
            popularity = anime.popularity?.toDouble(),
            statusText = anime.status,
        )
    }

    fun cleanEpisodeTitle(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && !placeholderTitle.matches(it) }

    /** Episode-number -> title map; numbers follow listing order (1-based). */
    fun episodeTitleMap(episodes: List<JikanEpisode>): Map<Int, String> =
        episodes.mapIndexedNotNull { index, episode ->
            cleanEpisodeTitle(episode.title)?.let { (index + 1) to it }
        }.toMap()

    fun toSeasonBundle(episodes: List<JikanEpisode>): List<ProviderSeasonBundle> {
        if (episodes.isEmpty()) return emptyList()
        val mapped = episodes.mapIndexed { index, episode ->
            ProviderEpisode(
                number = index + 1,
                absoluteNumber = index + 1,
                title = cleanEpisodeTitle(episode.title),
                synopsis = null,
                runtimeMinutes = null,
                airDate = isoDateOrNull(episode.aired),
                stillUrl = null,
            )
        }
        return listOf(
            ProviderSeasonBundle(
                season = ProviderSeason(number = 1, episodeCount = mapped.size),
                episodes = mapped,
            ),
        )
    }
}

/**
 * Jikan adapter (unofficial, optional). Kept as a secondary source: the app never
 * depends on it and outages simply surface as recoverable errors.
 */
class JikanProvider(
    private val api: JikanApi,
    private val dispatchers: AppDispatchers,
) : MetadataProvider {

    override val id: ProviderId = ProviderId.JIKAN
    override val displayName: String = "Jikan (MyAnimeList)"
    override val attributionKey: String = "jikan"
    override val requiresCredential: Boolean = false

    override fun isConfigured(): Boolean = true

    // Jikan allows ~3 requests/second and 60/minute.
    private val limiter = RateLimiter(maxPerMinute = 55, minIntervalMillis = 400)

    override suspend fun search(query: String, type: MediaType?, page: Int): Paged<ProviderMedia> {
        val response = rateLimitedCall(limiter, dispatchers) {
            api.search(query = query, page = page.coerceAtLeast(1))
        }
        val items = response.data.mapNotNull { JikanMapper.toProviderMedia(it) }
            .filter { type == null || it.type.hasEpisodes == type.hasEpisodes }
        return Paged(
            items = items,
            page = response.pagination?.currentPage ?: page,
            hasMore = response.pagination?.hasNextPage == true,
        )
    }

    override suspend fun trending(type: MediaType?, page: Int): Paged<ProviderMedia> {
        val response = rateLimitedCall(limiter, dispatchers) { api.top(page = page.coerceAtLeast(1)) }
        val items = response.data.mapNotNull { JikanMapper.toProviderMedia(it) }
        return Paged(
            items = items,
            page = response.pagination?.currentPage ?: page,
            hasMore = response.pagination?.hasNextPage == true,
        )
    }

    override suspend fun upcoming(from: LocalDate, to: LocalDate): List<ProviderMedia> {
        val response = rateLimitedCall(limiter, dispatchers) { api.upcoming() }
        return response.data.mapNotNull { JikanMapper.toProviderMedia(it) }
            .filter { it.releaseDate != null && !it.releaseDate.isBefore(from) && !it.releaseDate.isAfter(to) }
    }

    override suspend fun details(externalId: String): ProviderMediaDetails {
        val id = externalId.toIntOrNull() ?: throw ProviderException.Parse("invalid MAL id")
        val full = rateLimitedCall(limiter, dispatchers) { api.details(id) }
        val anime = full.data ?: throw ProviderException.Http(404)
        val media = JikanMapper.toProviderMedia(anime) ?: throw ProviderException.Parse("unmappable anime")
        return ProviderMediaDetails(
            media = media,
            seasons = JikanMapper.toSeasonBundle(allEpisodes(id)),
            related = emptyList(),
        )
    }

    /** All listed episodes (bounded); later pages are best-effort. */
    private suspend fun allEpisodes(id: Int): List<JikanEpisode> {
        val episodes = mutableListOf<JikanEpisode>()
        var page = 1
        while (page <= 3) {
            val result = rateLimitedCall(limiter, dispatchers) { api.episodes(id, page) }
            if (result.data.isEmpty()) break
            episodes += result.data
            if (result.pagination?.hasNextPage != true) break
            page += 1
        }
        return episodes
    }

    /**
     * Episode-number -> title map for a MAL id. Used to fill the missing episode
     * titles of AniList imports (AniList never publishes episode titles).
     * Failures are the caller's concern; this method throws like any provider.
     */
    suspend fun episodeTitles(malId: Int): Map<Int, String> {
        if (malId <= 0) return emptyMap()
        return JikanMapper.episodeTitleMap(allEpisodes(malId))
    }

    /** MyAnimeList community score (0..10) for a MAL id, when published. */
    suspend fun malScore(malId: Int): Double? {
        if (malId <= 0) return null
        val response = rateLimitedCall(limiter, dispatchers) { api.details(malId) }
        return response.data?.score?.takeIf { it > 0 }
    }

    override suspend fun related(externalId: String): List<ProviderRelation> = emptyList()

    override suspend fun seasonal(year: Int, season: String, page: Int): Paged<ProviderMedia> {
        val response = rateLimitedCall(limiter, dispatchers) {
            api.seasonal(year = year, season = season.lowercase(), page = page.coerceAtLeast(1))
        }
        return Paged(
            items = response.data.mapNotNull { JikanMapper.toProviderMedia(it) },
            page = response.pagination?.currentPage ?: page,
            hasMore = response.pagination?.hasNextPage == true,
        )
    }

    override suspend fun recommendations(externalId: String): List<ProviderMedia> {
        val id = externalId.toIntOrNull() ?: return emptyList()
        val response = rateLimitedCall(limiter, dispatchers) { api.recommendations(id) }
        return response.data.mapNotNull { it.entry?.let(JikanMapper::toProviderMedia) }
            .distinctBy { it.externalId }
    }
}
