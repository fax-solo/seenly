package com.seenly.app.data.repository

import androidx.room.withTransaction
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.time.IsoDates.toIso
import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import com.seenly.app.data.local.mapper.toDomain
import com.seenly.app.data.local.relation.EpisodeRow
import com.seenly.app.domain.model.CompletionNotice
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.EventType
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProgressSummary
import com.seenly.app.domain.model.WatchEvent
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.repository.WatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Watch actions and derived progress.
 *
 * Invariants:
 * - Watch events are append-only; rewatches never replace first-watch rows.
 * - Duplicate taps cannot create duplicate completed first-watch events.
 * - Episodes watched / next episode / rewatch counts are all derived from events,
 *   never stored twice.
 */
class WatchRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : WatchRepository {

    private val mediaDao = db.mediaDao()
    private val seasonDao = db.seasonDao()
    private val episodeDao = db.episodeDao()
    private val progressDao = db.progressDao()
    private val eventDao = db.watchEventDao()

    private val _completions = MutableSharedFlow<CompletionNotice>(extraBufferCapacity = 16)
    override val completions: SharedFlow<CompletionNotice> = _completions.asSharedFlow()

    override fun observeProgressSummary(mediaId: Long): Flow<ProgressSummary> =
        combine(
            progressDao.observe(mediaId),
            seasonDao.observeForMedia(mediaId),
            episodeDao.observeForMedia(mediaId),
            eventDao.observeCompletedForMedia(mediaId),
        ) { progress, seasons, rows, events ->
            buildSummary(mediaId, progress, seasons, rows, events)
        }.flowOn(dispatchers.io)

    private fun buildSummary(
        mediaId: Long,
        progress: ProgressEntity?,
        seasons: List<com.seenly.app.data.local.entity.SeasonEntity>,
        rows: List<EpisodeRow>,
        events: List<WatchEventEntity>,
    ): ProgressSummary {
        val seasonOrder = seasons.mapIndexed { index, season -> season.id to index }.toMap()
        val ordered = rows.sortedWith(
            compareBy<EpisodeRow> { seasonOrder[it.episode.seasonId] ?: Int.MAX_VALUE }
                .thenBy { it.episode.number ?: Int.MAX_VALUE }
                .thenBy { it.episode.id },
        )
        val today = clock.today()
        val nextRow = ordered.firstOrNull { row ->
            val airDate = row.episode.airDate.toLocalDateOrNull()
            row.watchedAt == null && (airDate == null || !airDate.isAfter(today))
        } ?: ordered.firstOrNull { it.watchedAt == null }
        val watchedCount = ordered.count { it.watchedAt != null }
        val lastActivity = events.maxOfOrNull { it.completedAt ?: it.startedAt }
        return ProgressSummary(
            mediaId = mediaId,
            status = WatchStatus.fromStorage(progress?.status),
            episodesWatched = watchedCount,
            episodesTotal = ordered.size,
            nextEpisode = nextRow?.toDomain(),
            rewatchCount = events.count { it.eventType == EventType.REWATCH.name },
            viewingsCount = events.size,
            startDate = progress?.startDate.toLocalDateOrNull(),
            completionDate = progress?.completionDate.toLocalDateOrNull(),
            pausedAt = progress?.pausedAt,
            lastActivityAt = lastActivity,
        )
    }

    override fun observeHistory(mediaId: Long): Flow<List<WatchEvent>> =
        eventDao.observeCompletedForMedia(mediaId)
            .map { events ->
                events.map { it.toDomain() }
                    .sortedWith(compareByDescending<WatchEvent> { it.localDate }.thenByDescending { it.startedAt })
            }
            .flowOn(dispatchers.io)

