package com.seenly.app.data.repository

import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.time.IsoDates.toIso
import com.seenly.app.core.time.IsoDates.toIsoOrNull
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.GenreEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaGenreCrossRef
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.ProviderIdEntity
import com.seenly.app.data.local.entity.SeasonEntity
import androidx.room.withTransaction
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.providers.ProviderMediaDetails

/**
 * Shared metadata persistence. Duplicate detection is provider-id based; title
 * matching alone is never used to merge unrelated titles.
 *
 * Refreshes never touch personal fields. When the user has edited local fields
 * ([MediaEntity.locallyEdited]) the editable core fields are left alone.
 */
internal object ProviderImport {

    suspend fun applyGenres(db: SeenlyDatabase, mediaId: Long, names: List<String>) {
        val mediaDao = db.mediaDao()
        mediaDao.clearGenres(mediaId)
        names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .forEach { name ->
                mediaDao.insertGenre(GenreEntity(name = name))
                val genreId = mediaDao.genreIdByName(name) ?: return@forEach
                mediaDao.linkGenre(MediaGenreCrossRef(mediaId = mediaId, genreId = genreId))
            }
    }

    suspend fun persistDetails(
        db: SeenlyDatabase,
        details: ProviderMediaDetails,
        now: Long,
        linkToMediaId: Long? = null,
    ): Long = db.withTransaction {
        val mediaDao = db.mediaDao()
        val seasonDao = db.seasonDao()
        val episodeDao = db.episodeDao()
        val progressDao = db.progressDao()
        val eventDao = db.watchEventDao()
        val mapped = details.media

        val link = mediaDao.findProviderId(mapped.provider.name, mapped.externalId)
        var existing: MediaEntity? = link?.let { mediaDao.getById(it.mediaId) }
        // A franchise may already be in the library under another cour. Recognize
        // it through any of the secondary identities before creating a duplicate.
        if (existing == null && linkToMediaId == null) {
            existing = details.additionalLinks.firstNotNullOfOrNull { extra ->
                mediaDao.findProviderId(extra.provider.name, extra.externalId)
                    ?.let { secondary -> mediaDao.getById(secondary.mediaId) }
            }
        }
        if (existing == null && linkToMediaId != null) {
            existing = mediaDao.getById(linkToMediaId)
            if (existing != null) {
                mediaDao.insertProviderId(
                    ProviderIdEntity(
                        mediaId = existing.id,
                        provider = mapped.provider.name,
                        externalId = mapped.externalId,
                        linkedAt = now,
                    ),
                )
            }
        }

        val seasonEpisodesCount = details.seasons.sumOf { it.episodes.size }
        // Franchise imports (anime) list every cour as one season: the episode
        // sum is the only correct total. A provider-declared total wins only for
        // single-entry imports where no episodes were listed.
        val resolvedTotalEpisodes = seasonEpisodesCount.takeIf { it > 0 }
            ?: mapped.totalEpisodes
            ?: existing?.totalEpisodes
        val numberedSeasons = details.seasons.count { it.season.number != null }
        val resolvedTotalSeasons = mapped.totalSeasons
            ?: numberedSeasons.takeIf { it > 0 }
            ?: existing?.totalSeasons

        val existingId = existing?.id
        val previousSeasons = existingId?.let { seasonDao.listForMedia(it) }.orEmpty()
        // Older anime imports stored only the selected cour as season 1. When a
        // refresh expands that title into a franchise, move its watch history to
        // the season the user originally opened instead of overwriting season 1.
        val legacyTargetSeason = details.selectedSeasonNumber?.takeIf {
            existingId != null &&
                previousSeasons.size == 1 &&
                details.seasons.size > previousSeasons.size
        }
        val legacyWatchedKeys = if (legacyTargetSeason != null && existingId != null) {
            val watchedIds = eventDao.watchedEpisodeIds(existingId).toSet()
            if (watchedIds.isEmpty()) {
                emptyList()
            } else {
                episodeDao.orderedEpisodeKeys(existingId)
                    .filter { key -> key.episodeId in watchedIds }
            }
        } else {
            emptyList()
        }

        val mediaId: Long
        if (existing == null) {
            mediaId = mediaDao.insert(
                MediaEntity(
                    type = mapped.type.name,
                    title = mapped.title,
                    originalTitle = mapped.originalTitle,
                    alternateTitles = mapped.alternateTitles,
                    synopsis = mapped.synopsis,
                    posterUrl = mapped.posterUrl,
                    backdropUrl = mapped.backdropUrl,
                    releaseDate = mapped.releaseDate?.toIso(),
                    endDate = mapped.endDate?.toIso(),
                    runtimeMinutes = mapped.runtimeMinutes,
                    totalSeasons = resolvedTotalSeasons,
                    totalEpisodes = resolvedTotalEpisodes,
                    studio = mapped.studio,
                    tags = mapped.tags,
                    directorNames = details.directors,
                    castNames = details.cast,
                    providerScore = mapped.score,
                    imdbId = mapped.imdbId,
                    imdbRating = mapped.imdbRating,
                    rottenTomatoesRating = mapped.rottenTomatoesRating,
                    malRating = mapped.malRating,
                    ratingsUpdatedAt = if (mapped.hasExternalRating) now else null,
                    metadataSource = mapped.provider.name,
                    metadataUpdatedAt = now,
                    addedAt = now,
                    lastModifiedAt = now,
                    locallyEdited = false,
                ),
            )
            progressDao.upsert(
                ProgressEntity(
                    mediaId = mediaId,
                    status = WatchStatus.PLAN_TO_WATCH.name,
                    updatedAt = now,
                ),
            )
        } else {
            mediaId = existing.id
            val keepLocal = existing.locallyEdited
            val updated = existing.copy(
                type = if (keepLocal) existing.type else mapped.type.name,
                title = if (keepLocal) existing.title else mapped.title,
                originalTitle = if (keepLocal) {
                    existing.originalTitle
                } else {
                    mapped.originalTitle ?: existing.originalTitle
                },
                alternateTitles = mapped.alternateTitles.ifEmpty { existing.alternateTitles },
                synopsis = mapped.synopsis ?: existing.synopsis,
                posterUrl = mapped.posterUrl ?: existing.posterUrl,
                backdropUrl = mapped.backdropUrl ?: existing.backdropUrl,
                releaseDate = if (keepLocal) existing.releaseDate else (mapped.releaseDate?.toIso() ?: existing.releaseDate),
                endDate = mapped.endDate?.toIso() ?: existing.endDate,
                runtimeMinutes = if (keepLocal) existing.runtimeMinutes else (mapped.runtimeMinutes ?: existing.runtimeMinutes),
                totalSeasons = if (keepLocal) existing.totalSeasons else resolvedTotalSeasons,
                totalEpisodes = if (keepLocal) existing.totalEpisodes else resolvedTotalEpisodes,
                studio = mapped.studio ?: existing.studio,
                tags = mapped.tags.ifEmpty { existing.tags },
                directorNames = details.directors.ifEmpty { existing.directorNames },
                castNames = details.cast.ifEmpty { existing.castNames },
                providerScore = mapped.score ?: existing.providerScore,
                imdbId = mapped.imdbId ?: existing.imdbId,
                imdbRating = mapped.imdbRating ?: existing.imdbRating,
                rottenTomatoesRating = mapped.rottenTomatoesRating ?: existing.rottenTomatoesRating,
                malRating = mapped.malRating ?: existing.malRating,
                ratingsUpdatedAt = if (mapped.hasExternalRating) now else existing.ratingsUpdatedAt,
                metadataSource = mapped.provider.name,
                metadataUpdatedAt = now,
                lastModifiedAt = now,
            )
            mediaDao.update(updated)
        }

        // Ensure the provider link exists for the fresh import path as well.
        mediaDao.insertProviderId(
            ProviderIdEntity(
                mediaId = mediaId,
                provider = mapped.provider.name,
                externalId = mapped.externalId,
                linkedAt = now,
            ),
        )

        // Secondary identities (e.g. every cour of an anime franchise) so that
        // searches and re-imports recognize the title instead of duplicating it.
        details.additionalLinks
            .distinctBy { it.provider.name to it.externalId }
            .filter { it.provider != mapped.provider || it.externalId != mapped.externalId }
            .forEach { extra ->
                mediaDao.insertProviderId(
                    ProviderIdEntity(
                        mediaId = mediaId,
                        provider = extra.provider.name,
                        externalId = extra.externalId,
                        linkedAt = now,
                    ),
                )
            }

        if (mapped.genres.isNotEmpty()) {
            applyGenres(db, mediaId, mapped.genres)
        }
        if (details.seasons.isNotEmpty()) {
            val keptSeasonIds = mutableListOf<Long>()
            val keptEpisodeIds = mutableListOf<Long>()

            details.seasons.forEachIndexed { index, bundle ->
                val number = bundle.season.number
                // Unnumbered "specials" seasons are resolved through the dedicated
                // lookup: NULLs are distinct in the unique index, so INSERT IGNORE
                // alone would duplicate them on every refresh.
                val existingSeason = if (number != null) {
                    seasonDao.findByNumber(mediaId, number)
                } else {
                    seasonDao.findSpecials(mediaId)
                }
                val seasonId: Long
                if (existingSeason != null) {
                    seasonId = existingSeason.id
                    seasonDao.update(
                        existingSeason.copy(
                            title = bundle.season.title ?: existingSeason.title,
                            synopsis = bundle.season.synopsis ?: existingSeason.synopsis,
                            episodeCount = bundle.season.episodeCount ?: existingSeason.episodeCount,
                            airDate = bundle.season.airDate?.toIso() ?: existingSeason.airDate,
                            endDate = bundle.season.endDate?.toIso() ?: existingSeason.endDate,
                            posterUrl = bundle.season.posterUrl ?: existingSeason.posterUrl,
                            metadataSource = mapped.provider.name,
                        ),
                    )
                } else {
                    val inserted = seasonDao.insert(
                        SeasonEntity(
                            mediaId = mediaId,
                            number = number,
                            title = bundle.season.title,
                            synopsis = bundle.season.synopsis,
                            episodeCount = bundle.season.episodeCount
                                ?: bundle.episodes.size.takeIf { it > 0 },
                            airDate = bundle.season.airDate?.toIso(),
                            endDate = bundle.season.endDate?.toIso(),
                            posterUrl = bundle.season.posterUrl,
                            metadataSource = mapped.provider.name,
                            sortOrder = number ?: index,
                        ),
                    )
                    seasonId = if (inserted > 0) {
                        inserted
                    } else {
                        // Lost a race with another import; resolve the existing row.
                        val resolved = if (number != null) {
                            seasonDao.findByNumber(mediaId, number)
                        } else {
                            seasonDao.findSpecials(mediaId)
                        }
                        resolved?.id ?: 0L
                    }
                }
                if (seasonId <= 0) return@forEachIndexed
                keptSeasonIds += seasonId

                bundle.episodes.forEach { episode ->
                    val number = episode.number ?: return@forEach
                    val existingEpisode = episodeDao.findBySeasonAndNumber(seasonId, number)
                    if (existingEpisode == null) {
                        val inserted = episodeDao.insert(
                            EpisodeEntity(
                                mediaId = mediaId,
                                seasonId = seasonId,
                                number = number,
                                absoluteNumber = episode.absoluteNumber,
                                title = episode.title?.takeIf { it.isNotBlank() },
                                synopsis = episode.synopsis,
                                runtimeMinutes = episode.runtimeMinutes,
                                airDate = episode.airDate?.toIso(),
                                stillUrl = episode.stillUrl,
                                metadataSource = mapped.provider.name,
                            ),
                        )
                        if (inserted > 0) keptEpisodeIds += inserted
                    } else {
                        keptEpisodeIds += existingEpisode.id
                        episodeDao.update(
                            existingEpisode.copy(
                                number = number,
                                absoluteNumber = episode.absoluteNumber ?: existingEpisode.absoluteNumber,
                                title = episode.title?.takeIf { it.isNotBlank() }
                                    ?: existingEpisode.title,
                                synopsis = episode.synopsis ?: existingEpisode.synopsis,
                                runtimeMinutes = episode.runtimeMinutes ?: existingEpisode.runtimeMinutes,
                                airDate = episode.airDate?.toIso() ?: existingEpisode.airDate,
                                stillUrl = episode.stillUrl ?: existingEpisode.stillUrl,
                                metadataSource = mapped.provider.name,
                            ),
                        )
                    }
                }
            }

            // Move legacy single-season watch history onto the matching season
            // before pruning, so the old watched rows can be cleaned up safely.
            if (legacyTargetSeason != null && legacyWatchedKeys.isNotEmpty()) {
                val targetSeasonId = seasonDao.findIdByNumber(mediaId, legacyTargetSeason)
                if (targetSeasonId != null) {
                    legacyWatchedKeys.forEach { key ->
                        val episodeNumber = key.episodeNumber ?: return@forEach
                        val target = episodeDao.findBySeasonAndNumber(
                            targetSeasonId,
                            episodeNumber,
                        ) ?: return@forEach
                        if (target.id != key.episodeId) {
                            eventDao.remapEpisode(
                                oldEpisodeId = key.episodeId,
                                newEpisodeId = target.id,
                            )
                        }
                    }
                }
            }

            // Prune only when the provider returned a complete episode listing.
            val completeListing = details.seasons.all { it.episodes.isNotEmpty() }
            if (completeListing && keptEpisodeIds.isNotEmpty()) {
                episodeDao.deleteEpisodesNotIn(mediaId, keptEpisodeIds)
                seasonDao.deleteSeasonsNotIn(mediaId, keptSeasonIds)
            }
        }

        if (legacyTargetSeason != null) {
            val progress = progressDao.get(mediaId)
            val watchedAfter = eventDao.watchedEpisodeIds(mediaId).toSet()
            val incomplete = resolvedTotalEpisodes == null ||
                watchedAfter.size < resolvedTotalEpisodes
            if (progress?.status == WatchStatus.COMPLETED.name &&
                watchedAfter.isNotEmpty() &&
                incomplete
            ) {
                // The old one-cour title was complete; the expanded franchise is
                // not. Reflect that in the library instead of showing 14/62 as
                // "completed".
                progressDao.upsert(
                    progress.copy(
                        status = WatchStatus.WATCHING.name,
                        completionDate = null,
                        pausedAt = null,
                        updatedAt = now,
                    ),
                )
            }
        }

        mediaId
    }
}
