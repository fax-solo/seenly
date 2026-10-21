package com.seenly.app.domain.repository

import android.net.Uri
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.AppSettings
import com.seenly.app.domain.model.AppLanguage
import com.seenly.app.domain.model.CalendarData
import com.seenly.app.domain.model.CompletionNotice
import com.seenly.app.domain.model.ContinueWatchingItem
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.Goal
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.ManualMediaInput
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.NotificationSettings
import com.seenly.app.domain.model.ProgressSummary
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.model.Season
import com.seenly.app.domain.model.ThemeMode
import com.seenly.app.domain.model.UpcomingEpisode
import com.seenly.app.domain.model.WatchEvent
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.domain.providers.Paged
import com.seenly.app.domain.providers.ProviderInfo
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.domain.providers.ProviderMediaDetails
import com.seenly.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate

interface LibraryRepository {
    fun observeLibrary(): Flow<List<LibraryEntry>>
    fun observeGenres(): Flow<List<String>>
    fun observeMedia(mediaId: Long): Flow<Media?>
    fun observeSeasons(mediaId: Long): Flow<List<Season>>
    fun observeEpisodes(mediaId: Long): Flow<List<Episode>>
    suspend fun getMedia(mediaId: Long): Media?
    suspend fun addManual(input: ManualMediaInput): Outcome<Long>
    suspend fun updatePersonalInfo(
        mediaId: Long,
        rating: Double?,
        review: String?,
        isSpoiler: Boolean,
        notes: String?,
    ): Outcome<Unit>

    suspend fun updateLocalInfo(
        mediaId: Long,
        title: String,
        type: MediaType,
        totalSeasons: Int?,
        totalEpisodes: Int?,
        runtimeMinutes: Int?,
    ): Outcome<Unit>

    suspend fun deleteMedia(mediaId: Long)
}

interface WatchRepository {
    fun observeProgressSummary(mediaId: Long): Flow<ProgressSummary>
    fun observeHistory(mediaId: Long): Flow<List<WatchEvent>>
    suspend fun markEpisodeWatched(episodeId: Long, date: LocalDate? = null): Outcome<Unit>
    suspend fun markEpisodeUnwatched(episodeId: Long): Outcome<Unit>
    suspend fun markSeasonWatched(seasonId: Long): Outcome<Unit>
    suspend fun markPreviousEpisodesWatched(episodeId: Long): Outcome<Unit>
    suspend fun markMovieWatched(mediaId: Long, date: LocalDate? = null): Outcome<Unit>
    suspend fun recordRewatch(mediaId: Long, episodeId: Long? = null): Outcome<Unit>

    /**
     * Replaces the title-level rewatch history with exactly [count] rewatches.
     * Used by the numeric rewatch dialog, so the displayed count matches what
     * the user entered instead of accumulating hidden duplicates.
     */
    suspend fun setRewatchCount(mediaId: Long, count: Int): Outcome<Unit>
    suspend fun setStatus(mediaId: Long, status: WatchStatus): Outcome<Unit>
    suspend fun setCurrentEpisode(mediaId: Long, episodeId: Long): Outcome<Unit>
    suspend fun resetProgress(mediaId: Long): Outcome<Unit>
    suspend fun updateEventDate(eventId: Long, date: LocalDate): Outcome<Unit>
    suspend fun deleteEvent(eventId: Long): Outcome<Unit>

    /**
     * Marks every title in [mediaIds] as completed: movies get a first-watch
     * event, series get every episode watched. Returns how many titles became
     * completed (already-completed titles are skipped).
     */
    suspend fun markAllCompleted(mediaIds: List<Long>): Outcome<Int>

    /** Emits whenever a title transitions into COMPLETED (one notice per title). */
    val completions: SharedFlow<CompletionNotice>
}