    override suspend fun markEpisodeWatched(episodeId: Long, date: LocalDate?): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val episode = episodeDao.getById(episodeId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                if (eventDao.completedFirstWatchForEpisode(episodeId) != null) {
                    return@withTransaction Outcome.Success(Unit)
                }
                val watchedOn = date ?: clock.today()
                insertFirstWatch(
                    mediaId = episode.mediaId,
                    episodeId = episodeId,
                    date = watchedOn,
                )
                completed = refreshSeriesProgress(episode.mediaId, clock.nowMillis())
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun markEpisodeUnwatched(episodeId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                val episode = episodeDao.getById(episodeId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                eventDao.deleteForEpisode(episodeId)
                refreshSeriesProgress(episode.mediaId, clock.nowMillis())
                Outcome.Success(Unit)
            }
        }

    override suspend fun markSeasonWatched(seasonId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val season = seasonDao.getById(seasonId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("season_not_found"))
                val ids = episodeDao.idsForSeason(seasonId)
                if (ids.isEmpty()) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("no_episodes"))
                }
                val watched = eventDao.watchedEpisodeIds(season.mediaId).toSet()
                val watchedOn = clock.today()
                ids.filterNot { it in watched }.forEach { id ->
                    insertFirstWatch(mediaId = season.mediaId, episodeId = id, date = watchedOn)
                }
                completed = refreshSeriesProgress(season.mediaId, clock.nowMillis())
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun markPreviousEpisodesWatched(episodeId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val episode = episodeDao.getById(episodeId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                val keys = episodeDao.orderedEpisodeKeys(episode.mediaId)
                val index = keys.indexOfFirst { it.episodeId == episodeId }
                if (index < 0) return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                val previous = keys.take(index).map { it.episodeId }
                val watched = eventDao.watchedEpisodeIds(episode.mediaId).toSet()
                val watchedOn = clock.today()
                previous.filterNot { it in watched }.forEach { id ->
                    insertFirstWatch(mediaId = episode.mediaId, episodeId = id, date = watchedOn)
                }
                completed = refreshSeriesProgress(episode.mediaId, clock.nowMillis())
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun markMovieWatched(mediaId: Long, date: LocalDate?): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val media = mediaDao.getById(mediaId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                val type = MediaType.fromStorage(media.type)
                if (type == null || type.hasEpisodes) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("not_a_movie"))
                }
                val progress = progressDao.get(mediaId)
                if (WatchStatus.fromStorage(progress?.status) == WatchStatus.COMPLETED) {
                    // Idempotent: another tap must not create a second completion event.
                    return@withTransaction Outcome.Success(Unit)
                }
                val watchedOn = date ?: clock.today()
                val now = clock.nowMillis()
                val hasFirstWatch = eventDao.completedForMedia(mediaId)
                    .any { it.episodeId == null && it.eventType == EventType.FIRST_WATCH.name }
                if (!hasFirstWatch) {
                    insertFirstWatch(mediaId = mediaId, episodeId = null, date = watchedOn)
                }
                progressDao.upsert(
                    ProgressEntity(
                        mediaId = mediaId,
                        status = WatchStatus.COMPLETED.name,
                        startDate = progress?.startDate ?: watchedOn.toIso(),
                        completionDate = watchedOn.toIso(),
                        pausedAt = null,
                        notes = progress?.notes,
                        updatedAt = now,
                    ),
                )
                completed = CompletionNotice(mediaId = mediaId, title = media.title, completedAt = now)
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun recordRewatch(mediaId: Long, episodeId: Long?): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                val media = mediaDao.getById(mediaId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                val type = MediaType.fromStorage(media.type) ?: return@withTransaction Outcome.Failure(
                    AppError.Invalid("unknown_type"),
                )
                if (type.hasEpisodes) {
                    val id = episodeId ?: return@withTransaction Outcome.Failure(
                        AppError.Invalid("episode_required"),
                    )
                    val episode = episodeDao.getById(id)
                        ?: return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                    if (episode.mediaId != mediaId) {
                        return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                    }
                    insertRewatch(mediaId = mediaId, episodeId = id)
                } else {
                    insertRewatch(mediaId = mediaId, episodeId = null)
                }
                Outcome.Success(Unit)
            }
        }

    override suspend fun setRewatchCount(mediaId: Long, count: Int): Outcome<Unit> =
        withContext(dispatchers.io) {
            if (count !in 0..MAX_REWATCH_COUNT) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_rewatch_count"))
            }
            db.withTransaction {
                mediaDao.getById(mediaId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                eventDao.deleteForMediaByType(mediaId, EventType.REWATCH.name)
                repeat(count) {
                    insertRewatch(mediaId = mediaId, episodeId = null)
                }
                Outcome.Success(Unit)
            }
        }

