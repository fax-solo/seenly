package com.seenly.app.domain.model

import java.time.LocalDate

data class Media(
    val id: Long,
    val type: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val alternateTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val runtimeMinutes: Int? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val studio: String? = null,
    val tags: List<String> = emptyList(),
    val directorNames: List<String> = emptyList(),
    val castNames: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val providerScore: Double? = null,
    /** External ratings, best-effort. Null means "not known", never a guess. */
    val imdbId: String? = null,
    val imdbRating: Double? = null,
    val rottenTomatoesRating: Int? = null,
    val malRating: Double? = null,
    val ratingsUpdatedAt: Long? = null,
    val metadataSource: ProviderId? = null,
    val metadataUpdatedAt: Long? = null,
    val addedAt: Long = 0,
    val lastModifiedAt: Long = 0,
    // Personal data — never overwritten by metadata refreshes.
    val personalRating: Double? = null,
    val review: String? = null,
    val reviewIsSpoiler: Boolean = false,
    val reviewCreatedAt: Long? = null,
    val reviewUpdatedAt: Long? = null,
    val personalNotes: String? = null,
)

data class Season(
    val id: Long,
    val mediaId: Long,
    val number: Int?,
    val title: String? = null,
    val synopsis: String? = null,
    val episodeCount: Int? = null,
    val airDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val posterUrl: String? = null,
    val sortOrder: Int = 0,
) {
    val isSpecials: Boolean get() = number == null || number == 0
}

data class Episode(
    val id: Long,
    val mediaId: Long,
    val seasonId: Long,
    val number: Int?,
    val absoluteNumber: Int? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val runtimeMinutes: Int? = null,
    val airDate: LocalDate? = null,
    val stillUrl: String? = null,
    /** Derived from completed first-watch events. */
    val watched: Boolean = false,
    val watchedAt: Long? = null,
    /** True when at least one rewatch event exists. */
    val rewatched: Boolean = false,
)

data class WatchEvent(
    val id: Long,
    val mediaId: Long,
    val episodeId: Long?,
    val eventType: EventType,
    val startedAt: Long,
    val completedAt: Long?,
    val localDate: LocalDate,
    val notes: String? = null,
)

data class Progress(
    val mediaId: Long,
    val status: WatchStatus,
    val startDate: LocalDate? = null,
    val completionDate: LocalDate? = null,
    val pausedAt: Long? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
) {
    companion object {
        fun initial(mediaId: Long, now: Long): Progress =
            Progress(mediaId = mediaId, status = WatchStatus.PLAN_TO_WATCH, updatedAt = now)
    }
}

/** Aggregated, derived progress for a title. */
data class ProgressSummary(
    val mediaId: Long,
    val status: WatchStatus,
    val episodesWatched: Int,
    val episodesTotal: Int,
    val nextEpisode: Episode?,
    val rewatchCount: Int,
    val viewingsCount: Int,
    val startDate: LocalDate?,
    val completionDate: LocalDate?,
    val pausedAt: Long?,
    val lastActivityAt: Long?,
) {
    val fraction: Float
        get() = if (episodesTotal <= 0) 0f else (episodesWatched.toFloat() / episodesTotal).coerceIn(0f, 1f)

    val hasKnownTotals: Boolean get() = episodesTotal > 0
}

/** One row in the library grid. */
data class LibraryEntry(
    val media: Media,
    val status: WatchStatus,
    val episodesWatched: Int = 0,
    val episodesTotal: Int = 0,
    val lastActivityAt: Long? = null,
    val rewatchCount: Int = 0,
) {
    val progressFraction: Float?
        get() = if (episodesTotal > 0) episodesWatched.toFloat() / episodesTotal else null
}

data class MediaList(
    val id: Long,
    val name: String,
    val description: String? = null,
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    val smartFilter: SmartList? = null,
    val coverUrl: String? = null,
    val itemCount: Int = 0,
)

data class ListMembership(
    val listId: Long,
    val mediaId: Long,
)

data class Goal(
    val id: Long,
    val type: GoalType,
    val metric: GoalMetric,
    val target: Int,
    val active: Boolean = true,
    val createdAt: Long = 0,
)

data class GoalProgress(
    val goal: Goal,
    val current: Int,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
) {
    val fraction: Float
        get() = if (goal.target <= 0) 0f else (current.toFloat() / goal.target).coerceIn(0f, 1f)
}

/** Item shown in "recent activity" feeds. */
data class ActivityItem(
    val eventId: Long,
    val mediaId: Long,
    val mediaTitle: String,
    val posterUrl: String?,
    val mediaType: MediaType,
    val episodeId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val watchedAtMillis: Long,
    val localDate: LocalDate,
    val eventType: EventType,
)

/** User input for creating a title that may not exist in any provider. */
data class ManualMediaInput(
    val title: String,
    val type: MediaType,
    val originalTitle: String? = null,
    val releaseDate: LocalDate? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val runtimeMinutes: Int? = null,
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
)

/** Filters applied to the local library. */
data class LibraryFilter(
    val query: String = "",
    val types: Set<MediaType> = emptySet(),
    val statuses: Set<WatchStatus> = emptySet(),
    val genres: Set<String> = emptySet(),
    val minimumRating: Double? = null,
    val releaseYearFrom: Int? = null,
    val releaseYearTo: Int? = null,
    val listId: Long? = null,
) {
    val isActive: Boolean
        get() = types.isNotEmpty() || statuses.isNotEmpty() || genres.isNotEmpty() ||
            minimumRating != null || releaseYearFrom != null || releaseYearTo != null || listId != null
}
