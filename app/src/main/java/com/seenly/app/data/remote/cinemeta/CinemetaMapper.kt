package com.seenly.app.data.remote.cinemeta

import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.LocalDate

/**
 * Cinemeta -> domain mapping. Pure functions, unit tested with fixtures.
 *
 * The catalog endpoints are used for search/browse and return lightweight rows;
 * the `meta` endpoint adds the IMDb rating, cast and episode list.
 */
object CinemetaMapper {

    fun runtimeMinutes(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return Regex("(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun isoDateOrNull(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    /** Parses "2011–2019" / "2011-" style release info into an end year. */
    fun endYear(releaseInfo: String?): Int? {
        if (releaseInfo.isNullOrBlank()) return null
        val years = Regex("(\\d{4})").findAll(releaseInfo).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()
        return years.lastOrNull()?.takeIf { years.size > 1 }
    }

    fun typeOf(rawType: String?, genres: List<String>): MediaType {
        val isSeries = rawType.equals("series", ignoreCase = true)
        val isAnimation = genres.any { it.equals("Animation", ignoreCase = true) }
        return MediaType.resolve(isSeries = isSeries, isAnimation = isAnimation, isAnime = false)
    }

    fun externalId(rawType: String?, imdbId: String): String {
        val prefix = if (rawType.equals("series", ignoreCase = true)) "series" else "movie"
        return "$prefix/$imdbId"
    }

    fun splitExternalId(externalId: String): Pair<String, String>? {
        val parts = externalId.split("/", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }

    fun toProviderMedia(meta: CinemetaMeta): ProviderMedia? {
        if (meta.id.isBlank() && meta.imdbId.isNullOrBlank()) return null
        if (meta.name.isBlank()) return null
        val genres = (meta.genres + meta.genre).distinct()
        val imdbId = meta.imdbId ?: meta.id.takeIf { it.startsWith("tt") }
        val isSeries = meta.type.equals("series", ignoreCase = true)
        return ProviderMedia(
            provider = ProviderId.CINEMETA,
            externalId = externalId(meta.type, imdbId ?: meta.id),
            type = typeOf(meta.type, genres),
            title = meta.name,
            synopsis = meta.description,
            posterUrl = meta.poster,
            backdropUrl = meta.background,
            releaseDate = isoDateOrNull(meta.released) ?: meta.year?.take(4)?.toIntOrNull()?.let {
                LocalDate.of(it, 1, 1)
            },
            endDate = endYear(meta.releaseInfo ?: meta.year)?.let { LocalDate.of(it, 12, 31) },
            runtimeMinutes = runtimeMinutes(meta.runtime),
            totalSeasons = if (isSeries) {
                meta.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().size.takeIf { it > 0 }
            } else {
                null
            },
            totalEpisodes = if (isSeries) {
                meta.videos.count { it.number != null && (it.season ?: 0) > 0 }.takeIf { it > 0 }
            } else {
                null
            },
            studio = null,
            genres = genres,
            tags = meta.country?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            score = meta.imdbRating?.toDoubleOrNull(),
            popularity = null,
            statusText = meta.status,
            imdbId = imdbId,
            imdbRating = meta.imdbRating?.toDoubleOrNull(),
        )
    }

    fun toProviderMedia(row: CinemetaCatalogMeta): ProviderMedia? {
        if (row.name.isBlank()) return null
        val imdbId = row.imdbId ?: row.id.takeIf { it.startsWith("tt") } ?: return null
        val isSeries = row.type.equals("series", ignoreCase = true)
        val genres = (row.genres + row.genre).distinct()
        return ProviderMedia(
            provider = ProviderId.CINEMETA,
            externalId = externalId(row.type, imdbId),
            type = typeOf(row.type, genres),
            title = row.name,
            posterUrl = row.poster,
            backdropUrl = row.background,
            releaseDate = row.releaseInfo?.take(4)?.toIntOrNull()?.let { LocalDate.of(it, 1, 1) },
            endDate = endYear(row.releaseInfo)?.let { LocalDate.of(it, 12, 31) },
            genres = genres,
            score = row.imdbRating?.toDoubleOrNull(),
            imdbId = imdbId,
            imdbRating = row.imdbRating?.toDoubleOrNull(),
        ).takeIf { isSeries || row.type.equals("movie", ignoreCase = true) }
    }

    fun toSeasonBundles(meta: CinemetaMeta): List<ProviderSeasonBundle> {
        if (!meta.type.equals("series", ignoreCase = true)) return emptyList()
        val videos = meta.videos.filter { it.number != null }
        if (videos.isEmpty()) return emptyList()
        var absolute = 0
        return videos
            .groupBy { it.season ?: 0 }
            .entries
            .sortedBy { it.key }
            .map { (seasonNumber, seasonVideos) ->
                val episodes = seasonVideos
                    .sortedBy { it.number ?: Int.MAX_VALUE }
                    .map { video ->
                        absolute += 1
                        ProviderEpisode(
                            number = video.number,
                            absoluteNumber = absolute,
                            title = video.name?.takeIf { it.isNotBlank() },
                            synopsis = video.overview?.takeIf { it.isNotBlank() },
                            runtimeMinutes = null,
                            airDate = isoDateOrNull(video.firstAired),
                            stillUrl = video.thumbnail,
                        )
                    }
                ProviderSeasonBundle(
                    season = ProviderSeason(
                        number = seasonNumber.takeIf { it > 0 },
                        episodeCount = episodes.size,
                        airDate = episodes.mapNotNull { it.airDate }.minOrNull(),
                        endDate = episodes.mapNotNull { it.airDate }.maxOrNull(),
                    ),
                    episodes = episodes,
                )
            }
    }

    fun toDetails(meta: CinemetaMeta): ProviderMediaDetails? {
        val media = toProviderMedia(meta) ?: return null
        return ProviderMediaDetails(
            media = media,
            seasons = toSeasonBundles(meta),
            related = emptyList(),
            cast = meta.cast,
            directors = meta.director,
        )
    }
}