    override suspend fun setStatus(mediaId: Long, status: WatchStatus): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val media = mediaDao.getById(mediaId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("title_not_found"))
                val type = MediaType.fromStorage(media.type)
                val progress = progressDao.get(mediaId)
                val wasCompleted = WatchStatus.fromStorage(progress?.status) == WatchStatus.COMPLETED
                val now = clock.nowMillis()
                val today = clock.today()

                if (status == WatchStatus.COMPLETED && type != null && !type.hasEpisodes) {
                    val hasFirstWatch = eventDao.completedForMedia(mediaId)
                        .any { it.episodeId == null && it.eventType == EventType.FIRST_WATCH.name }
                    if (!hasFirstWatch) {
                        insertFirstWatch(mediaId = mediaId, episodeId = null, date = today)
                    }
                }
                if (status == WatchStatus.COMPLETED && type?.hasEpisodes == true) {
                    // Choosing Completed from the status menu is an explicit
                    // whole-title action, so it must also mark the known
                    // episodes watched. Otherwise the title would show 0/N
                    // episodes and the next progress edit would silently demote
                    // it back to Watching.
                    val watched = eventDao.watchedEpisodeIds(mediaId).toSet()
                    episodeDao.orderedEpisodeKeys(mediaId)
                        .map { it.episodeId }
                        .filterNot { it in watched }
                        .forEach { episodeId ->
                            insertFirstWatch(mediaId = mediaId, episodeId = episodeId, date = today)
                        }
                }

                val startDate = when (status) {
                    WatchStatus.PLAN_TO_WATCH -> null
                    else -> progress?.startDate ?: today.toIso()
                }
                val completionDate = when (status) {
                    WatchStatus.COMPLETED -> progress?.completionDate ?: today.toIso()
                    else -> null
                }
                val pausedAt = when (status) {
                    WatchStatus.ON_HOLD, WatchStatus.DROPPED -> now
                    else -> null
                }
                progressDao.upsert(
                    ProgressEntity(
                        mediaId = mediaId,
                        status = status.name,
                        startDate = startDate,
                        completionDate = completionDate,
                        pausedAt = pausedAt,
                        notes = progress?.notes,
                        updatedAt = now,
                    ),
                )
                if (status == WatchStatus.COMPLETED && !wasCompleted) {
                    completed = CompletionNotice(mediaId = mediaId, title = media.title, completedAt = now)
                }
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun setCurrentEpisode(mediaId: Long, episodeId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            var completed: CompletionNotice? = null
            val outcome = db.withTransaction {
                val episode = episodeDao.getById(episodeId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                if (episode.mediaId != mediaId) {
                    return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                }
                val keys = episodeDao.orderedEpisodeKeys(mediaId)
                val index = keys.indexOfFirst { it.episodeId == episodeId }
                if (index < 0) return@withTransaction Outcome.Failure(AppError.Invalid("episode_not_found"))
                val previous = keys.take(index).map { it.episodeId }
                val watched = eventDao.watchedEpisodeIds(mediaId).toSet()
                val watchedOn = clock.today()
                previous.filterNot { it in watched }.forEach { id ->
                    insertFirstWatch(mediaId = mediaId, episodeId = id, date = watchedOn)
                }
                completed = refreshSeriesProgress(mediaId, clock.nowMillis(), forceWatching = true)
                Outcome.Success(Unit)
            }
            completed?.let(_completions::tryEmit)
            outcome
        }

    override suspend fun resetProgress(mediaId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                eventDao.deleteForMedia(mediaId)
                progressDao.delete(mediaId)
                Outcome.Success(Unit)
            }
        }

