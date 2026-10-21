package com.seenly.app.data.repository

import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.core.time.IsoDates.toIso
import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.data.local.mapper.toDomain
import com.seenly.app.data.local.relation.UpcomingEpisodeRow
import com.seenly.app.data.local.relation.WatchedEventStatRow
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.CalendarData
import com.seenly.app.domain.model.ContinueWatchingItem
import com.seenly.app.domain.model.EventType
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.UpcomingEpisode
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.repository.StatisticsRepository
import com.seenly.app.domain.statistics.CompletedViewing
import com.seenly.app.domain.statistics.GoalProgressCalculator
import com.seenly.app.domain.statistics.StatisticsEngine
import com.seenly.app.domain.statistics.StatisticsInput
import com.seenly.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Read-only projections over the local database. Nothing here performs network
 * calls; statistics and feeds remain available in airplane mode.
 */
class StatisticsRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : StatisticsRepository {

    private val mediaDao = db.mediaDao()
    private val eventDao = db.watchEventDao()
    private val episodeDao = db.episodeDao()
    private val progressDao = db.progressDao()
    private val goalDao = db.goalDao()

    private data class BaseData(
        val library: List<LibraryEntry>,
        val genres: Map<Long, List<String>>,
        val viewings: List<CompletedViewing>,
    ) {
        fun toInput(): StatisticsInput = StatisticsInput(
            library = library,
            viewings = viewings,
            genresByMedia = genres,
        )
    }

    private val baseData: Flow<BaseData> = combine(
        mediaDao.observeLibraryRows(),
        mediaDao.observeMediaGenres(),
        eventDao.observeCompletedEventsForStats(),
    ) { rows, genreRows, eventRows ->
        val genres = genreRows.groupBy({ it.mediaId }, { it.name })
        BaseData(
            library = rows.map { row -> row.toDomain(genres[row.media.id].orEmpty()) },
            genres = genres,
            viewings = eventRows.mapNotNull { it.toCompletedViewing() }
                .filterNot { viewing ->
                    // A numeric series rewatch is a title-level marker, not a
                    // specific episode viewing; keep it out of runtime/viewing
                    // statistics while still counting it in the rewatch total.
                    viewing.mediaType.hasEpisodes &&
                        viewing.episodeId == null &&
                        viewing.eventType == EventType.REWATCH
                },
        )
    }

    override fun observeStatistics(): Flow<StatisticsSnapshot> =
        baseData
            .map { data -> StatisticsEngine.calculate(data.toInput(), clock.today()) }
            .flowOn(dispatchers.io)

    override fun observeGoalProgress(): Flow<List<GoalProgress>> =
        combine(baseData, goalDao.observeActive()) { data, goals ->
            GoalProgressCalculator.calculate(
                goals = goals.map { it.toDomain() },
                input = data.toInput(),
                today = clock.today(),
            )
        }.flowOn(dispatchers.io)

    override fun observeRecentActivity(limit: Int): Flow<List<ActivityItem>> =
        eventDao.observeRecentActivity(limit)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeContinueWatching(limit: Int): Flow<List<ContinueWatchingItem>> =
        combine(
            mediaDao.observeLibraryRows(),
            mediaDao.observeMediaGenres(),
        ) { rows, genreRows ->
            val genres = genreRows.groupBy({ it.mediaId }, { it.name })
            rows.map { row -> row.toDomain(genres[row.media.id].orEmpty()) }
                .filter { it.status == WatchStatus.WATCHING }
                .sortedWith(
                    compareByDescending<LibraryEntry> { it.lastActivityAt ?: 0L }
                        .thenByDescending { it.media.addedAt },
                )
                .take(limit)
        }
            .map { entries ->
                entries.map { entry ->
                    ContinueWatchingItem(
                        entry = entry,
                        nextEpisode = episodeDao.firstUnwatched(entry.media.id)?.toDomain(),
                    )
                }
            }
            .flowOn(dispatchers.io)

    override fun observeUpcomingEpisodes(days: Int, limit: Int): Flow<List<UpcomingEpisode>> {
        val from = clock.today()
        val to = from.plusDays(days.coerceAtLeast(0).toLong())
        return episodeDao.observeUpcomingEpisodes(from.toIso(), to.toIso(), limit)
            .map { rows -> rows.mapNotNull { it.toUpcomingEpisode() } }
            .flowOn(dispatchers.io)
    }

    override fun observeCalendar(): Flow<CalendarData> {
        val today = clock.today()
        val from = today.toIso()
        val horizon = today.plusDays(400).toIso()
        return combine(
            eventDao.observeAllActivity(),
            episodeDao.observeUpcomingEpisodes(from, horizon, 1000),
            mediaDao.observeUpcomingReleases(from, 500),
            mediaDao.observeMediaGenres(),
        ) { activityRows, upcomingRows, releaseRows, genreRows ->
            val genres = genreRows.groupBy({ it.mediaId }, { it.name })
            val activity = activityRows.mapNotNull { row ->
                row.localDate.toLocalDateOrNull()?.let { date -> date to row.toDomain() }
            }.groupBy({ it.first }, { it.second })
            val upcoming = upcomingRows.mapNotNull { it.toUpcomingEpisode() }
                .groupBy { it.airDate }
            val releases = releaseRows.mapNotNull { row ->
                row.media.releaseDate.toLocalDateOrNull()?.let { date ->
                    date to row.toDomain(genres[row.media.id].orEmpty())
                }
            }.groupBy({ it.first }, { it.second })
            CalendarData(
                activityByDate = activity,
                upcomingEpisodesByDate = upcoming,
                upcomingReleasesByDate = releases,
            )
        }.flowOn(dispatchers.io)
    }

    private fun WatchedEventStatRow.toCompletedViewing(): CompletedViewing? {
        val date = localDate.toLocalDateOrNull() ?: return null
        val type = MediaType.fromStorage(mediaType) ?: return null
        val event = EventType.entries.firstOrNull { it.name == eventType } ?: return null
        return CompletedViewing(
            mediaId = mediaId,
            mediaType = type,
            episodeId = episodeId,
            eventType = event,
            localDate = date,
            minutes = minutes,
        )
    }

    private fun UpcomingEpisodeRow.toUpcomingEpisode(): UpcomingEpisode? {
        val date = episode.airDate.toLocalDateOrNull() ?: return null
        val type = MediaType.fromStorage(mediaType) ?: return null
        return UpcomingEpisode(
            mediaId = episode.mediaId,
            mediaTitle = mediaTitle,
            posterUrl = mediaPosterUrl,
            mediaType = type,
            episodeId = episode.id,
            seasonId = episode.seasonId,
            seasonNumber = seasonNumber,
            episodeNumber = episode.number,
            episodeTitle = episode.title,
            airDate = date,
        )
    }
}
