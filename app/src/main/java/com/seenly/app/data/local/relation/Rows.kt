package com.seenly.app.data.local.relation

import androidx.room.Embedded
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaListEntity

/** Library grid row: media plus derived progress aggregates. */
data class LibraryRow(
    @Embedded val media: MediaEntity,
    val status: String?,
    val episodesWatched: Int,
    val episodesTotal: Int,
    val lastActivityAt: Long?,
    val viewingsCount: Int,
    val rewatchCount: Int,
)

data class ListRow(
    @Embedded val list: MediaListEntity,
    val itemCount: Int,
)

/** Episode row with derived watched state (never stored redundantly). */
data class EpisodeRow(
    @Embedded val episode: EpisodeEntity,
    val watchedAt: Long?,
    val rewatched: Boolean,
)

/** Upcoming episode joined with its title's display info (calendar / home). */
data class UpcomingEpisodeRow(
    @Embedded val episode: EpisodeEntity,
    val seasonNumber: Int?,
    val mediaTitle: String,
    val mediaPosterUrl: String?,
    val mediaType: String,
)

data class MediaGenreName(
    val mediaId: Long,
    val name: String,
)

/** Stable ordering key for episodes across seasons (used for "previous episodes"). */
data class EpisodeKey(
    val episodeId: Long,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

data class ActivityRow(
    val eventId: Long,
    val mediaId: Long,
    val mediaTitle: String,
    val posterUrl: String?,
    val mediaType: String,
    val episodeId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val watchedAtMillis: Long,
    val localDate: String,
    val eventType: String,
)

/** Completed watch event joined with runtime info, for the statistics engine. */
data class WatchedEventStatRow(
    val mediaId: Long,
    val mediaType: String,
    val episodeId: Long?,
    val eventType: String,
    val localDate: String,
    val completedAtMillis: Long,
    /** Episode runtime when known, else the title runtime; null when unknown. */
    val minutes: Int?,
)