    override suspend fun updateEventDate(eventId: Long, date: LocalDate): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                val event = eventDao.getById(eventId)
                    ?: return@withTransaction Outcome.Failure(AppError.Invalid("event_not_found"))
                val timestamp = timestampFor(date)
                eventDao.updateDate(
                    id = eventId,
                    startedAt = timestamp,
                    completedAt = event.completedAt?.let { timestamp },
                    localDate = date.toIso(),
                )
                val now = clock.nowMillis()
                val type = mediaDao.getById(event.mediaId)?.type?.let(MediaType::fromStorage)
                if (type?.hasEpisodes == true) {
                    refreshSeriesProgress(event.mediaId, now)
                } else {
                    refreshMovieProgressDates(event.mediaId, now)
                }
                Outcome.Success(Unit)
            }
        }

    override suspend fun deleteEvent(eventId: Long): Outcome<Unit> =
        withContext(dispatchers.io) {
            db.withTransaction {
                val event = eventDao.getById(eventId)
                    ?: return@withTransaction Outcome.Success(Unit)
                eventDao.deleteById(eventId)
                val now = clock.nowMillis()
                val type = mediaDao.getById(event.mediaId)?.type?.let(MediaType::fromStorage)
                if (type?.hasEpisodes == true) {
                    refreshSeriesProgress(event.mediaId, now)
                } else {
                    refreshMovieProgressDates(event.mediaId, now)
                }
                Outcome.Success(Unit)
            }
        }

    override suspend fun markAllCompleted(mediaIds: List<Long>): Outcome<Int> =
        withContext(dispatchers.io) {
            if (mediaIds.isEmpty()) return@withContext Outcome.Success(0)
            val notices = mutableListOf<CompletionNotice>()
            val completedCount = db.withTransaction {
                var newlyCompleted = 0
                val today = clock.today()
                val now = clock.nowMillis()
                mediaIds.distinct().forEach { mediaId ->
                    val media = mediaDao.getById(mediaId) ?: return@forEach
                    val type = MediaType.fromStorage(media.type) ?: return@forEach
                    val progress = progressDao.get(mediaId)
                    if (WatchStatus.fromStorage(progress?.status) == WatchStatus.COMPLETED) return@forEach

                    val notice: CompletionNotice?
                    if (!type.hasEpisodes) {
                        val hasFirstWatch = eventDao.completedForMedia(mediaId)
                            .any { it.episodeId == null && it.eventType == EventType.FIRST_WATCH.name }
                        if (!hasFirstWatch) {
                            insertFirstWatch(mediaId = mediaId, episodeId = null, date = today)
                        }
                        progressDao.upsert(
                            ProgressEntity(
                                mediaId = mediaId,
                                status = WatchStatus.COMPLETED.name,
                                startDate = progress?.startDate ?: today.toIso(),
                                completionDate = today.toIso(),
                                pausedAt = null,
                                notes = progress?.notes,
                                updatedAt = now,
                            ),
                        )
                        notice = CompletionNotice(mediaId = mediaId, title = media.title, completedAt = now)
                    } else {
                        val episodeIds = episodeDao.orderedEpisodeKeys(mediaId).map { it.episodeId }
                        val watched = eventDao.watchedEpisodeIds(mediaId).toSet()
                        episodeIds.filterNot { it in watched }.forEach { id ->
                            insertFirstWatch(mediaId = mediaId, episodeId = id, date = today)
                        }
                        val derived = refreshSeriesProgress(mediaId, now)
                        notice = derived ?: forceComplete(mediaId, media.title, today, now)
                    }
                    if (notice != null) {
                        notices += notice
                        newlyCompleted += 1
                    }
                }
                newlyCompleted
            }
            notices.forEach(_completions::tryEmit)
            Outcome.Success(completedCount)
        }

    /**
     * Explicit completion fallback for the bulk action: every listed episode is
     * watched but the declared total may be missing or larger, so the user's
     * explicit request wins. Dates are preserved from the derived progress.
     */
    private suspend fun forceComplete(
        mediaId: Long,
        title: String,
        today: LocalDate,
        now: Long,
    ): CompletionNotice? {
        val progress = progressDao.get(mediaId)
        if (progress != null && WatchStatus.fromStorage(progress.status) == WatchStatus.COMPLETED) return null
        progressDao.upsert(
            ProgressEntity(
                mediaId = mediaId,
                status = WatchStatus.COMPLETED.name,
                startDate = progress?.startDate ?: today.toIso(),
                completionDate = progress?.completionDate ?: today.toIso(),
                pausedAt = null,
                notes = progress?.notes,
                updatedAt = now,
            ),
        )
        return CompletionNotice(mediaId = mediaId, title = title, completedAt = now)
    }

    // --- Helpers ---

    private suspend fun insertFirstWatch(mediaId: Long, episodeId: Long?, date: LocalDate) {
        val timestamp = timestampFor(date)
        eventDao.insert(
            WatchEventEntity(
                mediaId = mediaId,
                episodeId = episodeId,
                eventType = EventType.FIRST_WATCH.name,
                startedAt = timestamp,
                completedAt = timestamp,
                localDate = date.toIso(),
            ),
        )
    }

    private suspend fun insertRewatch(mediaId: Long, episodeId: Long?) {
        val now = clock.nowMillis()
        eventDao.insert(
            WatchEventEntity(
                mediaId = mediaId,
                episodeId = episodeId,
                eventType = EventType.REWATCH.name,
                startedAt = now,
                completedAt = now,
                localDate = clock.today().toIso(),
            ),
        )
    }

    private fun timestampFor(date: LocalDate): Long =
        if (date == clock.today()) {
            clock.nowMillis()
        } else {
            date.atTime(12, 0).atZone(clock.zone()).toInstant().toEpochMilli()
        }

    private suspend fun refreshSeriesProgress(
        mediaId: Long,
        now: Long,
        forceWatching: Boolean = false,
    ): CompletionNotice? {
        val media = mediaDao.getById(mediaId) ?: return null
        val type = MediaType.fromStorage(media.type) ?: return null
        if (!type.hasEpisodes) return null
        val progress = progressDao.get(mediaId)
        val current = progress?.let { WatchStatus.fromStorage(it.status) } ?: WatchStatus.PLAN_TO_WATCH
        val watched = eventDao.watchedEpisodeIds(mediaId).toSet()
        val declaredTotal = media.totalEpisodes?.takeIf { it > 0 }
        // Some providers omit the title-level total but do know each season's
        // episode count. Fall back to the imported rows only when both are
        // missing, so a partially listed season cannot complete the whole title.
        val seasonTotal = seasonDao.listForMedia(mediaId)
            .sumOf { it.episodeCount ?: 0 }
            .takeIf { it > 0 }
        val knownTotal = episodeDao.orderedEpisodeKeys(mediaId).size.takeIf { it > 0 }
        val total = declaredTotal ?: seasonTotal ?: knownTotal
        val complete = total != null && watched.size >= total
        val status = when {
            complete -> WatchStatus.COMPLETED
            watched.isNotEmpty() || forceWatching -> WatchStatus.WATCHING
            current == WatchStatus.COMPLETED -> WatchStatus.WATCHING
            else -> current
        }
        if (progress == null && status == WatchStatus.PLAN_TO_WATCH) return null

        val firstWatchDates = eventDao.completedForMedia(mediaId)
            .filter { it.eventType == EventType.FIRST_WATCH.name }
            .mapNotNull { it.localDate.toLocalDateOrNull() }
        val startDate = firstWatchDates.minOrNull()?.toIso() ?: progress?.startDate
        val completionDate = if (status == WatchStatus.COMPLETED) {
            firstWatchDates.maxOrNull()?.toIso() ?: progress?.completionDate ?: clock.today().toIso()
        } else {
            null
        }
        val pausedAt = when (status) {
            WatchStatus.ON_HOLD, WatchStatus.DROPPED -> progress?.pausedAt ?: now
            else -> null
        }
        if (progress != null &&
            progress.status == status.name &&
            progress.startDate == startDate &&
            progress.completionDate == completionDate &&
            progress.pausedAt == pausedAt
        ) {
            return null
        }
        progressDao.upsert(
            ProgressEntity(
                mediaId = mediaId,
                status = status.name,
                startDate = startDate,
                completionDate = completionDate,
                pausedAt = pausedAt,
                notes = progress?.notes,
                updatedAt = now,
            ),
        )
        return if (status == WatchStatus.COMPLETED && current != WatchStatus.COMPLETED) {
            CompletionNotice(mediaId = mediaId, title = media.title, completedAt = now)
        } else {
            null
        }
    }

    private companion object {
        const val MAX_REWATCH_COUNT = 100
    }

    private suspend fun refreshMovieProgressDates(mediaId: Long, now: Long) {
        val progress = progressDao.get(mediaId) ?: return
        val dates = eventDao.completedForMedia(mediaId)
            .mapNotNull { it.localDate.toLocalDateOrNull() }
        val startDate = dates.minOrNull()?.toIso() ?: progress.startDate
        val completionDate = if (progress.status == WatchStatus.COMPLETED.name) {
            dates.maxOrNull()?.toIso() ?: progress.completionDate
        } else {
            progress.completionDate
        }
        progressDao.upsert(
            progress.copy(
                startDate = startDate,
                completionDate = completionDate,
                updatedAt = now,
            ),
        )
    }
}
