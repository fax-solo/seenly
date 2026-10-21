package com.seenly.app.data.remote.tvmaze

import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.LocalDate

/** TVmaze -> domain mapping. Pure functions, unit tested with fixtures. */
object TvmazeMapper {

    private val htmlTag = Regex("<[^>]+>")

    fun stripHtml(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return htmlTag.replace(input, "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")
            .trim()
            .ifBlank { null }
    }

    fun isoDateOrNull(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    fun typeOf(genres: List<String>): MediaType {
        val isAnime = genres.any { it.equals("Anime", ignoreCase = true) }
        val isAnimation = isAnime || genres.any { it.equals("Animation", ignoreCase = true) }
        return MediaType.resolve(isSeries = true, isAnimation = isAnimation, isAnime = isAnime)
    }

    fun toProviderMedia(show: TvmazeShow): ProviderMedia? {
        if (show.id <= 0 || show.name.isBlank()) return null
        val genres = show.genres.distinct()
        return ProviderMedia(
            provider = ProviderId.TVMAZE,
            externalId = show.id.toString(),
            type = typeOf(genres),
            title = show.name,
            synopsis = stripHtml(show.summary),
            posterUrl = show.image?.original ?: show.image?.medium,
            releaseDate = isoDateOrNull(show.premiered),
            endDate = isoDateOrNull(show.ended),
            runtimeMinutes = show.averageRuntime ?: show.runtime,
            totalSeasons = show.embedded?.seasons?.count { (it.number ?: 0) > 0 }?.takeIf { it > 0 },
            totalEpisodes = show.embedded?.episodes?.count { (it.season ?: 0) > 0 }?.takeIf { it > 0 },
            studio = show.network?.name ?: show.webChannel?.name,
            genres = genres,
            tags = listOfNotNull(show.type, show.language).filter { it.isNotBlank() },
            score = show.rating?.average,
            popularity = show.weight?.toDouble(),
            statusText = show.status,
            imdbId = show.externals?.imdb?.takeIf { it.startsWith("tt") },
        )
    }

    fun toSeasonBundles(show: TvmazeShow): List<ProviderSeasonBundle> {
        val episodes = show.embedded?.episodes.orEmpty().filter { it.number != null }
        if (episodes.isEmpty()) return emptyList()
        val seasonsById = show.embedded?.seasons.orEmpty().associateBy { it.number }
        var absolute = 0
        return episodes
            .groupBy { it.season ?: 0 }
            .entries
            .sortedBy { it.key }
            .map { (seasonNumber, seasonEpisodes) ->
                val meta = seasonsById[seasonNumber]
                val mapped = seasonEpisodes
                    .sortedBy { it.number ?: Int.MAX_VALUE }
                    .map { episode ->
                        absolute += 1
                        ProviderEpisode(
                            number = episode.number,
                            absoluteNumber = absolute,
                            title = episode.name?.takeIf { it.isNotBlank() },
                            synopsis = stripHtml(episode.summary),
                            runtimeMinutes = episode.runtime,
                            airDate = isoDateOrNull(episode.airdate),
                            stillUrl = episode.image?.original ?: episode.image?.medium,
                        )
                    }
                ProviderSeasonBundle(
                    season = ProviderSeason(
                        number = seasonNumber.takeIf { it > 0 },
                        title = meta?.name?.takeIf { it.isNotBlank() },
                        episodeCount = meta?.episodeOrder ?: mapped.size,
                        airDate = isoDateOrNull(meta?.premiereDate) ?: mapped.mapNotNull { it.airDate }.minOrNull(),
                        endDate = isoDateOrNull(meta?.endDate) ?: mapped.mapNotNull { it.airDate }.maxOrNull(),
                    ),
                    episodes = mapped,
                )
            }
    }

    fun toDetails(show: TvmazeShow): ProviderMediaDetails? {
        val media = toProviderMedia(show) ?: return null
        return ProviderMediaDetails(media = media, seasons = toSeasonBundles(show))
    }
}