interface ListsRepository {
    fun observeLists(): Flow<List<MediaList>>
    fun observeListItems(listId: Long): Flow<List<LibraryEntry>>
    fun observeMembership(mediaId: Long): Flow<Set<Long>>
    suspend fun ensureBuiltinLists()
    suspend fun createList(name: String, description: String?): Outcome<Long>
    suspend fun updateList(listId: Long, name: String, description: String?): Outcome<Unit>
    suspend fun deleteList(listId: Long): Outcome<Unit>
    suspend fun addToList(listId: Long, mediaId: Long): Outcome<Unit>
    suspend fun removeFromList(listId: Long, mediaId: Long)
    suspend fun reorder(listId: Long, orderedMediaIds: List<Long>)
    suspend fun toggleFavorite(mediaId: Long): Outcome<Boolean>
    fun observeFavorites(): Flow<List<LibraryEntry>>
}

interface GoalsRepository {
    fun observeGoals(): Flow<List<Goal>>
    suspend fun saveGoal(type: GoalType, metric: GoalMetric, target: Int): Outcome<Unit>
    suspend fun deleteGoal(goalId: Long)
}

interface StatisticsRepository {
    fun observeStatistics(): Flow<StatisticsSnapshot>
    fun observeGoalProgress(): Flow<List<GoalProgress>>
    fun observeRecentActivity(limit: Int = 30): Flow<List<ActivityItem>>
    fun observeContinueWatching(limit: Int = 12): Flow<List<ContinueWatchingItem>>
    fun observeUpcomingEpisodes(days: Int = 90, limit: Int = 200): Flow<List<UpcomingEpisode>>
    fun observeCalendar(): Flow<CalendarData>
}

interface CatalogRepository {
    fun providerInfos(): List<ProviderInfo>
    suspend fun search(
        query: String,
        provider: ProviderId?,
        type: MediaType?,
        page: Int = 1,
    ): Outcome<Paged<ProviderMedia>>

    suspend fun trending(provider: ProviderId, type: MediaType?, page: Int = 1): Outcome<Paged<ProviderMedia>>

    /** Merged trending across the providers routed for the category. */
    suspend fun trendingAll(type: MediaType?, page: Int = 1): Outcome<Paged<ProviderMedia>>
    suspend fun upcoming(provider: ProviderId, from: LocalDate, to: LocalDate): Outcome<List<ProviderMedia>>
    suspend fun seasonal(provider: ProviderId, year: Int, season: String, page: Int = 1): Outcome<Paged<ProviderMedia>>
    suspend fun details(provider: ProviderId, externalId: String): Outcome<ProviderMediaDetails>
    suspend fun importTitle(
        provider: ProviderId,
        externalId: String,
        linkToMediaId: Long? = null,
    ): Outcome<Long>
    suspend fun refresh(mediaId: Long): Outcome<Unit>
    fun observeLibraryMatch(provider: ProviderId, externalId: String): Flow<Long?>
    suspend fun providerRecommendations(mediaId: Long): Outcome<List<ProviderMedia>>

    /**
     * Taste-driven candidates for the recommendation deck, excluding titles
     * already in the library. Falls back to current trending when the taste
     * profile is empty.
     */
    suspend fun recommendationCandidates(limit: Int = 30): Outcome<List<ProviderMedia>>

    /**
     * Finds an existing local title with the same exact (case-insensitive) title,
     * type and release year. Used only to *offer* linking to the user; merges are
     * never automatic.
     */
    suspend fun findPotentialLocalMatch(media: ProviderMedia): Long?

    /** Opportunistic background refresh of airing data for currently watched titles. */
    suspend fun refreshAiringForWatchedTitles(limit: Int = 5)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setDefaultLibrarySort(sort: LibrarySort)
    suspend fun setHomeSections(sections: List<HomeSectionKey>)
    suspend fun setNotifications(settings: NotificationSettings)

    /** Candidate keys ("PROVIDER/externalId") swiped away in the recommendation deck. */
    suspend fun setDeckDismissedCandidates(keys: Set<String>)
}

enum class ImportMode { MERGE, REPLACE }

data class BackupSummary(
    val mediaCount: Int,
    val episodeCount: Int,
    val eventCount: Int,
    val listCount: Int,
    val goalCount: Int,
    val schemaVersion: Int = 1,
    val exportedAt: Long? = null,
)

interface BackupRepository {
    suspend fun export(uri: Uri): Outcome<BackupSummary>
    suspend fun import(uri: Uri, mode: ImportMode): Outcome<BackupSummary>
}
