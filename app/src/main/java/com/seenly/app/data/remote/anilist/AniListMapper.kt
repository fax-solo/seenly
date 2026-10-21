package com.seenly.app.data.remote.anilist

import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.providers.ProviderRelation
import com.seenly.app.domain.providers.ProviderSeason
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * AniList -> domain mapping. Pure functions; thoroughly unit tested with fixtures.
 */
object AniListMapper {

    private val htmlTag = Regex("<[^>]+>")
    private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    fun stripHtml(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val cleaned = lineBreak.replace(input, "\n")
            .let { htmlTag.replace(it, "") }
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
        return cleaned.trim().ifBlank { null }
    }

    fun fuzzyDate(date: AniListFuzzyDate?): LocalDate? {
        if (date == null) return null
        val year = date.year ?: return null
        val month = date.month ?: 1
        val day = date.day ?: 1
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    fun typeOf(format: String?): MediaType = when (format?.uppercase()) {
        "MOVIE" -> MediaType.ANIME_MOVIE
        else -> MediaType.ANIME_SERIES
    }

    fun toProviderMedia(media: AniListMedia, zone: ZoneId = ZoneId.systemDefault()): ProviderMedia? {
        if (media.isAdult == true) return null
        val english = media.title?.english
        val preferred = media.title?.userPreferred
        val romaji = media.title?.romaji
        val native = media.title?.native
        val title = listOfNotNull(english, preferred, romaji, native).firstOrNull() ?: return null
        val alternates = listOfNotNull(romaji, native, english, preferred)
            .filter { it != title }
            .distinct()
        return ProviderMedia(
            provider = ProviderId.ANILIST,
            externalId = media.id.toString(),
            type = typeOf(media.format),
            title = title,
            originalTitle = native ?: romaji,
            alternateTitles = alternates,
            synopsis = stripHtml(media.description),
            posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large ?: media.coverImage?.medium,
            backdropUrl = media.bannerImage,
            releaseDate = fuzzyDate(media.startDate) ?: media.seasonYear?.let { LocalDate.of(it, 1, 1) },
            endDate = fuzzyDate(media.endDate),
            runtimeMinutes = media.duration,
            totalSeasons = null,
            totalEpisodes = media.episodes,
            studio = media.studios?.nodes
                ?.firstOrNull { it.isAnimationStudio == true }?.name
                ?: media.studios?.nodes?.firstOrNull()?.name,
            genres = media.genres.distinct(),
            tags = media.tags
                .filter { it.isGeneralSpoiler != true }
                .sortedWith(compareByDescending<AniListTag> { it.rank ?: 0 })
                .take(8)
                .map { it.name }
                .distinct(),
            score = media.averageScore?.let { it / 10.0 },
            popularity = media.popularity?.toDouble(),
            statusText = media.status,
            nextEpisodeAirDate = media.nextAiringEpisode?.airingAt?.let { airingAt ->
                Instant.ofEpochSecond(airingAt).atZone(zone).toLocalDate()
            },
            nextEpisodeNumber = media.nextAiringEpisode?.episode,
            malId = media.idMal?.takeIf { it > 0 },
        )
    }

    fun toRelationList(media: AniListMedia, zone: ZoneId = ZoneId.systemDefault()): List<ProviderRelation> {
        val relations = media.relations?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val mapped = toProviderMedia(node, zone) ?: return@mapNotNull null
            ProviderRelation(relation = edge.relationType ?: "RELATED", media = mapped)
        }
        val recommendations = media.recommendations?.nodes.orEmpty().mapNotNull { rec ->
            val node = rec.mediaRecommendation ?: return@mapNotNull null
            val mapped = toProviderMedia(node, zone) ?: return@mapNotNull null
            ProviderRelation(relation = "RECOMMENDATION", media = mapped)
        }
        return relations + recommendations
    }

