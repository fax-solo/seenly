package com.seenly.app.domain.model

import java.time.LocalDate

/** A title currently being watched, with the next episode to resume. */
data class ContinueWatchingItem(
    val entry: LibraryEntry,
    val nextEpisode: Episode?,
)

/** An episode releasing in the future for a title in the library. */
data class UpcomingEpisode(
    val mediaId: Long,
    val mediaTitle: String,
    val posterUrl: String?,
    val mediaType: MediaType,
    val episodeId: Long,
    val seasonId: Long,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val airDate: LocalDate,
)

/** Calendar data split into past activity and future releases. */
data class CalendarData(
    val activityByDate: Map<LocalDate, List<ActivityItem>> = emptyMap(),
    val upcomingEpisodesByDate: Map<LocalDate, List<UpcomingEpisode>> = emptyMap(),
    val upcomingReleasesByDate: Map<LocalDate, List<LibraryEntry>> = emptyMap(),
)
