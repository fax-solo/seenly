package com.seenly.app.data.repository

import com.seenly.app.domain.providers.ProviderEpisode
import com.seenly.app.domain.providers.ProviderSeasonBundle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Aligns anime episodes with IMDb/Cinemeta episodes by the start date of each
 * provider season.
 *
 * Absolute numbering alone breaks when AniList and IMDb disagree about an
 * episode count (recaps, split cours, or combined seasons), shifting every
 * later season by one or more episodes. Anchoring each local cour to the
 * closest IMDb air date and bounding it by the next cour keeps split cours and
 * count mismatches in the right place. Episodes without a resolvable anchor
 * fall back to absolute numbering only when nothing else can be aligned.
 */
internal object CinemetaEpisodeMapping {

    private data class RemoteEpisode(
        val episode: ProviderEpisode,
        val season: Int?,
        val index: Int,
    )

    private data class LocalEpisode(
        val absoluteNumber: Int,
        val seasonIndex: Int,
        val episodeIndex: Int,
    )

    private const val MAX_ANCHOR_DAYS = 21L

    fun map(
        localSeasons: List<ProviderSeasonBundle>,
        cinemaSeasons: List<ProviderSeasonBundle>,
    ): Map<Int, ProviderEpisode> {
        val remote = cinemaSeasons
            .filter { (it.season.number ?: 0) > 0 }
            .sortedBy { it.season.number }
            .flatMap { bundle ->
                bundle.episodes
                    .sortedBy { it.number ?: Int.MAX_VALUE }
                    .map { episode -> RemoteEpisode(episode, bundle.season.number, 0) }
            }
            .mapIndexed { index, entry -> entry.copy(index = index) }
        if (remote.isEmpty()) return emptyMap()

        var sequential = 0
        val local = localSeasons.mapIndexed { seasonIndex, bundle ->
            bundle.episodes.mapIndexed { episodeIndex, episode ->
                val absolute = episode.absoluteNumber?.takeIf { it > 0 } ?: (sequential + 1)
                sequential = maxOf(sequential, absolute)
                LocalEpisode(absolute, seasonIndex, episodeIndex)
            }
        }

        val anchors = localSeasons.map { bundle ->
            val start = bundle.season.airDate ?: return@map -1
            var bestIndex = -1
            var bestDistance = Long.MAX_VALUE
            remote.forEach { entry ->
                val airDate = entry.episode.airDate ?: return@forEach
                val distance = abs(ChronoUnit.DAYS.between(start, airDate))
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = entry.index
                }
            }
            if (bestDistance <= MAX_ANCHOR_DAYS) bestIndex else -1
        }

        val mapped = mutableMapOf<Int, ProviderEpisode>()
        var anyAnchor = false
        local.forEachIndexed { seasonIndex, episodes ->
            val anchor = anchors[seasonIndex]
            if (anchor < 0) return@forEachIndexed
            anyAnchor = true
            val seasonNumber = remote[anchor].season
            val nextAnchor = anchors.drop(seasonIndex + 1).firstOrNull { it >= 0 }
            val endExclusive = if (
                nextAnchor != null &&
                nextAnchor > anchor &&
                remote[nextAnchor].season == seasonNumber
            ) {
                nextAnchor
            } else {
                remote.indexOfLast { it.season == seasonNumber } + 1
            }
            episodes.forEachIndexed { episodeIndex, episode ->
                val remoteIndex = anchor + episodeIndex
                if (remoteIndex >= endExclusive) return@forEachIndexed
                mapped[episode.absoluteNumber] = remote[remoteIndex].episode
            }
        }

        // Only use raw absolute numbering when no season could be date-anchored.
        // Mixing the two modes can silently shift the anchored seasons.
        if (!anyAnchor) {
            local.flatten().forEach { episode ->
                remote.getOrNull(episode.absoluteNumber - 1)?.let { entry ->
                    mapped.putIfAbsent(episode.absoluteNumber, entry.episode)
                }
            }
        }
        return mapped
    }
}