    /**
     * AniList exposes an episode count and (partial) airing schedule rather than an
     * episode list. Known air dates always win; missing dates are projected from
     * the season's exact start date at a weekly cadence so currently airing anime
     * can appear in the calendar. A season that only has a release year is left
     * undated because a January projection would be misleading.
     */
    fun toEpisodeBundle(media: AniListMedia, zone: ZoneId = ZoneId.systemDefault()): List<ProviderSeasonBundle> {
        if (typeOf(media.format) == MediaType.ANIME_MOVIE) return emptyList()
        val bundle = entryBundle(media, seasonNumber = 1, firstAbsoluteNumber = 1, zone = zone) ?: return emptyList()
        return listOf(bundle)
    }

    /**
     * Groups an ordered franchise chain (prequel -> ... -> sequel) into numbered
     * seasons of one local title, so e.g. the three TV seasons of a long-running
     * anime appear as seasons 1..3 instead of one lone season.
     */
    fun toFranchiseBundles(
        chain: List<AniListMedia>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ProviderSeasonBundle> {
        if (chain.isEmpty()) return emptyList()
        if (chain.size == 1) return toEpisodeBundle(chain.first(), zone)
        val bundles = mutableListOf<ProviderSeasonBundle>()
        var absolute = 1
        chain.forEachIndexed { index, entry ->
            val bundle = entryBundle(entry, seasonNumber = index + 1, firstAbsoluteNumber = absolute, zone = zone) ?: return@forEachIndexed
            bundles += bundle
            absolute += bundle.episodes.size
        }
        return bundles
    }

    private fun entryBundle(
        media: AniListMedia,
        seasonNumber: Int,
        firstAbsoluteNumber: Int,
        zone: ZoneId,
    ): ProviderSeasonBundle? {
        if (typeOf(media.format) == MediaType.ANIME_MOVIE) return null
        val episodeCount = media.episodes
        if (episodeCount != null && episodeCount <= 0) return null
        val airDates: Map<Int, LocalDate> = media.airingSchedule?.nodes.orEmpty()
            .mapNotNull { node ->
                val episode = node.episode ?: return@mapNotNull null
                val airingAt = node.airingAt ?: return@mapNotNull null
                episode to Instant.ofEpochSecond(airingAt).atZone(zone).toLocalDate()
            }
            .toMap()
        val start = fuzzyDate(media.startDate)
        val hasExactStart = media.startDate?.month != null && media.startDate?.day != null
        val end = fuzzyDate(media.endDate)
        // Announced seasons often have no episode count yet. Keep the season in
        // the franchise instead of dropping it, so an upcoming cour remains
        // selectable and can later be moved from Plan to Watch to Watching.
        val episodes = episodeCount?.let { count ->
            (1..count).map { number ->
                val projected = if (start != null && hasExactStart) {
                    start.plusWeeks((number - 1).toLong())
                        .takeIf { expected -> end == null || !expected.isAfter(end) }
                } else {
                    null
                }
                ProviderEpisode(
                    number = number,
                    absoluteNumber = firstAbsoluteNumber + number - 1,
                    title = null,
                    synopsis = null,
                    runtimeMinutes = media.duration,
                    airDate = airDates[number] ?: projected,
                )
            }
        }.orEmpty()
        return ProviderSeasonBundle(
            season = ProviderSeason(
                number = seasonNumber,
                title = media.title?.let { title ->
                    listOfNotNull(title.english, title.userPreferred, title.romaji).firstOrNull()
                },
                episodeCount = episodeCount,
                airDate = fuzzyDate(media.startDate),
                endDate = fuzzyDate(media.endDate),
            ),
            episodes = episodes,
        )
    }

    fun toDetails(media: AniListMedia, zone: ZoneId = ZoneId.systemDefault()): ProviderMediaDetails? {
        val mapped = toProviderMedia(media, zone) ?: return null
        return ProviderMediaDetails(
            media = mapped,
            seasons = toEpisodeBundle(media, zone),
            related = toRelationList(media, zone),
        )
    }
}
